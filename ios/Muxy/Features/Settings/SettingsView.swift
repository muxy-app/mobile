import SwiftUI

struct SettingsView: View {
    @Bindable var settings: AppSettings
    let onDone: () -> Void

    var body: some View {
        NavigationStack {
            Form {
                Section("Terminal") {
                    Toggle(isOn: $settings.useNerdFont) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Use Nerd Font")
                            Text("Caskaydia Mono with powerline and icon glyphs.")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }

                    Toggle(isOn: $settings.autoFocusTerminal) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Auto-focus terminal")
                            Text("Focus the terminal automatically when switching or creating tabs. May open the on-screen keyboard.")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                }

                Section("Demo") {
                    Toggle(isOn: $settings.demoMode) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Demo Mode")
                            Text("Loads sample data so you can try the app without a desktop. Switching it off restores your real devices.")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .navigationTitle("Settings")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done", action: onDone)
                }
            }
        }
    }
}
