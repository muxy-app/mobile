import OSLog
import SwiftTerm
import SwiftUI

struct TerminalViewContainer: UIViewRepresentable {
    let session: any TerminalIO
    let theme: TerminalTheme
    let fontSize: CGFloat
    let useNerdFont: Bool
    let autoFocusTerminal: Bool
    let onKeyboardOffsetChange: (CGFloat) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(session: session)
    }

    func makeUIView(context: Context) -> TerminalViewportHostView {
        let view = FollowAwareTerminalView(frame: .zero, font: TerminalFont.mono(size: fontSize, useNerdFont: useNerdFont))
        let host = TerminalViewportHostView(terminalView: view)
        host.onKeyboardOffsetChange = onKeyboardOffsetChange
        view.autoFocusTerminal = autoFocusTerminal
        view.onUserScroll = { [weak coordinator = context.coordinator] position in
            coordinator?.userScrolled(toPosition: position)
        }
        view.session = session
        view.configureAccessoryBar()
        view.terminalDelegate = context.coordinator
        view.allowMouseReporting = true
        apply(theme: theme, to: view)
        host.applyBackgroundColor(theme.background)
        session.attach(view)
        context.coordinator.appliedTheme = theme
        return host
    }

    func updateUIView(_ host: TerminalViewportHostView, context: Context) {
        let view = host.terminalView
        host.onKeyboardOffsetChange = onKeyboardOffsetChange
        session.terminalDidLayout()
        view.autoFocusTerminal = autoFocusTerminal
        if context.coordinator.appliedTheme != theme {
            apply(theme: theme, to: view)
            host.applyBackgroundColor(theme.background)
            context.coordinator.appliedTheme = theme
        }
    }

    private func apply(theme: TerminalTheme, to view: TerminalView) {
        view.backgroundColor = theme.background
        view.nativeForegroundColor = theme.foreground
        view.nativeBackgroundColor = theme.background
        if !theme.palette.isEmpty {
            view.installColors(theme.palette)
        }
        (view as? FollowAwareTerminalView)?.applyAccessoryTheme(background: theme.background, foreground: theme.foreground)
    }

    @MainActor
    final class Coordinator: NSObject, TerminalViewDelegate {
        private let session: any TerminalIO
        var appliedTheme: TerminalTheme?

        init(session: any TerminalIO) {
            self.session = session
        }

        func sizeChanged(source: TerminalView, newCols: Int, newRows: Int) {
            session.terminalDidResize(cols: newCols, rows: newRows)
        }

        func send(source: TerminalView, data: ArraySlice<UInt8>) {
            Log.terminal.debug("delegate.send \(data.count, privacy: .public) bytes")
            session.sendBytes(data)
        }

        func scrolled(source: TerminalView, position: Double) {
            guard source.isTracking || source.isDragging || source.isDecelerating else { return }
            userScrolled(toPosition: position)
        }

        func userScrolled(toPosition position: Double) {
            session.userScrolled(toPosition: position)
        }

        func setTerminalTitle(source: TerminalView, title: String) {
            session.setTitle(title)
        }

        func hostCurrentDirectoryUpdate(source: TerminalView, directory: String?) {}

        func requestOpenLink(source: TerminalView, link: String, params: [String: String]) {
            guard let url = URL(string: link), UIApplication.shared.canOpenURL(url) else { return }
            UIApplication.shared.open(url)
        }

        func clipboardCopy(source: TerminalView, content: Data) {
            guard let text = String(data: content, encoding: .utf8) else { return }
            UIPasteboard.general.string = text
        }

        func rangeChanged(source: TerminalView, startY: Int, endY: Int) {
            (source as? FollowAwareTerminalView)?.refreshCopyAvailability()
        }
    }
}

final class FollowAwareTerminalView: TerminalView {
    var onUserScroll: ((Double) -> Void)?
    var autoFocusTerminal = true

    weak var session: (any TerminalIO)?
    weak var viewportHost: TerminalViewportHostView?

    private let accessoryBar = TerminalAccessoryBar()
    private var keyboardHidden = false
    private var protectedContentOffset: CGPoint?
    private var contentOffsetProtectionDepth = 0
    private var isRestoringProtectedOffset = false

    private let hiddenKeyboardPlaceholder: UIView = {
        let view = UIView(frame: .zero)
        view.isHidden = true
        return view
    }()

    override var contentOffset: CGPoint {
        didSet {
            if let protectedContentOffset, !isRestoringProtectedOffset {
                isRestoringProtectedOffset = true
                contentOffset = protectedContentOffset
                isRestoringProtectedOffset = false
                return
            }
            guard isTerminalScrollInteractionActive else { return }
            onUserScroll?(normalizedScrollPosition)
        }
    }

    func preserveInteractiveOffsetDuringTerminalUpdate(_ update: () -> Void) {
        guard isTerminalScrollInteractionActive else {
            update()
            return
        }
        preserveContentOffset(update)
    }

    override func insertText(_ text: String) {
        preserveContentOffset {
            super.insertText(text)
        }
    }

    override func deleteBackward() {
        preserveContentOffset {
            super.deleteBackward()
        }
    }

    var isTerminalScrollInteractionActive: Bool {
        isTracking || isDragging || isDecelerating || viewportHost?.isInteracting == true
    }

    func cancelViewportMomentum() {
        viewportHost?.cancelMomentum()
    }

    func configureAccessoryBar() {
        inputAccessoryView = accessoryBar
        accessoryBar.onKey = { [weak self] text in self?.session?.sendText(text) }
        accessoryBar.onPaste = { [weak self] in self?.session?.paste() }
        accessoryBar.onCopy = { [weak self] in self?.session?.copySelection() }
        accessoryBar.onModifierToggle = { [weak self] armed in self?.session?.setModifierArmed(armed) }
        accessoryBar.onModifierChange = { [weak self] modifier in self?.session?.selectModifier(modifier) }
        accessoryBar.onKeyboardToggle = { [weak self] in self?.toggleKeyboard() }
        session?.onModifierStateChange = { [weak self] modifier, armed in
            self?.accessoryBar.syncActiveModifier(modifier)
            self?.accessoryBar.syncModifierArmed(armed)
        }
    }

    func refreshCopyAvailability() {
        accessoryBar.setCanCopySelection(session?.canCopySelection ?? false)
    }

    func applyAccessoryTheme(background: UIColor, foreground: UIColor) {
        accessoryBar.applyTheme(background: background, foreground: foreground)
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        guard window != nil else { return }
        guard autoFocusTerminal else { return }
        _ = becomeFirstResponder()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        session?.terminalDidLayout()
    }

    private func toggleKeyboard() {
        keyboardHidden.toggle()
        accessoryBar.setKeyboardVisible(!keyboardHidden)
        inputView = keyboardHidden ? hiddenKeyboardPlaceholder : nil
        if !isFirstResponder { _ = becomeFirstResponder() }
        reloadInputViews()
    }

    private func preserveContentOffset(_ update: () -> Void) {
        if contentOffsetProtectionDepth == 0 {
            protectedContentOffset = contentOffset
        }
        contentOffsetProtectionDepth += 1
        defer {
            contentOffsetProtectionDepth -= 1
            if contentOffsetProtectionDepth == 0 {
                restoreProtectedContentOffset()
                protectedContentOffset = nil
            }
        }
        update()
    }

    private func restoreProtectedContentOffset() {
        guard let protectedContentOffset, contentOffset != protectedContentOffset else { return }
        isRestoringProtectedOffset = true
        contentOffset = protectedContentOffset
        isRestoringProtectedOffset = false
    }

    private var normalizedScrollPosition: Double {
        let maxOffset = max(0, contentSize.height - bounds.height)
        guard maxOffset > 0 else { return 1 }
        return Double(min(max(contentOffset.y, 0), maxOffset) / maxOffset)
    }

    override func mouseModeChanged(source: Terminal) {
        super.mouseModeChanged(source: source)
        viewportHost?.refreshGesturePriorities()
    }
}
