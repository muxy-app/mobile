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
