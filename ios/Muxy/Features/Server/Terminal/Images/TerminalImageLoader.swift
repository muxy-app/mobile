import OSLog
import PhotosUI
import SwiftUI
import UIKit

enum TerminalImageLoader {
    static let pickerSelectionLimit = 10

    static func images(from items: [PhotosPickerItem]) async -> [TerminalImage] {
        var images: [TerminalImage] = []
        for item in items {
            guard let data = await data(from: item), let image = await converted(data) else { continue }
            images.append(image)
        }
        return images
    }

    static func images(from providers: [NSItemProvider]) async -> [TerminalImage] {
        var images: [TerminalImage] = []
        for provider in providers {
            guard let data = await data(from: provider), let image = await converted(data) else { continue }
            images.append(image)
        }
        return images
    }

    @concurrent
    private nonisolated static func converted(_ data: Data) async -> TerminalImage? {
        TerminalImage(data: data)
    }

    private static func data(from item: PhotosPickerItem) async -> Data? {
        do {
            return try await item.loadTransferable(type: Data.self)
        } catch {
            Log.terminal.error("Loading a picked image failed: \(error.localizedDescription, privacy: .public)")
            return nil
        }
    }

    private static func data(from provider: NSItemProvider) async -> Data? {
        guard let type = TerminalImage.preferredType(among: provider.registeredContentTypes) else { return nil }
        return await withCheckedContinuation { continuation in
            _ = provider.loadDataRepresentation(for: type) { data, error in
                if let error {
                    Log.terminal.error("Loading an image failed: \(error.localizedDescription, privacy: .public)")
                }
                continuation.resume(returning: data)
            }
        }
    }
}
