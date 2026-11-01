import Testing
@testable import Muxy

@MainActor
struct DesktopPairingGuideTests {
    @Test func desktopStepsFollowTheMuxy2PairingFlow() {
        #expect(DesktopPairingGuide.steps.map(\.title) == ["Open Muxy 2", "Manage devices", "Show pairing code"])
        #expect(DesktopPairingGuide.steps.map(\.body) == [
            "Open the Muxy 2 desktop app on your computer.",
            "Click the globe icon in the bottom-left corner, then choose Manage devices.",
            "Click “Show pairing code” to display the QR code."
        ])
    }

    @Test func scanningInstructionsIncludeExplicitConfirmation() {
        #expect(DesktopPairingGuide.scanInstructions == "Tap Scan QR Code to scan the code on your desktop, then tap Add to connect.")
    }
}
