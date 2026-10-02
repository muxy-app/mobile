import Foundation
import Network
import Observation
import OSLog

@MainActor
@Observable
final class BonjourBrowser: BonjourBrowsing {
    private(set) var services: [DiscoveredService] = []

    private var browser: NWBrowser?
    private var resolvers: [String: BonjourResolver] = [:]

    func start() {
        guard browser == nil else { return }

        let descriptor = NWBrowser.Descriptor.bonjour(type: BonjourMapping.serviceType, domain: nil)
        let parameters = NWParameters()
        parameters.includePeerToPeer = true

        let browser = NWBrowser(for: descriptor, using: parameters)
        browser.browseResultsChangedHandler = { [weak self] results, _ in
            Task { @MainActor in
                self?.handle(results: results)
            }
        }
        browser.stateUpdateHandler = { state in
            Log.discovery.debug("Bonjour browser state: \(String(describing: state), privacy: .public)")
        }
        self.browser = browser
        browser.start(queue: .main)
    }

    func stop() {
        browser?.cancel()
        browser = nil
        resolvers.values.forEach { $0.cancel() }
        resolvers.removeAll()
        services.removeAll()
    }

    private func handle(results: Set<NWBrowser.Result>) {
        let descriptors = results.compactMap(descriptor)
        let names = Set(descriptors.map(\.name))
        services.removeAll { !names.contains($0.name) }

        for descriptor in descriptors {
            guard !services.contains(where: { $0.name == descriptor.name }) else { continue }
            guard resolvers[descriptor.name] == nil else { continue }
            resolve(descriptor)
        }
    }

    private func descriptor(for result: NWBrowser.Result) -> ServiceDescriptor? {
        guard case let .service(name, type, domain, _) = result.endpoint else { return nil }
        return ServiceDescriptor(name: name, type: type, domain: domain)
    }

    private func resolve(_ descriptor: ServiceDescriptor) {
        let service = NetService(domain: descriptor.domain, type: descriptor.type, name: descriptor.name)
        let resolver = BonjourResolver(service: service) { [weak self] resolved in
            self?.completeResolution(name: descriptor.name, resolved: resolved)
        }
        resolvers[descriptor.name] = resolver
        resolver.resolve()
    }

    private func completeResolution(name: String, resolved: DiscoveredService?) {
        resolvers[name] = nil
        guard let resolved else { return }
        guard !services.contains(where: { $0.name == resolved.name }) else { return }
        services.append(resolved)
    }
}

private struct ServiceDescriptor {
    let name: String
    let type: String
    let domain: String
}
