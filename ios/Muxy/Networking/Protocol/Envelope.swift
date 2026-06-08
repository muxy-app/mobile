import Foundation

nonisolated struct TaggedPayload<Value: Codable & Sendable>: Codable, Sendable {
    let type: String
    let value: Value
}

nonisolated struct RawTagged: Decodable, Sendable {
    let type: String
    let valueData: Data

    private enum CodingKeys: String, CodingKey {
        case type
        case value
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        type = try container.decode(String.self, forKey: .type)
        let value = try container.decodeIfPresent(JSONValue.self, forKey: .value) ?? .null
        valueData = try JSONEncoder().encode(value)
    }

    func decode<T: Decodable>(_ type: T.Type) throws -> T {
        try JSONDecoder().decode(T.self, from: valueData)
    }
}

nonisolated struct RequestEnvelope<Params: Codable & Sendable>: Encodable, Sendable {
    let id: String
    let method: String
    let params: TaggedPayload<Params>?

    private enum RootKey: String, CodingKey {
        case type
        case payload
    }

    private enum PayloadKey: String, CodingKey {
        case id
        case method
        case params
    }

    func encode(to encoder: Encoder) throws {
        var root = encoder.container(keyedBy: RootKey.self)
        try root.encode("request", forKey: .type)

        var payload = root.nestedContainer(keyedBy: PayloadKey.self, forKey: .payload)
        try payload.encode(id, forKey: .id)
        try payload.encode(method, forKey: .method)

        guard let params else {
            try payload.encodeNil(forKey: .params)
            return
        }
        try payload.encode(params, forKey: .params)
    }
}

nonisolated struct ResponseEnvelope: Decodable, Sendable {
    let id: String
    let result: RawTagged?
    let error: ProtocolErrorBody?
}

nonisolated struct EventEnvelope: Decodable, Sendable {
    let event: String
    let data: RawTagged?
}

nonisolated enum FrameError: Error, Equatable, Sendable {
    case unknownType(String)
}

nonisolated enum IncomingFrame: Sendable {
    case response(ResponseEnvelope)
    case event(EventEnvelope)

    init(json: Data) throws {
        let decoder = JSONDecoder()
        let probe = try decoder.decode(FrameProbe.self, from: json)

        switch probe.type {
        case "response":
            self = .response(try decoder.decode(ResponseFrame.self, from: json).payload)
        case "event":
            self = .event(try decoder.decode(EventFrame.self, from: json).payload)
        default:
            throw FrameError.unknownType(probe.type)
        }
    }
}

private struct FrameProbe: Decodable {
    let type: String
}

private struct ResponseFrame: Decodable {
    let payload: ResponseEnvelope
}

private struct EventFrame: Decodable {
    let payload: EventEnvelope
}
