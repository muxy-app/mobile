import SwiftUI

struct WorkspaceFilterBar: View {
    let workspaces: [ProjectWorkspace]
    @Binding var selectedWorkspaceID: UUID?

    @Environment(\.appTheme) private var theme

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                chip(title: "All", isSelected: selectedWorkspaceID == nil) {
                    selectedWorkspaceID = nil
                }

                ForEach(workspaces) { workspace in
                    chip(title: workspace.name, isSelected: selectedWorkspaceID == workspace.id) {
                        selectedWorkspaceID = workspace.id
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
        .background(theme.background)
    }

    private func chip(title: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(isSelected ? theme.selectionForeground : theme.foreground)
                .lineLimit(1)
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(Capsule().fill(isSelected ? theme.selectionBackground : theme.surface))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}
