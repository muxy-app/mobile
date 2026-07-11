import SwiftUI

enum KeyboardOverlap {
    static let animation = Animation.interpolatingSpring(mass: 3, stiffness: 1000, damping: 500)

    static func bottomOverlap(of frame: CGRect, keyboardFrame: CGRect) -> CGFloat {
        guard keyboardFrame.maxX > frame.minX, keyboardFrame.minX < frame.maxX else { return 0 }
        return max(0, frame.maxY - keyboardFrame.minY)
    }

    static func endFrame(from notification: Notification) -> CGRect? {
        (notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? NSValue)?.cgRectValue
    }
}

struct KeyboardOcclusionCover: View {
    let color: Color

    @State private var height: CGFloat = 0
    @State private var coverFrame: CGRect = .zero

    var body: some View {
        color
            .frame(height: height)
            .frame(maxWidth: .infinity)
            .allowsHitTesting(false)
            .onGeometryChange(for: CGRect.self) { proxy in
                proxy.frame(in: .global)
            } action: { frame in
                coverFrame = frame
                syncToCurrentKeyboard()
            }
            .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillChangeFrameNotification)) { notification in
                guard let keyboardFrame = KeyboardOverlap.endFrame(from: notification) else { return }
                setHeight(KeyboardOverlap.bottomOverlap(of: coverFrame, keyboardFrame: keyboardFrame))
            }
            .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in
                setHeight(0)
            }
    }

    private func syncToCurrentKeyboard() {
        let target = KeyboardFrameTracker.shared.endFrame
            .map { KeyboardOverlap.bottomOverlap(of: coverFrame, keyboardFrame: $0) } ?? 0
        guard height != target else { return }
        height = target
    }

    private func setHeight(_ newHeight: CGFloat) {
        withAnimation(KeyboardOverlap.animation) {
            height = newHeight
        }
    }
}

struct KeyboardOverlapPadding: ViewModifier {
    @State private var bottomPadding: CGFloat = 0
    @State private var unpaddedFrame: CGRect = .zero
    @State private var hasReceivedKeyboardEvent = false

    func body(content: Content) -> some View {
        content
            .onGeometryChange(for: CGRect.self) { proxy in
                proxy.frame(in: .global)
            } action: { frame in
                unpaddedFrame = frame.offsetBy(dx: 0, dy: bottomPadding)
                syncToCurrentKeyboard()
            }
            .padding(.bottom, bottomPadding)
            .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillChangeFrameNotification)) { notification in
                hasReceivedKeyboardEvent = true
                guard let keyboardFrame = KeyboardOverlap.endFrame(from: notification) else { return }
                setPadding(KeyboardOverlap.bottomOverlap(of: unpaddedFrame, keyboardFrame: keyboardFrame))
            }
            .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in
                hasReceivedKeyboardEvent = true
                setPadding(0)
            }
    }

    private func syncToCurrentKeyboard() {
        guard !hasReceivedKeyboardEvent else { return }
        guard let keyboardFrame = KeyboardFrameTracker.shared.endFrame else { return }
        bottomPadding = KeyboardOverlap.bottomOverlap(of: unpaddedFrame, keyboardFrame: keyboardFrame)
    }

    private func setPadding(_ padding: CGFloat) {
        withAnimation(KeyboardOverlap.animation) {
            bottomPadding = padding
        }
    }
}

extension View {
    func keyboardOverlapPadding() -> some View {
        modifier(KeyboardOverlapPadding())
    }
}
