import Foundation
import Testing
@testable import Muxy

struct EndpointTests {
    @Test func buildsWebSocketURLForHostname() throws {
        let url = try #require(Endpoint(host: "studio.tailnet.ts.net", port: 4865).webSocketURL)

        #expect(url.absoluteString == "ws://studio.tailnet.ts.net:4865")
    }

    @Test func buildsWebSocketURLForTailscaleIPv4() throws {
        let url = try #require(Endpoint(host: "100.64.0.1", port: 4865).webSocketURL)

        #expect(url.absoluteString == "ws://100.64.0.1:4865")
    }

    @Test func wrapsIPv6HostForWebSocketURL() throws {
        let url = try #require(Endpoint(host: "fd7a:115c:a1e0::1", port: 4865).webSocketURL)

        #expect(url.absoluteString == "ws://[fd7a:115c:a1e0::1]:4865")
    }

    @Test func keepsBracketedIPv6HostForWebSocketURL() throws {
        let url = try #require(Endpoint(host: "[fd7a:115c:a1e0::1]", port: 4865).webSocketURL)

        #expect(url.absoluteString == "ws://[fd7a:115c:a1e0::1]:4865")
    }
}
