import SwiftUI

struct SettingsView: View {
    @Bindable var settings: AppSettings
    let onDone: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    toggle(
                        isOn: $settings.useNerdFont,
                        title: "Use Nerd Font",
                        caption: "Caskaydia Mono with powerline and icon glyphs."
                    )

                    toggle(
                        isOn: $settings.autoFocusTerminal,
                        title: "Auto-focus terminal",
                        caption: "Focus the terminal automatically when switching or creating tabs. May open the on-screen keyboard."
                    )
                } header: {
                    sectionHeader("Terminal")
                }

                Section {
                    toggle(
                        isOn: $settings.demoMode,
                        title: "Demo Mode",
                        caption: "Loads sample data so you can try the app without a desktop. Switching it off restores your real devices."
                    )
                } header: {
                    sectionHeader("Demo")
                }
            }
            .themedSurface()
            .tint(theme.accent)
            .navigationTitle("Settings")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done", action: onDone)
                        .foregroundStyle(theme.foreground)
                }
            }
        }
    }

    private func toggle(isOn: Binding<Bool>, title: String, caption: String) -> some View {
        Toggle(isOn: isOn) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .foregroundStyle(theme.foreground)
                Text(caption)
                    .font(.footnote)
                    .foregroundStyle(theme.secondaryForeground)
            }
        }
        .tint(theme.accent)
    }

    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .foregroundStyle(theme.secondaryForeground)
    }
}
