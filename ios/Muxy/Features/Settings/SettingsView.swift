import SwiftUI

struct SettingsView: View {
    @Bindable var settings: AppSettings
    let onDone: () -> Void

    var body: some View {
        NavigationStack {
            CardList {
                CardSection(
                    "Appearance",
                    footer: "The app follows your device’s light or dark appearance. Terminal colors are independent."
                ) {
                    NavigationLink {
                        ThemePickerView(settings: settings)
                    } label: {
                        CardRow("Terminal theme", systemImage: "paintpalette", value: settings.themePalette.name)
                    }
                    .buttonStyle(.cardRow)
                }

                CardSection("Terminal") {
                    toggleRow(
                        isOn: $settings.useNerdFont,
                        title: "Use Nerd Font",
                        systemImage: "textformat",
                        caption: "Caskaydia Mono with powerline and icon glyphs."
                    )
                    toggleRow(
                        isOn: $settings.autoFocusTerminal,
                        title: "Auto-focus terminal",
                        systemImage: "keyboard",
                        caption: "Focus the terminal automatically when switching or creating tabs. May open the on-screen keyboard."
                    )
                }

                CardSection("Demo") {
                    toggleRow(
                        isOn: $settings.demoMode,
                        title: "Demo Mode",
                        systemImage: "sparkles.rectangle.stack",
                        caption: "Loads sample data so you can try the app without a desktop. Switching it off restores your real devices."
                    )
                }
            }
            .screenTitle("Settings")
            .toolbar {
                DoneToolbarItem(action: onDone)
            }
        }
    }

    private func toggleRow(isOn: Binding<Bool>, title: String, systemImage: String, caption: String) -> some View {
        CardRow(title, systemImage: systemImage, subtitle: caption) {
            Toggle(title, isOn: isOn)
                .labelsHidden()
        }
    }
}
