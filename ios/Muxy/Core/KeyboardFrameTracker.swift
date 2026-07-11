import UIKit

@MainActor
final class KeyboardFrameTracker: NSObject {
    static let shared = KeyboardFrameTracker()

    private(set) var endFrame: CGRect?

    private override init() {
        super.init()
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

    @objc private func keyboardWillChangeFrame(_ notification: Notification) {
        guard isLocalKeyboard(notification) else { return }
        endFrame = (notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? NSValue)?.cgRectValue
    }

    @objc private func keyboardWillHide(_ notification: Notification) {
        guard isLocalKeyboard(notification) else { return }
        endFrame = nil
    }

    private func isLocalKeyboard(_ notification: Notification) -> Bool {
        notification.userInfo?[UIResponder.keyboardIsLocalUserInfoKey] as? Bool ?? true
    }
}
