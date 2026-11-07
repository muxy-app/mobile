import OSLog
import PhotosUI
import SwiftUI
import UIKit

enum TerminalImageLoader {
    static let pickerSelectionLimit = 10

    static func images(in pasteboard: UIPasteboard) -> [TerminalImage] {
        pasteboard.items.compactMap { TerminalImage(pasteboardItem: $0) }
    }

    static func images(from items: [PhotosPickerItem]) async -> [TerminalImage] {
        var images: [TerminalImage] = []
        for item in items {
            guard let image = await image(from: item) else { continue }
            images.append(image)
        }
        return images
    }

    static func images(from providers: [NSItemProvider]) async -> [TerminalImage] {
        var images: [TerminalImage] = []
        for provider in providers {
            guard let image = await image(from: provider) else { continue }
            images.append(image)
        }
        return images
    }

    private static func image(from item: PhotosPickerItem) async -> TerminalImage? {
        do {
            guard let data = try await item.loadTransferable(type: Data.self) else { return nil }
            return TerminalImage(data: data)
        } catch {
            Log.terminal.error("Loading a picked image failed: \(error.localizedDescription, privacy: .public)")
            return nil
        }
    }

    private static func image(from provider: NSItemProvider) async -> TerminalImage? {
        guard let type = TerminalImage.preferredType(among: provider.registeredContentTypes) else { return nil }
        let data: Data? = await withCheckedContinuation { continuation in
            _ = provider.loadDataRepresentation(for: type) { data, error in
                if let error {
                    Log.terminal.error("Loading a dropped image failed: \(error.localizedDescription, privacy: .public)")
                }
                continuation.resume(returning: data)
            }
        }
        return data.flatMap(TerminalImage.init(data:))
    }
}
