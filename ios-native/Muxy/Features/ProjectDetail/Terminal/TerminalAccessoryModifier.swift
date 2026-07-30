import SwiftUI

enum TerminalModifier: String, CaseIterable, Identifiable {
    case ctrl
    case shift
    case alt
    case cmd

    var id: String { rawValue }
    var title: String { rawValue }

    var displayName: String {
        switch self {
        case .ctrl: "Control"
        case .shift: "Shift"
        case .alt: "Option"
        case .cmd: "Command"
        }
    }

    var glyph: String {
        switch self {
        case .ctrl: "⌃"
        case .shift: "⇧"
        case .alt: "⌥"
        case .cmd: "⌘"
        }
    }
}

struct DPadControl: View {
    let tint: SwiftUI.Color
    let onDirection: (String) -> Void

    private let outerSize: CGFloat = 44
    private let thumbSize: CGFloat = 18
    private let deadZone: CGFloat = 5

    @State private var thumbOffset: CGSize = .zero
    @State private var activeDirection: Direction?
    @State private var repeatTask: Task<Void, Never>?

    private enum Direction {
        case up
        case down
        case left
        case right

        var payload: String {
            switch self {
            case .up: "\u{1B}[A"
            case .down: "\u{1B}[B"
            case .left: "\u{1B}[D"
            case .right: "\u{1B}[C"
            }
        }

        var unit: CGSize {
            switch self {
            case .up: .init(width: 0, height: -1)
            case .down: .init(width: 0, height: 1)
            case .left: .init(width: -1, height: 0)
            case .right: .init(width: 1, height: 0)
            }
        }
    }

    var body: some View {
        ZStack {
            Circle()
                .fill(Color.black.opacity(0.35))
            Circle()
                .fill(tint.opacity(0.55))
                .frame(width: thumbSize, height: thumbSize)
                .offset(thumbOffset)
                .animation(.interactiveSpring(response: 0.18, dampingFraction: 0.8), value: thumbOffset)
        }
        .frame(width: outerSize, height: outerSize)
        .contentShape(Circle())
        .glassEffect(.regular.interactive(), in: Circle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { value in handleDrag(translation: value.translation) }
                .onEnded { _ in
                    resetThumb()
                    stopRepeating()
                }
        )
    }

    private func handleDrag(translation: CGSize) {
        let dx = translation.width
        let dy = translation.height
        let magnitude = hypot(dx, dy)
        guard magnitude > deadZone else {
            if activeDirection != nil {
                stopRepeating()
                activeDirection = nil
            }
            thumbOffset = .zero
            return
        }
        let direction: Direction = abs(dx) > abs(dy)
            ? (dx > 0 ? .right : .left)
            : (dy > 0 ? .down : .up)

        let maxReach = (outerSize - thumbSize) / 2 - 2
        thumbOffset = CGSize(
            width: direction.unit.width * maxReach,
            height: direction.unit.height * maxReach
        )

        guard direction != activeDirection else { return }
        activeDirection = direction
        startRepeating(direction: direction)
    }

    private func resetThumb() {
        activeDirection = nil
        thumbOffset = .zero
    }

    private func startRepeating(direction: Direction) {
        stopRepeating()
        onDirection(direction.payload)
        repeatTask = Task {
            try? await Task.sleep(for: .milliseconds(300))
            while !Task.isCancelled {
                onDirection(direction.payload)
                try? await Task.sleep(for: .milliseconds(60))
            }
        }
    }

    private func stopRepeating() {
        repeatTask?.cancel()
        repeatTask = nil
    }
}
