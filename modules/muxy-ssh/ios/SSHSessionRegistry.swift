import Foundation

actor SSHSessionRegistry {
  private var sessions: [String: MuxySSHSession] = [:]

  func insert(_ session: MuxySSHSession) {
    sessions[session.id] = session
  }

  func session(id: String) -> MuxySSHSession? {
    sessions[id]
  }

  func remove(id: String) {
    sessions[id] = nil
  }

  func takeAll() -> [MuxySSHSession] {
    let existingSessions = Array(sessions.values)
    sessions.removeAll()
    return existingSessions
  }
}
