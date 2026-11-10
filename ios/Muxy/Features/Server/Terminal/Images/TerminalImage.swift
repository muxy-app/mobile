import ImageIO
import UIKit
import UniformTypeIdentifiers

nonisolated struct TerminalImage: Equatable, Sendable {
    static let maximumBytes = 100 * 1024 * 1024

    private static let jpegQuality: CGFloat = 0.9
    private static let sentAsIs: [(type: UTType, fileExtension: String)] = [
        (.png, "png"),
        (.jpeg, "jpg"),
        (.gif, "gif"),
        (.webP, "webp"),
    ]

    let data: Data
    let fileExtension: String

    var fileName: String {
        "pasted-image.\(fileExtension)"
    }

    init?(data: Data) {
        guard let type = Self.detectedType(of: data) else { return nil }
        if let fileExtension = Self.extensionSentAsIs(for: type) {
            self.init(data: data, fileExtension: fileExtension)
            return
        }
        guard let image = UIImage(data: data),
              let jpeg = image.jpegData(compressionQuality: Self.jpegQuality)
        else { return nil }
        self.init(data: jpeg, fileExtension: "jpg")
    }

    private init(data: Data, fileExtension: String) {
        self.data = data
        self.fileExtension = fileExtension
    }

    static func preferredType(among types: [UTType]) -> UTType? {
        types.first { extensionSentAsIs(for: $0) != nil } ?? types.first { $0.conforms(to: .image) }
    }

    private static func extensionSentAsIs(for type: UTType) -> String? {
        sentAsIs.first { type.conforms(to: $0.type) }?.fileExtension
    }

    private static func detectedType(of data: Data) -> UTType? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let identifier = CGImageSourceGetType(source)
        else { return nil }
        return UTType(identifier as String)
    }
}
