import Foundation

nonisolated enum SSHConnectionTester {
    private static let probeCols = 80
    private static let probeRows = 24

    static func test(connection: Connection, keychain: KeychainStore) async -> Result<Void, SSHError> {
        let session = SSHSession(connection: connection, keychain: keychain)
        let states = await session.stateUpdates()
        await session.connect(cols: probeCols, rows: probeRows)

        var outcome: Result<Void, SSHError> = .failure(.unreachable)
        for await state in states {
            switch state {
            case .connected:
                outcome = .success(())
            case let .failed(error):
                outcome = .failure(error)
            case .idle, .connecting, .disconnected:
                continue
            }
            break
        }

        await session.close()
        return outcome
    }
}
