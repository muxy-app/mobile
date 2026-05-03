import SwiftUI
import UIKit

enum TerminalFont {
    static let nerdFontName = "JetBrainsMonoNFM-Regular"
    static let nerdFontBoldName = "JetBrainsMonoNFM-Bold"
    static let defaultSize: CGFloat = 12

    static var fontSize: CGFloat {
        get { UserDefaults.standard.object(forKey: "terminalFontSize") as? CGFloat ?? defaultSize }
        set { UserDefaults.standard.set(newValue, forKey: "terminalFontSize") }
    }

    static var useNerdFont: Bool {
        get {
            if UserDefaults.standard.object(forKey: "useNerdFont") == nil { return true }
            return UserDefaults.standard.bool(forKey: "useNerdFont")
        }
        set { UserDefaults.standard.set(newValue, forKey: "useNerdFont") }
    }

    static func regular(size: CGFloat) -> UIFont {
        if useNerdFont, let font = UIFont(name: nerdFontName, size: size) { return font }
        return UIFont.monospacedSystemFont(ofSize: size, weight: .regular)
    }

    static func bold(size: CGFloat) -> UIFont {
        if useNerdFont, let font = UIFont(name: nerdFontBoldName, size: size) { return font }
        return UIFont.monospacedSystemFont(ofSize: size, weight: .bold)
    }
}

struct TerminalView: View {
    let paneID: UUID
    @Environment(ConnectionManager.self) private var connection
    @State private var autoTakenPaneID: UUID?
    @State private var takeOverInFlight = false
    @State private var reportedCols: UInt32?
    @State private var reportedRows: UInt32?

    private var themeBg: SwiftUI.Color {
        connection.deviceTheme?.bgColor ?? .black
    }

    private var isOwnedBySelf: Bool {
        connection.paneIsOwnedBySelf(paneID)
    }

    var body: some View {
        ZStack {
            SwiftTermRepresentable(paneID: paneID) { cols, rows in
                reportedCols = cols
                reportedRows = rows
            }
            .opacity(isOwnedBySelf ? 1 : 0)
            .allowsHitTesting(isOwnedBySelf)

            if !isOwnedBySelf, !takeOverInFlight {
                MobileTakeOverOverlay(
                    ownerName: ownerDisplayName,
                    theme: connection.deviceTheme,
                    takeOver: takeOverCurrentPane
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(themeBg)
            }
        }
        .background(themeBg)
        .onAppear { attemptAutoTakeOver() }
        .onDisappear {
            Task { await connection.releasePane(paneID: paneID) }
        }
        .onChange(of: paneID) { _, _ in
            takeOverInFlight = false
            autoTakenPaneID = nil
            attemptAutoTakeOver()
        }
        .onChange(of: reportedCols) { _, _ in attemptAutoTakeOver() }
        .onChange(of: reportedRows) { _, _ in attemptAutoTakeOver() }
    }

    private var ownerDisplayName: String {
        if case let .mac(name) = connection.paneOwner(for: paneID) { return name }
        if case let .remote(_, name) = connection.paneOwner(for: paneID) { return name }
        return "Mac"
    }

    private func takeOverCurrentPane() {
        guard let cols = reportedCols, let rows = reportedRows else { return }
        takeOverInFlight = true
        Task {
            await connection.takeOverPane(paneID: paneID, cols: cols, rows: rows)
            takeOverInFlight = false
        }
    }

    private func attemptAutoTakeOver() {
        guard let cols = reportedCols, let rows = reportedRows else { return }
        guard autoTakenPaneID != paneID else { return }
        autoTakenPaneID = paneID
        takeOverInFlight = true
        Task {
            await connection.takeOverPane(paneID: paneID, cols: cols, rows: rows)
            takeOverInFlight = false
        }
    }
}

struct MobileTakeOverOverlay: View {
    let ownerName: String
    let theme: ConnectionManager.DeviceTheme?
    let takeOver: () -> Void

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: "desktopcomputer")
                .font(.system(size: 28))
                .foregroundStyle(accentColor)
            Text("Controlled on \(ownerName)")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(primaryColor)
            Text("This terminal is currently being used on \(ownerName). Take over to control it from here.")
                .font(.system(size: 13))
                .foregroundStyle(secondaryColor)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 280)
            Button(action: takeOver) {
                Text("Take Over")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(buttonForeground)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 10)
                    .background(
                        RoundedRectangle(cornerRadius: 12)
                            .fill(accentColor)
                    )
            }
            .buttonStyle(.plain)
        }
        .padding(24)
        .frame(maxWidth: 340)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(panelBackground)
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .strokeBorder(accentColor.opacity(0.2), lineWidth: 1)
                )
        )
        .padding(.horizontal, 24)
    }

    private var accentColor: SwiftUI.Color { theme?.fgColor ?? .white }
    private var primaryColor: SwiftUI.Color { theme?.fgColor ?? .white }
    private var secondaryColor: SwiftUI.Color { (theme?.fgColor ?? .white).opacity(0.7) }
    private var buttonForeground: SwiftUI.Color { theme?.bgColor ?? .black }
    private var panelBackground: SwiftUI.Color { (theme?.fgColor ?? .white).opacity(0.08) }
}
