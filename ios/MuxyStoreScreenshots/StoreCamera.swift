import UIKit
import XCTest

@MainActor
enum StoreCamera {
    static let outputKey = "STORE_SCREENSHOTS_DIR"

    static func requireOutputDirectory() throws {
        _ = try outputDirectory()
    }

    static func capture(_ name: String) throws {
        let directory = try outputDirectory()
        let image = largestScreenImage()
        guard let data = upright(image).pngData() else {
            XCTFail("Could not encode screenshot '\(name)'")
            return
        }
        try data.write(to: directory.appendingPathComponent("\(name).png"))
    }

    private static func outputDirectory() throws -> URL {
        guard let path = ProcessInfo.processInfo.environment[outputKey], !path.isEmpty else {
            throw XCTSkip("Set TEST_RUNNER_\(outputKey) to capture store screenshots")
        }
        let directory = URL(fileURLWithPath: path, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }

    private static func largestScreenImage() -> UIImage {
        let images = XCUIScreen.screens.map { $0.screenshot().image }
        return images.max { pixelArea($0) < pixelArea($1) } ?? XCUIScreen.main.screenshot().image
    }

    private static func pixelArea(_ image: UIImage) -> CGFloat {
        image.size.width * image.size.height * image.scale * image.scale
    }

    private static func upright(_ image: UIImage) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = image.scale
        format.opaque = true
        return UIGraphicsImageRenderer(size: image.size, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: image.size))
        }
    }
}
