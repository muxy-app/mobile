import ExpoModulesCore
import UIKit

public final class MuxyTerminalInputView: ExpoView, UITextViewDelegate {
  let onTextChange = EventDispatcher()
  let onFocus = EventDispatcher()
  let onBlur = EventDispatcher()
  let onHardwareInput = EventDispatcher()

  private let textView = TerminalTextView()
  private var applyingValue = false

  public required init(appContext: AppContext? = nil) {
    super.init(appContext: appContext)

    textView.delegate = self
    textView.backgroundColor = .clear
    textView.textColor = .clear
    textView.tintColor = .clear
    textView.autocorrectionType = .no
    textView.autocapitalizationType = .none
    textView.spellCheckingType = .no
    textView.smartQuotesType = .no
    textView.smartDashesType = .no
    textView.smartInsertDeleteType = .no
    textView.isScrollEnabled = false
    textView.textContainerInset = .zero
    textView.textContainer.lineFragmentPadding = 0
    textView.onHardwareInput = { [weak self] bytes in
      self?.onHardwareInput(["base64": Data(bytes).base64EncodedString()])
    }
    addSubview(textView)
  }

  public override func layoutSubviews() {
    super.layoutSubviews()
    textView.frame = bounds
  }

  func setValue(_ value: String) {
    if textView.text != value {
      applyingValue = true
      textView.text = value
      applyingValue = false
    }
    moveSelectionToEnd()
  }

  func focus() {
    textView.becomeFirstResponder()
  }

  func blur() {
    textView.resignFirstResponder()
  }

  public func textViewDidChange(_ textView: UITextView) {
    guard !applyingValue else { return }
    onTextChange(["text": textView.text ?? ""])
  }

  public func textViewDidBeginEditing(_ textView: UITextView) {
    onFocus()
  }

  public func textViewDidEndEditing(_ textView: UITextView) {
    onBlur()
  }

  public func textViewDidChangeSelection(_ textView: UITextView) {
    guard textView.markedTextRange == nil else { return }
    moveSelectionToEnd()
  }

  private func moveSelectionToEnd() {
    let end = textView.text.utf16.count
    let selection = NSRange(location: end, length: 0)
    if textView.selectedRange != selection {
      textView.selectedRange = selection
    }
  }
}

private final class TerminalTextView: UITextView {
  var onHardwareInput: (([UInt8]) -> Void)?

  override func pressesBegan(_ presses: Set<UIPress>, with event: UIPressesEvent?) {
    var forwarded = presses

    for press in presses {
      guard let key = press.key,
            let bytes = TerminalHardwareKeyEncoder.encode(key) else {
        continue
      }
      forwarded.remove(press)
      onHardwareInput?(bytes)
    }

    if !forwarded.isEmpty {
      super.pressesBegan(forwarded, with: event)
    }
  }
}
