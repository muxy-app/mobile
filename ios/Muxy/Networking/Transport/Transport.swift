import Foundation

protocol Transport: Sendable {
    func connect() async throws
    func send(_ text: String) async throws
    func receive() async throws -> String
    func close() async
}
