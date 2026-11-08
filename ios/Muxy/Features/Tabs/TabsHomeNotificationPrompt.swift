import SwiftUI

struct TabsHomeNotificationPrompt: View {
    let onEnable: () -> Void
    let onDismiss: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        CardSection {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .top, spacing: CardMetrics.iconSpacing) {
                    CardRowSymbol("bell.badge")
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Get notified when agents need you")
                            .font(.headline)
                            .foregroundStyle(theme.foreground)
                        Text("Muxy can tell you when an agent is waiting for you or has finished, even while the app is closed.")
                            .font(.subheadline)
                            .foregroundStyle(theme.secondaryForeground)
                    }
                }
                HStack {
                    Spacer()
                    Button("Not Now", action: onDismiss)
                        .buttonStyle(.glass)
                    Button("Turn On", action: onEnable)
                        .buttonStyle(.glassProminent)
                }
            }
            .padding(CardMetrics.rowPadding)
        }
    }
}
