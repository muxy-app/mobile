enum DesktopPairingGuide {
    struct Step: Identifiable {
        let symbolName: String
        let title: String
        let body: String

        var id: String { title }
    }

    static let steps: [Step] = [
        Step(
            symbolName: "desktopcomputer",
            title: "Open Muxy 2",
            body: "Open the Muxy 2 desktop app on your computer."
        ),
        Step(
            symbolName: "globe",
            title: "Manage devices",
            body: "Click the globe icon in the bottom-left corner, then choose Manage devices."
        ),
        Step(
            symbolName: "qrcode",
            title: "Show pairing code",
            body: "Click “Show pairing code” to display the QR code."
        )
    ]

    static let scanInstructions = "Tap Scan QR Code to scan the code on your desktop, then tap Add to connect."
}
