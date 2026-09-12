import Foundation
import Observation
import OSLog

@MainActor
@Observable
final class FileManagerViewModel {
    enum Route {
        case browser
        case preview
        case move
    }

    let project: Project
    private(set) var route = Route.browser
    private(set) var currentPath = ""
    private(set) var entries: [RemoteFileEntry] = []
    private(set) var isLoadingDirectory = false
    private(set) var isLoadingPreview = false
    private(set) var isLoadingMove = false
    private(set) var isBusy = false
    private(set) var isConnected = false
    private(set) var hasContextChanged = false
    private(set) var errorMessage: String?
    private(set) var preview: FilePreviewState?
    private(set) var movePath = ""
    private(set) var moveEntries: [RemoteFileEntry] = []
    private(set) var movingPaths: [String] = []
    private(set) var selectedPaths: Set<String> = []
    private(set) var contextID = UUID()
    var selectionMode = false

    private let client: FileClient
    private var activeWorktreeID: UUID?
    private var currentDirectoryPath: String?
    private var moveDirectoryPath: String?
    private var directoryRequestID = UUID()
    private var previewRequestID = UUID()
    private var moveRequestID = UUID()
    private var isPresented = false
    private var refreshTask: Task<Void, Never>?
    private var pendingPreviewRefresh = false
    private var pendingDirectoryRefresh = false

    var isDirty: Bool { preview?.isDirty == true }
    var canMutate: Bool { isConnected && !isBusy && !hasContextChanged }
    var canMoveHere: Bool {
        canMutate && !isLoadingMove && !movingPaths.isEmpty
            && !movingPaths.contains { RemoteFilePath.contains(movePath, in: $0) }
            && !movingPaths.allSatisfy { RemoteFilePath.parent($0) == movePath }
    }

    init(project: Project, worktreeID: UUID?, channel: any FileChannel) {
        self.project = project
        activeWorktreeID = worktreeID
        client = FileClient(projectID: project.id, channel: channel)
    }

    func run() async {
        isPresented = true
        defer { stop() }
        let events = await client.channel.events()
        let states = await client.channel.stateUpdates()
        await withTaskGroup(of: Void.self) { group in
            group.addTask { await self.observeEvents(events) }
            group.addTask { await self.observeConnection(states) }
        }
    }

    func goToDirectory(_ path: String) async {
        guard canMutate else { return }
        currentPath = path
        currentDirectoryPath = nil
        entries = []
        clearSelection()
        await refreshDirectory()
    }

    func refreshDirectory(clearError: Bool = true) async {
        guard isPresented, isConnected, !hasContextChanged else { return }
        let requestID = UUID()
        directoryRequestID = requestID
        let context = contextID
        let path = currentPath
        isLoadingDirectory = true
        if clearError { errorMessage = nil }
        defer {
            if directoryRequestID == requestID { isLoadingDirectory = false }
        }
        do {
            let loaded = try await client.list(path)
            guard isCurrent(context), directoryRequestID == requestID else { return }
            currentDirectoryPath = loaded.path
            entries = loaded.entries
            selectedPaths.formIntersection(Set(loaded.entries.map(\.path)))
        } catch {
            guard isCurrent(context), directoryRequestID == requestID else { return }
            report(error)
        }
    }

    func open(_ entry: RemoteFileEntry) async {
        guard canMutate else { return }
        if selectionMode {
            toggleSelection(entry.path)
            return
        }
        if entry.isDirectory {
            await goToDirectory(entry.path)
            return
        }
        preview = FilePreviewState(entry: entry)
        route = .preview
        await reloadPreview()
    }

    func toggleSelection(_ path: String) {
        guard canMutate else { return }
        selectionMode = true
        if selectedPaths.contains(path) {
            selectedPaths.remove(path)
            return
        }
        selectedPaths.insert(path)
    }

    func clearSelection() {
        selectionMode = false
        selectedPaths = []
    }

    func reloadPreview() async {
        guard isPresented, isConnected, !hasContextChanged, let preview else { return }
        let requestID = UUID()
        previewRequestID = requestID
        let context = contextID
        let draft = preview.draft
        isLoadingPreview = true
        errorMessage = nil
        defer {
            if previewRequestID == requestID { isLoadingPreview = false }
        }
        do {
            let stat = try await client.stat(preview.entry.path)
            guard isCurrent(context), previewRequestID == requestID else { return }
            preview.stat = stat
            guard !stat.isDirectory else { throw FileManagerError.message("This item is now a folder. Return to Files to open it.") }
            guard stat.size <= FileClient.maximumFileBytes else {
                throw FileManagerError.message("Files larger than 5 MiB cannot be opened from the app.")
            }
            let isImage = RemoteFilePath.isImage(preview.entry.path)
            let content = try await client.read(preview.entry.path, encoding: isImage ? .base64 : .utf8)
            let image = isImage ? await FileImageDecoder.decode(content.content) : nil
            guard isCurrent(context), previewRequestID == requestID else { return }
            guard preview.draft == draft else {
                preview.hasExternalChanges = true
                return
            }
            if isImage, image == nil { throw FileManagerError.message("This image could not be decoded.") }
            preview.apply(content)
            preview.kind = isImage ? .image : .text
            preview.image = image
        } catch {
            guard isCurrent(context), previewRequestID == requestID else { return }
            guard preview.draft == draft else {
                preview.hasExternalChanges = true
                return
            }
            let message = FileManagerError.description(error).lowercased()
            if message.contains("utf-8") || message.contains("utf8") {
                preview.content = nil
                preview.draft = ""
                preview.displayText = ""
                preview.image = nil
                preview.isEditing = false
                preview.hasExternalChanges = false
                preview.kind = .unsupported
                return
            }
            if !preview.isDirty {
                preview.content = nil
                preview.image = nil
                preview.displayText = ""
                preview.isEditing = false
            }
            report(error)
        }
    }

    func beginEditing() {
        guard canMutate, !isLoadingPreview, let preview, preview.kind == .text, let content = preview.content else { return }
        preview.draft = content.content
        preview.isEditing = true
    }

    func save() async -> Bool {
        guard let preview, preview.isEditing, preview.kind == .text else { return false }
        let draft = preview.draft
        let worktreeID = activeWorktreeID
        let succeeded = await mutate {
            try await self.client.write(preview.entry.path, contents: draft, worktreeID: worktreeID)
        }
        guard succeeded else { return false }
        preview.apply(RemoteFileContent(path: preview.entry.path, content: draft, size: draft.utf8.count, encoding: .utf8))
        preview.stat = RemoteFileStat(name: preview.entry.name, path: preview.entry.path, isDirectory: false, size: draft.utf8.count)
        await refreshDirectory()
        return true
    }

    func create(name: String, isDirectory: Bool) async -> Bool {
        let context = contextID
        let path: String
        do {
            let name = try RemoteFilePath.validatedName(name)
            guard isDirectory || !entries.contains(where: { $0.name.caseInsensitiveCompare(name) == .orderedSame }) else {
                throw FileManagerError.message("“\(name)” already exists in this folder.")
            }
            path = RemoteFilePath.join(currentPath, name)
        } catch {
            report(error)
            return false
        }
        let worktreeID = activeWorktreeID
        var createdPath = path
        let succeeded = await mutate {
            createdPath = try await isDirectory
                ? self.client.mkdir(path, worktreeID: worktreeID)
                : self.client.create(path, worktreeID: worktreeID)
        }
        guard succeeded else { return false }
        await refreshDirectory()
        guard isCurrent(context) else { return false }
        guard !isDirectory else { return true }
        clearSelection()
        await open(RemoteFileEntry(name: RemoteFilePath.name(createdPath), path: createdPath, isDirectory: false, isIgnored: false))
        beginEditing()
        return true
    }

    func rename(_ entry: RemoteFileEntry, name: String) async -> Bool {
        let worktreeID = activeWorktreeID
        let succeeded = await mutate {
            _ = try await self.client.rename(entry.path, name: name, worktreeID: worktreeID)
        }
        guard succeeded else { return false }
        await returnToBrowser()
        return true
    }

    func delete(_ paths: [String]) async {
        let worktreeID = activeWorktreeID
        let succeeded = await mutate {
            try await self.client.delete(paths, worktreeID: worktreeID)
        }
        guard succeeded else { return }
        await returnToBrowser()
    }

    func startMove(_ paths: [String]) async {
        guard canMutate, !paths.isEmpty else { return }
        movingPaths = paths
        route = .move
        await goToMoveDirectory("")
    }

    func goToMoveDirectory(_ path: String) async {
        guard canMutate, !movingPaths.contains(where: { RemoteFilePath.contains(path, in: $0) }) else { return }
        movePath = path
        moveDirectoryPath = nil
        moveEntries = []
        await refreshMoveDirectory()
    }

    func moveHere() async {
        guard canMoveHere else { return }
        let paths = movingPaths
        let destination = movePath
        let worktreeID = activeWorktreeID
        let succeeded = await mutate {
            try await self.client.move(paths, into: destination, worktreeID: worktreeID)
        }
        guard succeeded else { return }
        await returnToBrowser()
    }

    func returnToBrowser() async {
        guard !isBusy else { return }
        previewRequestID = UUID()
        moveRequestID = UUID()
        preview = nil
        movingPaths = []
        moveEntries = []
        moveDirectoryPath = nil
        isLoadingPreview = false
        isLoadingMove = false
        clearSelection()
        route = .browser
        if hasContextChanged {
            currentPath = ""
            currentDirectoryPath = nil
            entries = []
            hasContextChanged = false
            await refreshContext()
            return
        }
        await refreshDirectory()
    }

    private func refreshMoveDirectory() async {
        guard route == .move, isConnected, !hasContextChanged else { return }
        let requestID = UUID()
        moveRequestID = requestID
        let context = contextID
        let path = movePath
        isLoadingMove = true
        errorMessage = nil
        defer {
            if moveRequestID == requestID { isLoadingMove = false }
        }
        do {
            let loaded = try await client.list(path)
            guard isCurrent(context), moveRequestID == requestID else { return }
            moveDirectoryPath = loaded.path
            moveEntries = loaded.entries.filter(\.isDirectory)
        } catch {
            guard isCurrent(context), moveRequestID == requestID else { return }
            report(error)
        }
    }

    private func mutate(_ operation: () async throws -> Void) async -> Bool {
        guard canMutate else { return false }
        let context = contextID
        isBusy = true
        errorMessage = nil
        defer { isBusy = false }
        do {
            try await operation()
            guard isCurrent(context) else { return false }
            Log.files.debug("File operation completed")
            return true
        } catch {
            guard isCurrent(context) else { return false }
            if case FileManagerError.worktreeChanged = error {
                hasContextChanged = true
            } else {
                await refreshDirectory()
                if route == .move { await refreshMoveDirectory() }
            }
            guard isCurrent(context) else { return false }
            report(error)
            return false
        }
    }

    private func observeEvents(_ events: AsyncStream<EventEnvelope>) async {
        for await event in events {
            guard !Task.isCancelled else { return }
            do {
                if event.event == EventName.workspaceChanged, let data = event.data, data.type == EventType.workspace {
                    let workspace = try data.decode(Workspace.self)
                    guard workspace.projectID == project.id else { continue }
                    if updateWorktree(workspace.worktreeID) { await refreshDirectory() }
                    continue
                }
                guard event.event == EventName.fileChanged, let data = event.data, data.type == EventType.fileChanged else { continue }
                receive(try data.decode(FileChangedEvent.self))
            } catch {
                Log.files.error("Invalid file event: \(FileManagerError.description(error), privacy: .private)")
            }
        }
    }

    private func observeConnection(_ states: AsyncStream<ConnectionState>) async {
        for await state in states {
            guard !Task.isCancelled else { return }
            let wasConnected = isConnected
            isConnected = state == .connected
            guard isConnected else {
                contextID = UUID()
                invalidateRequests()
                continue
            }
            guard !wasConnected else { continue }
            await refreshContext()
        }
    }

    private func refreshContext() async {
        let context = contextID
        do {
            let worktreeID = try await client.activeWorktreeID()
            guard isCurrent(context) else { return }
            _ = updateWorktree(worktreeID)
            guard !hasContextChanged else { return }
            await refreshDirectory()
            if isDirty {
                preview?.hasExternalChanges = true
            } else if route == .preview {
                await reloadPreview()
            }
            if route == .move { await refreshMoveDirectory() }
        } catch {
            guard isCurrent(context) else { return }
            report(error)
        }
    }

    private func updateWorktree(_ worktreeID: UUID?) -> Bool {
        guard activeWorktreeID != worktreeID else { return false }
        activeWorktreeID = worktreeID
        contextID = UUID()
        invalidateRequests()
        Log.files.debug("File workspace context changed")
        if isDirty || (isBusy && preview != nil) {
            hasContextChanged = true
            return false
        }
        preview = nil
        route = .browser
        currentPath = ""
        currentDirectoryPath = nil
        entries = []
        movingPaths = []
        moveEntries = []
        moveDirectoryPath = nil
        hasContextChanged = false
        clearSelection()
        return true
    }

    private func receive(_ change: FileChangedEvent) {
        guard !hasContextChanged, change.projectID == project.id, change.worktreeID == activeWorktreeID else { return }
        if let preview, route == .preview {
            pendingPreviewRefresh = pendingPreviewRefresh || change.truncated || change.paths.contains { path in
                RemoteFilePath.contains(preview.entry.path, in: path)
                    || preview.content.map { RemoteFilePath.contains($0.path, in: path) } == true
            }
        }
        pendingDirectoryRefresh = pendingDirectoryRefresh || change.truncated
            || (isLoadingDirectory && currentDirectoryPath == nil)
            || (route == .move && isLoadingMove && moveDirectoryPath == nil)
            || change.paths.contains {
                affectsDirectory($0, path: currentPath, resolvedPath: currentDirectoryPath)
                    || (route == .move && affectsDirectory($0, path: movePath, resolvedPath: moveDirectoryPath))
        }
        refreshTask?.cancel()
        refreshTask = Task { [weak self] in
            do {
                try await Task.sleep(for: .milliseconds(180))
                while self?.isBusy == true { try await Task.sleep(for: .milliseconds(180)) }
                guard let self, !Task.isCancelled, self.isPresented, !self.hasContextChanged else { return }
                let refreshPreview = self.pendingPreviewRefresh
                let refreshDirectory = self.pendingDirectoryRefresh
                self.pendingPreviewRefresh = false
                self.pendingDirectoryRefresh = false
                if refreshPreview {
                    if self.isDirty {
                        self.preview?.hasExternalChanges = true
                    } else {
                        await self.reloadPreview()
                    }
                }
                if refreshDirectory {
                    await self.refreshDirectory(clearError: false)
                    if self.route == .move { await self.refreshMoveDirectory() }
                }
            } catch {}
        }
    }

    private func isCurrent(_ context: UUID) -> Bool {
        isPresented && isConnected && context == contextID && !Task.isCancelled
    }

    private func affectsDirectory(_ changedPath: String, path: String, resolvedPath: String?) -> Bool {
        if RemoteFilePath.affectsDirectory(changedPath, directory: path) { return true }
        guard let resolvedPath else { return false }
        return RemoteFilePath.affectsDirectory(changedPath, directory: resolvedPath)
    }

    private func report(_ error: Error) {
        guard !(error is CancellationError) else { return }
        errorMessage = FileManagerError.description(error)
        Log.files.error("File operation failed: \(FileManagerError.description(error), privacy: .private)")
    }

    private func invalidateRequests() {
        directoryRequestID = UUID()
        previewRequestID = UUID()
        moveRequestID = UUID()
        isLoadingDirectory = false
        isLoadingPreview = false
        isLoadingMove = false
        refreshTask?.cancel()
        refreshTask = nil
        pendingDirectoryRefresh = false
        pendingPreviewRefresh = false
    }

    private func stop() {
        isPresented = false
        isConnected = false
        contextID = UUID()
        invalidateRequests()
        preview = nil
    }
}
