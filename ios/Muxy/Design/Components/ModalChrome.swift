import SwiftUI

struct DoneToolbarItem: ToolbarContent {
    var isDisabled = false
    let action: () -> Void

    var body: some ToolbarContent {
        ToolbarItem(placement: .confirmationAction) {
            Button("Done", systemImage: "checkmark", action: action)
                .disabled(isDisabled)
        }
    }
}

extension View {
    func pageModalPresentation() -> some View {
        presentationDetents([.large])
            .presentationSizing(.page)
    }

    func formModalPresentation() -> some View {
        presentationSizing(.form)
    }
}
