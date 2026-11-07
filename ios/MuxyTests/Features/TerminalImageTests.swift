import Foundation
import Testing
import UIKit
import UniformTypeIdentifiers
@testable import Muxy

@MainActor
struct TerminalImageTests {
    private let picture = UIGraphicsImageRenderer(size: CGSize(width: 8, height: 8)).image { context in
        UIColor.systemTeal.setFill()
        context.fill(CGRect(x: 0, y: 0, width: 8, height: 8))
    }

    @Test func formatsAiToolsReadAreSentAsIs() throws {
        let png = try #require(picture.pngData())
        let jpeg = try #require(picture.jpegData(compressionQuality: 0.8))
        #expect(TerminalImage(data: png) == TerminalImage(pasteboardItem: [UTType.png.identifier: png]))
        #expect(TerminalImage(data: png)?.data == png)
        #expect(TerminalImage(data: png)?.fileName == "pasted-image.png")
        #expect(TerminalImage(data: jpeg)?.data == jpeg)
        #expect(TerminalImage(data: jpeg)?.fileExtension == "jpg")
    }

    @Test func otherFormatsAreConvertedToJPEG() throws {
        let heic = try #require(picture.heicData())
        let image = try #require(TerminalImage(data: heic))
        #expect(image.fileExtension == "jpg")
        #expect(image.data != heic)
        #expect(TerminalImage(image: picture)?.fileName == "pasted-image.jpg")
    }

    @Test func dataThatIsNotAnImageIsRejected() {
        #expect(TerminalImage(data: Data("not an image".utf8)) == nil)
        #expect(TerminalImage(data: Data()) == nil)
    }

    @Test func pasteboardItemsPreferAFormatSentAsIs() throws {
        let png = try #require(picture.pngData())
        let heic = try #require(picture.heicData())
        let item: [String: Any] = [
            UTType.heic.identifier: heic,
            UTType.png.identifier: png,
            UTType.plainText.identifier: "caption",
        ]
        #expect(TerminalImage(pasteboardItem: item)?.data == png)
        #expect(TerminalImage(pasteboardItem: [UTType.heic.identifier: picture])?.fileExtension == "jpg")
        #expect(TerminalImage(pasteboardItem: [UTType.plainText.identifier: "text"]) == nil)
    }

    @Test func providersLoadTheBestImageType() {
        #expect(TerminalImage.preferredType(among: [.heic, .png]) == .png)
        #expect(TerminalImage.preferredType(among: [.plainText, .heic]) == .heic)
        #expect(TerminalImage.preferredType(among: [.plainText, .url]) == nil)
    }
}
