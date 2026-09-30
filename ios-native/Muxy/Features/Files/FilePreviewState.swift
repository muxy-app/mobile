import ImageIO
import Observation
import UIKit

enum FilePreviewContent {
    case text(RemoteTextFile)
    case image(UIImage)
}

@MainActor
@Observable
final class FilePreviewState {
    enum Kind {
        case text
        case image
        case unsupported
    }

    static let previewCharacterLimit = 200_000

    let entry: RemoteFileEntry
    var stat: RemoteFileStat?
    var text: RemoteTextFile?
    var kind = Kind.text
    var image: UIImage?
    var displayText = ""
    var isPreviewShortened = false
    var draft = ""
    var isEditing = false
    var wrapsLines = true
    var hasExternalChanges = false

    var isDirty: Bool { isEditing && draft != text?.text }
    var hasContent: Bool { text != nil || image != nil }

    init(entry: RemoteFileEntry) {
        self.entry = entry
    }

    func show(_ content: FilePreviewContent) {
        switch content {
        case let .text(text):
            apply(text)
        case let .image(image):
            showImage(image)
        }
    }

    func apply(_ text: RemoteTextFile) {
        self.text = text
        image = nil
        kind = .text
        draft = text.text
        displayText = String(draft.prefix(Self.previewCharacterLimit))
        isPreviewShortened = draft.count > Self.previewCharacterLimit
        isEditing = false
        hasExternalChanges = false
    }

    func showUnsupported() {
        text = nil
        image = nil
        kind = .unsupported
        draft = ""
        displayText = ""
        isEditing = false
        hasExternalChanges = false
    }

    func clearContent() {
        text = nil
        image = nil
        displayText = ""
        isEditing = false
    }

    private func showImage(_ image: UIImage) {
        text = nil
        self.image = image
        kind = .image
        draft = ""
        displayText = ""
        isPreviewShortened = false
        isEditing = false
        hasExternalChanges = false
    }
}

nonisolated enum FileImageDecoder {
    static func decode(_ data: Data) async -> UIImage? {
        await Task.detached(priority: .userInitiated) {
            guard let source = CGImageSourceCreateWithData(data as CFData, nil),
                  let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                    kCGImageSourceCreateThumbnailFromImageAlways: true,
                    kCGImageSourceCreateThumbnailWithTransform: true,
                    kCGImageSourceThumbnailMaxPixelSize: 2048,
                    kCGImageSourceShouldCacheImmediately: true,
                  ] as CFDictionary) else { return nil }
            return UIImage(cgImage: image)
        }.value
    }
}
