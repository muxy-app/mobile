import SwiftUI
import UIKit

extension View {
    func swipeToClose(isEnabled: Bool = true, label: String = "Close", onClose: @escaping () -> Void) -> some View {
        modifier(SwipeToClose(isEnabled: isEnabled, label: label, onClose: onClose))
    }
}

private struct SwipeToClose: ViewModifier {
    let isEnabled: Bool
    let label: String
    let onClose: () -> Void

    @Environment(\.appTheme) private var theme
    @State private var offset: CGFloat = 0
    @State private var isRevealed = false
    @State private var width: CGFloat = 0

    private static let revealWidth: CGFloat = 76
    private static let gap: CGFloat = 6
    private static let fullSwipeShare: CGFloat = 0.6
    private static let velocityProjection: CGFloat = 0.2

    func body(content: Content) -> some View {
        content
            .overlay {
                if isRevealed {
                    Color.clear
                        .contentShape(Rectangle())
                        .onTapGesture(perform: reset)
                }
            }
            .offset(x: offset)
            .background(alignment: .trailing) { closeButton }
            .clipShape(.rect(cornerRadius: SurfaceCorner.row, style: .continuous))
            .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
            .gesture(HorizontalPan(isEnabled: isEnabled, onChange: drag, onEnd: endDrag))
            .sensoryFeedback(.impact, trigger: passesFullSwipe)
            .accessibilityAction(named: label, onClose)
    }

    private var closeButton: some View {
        let visible = max(0, -offset - Self.gap)
        return Button(action: close) {
            Image(systemName: "xmark")
                .font(.body.weight(.semibold))
                .foregroundStyle(.white)
                .opacity(visible > 32 ? 1 : 0)
                .frame(width: visible)
                .frame(maxHeight: .infinity)
                .background(theme.red, in: .rect(cornerRadius: SurfaceCorner.row, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityHidden(true)
    }

    private var passesFullSwipe: Bool {
        width > 0 && -offset > width * Self.fullSwipeShare
    }

    private var restingOffset: CGFloat {
        isRevealed ? -Self.revealWidth : 0
    }

    private func drag(by translation: CGFloat) {
        offset = min(0, restingOffset + translation)
    }

    private func endDrag(translation: CGFloat, velocity: CGFloat) {
        let projected = restingOffset + translation + velocity * Self.velocityProjection
        if passesFullSwipe || -projected > width * Self.fullSwipeShare {
            close()
            return
        }
        withAnimation(.snappy) {
            isRevealed = -offset > Self.revealWidth / 2 || -projected > Self.revealWidth
            offset = restingOffset
        }
    }

    private func reset() {
        withAnimation(.snappy) {
            isRevealed = false
            offset = 0
        }
    }

    private func close() {
        withAnimation(.snappy) {
            offset = -width
        }
        onClose()
    }
}

private struct HorizontalPan: UIGestureRecognizerRepresentable {
    let isEnabled: Bool
    let onChange: (CGFloat) -> Void
    let onEnd: (CGFloat, CGFloat) -> Void

    func makeUIGestureRecognizer(context: Context) -> UIPanGestureRecognizer {
        let recognizer = UIPanGestureRecognizer()
        recognizer.delegate = context.coordinator
        return recognizer
    }

    func updateUIGestureRecognizer(_ recognizer: UIPanGestureRecognizer, context: Context) {
        context.coordinator.isEnabled = isEnabled
    }

    func handleUIGestureRecognizerAction(_ recognizer: UIPanGestureRecognizer, context: Context) {
        let translation = recognizer.translation(in: recognizer.view).x
        switch recognizer.state {
        case .changed:
            onChange(translation)
        case .ended, .cancelled, .failed:
            onEnd(translation, recognizer.velocity(in: recognizer.view).x)
        default:
            break
        }
    }

    func makeCoordinator(converter: CoordinateSpaceConverter) -> Coordinator {
        Coordinator()
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var isEnabled = true

        func gestureRecognizerShouldBegin(_ recognizer: UIGestureRecognizer) -> Bool {
            guard isEnabled, let pan = recognizer as? UIPanGestureRecognizer else { return false }
            let velocity = pan.velocity(in: pan.view)
            return abs(velocity.x) > abs(velocity.y)
        }
    }
}
