import SwiftUI
import Testing
@testable import Muxy

@MainActor
struct ProjectIconColorTests {
    private func components(_ color: Color) -> (Float, Float, Float) {
        let resolved = color.resolve(in: EnvironmentValues())
        return (resolved.red, resolved.green, resolved.blue)
    }

    private func isFallback(_ color: Color) -> Bool {
        components(color) == components(.primary)
    }

    @Test func parsesHexColor() {
        let color = ProjectIconColor.color(for: "#7C3AED")
        let (red, green, blue) = components(color)

        #expect(abs(red - 0x7C / 255) < 0.01)
        #expect(abs(green - 0x3A / 255) < 0.01)
        #expect(abs(blue - 0xED / 255) < 0.01)
        #expect(!isFallback(color))
    }

    @Test func parsesShortHexColor() {
        let color = ProjectIconColor.color(for: "#FFF")
        let (red, green, blue) = components(color)

        #expect(red > 0.99)
        #expect(green > 0.99)
        #expect(blue > 0.99)
    }

    @Test func mapsNamedPaletteTokens() {
        #expect(components(ProjectIconColor.color(for: "blue")) == components(.blue))
        #expect(components(ProjectIconColor.color(for: "violet")) == components(.purple))
        #expect(components(ProjectIconColor.color(for: "RED")) == components(.red))
    }

    @Test func fallsBackToForegroundForNil() {
        #expect(isFallback(ProjectIconColor.color(for: nil)))
        #expect(isFallback(ProjectIconColor.color(for: "")))
    }

    @Test func fallsBackToForegroundForUnknown() {
        #expect(isFallback(ProjectIconColor.color(for: "chartreuse")))
        #expect(isFallback(ProjectIconColor.color(for: "#ZZZ")))
        #expect(isFallback(ProjectIconColor.color(for: "#12")))
    }
}
