import ImageIO
import Observation
import UIKit

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
    var content: RemoteFileContent?
    var kind = Kind.text
    var image: UIImage?
    var displayText = ""
    var isPreviewShortened = false
    var draft = ""
    var isEditing = false
    var wrapsLines = true
    var hasExternalChanges = false

    var isDirty: Bool { isEditing && draft != content?.content }

    init(entry: RemoteFileEntry) {
        self.entry = entry
    }

    func apply(_ content: RemoteFileContent) {
        self.content = content
        draft = content.encoding == .utf8 ? content.content : ""
        displayText = String(draft.prefix(Self.previewCharacterLimit))
        isPreviewShortened = draft.count > Self.previewCharacterLimit
        isEditing = false
        hasExternalChanges = false
    }
}

nonisolated enum FileImageDecoder {
    static func decode(_ content: String) async -> UIImage? {
        await Task.detached(priority: .userInitiated) {
            guard let data = Data(base64Encoded: content),
                  let source = CGImageSourceCreateWithData(data as CFData, nil),
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
