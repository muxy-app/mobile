import Foundation

nonisolated struct ProtocolErrorBody: Codable, Equatable, Sendable {
    let code: Int
    let message: String
}

nonisolated enum ErrorCode: Int, Sendable {
    case invalidParams = 400
    case unauthorized = 401
    case forbidden = 403
    case notFound = 404
    case pairingTimeout = 408
    case internalError = 500
}

nonisolated struct ProtocolError: Error, Equatable, Sendable {
    let body: ProtocolErrorBody

    var code: ErrorCode? {
        ErrorCode(rawValue: body.code)
    }

    init(_ body: ProtocolErrorBody) {
        self.body = body
    }
}
