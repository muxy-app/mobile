import Foundation

protocol ConnectionStore: Sendable {
    func load() -> [Connection]
    func save(_ connections: [Connection])
    func upsert(_ connection: Connection)
    func delete(id: Connection.ID)
}
