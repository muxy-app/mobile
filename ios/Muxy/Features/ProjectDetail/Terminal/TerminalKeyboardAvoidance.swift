import UIKit

struct KeyboardTransition: Equatable {
    let overlap: CGFloat
    let duration: TimeInterval
    let animationOptions: UIView.AnimationOptions

    static func parse(userInfo: [AnyHashable: Any]?, overlap: CGFloat) -> KeyboardTransition {
        let duration = userInfo?[UIResponder.keyboardAnimationDurationUserInfoKey] as? TimeInterval ?? 0.25
        let curveRawValue = userInfo?[UIResponder.keyboardAnimationCurveUserInfoKey] as? UInt
        let animationOptions = curveRawValue.map { UIView.AnimationOptions(rawValue: $0 << 16) } ?? .curveEaseInOut
        return KeyboardTransition(overlap: overlap, duration: duration, animationOptions: animationOptions)
    }
}

@MainActor
final class TerminalKeyboardAvoidance: NSObject {
    private weak var view: UIView?
    private let onTransition: (KeyboardTransition) -> Void
    private var isObserving = false

    init(view: UIView, onTransition: @escaping (KeyboardTransition) -> Void) {
        self.view = view
        self.onTransition = onTransition
        _ = KeyboardFrameTracker.shared
    }

    func syncToCurrentKeyboard() {
        guard let view, let window = view.window else { return }
        guard let keyboardFrame = KeyboardFrameTracker.shared.endFrame else {
            onTransition(KeyboardTransition(overlap: 0, duration: 0, animationOptions: .curveEaseInOut))
            return
        }
        let overlap = TerminalScrollGeometry.keyboardOverlap(
            viewFrameInWindow: view.convert(view.bounds, to: window),
            keyboardFrameInWindow: window.convert(keyboardFrame, from: nil)
        )
        onTransition(KeyboardTransition(overlap: overlap, duration: 0, animationOptions: .curveEaseInOut))
    }

    func startObserving() {
        guard !isObserving else { return }
        isObserving = true
        let center = NotificationCenter.default
        center.addObserver(
            self,
            selector: #selector(keyboardWillChangeFrame),
            name: UIResponder.keyboardWillChangeFrameNotification,
            object: nil
        )
        center.addObserver(
            self,
            selector: #selector(keyboardWillHide),
            name: UIResponder.keyboardWillHideNotification,
            object: nil
        )
    }

    func stopObserving() {
        guard isObserving else { return }
        isObserving = false
        NotificationCenter.default.removeObserver(self)
    }

    @objc private func keyboardWillChangeFrame(_ notification: Notification) {
        guard let view, let window = view.window else { return }
        guard isLocalKeyboard(notification) else { return }
        guard let endFrame = (notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? NSValue)?.cgRectValue else { return }
        let overlap = TerminalScrollGeometry.keyboardOverlap(
            viewFrameInWindow: view.convert(view.bounds, to: window),
            keyboardFrameInWindow: window.convert(endFrame, from: nil)
        )
        onTransition(.parse(userInfo: notification.userInfo, overlap: overlap))
    }

    @objc private func keyboardWillHide(_ notification: Notification) {
        guard view?.window != nil else { return }
        guard isLocalKeyboard(notification) else { return }
        onTransition(.parse(userInfo: notification.userInfo, overlap: 0))
    }

    private func isLocalKeyboard(_ notification: Notification) -> Bool {
        notification.userInfo?[UIResponder.keyboardIsLocalUserInfoKey] as? Bool ?? true
    }
}
