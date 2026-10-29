import SwiftUI

struct FilePreviewView: View {
    let viewModel: FileManagerViewModel
    @Bindable var preview: FilePreviewState
    let onReload: () -> Void

    @Environment(\.appTheme) private var theme
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    private var host: FileHost { viewModel.location.host }

    private var saveStatus: String {
        if viewModel.isBusy { return "Saving changes…" }
        if preview.isDirty { return "Unsaved changes" }
        if preview.hasExternalChanges { return host.changedStatus }
        if preview.isEditing { return "No changes yet" }
        return host.savedStatus
    }

    private var fileDetails: String {
        var details = [FilePresentation(entry: preview.entry).typeName]
        if preview.text != nil { details.append("UTF-8") }
        if let size = preview.stat?.size ?? preview.text?.size {
            details.append(ByteCountFormatter.string(fromByteCount: Int64(size), countStyle: .file))
        }
        return details.joined(separator: " · ")
    }

    var body: some View {
        VStack(spacing: 14) {
            fileHeader
            if preview.hasExternalChanges && !viewModel.hasContextChanged {
                FileNotice(
                    title: host.changedNoticeTitle,
                    message: preview.isDirty
                        ? host.draftReplacementMessage
                        : "Reload to see the latest version of this file."
                ) {
                    HStack(spacing: 20) {
                        Button("Reload", action: onReload)
                            .frame(minHeight: 44)
                        if preview.isEditing {
                            Button("Keep editing") { preview.hasExternalChanges = false }
                                .frame(minHeight: 44)
                        }
                    }
                }
            }
            content
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .padding(.horizontal, PageMargin.horizontal)
        .padding(.vertical, CardMetrics.rowPadding)
        .background(theme.groupedBackground)
        .safeAreaInset(edge: .bottom, spacing: 0) { footer }
    }

    private var fileHeader: some View {
        HStack(alignment: .firstTextBaseline, spacing: CardMetrics.iconSpacing) {
            FileIcon(entry: preview.entry, prominent: true)
            VStack(alignment: .leading, spacing: 5) {
                Text(preview.entry.name)
                    .font(.headline)
                    .foregroundStyle(theme.foreground)
                    .fixedSize(horizontal: false, vertical: true)
                    .textSelection(.enabled)
                Text(RemoteFilePath.parent(preview.entry.path).isEmpty
                    ? viewModel.location.name
                    : RemoteFilePath.parent(preview.entry.path))
                    .font(.caption.monospaced())
                    .foregroundStyle(theme.secondaryForeground)
                    .fixedSize(horizontal: false, vertical: true)
                    .textSelection(.enabled)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, CardMetrics.rowPadding)
    }

    @ViewBuilder
    private var content: some View {
        if viewModel.isLoadingPreview && !preview.hasContent {
            ProgressView("Opening file…")
        } else if preview.kind == .image, let image = preview.image {
            Image(uiImage: image)
                .resizable()
                .scaledToFit()
                .padding(CardMetrics.rowPadding)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .cardSurface()
                .accessibilityLabel(preview.entry.name)
        } else if preview.kind == .unsupported {
            ThemedEmptyState(
                title: "Preview unavailable",
                systemImage: "doc",
                message: "This file isn’t UTF-8 text or a supported image. Use File actions to rename, move, or delete it."
            )
        } else if !preview.hasContent {
            ThemedEmptyState(
                title: "Couldn’t open this file",
                systemImage: "doc.badge.ellipsis",
                message: "Use Reload file above, or return to Files to choose another item."
            )
        } else {
            textContent
        }
    }

    private var textContent: some View {
        VStack(spacing: 0) {
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 10) {
                    formatLabel
                    Spacer(minLength: 0)
                    wrapButton
                }
                VStack(alignment: .leading, spacing: 2) {
                    formatLabel
                    wrapButton
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, CardMetrics.rowPadding)
            theme.separator.frame(height: CardMetrics.separatorHeight)
            FileTextEditor(
                text: preview.isEditing ? $preview.draft : .constant(preview.displayText),
                isEditable: preview.isEditing && !viewModel.isBusy,
                wrapsLines: preview.wrapsLines,
                accessibilityLabel: "\(preview.isEditing ? "Edit" : "Contents of") \(preview.entry.name)"
            )
            if preview.isPreviewShortened && !preview.isEditing {
                theme.separator.frame(height: CardMetrics.separatorHeight)
                FileGuidance(message: "Preview shortened. Tap Edit file to load the complete text.")
                    .padding(CardMetrics.rowPadding)
            }
        }
        .cardSurface()
    }

    private var formatLabel: some View {
        Text(fileDetails)
            .font(.caption2)
            .foregroundStyle(theme.secondaryForeground)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.vertical, 8)
    }

    private var wrapButton: some View {
        Button { preview.wrapsLines.toggle() } label: {
            Label(preview.wrapsLines ? "Wrap on" : "Wrap off", systemImage: "text.word.spacing")
                .font(.caption)
                .foregroundStyle(preview.wrapsLines ? theme.accent : theme.secondaryForeground)
                .fixedSize()
                .frame(minHeight: 44)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Word wrap")
        .accessibilityValue(preview.wrapsLines ? "On" : "Off")
        .accessibilityHint("Toggle whether long lines fit the screen.")
    }

    @ViewBuilder
    private var footer: some View {
        if preview.kind == .text, preview.text != nil {
            let layout = dynamicTypeSize.isAccessibilitySize
                ? AnyLayout(VStackLayout(alignment: .leading, spacing: 10))
                : AnyLayout(HStackLayout(spacing: 12))
            layout {
                Label {
                    Text(saveStatus)
                } icon: {
                    Image(systemName: preview.isDirty ? "pencil" : preview.hasExternalChanges ? "exclamationmark.circle" : "checkmark")
                        .foregroundStyle((preview.isDirty || preview.hasExternalChanges ? FileTint.warning : .success).color(in: theme))
                }
                    .font(.caption)
                    .foregroundStyle(theme.secondaryForeground)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityAddTraits(.updatesFrequently)
                Button {
                    if preview.isEditing {
                        Task { _ = await viewModel.save() }
                    } else {
                        viewModel.beginEditing()
                    }
                } label: {
                    Label(preview.isEditing ? "Save changes" : "Edit file", systemImage: preview.isEditing ? "checkmark" : "pencil")
                        .fixedSize(horizontal: false, vertical: true)
                }
                .buttonStyle(ThemedProminentButtonStyle())
                .disabled(!viewModel.canMutate || viewModel.isLoadingPreview || (preview.isEditing && !preview.isDirty))
            }
            .fileFooter()
        } else if preview.stat != nil {
            Text(fileDetails)
                .font(.caption)
                .foregroundStyle(theme.secondaryForeground)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fileFooter()
        }
    }
}
