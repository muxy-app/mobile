import Foundation

@MainActor
final class BonjourResolver: NSObject {
    private let service: NetService
    private let onResolved: (DiscoveredService?) -> Void
    private let name: String
    private var didFinish = false

    init(service: NetService, onResolved: @escaping (DiscoveredService?) -> Void) {
        self.service = service
        self.onResolved = onResolved
        self.name = service.name
        super.init()
        self.service.delegate = self
    }

    func resolve(timeout: TimeInterval = 5) {
        service.resolve(withTimeout: timeout)
    }

    func cancel() {
        service.stop()
    }

    private func finish(with resolved: DiscoveredService?) {
        guard !didFinish else { return }
        didFinish = true
        service.stop()
        onResolved(resolved)
    }
}

extension BonjourResolver: NetServiceDelegate {
    nonisolated func netServiceDidResolveAddress(_ sender: NetService) {
        let hostName = sender.hostName
        let port = sender.port
        let name = sender.name
        Task { @MainActor in
            self.finish(with: BonjourMapping.service(name: name, hostName: hostName, port: port))
        }
    }

    nonisolated func netService(_ sender: NetService, didNotResolve errorDict: [String: NSNumber]) {
        Task { @MainActor in
            self.finish(with: nil)
        }
    }
}
