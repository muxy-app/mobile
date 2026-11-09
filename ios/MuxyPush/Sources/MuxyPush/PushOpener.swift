import Foundation

public enum PushOpener {
    public static func open(_ encoded: String, keys: [PushKey]) -> PushMessage? {
        guard let payload = PushPayload(encoded) else { return nil }
        guard let key = keys.first(where: { $0.keyID == payload.keyID }) else { return nil }
        guard let plaintext = payload.open(with: key) else { return nil }
        guard let message = try? JSONDecoder().decode(PushMessage.self, from: plaintext) else { return nil }
        guard message.version == PushMessage.supportedVersion, message.serverId == key.serverId else { return nil }
        return message
    }

    public static func key(for message: PushMessage, in keys: [PushKey]) -> PushKey? {
        keys.first { $0.serverId == message.serverId }
    }
}
