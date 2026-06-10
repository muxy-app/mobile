import SwiftUI
import Testing
@testable import Muxy

@MainActor
struct ProjectIconColorTests {
    private func components(_ color: Color) -> (Float, Float, Float) {
        let resolved = color.resolve(in: EnvironmentValues())
        return (resolved.red, resolved.green, resolved.blue)
    }

    private let fallback = Color.pink

    private func isFallback(_ color: Color) -> Bool {
        components(color) == components(fallback)
    }

    @Test func parsesHexColor() {
        let color = ProjectIconColor.color(for: "#7C3AED", fallback: fallback)
        let (red, green, blue) = components(color)

        #expect(abs(red - 0x7C / 255) < 0.01)
        #expect(abs(green - 0x3A / 255) < 0.01)
        #expect(abs(blue - 0xED / 255) < 0.01)
        #expect(!isFallback(color))
    }

    @Test func parsesShortHexColor() {
        let color = ProjectIconColor.color(for: "#FFF", fallback: fallback)
        let (red, green, blue) = components(color)

        #expect(red > 0.99)
        #expect(green > 0.99)
        #expect(blue > 0.99)
    }

    @Test func mapsNamedPaletteTokens() {
        #expect(components(ProjectIconColor.color(for: "blue", fallback: fallback)) == components(.blue))
        #expect(components(ProjectIconColor.color(for: "violet", fallback: fallback)) == components(.purple))
        #expect(components(ProjectIconColor.color(for: "RED", fallback: fallback)) == components(.red))
    }

    @Test func fallsBackForNil() {
        #expect(isFallback(ProjectIconColor.color(for: nil, fallback: fallback)))
        #expect(isFallback(ProjectIconColor.color(for: "", fallback: fallback)))
    }

    @Test func fallsBackForUnknown() {
        #expect(isFallback(ProjectIconColor.color(for: "chartreuse", fallback: fallback)))
        #expect(isFallback(ProjectIconColor.color(for: "#ZZZ", fallback: fallback)))
        #expect(isFallback(ProjectIconColor.color(for: "#12", fallback: fallback)))
    }
}
