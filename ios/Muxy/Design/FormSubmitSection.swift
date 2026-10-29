import SwiftUI

struct FormSubmitSection: View {
    private let title: String
    private let isSubmitting: Bool
    private let isDisabled: Bool
    private let action: () -> Void

    @Environment(\.appTheme) private var theme

    init(_ title: String, isSubmitting: Bool = false, isDisabled: Bool = false, action: @escaping () -> Void) {
        self.title = title
        self.isSubmitting = isSubmitting
        self.isDisabled = isDisabled
        self.action = action
    }

    var body: some View {
        Section {
            Button(action: action) {
                Text(title)
                    .opacity(isSubmitting ? 0 : 1)
                    .overlay {
                        if isSubmitting {
                            ProgressView()
                                .tint(theme.onAccent)
                        }
                    }
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(ThemedProminentButtonStyle())
            .disabled(isDisabled || isSubmitting)
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.clear)
            .accessibilityValue(isSubmitting ? "In progress" : "")
        }
    }
}
