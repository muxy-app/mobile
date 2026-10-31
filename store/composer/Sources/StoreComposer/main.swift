import AppKit
import Foundation

let usage = """
Usage:
  StoreComposer <iphone|iphone-duo|ipad|android-phone|android-tablet> <captures-dir> <output-dir>
  StoreComposer feature-graphic <captures-dir> <icon-png> <output-png>
  StoreComposer <app-store-header|app-store-search> <iphone-captures-dir> <ipad-captures-dir> <output-png>
"""

func capture(_ name: String, in captures: [String: NSImage], for target: String) throws -> NSImage {
    guard let image = captures[name] else { throw RenderError.failed("\(target): missing \(name) capture") }
    return image
}

func loadCaptures(from directory: URL) -> [String: NSImage] {
    let files = (try? FileManager.default.contentsOfDirectory(atPath: directory.path)) ?? []
    return files.filter { $0.hasSuffix(".png") }.reduce(into: [:]) { captures, file in
        captures[String(file.dropLast(4))] = NSImage(contentsOf: directory.appendingPathComponent(file))
    }
}

func loadImage(_ path: String) -> NSImage {
    guard let image = NSImage(contentsOfFile: path) else {
        FileHandle.standardError.write(Data("Missing image \(path)\n".utf8))
        exit(1)
    }
    return image
}

@MainActor
func run(_ arguments: [String]) throws {
    if arguments.count == 4, arguments[0] == "feature-graphic" {
        let captures = loadCaptures(from: URL(fileURLWithPath: arguments[1]))
        let graphic = FeatureGraphic(icon: loadImage(arguments[2]), screenshot: try capture("agent", in: captures, for: "feature graphic"))
        try Renderer.write(graphic, size: FeatureGraphic.canvas, to: URL(fileURLWithPath: arguments[3]))
        print("rendered \(arguments[3])")
        return
    }
    if arguments.count == 4, let kind = BannerKind(rawValue: arguments[0]) {
        let phones = loadCaptures(from: URL(fileURLWithPath: arguments[1]))
        let tablets = loadCaptures(from: URL(fileURLWithPath: arguments[2]))
        let shots = BannerShots(
            tablet: try capture("projects", in: tablets, for: kind.rawValue),
            leadingPhone: try capture("editor", in: phones, for: kind.rawValue),
            trailingPhone: try capture("git", in: phones, for: kind.rawValue)
        )
        try Renderer.write(Banner(kind: kind, shots: shots), size: kind.canvas, to: URL(fileURLWithPath: arguments[3]))
        print("rendered \(arguments[3])")
        return
    }
    guard arguments.count == 3, let target = StoreTarget(rawValue: arguments[0]) else {
        print(usage)
        exit(1)
    }
    let captures = loadCaptures(from: URL(fileURLWithPath: arguments[1]))
    let output = URL(fileURLWithPath: arguments[2])
    try FileManager.default.createDirectory(at: output, withIntermediateDirectories: true)
    for (index, slide) in Slide.all(for: target).enumerated() {
        let view = SlideView(slide: slide, index: index, target: target, images: captures)
        try Renderer.write(view, size: target.canvas, to: output.appendingPathComponent("\(slide.id).png"))
        print("rendered \(target.rawValue)/\(slide.id)")
    }
}

do {
    try MainActor.assumeIsolated { try run(Array(CommandLine.arguments.dropFirst())) }
} catch {
    FileHandle.standardError.write(Data("\(error)\n".utf8))
    exit(1)
}
