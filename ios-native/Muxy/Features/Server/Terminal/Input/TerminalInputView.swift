import UIKit

@MainActor
protocol TerminalInputViewDelegate: AnyObject {
    func inputView(_ inputView: TerminalInputView, didProduce effects: [TerminalInputEffect])
    func inputView(_ inputView: TerminalInputView, didPress stroke: TerminalKeyStroke)
    func inputView(_ inputView: TerminalInputView, didChangeMarkedText markedText: String?)
    func inputViewDidRequestPaste(_ inputView: TerminalInputView)
    func inputViewDidRequestCopy(_ inputView: TerminalInputView)
    func inputViewCaretRect(_ inputView: TerminalInputView) -> CGRect
}

final class TerminalInputView: UIView, UITextInput {
    weak var delegate: TerminalInputViewDelegate?
    weak var inputDelegate: UITextInputDelegate?
    var markedTextStyle: [NSAttributedString.Key: Any]?

    var autocorrectionType: UITextAutocorrectionType = .no
    var autocapitalizationType: UITextAutocapitalizationType = .none
    var spellCheckingType: UITextSpellCheckingType = .no
    var smartQuotesType: UITextSmartQuotesType = .no
    var smartDashesType: UITextSmartDashesType = .no
    var smartInsertDeleteType: UITextSmartInsertDeleteType = .no
    var inlinePredictionType: UITextInlinePredictionType = .no
    var mathExpressionCompletionType: UITextMathExpressionCompletionType = .no
    var writingToolsBehavior: UIWritingToolsBehavior = .none
    var keyboardType: UIKeyboardType = .default
    var keyboardAppearance: UIKeyboardAppearance = .dark
    var returnKeyType: UIReturnKeyType = .default

    private var state = TerminalTextInputState()
    private let repeater = KeyRepeater()
    private var accessory: UIView?
    private var isSoftKeyboardHidden = false
    private let hiddenKeyboard = UIView(frame: .zero)

    override init(frame: CGRect) {
        super.init(frame: frame)
        inputAssistantItem.leadingBarButtonGroups = []
        inputAssistantItem.trailingBarButtonGroups = []
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        nil
    }

    override var canBecomeFirstResponder: Bool {
        true
    }

    override var inputAccessoryView: UIView? {
        accessory
    }

    override var inputView: UIView? {
        isSoftKeyboardHidden ? hiddenKeyboard : nil
    }

    var softKeyboardHidden: Bool {
        isSoftKeyboardHidden
    }

    func setAccessory(_ view: UIView) {
        accessory = view
    }

    func toggleSoftKeyboard() {
        isSoftKeyboardHidden.toggle()
        if !isFirstResponder {
            _ = becomeFirstResponder()
        }
        reloadInputViews()
    }

    @discardableResult
    override func resignFirstResponder() -> Bool {
        repeater.stop()
        resetBuffer()
        return super.resignFirstResponder()
    }

    func resetBuffer() {
        guard state.length > 0 || state.marked != nil else { return }
        edit { $0.reset() }
        delegate?.inputView(self, didChangeMarkedText: nil)
    }

    var hasText: Bool {
        true
    }

    func insertText(_ text: String) {
        let effects = edit { $0.insert(text) }
        delegate?.inputView(self, didChangeMarkedText: nil)
        deliver(effects)
    }

    func deleteBackward() {
        let effects = edit { $0.deleteBackward() }
        deliver(effects)
    }

    func text(in range: UITextRange) -> String? {
        guard let range = range as? TerminalTextRange else { return nil }
        return state.substring(range.range)
    }

    func replace(_ range: UITextRange, withText text: String) {
        guard let range = range as? TerminalTextRange else { return }
        let effects = edit { $0.replace(range.range, with: text) }
        deliver(effects)
    }

    var selectedTextRange: UITextRange? {
        get { TerminalTextRange(state.selection) }
        set {
            guard let range = newValue as? TerminalTextRange else { return }
            inputDelegate?.selectionWillChange(self)
            state.select(range.range)
            inputDelegate?.selectionDidChange(self)
        }
    }

    var markedTextRange: UITextRange? {
        guard let marked = state.marked else { return nil }
        return TerminalTextRange(marked)
    }

    func setMarkedText(_ markedText: String?, selectedRange: NSRange) {
        edit { $0.setMarkedText(markedText, selectedRange: selectedRange) }
        delegate?.inputView(self, didChangeMarkedText: state.markedText)
    }

    func unmarkText() {
        let effects = edit { $0.unmarkText() }
        delegate?.inputView(self, didChangeMarkedText: nil)
        deliver(effects)
    }

    var beginningOfDocument: UITextPosition {
        TerminalTextPosition(0)
    }

    var endOfDocument: UITextPosition {
        TerminalTextPosition(state.length)
    }

    func textRange(from fromPosition: UITextPosition, to toPosition: UITextPosition) -> UITextRange? {
        guard let from = fromPosition as? TerminalTextPosition, let to = toPosition as? TerminalTextPosition else { return nil }
        let lower = min(from.offset, to.offset)
        return TerminalTextRange(NSRange(location: lower, length: abs(to.offset - from.offset)))
    }

    func position(from position: UITextPosition, offset: Int) -> UITextPosition? {
        guard let position = position as? TerminalTextPosition else { return nil }
        let target = position.offset + offset
        guard target >= 0, target <= state.length else { return nil }
        return TerminalTextPosition(target)
    }

    func position(from position: UITextPosition, in direction: UITextLayoutDirection, offset: Int) -> UITextPosition? {
        switch direction {
        case .left:
            return self.position(from: position, offset: -offset)
        case .right:
            return self.position(from: position, offset: offset)
        case .up, .down:
            return nil
        @unknown default:
            return nil
        }
    }

    func compare(_ position: UITextPosition, to other: UITextPosition) -> ComparisonResult {
        let lhs = (position as? TerminalTextPosition)?.offset ?? 0
        let rhs = (other as? TerminalTextPosition)?.offset ?? 0
        if lhs < rhs { return .orderedAscending }
        return lhs > rhs ? .orderedDescending : .orderedSame
    }

    func offset(from: UITextPosition, to toPosition: UITextPosition) -> Int {
        let lhs = (from as? TerminalTextPosition)?.offset ?? 0
        let rhs = (toPosition as? TerminalTextPosition)?.offset ?? 0
        return rhs - lhs
    }

    var tokenizer: UITextInputTokenizer {
        UITextInputStringTokenizer(textInput: self)
    }

    func position(within range: UITextRange, farthestIn direction: UITextLayoutDirection) -> UITextPosition? {
        switch direction {
        case .left, .up:
            return range.start
        case .right, .down:
            return range.end
        @unknown default:
            return range.end
        }
    }

    func characterRange(byExtending position: UITextPosition, in direction: UITextLayoutDirection) -> UITextRange? {
        guard let target = self.position(from: position, in: direction, offset: 1) else { return nil }
        return textRange(from: position, to: target)
    }

    func baseWritingDirection(for position: UITextPosition, in direction: UITextStorageDirection) -> NSWritingDirection {
        .leftToRight
    }

    func setBaseWritingDirection(_ writingDirection: NSWritingDirection, for range: UITextRange) {}

    func firstRect(for range: UITextRange) -> CGRect {
        caretRect()
    }

    func caretRect(for position: UITextPosition) -> CGRect {
        caretRect()
    }

    func selectionRects(for range: UITextRange) -> [UITextSelectionRect] {
        []
    }

    func closestPosition(to point: CGPoint) -> UITextPosition? {
        endOfDocument
    }

    func closestPosition(to point: CGPoint, within range: UITextRange) -> UITextPosition? {
        range.end
    }

    func characterRange(at point: CGPoint) -> UITextRange? {
        nil
    }

    override func pressesBegan(_ presses: Set<UIPress>, with event: UIPressesEvent?) {
        guard state.marked == nil else {
            super.pressesBegan(presses, with: event)
            return
        }
        var unhandled = Set<UIPress>()
        for press in presses {
            guard let key = press.key, let action = HardwareKeyMapper.action(
                keyCode: key.keyCode,
                modifierFlags: key.modifierFlags,
                charactersIgnoringModifiers: key.charactersIgnoringModifiers
            ) else {
                unhandled.insert(press)
                continue
            }
            perform(action)
        }
        guard !unhandled.isEmpty else { return }
        super.pressesBegan(unhandled, with: event)
    }

    override func pressesEnded(_ presses: Set<UIPress>, with event: UIPressesEvent?) {
        repeater.stop()
        super.pressesEnded(presses, with: event)
    }

    override func pressesCancelled(_ presses: Set<UIPress>, with event: UIPressesEvent?) {
        repeater.stop()
        super.pressesCancelled(presses, with: event)
    }

    override func paste(_ sender: Any?) {
        delegate?.inputViewDidRequestPaste(self)
    }

    override func copy(_ sender: Any?) {
        delegate?.inputViewDidRequestCopy(self)
    }

    override func canPerformAction(_ action: Selector, withSender sender: Any?) -> Bool {
        action == #selector(paste(_:)) || action == #selector(copy(_:))
    }

    private func perform(_ action: HardwareKeyAction) {
        switch action {
        case let .stroke(stroke):
            resetBuffer()
            delegate?.inputView(self, didPress: stroke)
            guard HardwareKeyMapper.repeatableKeys.contains(stroke.key) else { return }
            repeater.start { [weak self] in
                guard let self else { return }
                self.delegate?.inputView(self, didPress: stroke)
            }
        case .paste:
            delegate?.inputViewDidRequestPaste(self)
        case .copy:
            delegate?.inputViewDidRequestCopy(self)
        }
    }

    private func deliver(_ effects: [TerminalInputEffect]) {
        guard !effects.isEmpty else { return }
        delegate?.inputView(self, didProduce: effects)
        guard state.needsReset else { return }
        resetBuffer()
    }

    @discardableResult
    private func edit<Result>(_ change: (inout TerminalTextInputState) -> Result) -> Result {
        inputDelegate?.selectionWillChange(self)
        inputDelegate?.textWillChange(self)
        let result = change(&state)
        inputDelegate?.textDidChange(self)
        inputDelegate?.selectionDidChange(self)
        return result
    }

    private func caretRect() -> CGRect {
        delegate?.inputViewCaretRect(self) ?? .zero
    }
}

final class TerminalTextPosition: UITextPosition {
    let offset: Int

    init(_ offset: Int) {
        self.offset = offset
    }
}

final class TerminalTextRange: UITextRange {
    let range: NSRange

    init(_ range: NSRange) {
        self.range = range
    }

    override var start: UITextPosition {
        TerminalTextPosition(range.location)
    }

    override var end: UITextPosition {
        TerminalTextPosition(range.location + range.length)
    }

    override var isEmpty: Bool {
        range.length == 0
    }
}
