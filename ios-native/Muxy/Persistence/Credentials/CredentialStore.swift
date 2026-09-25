import Foundation
import MuxyMobile

nonisolated protocol CredentialStore: Sendable {
    func all() throws -> [ServerCredential]
    func credential(serverId: String) throws -> ServerCredential?
    func save(_ credential: ServerCredential) throws
    func delete(serverId: String) throws
}
