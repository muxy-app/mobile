import Foundation

nonisolated enum TerminalInputEffect: Equatable, Sendable {
    case text(String)
    case backspaces(Int)
}

nonisolated struct TerminalTextInputState: Sendable {
    private static let resetLength = 256

    private(set) var text = ""
    private(set) var selection = NSRange(location: 0, length: 0)
    private(set) var marked: NSRange?

    var length: Int {
        text.utf16.count
    }

    var markedText: String? {
        marked.map(substring)
    }

    var needsReset: Bool {
        marked == nil && (length > Self.resetLength || text.contains(where: \.isNewline))
    }

    func substring(_ range: NSRange) -> String {
        let clamped = clamp(range)
        let utf16 = text.utf16
        let start = utf16.index(utf16.startIndex, offsetBy: clamped.location)
        let end = utf16.index(start, offsetBy: clamped.length)
        return String(utf16[start..<end]) ?? ""
    }

    mutating func insert(_ inserted: String) -> [TerminalInputEffect] {
        if let marked {
            splice(marked, with: inserted)
            self.marked = nil
            selection = NSRange(location: marked.location + inserted.utf16.count, length: 0)
            return inserted.isEmpty ? [] : [.text(inserted)]
        }
        let replaced = substring(selection)
        let location = selection.location
        splice(selection, with: inserted)
        selection = NSRange(location: location + inserted.utf16.count, length: 0)
        return Self.effects(removing: replaced, inserting: inserted)
    }

    mutating func deleteBackward() -> [TerminalInputEffect] {
        if selection.length > 0 {
            let removed = substring(selection)
            splice(selection, with: "")
            selection = NSRange(location: selection.location, length: 0)
            return Self.effects(removing: removed, inserting: "")
        }
        guard selection.location > 0 else { return [.backspaces(1)] }
        let range = (text as NSString).rangeOfComposedCharacterSequence(at: selection.location - 1)
        splice(range, with: "")
        selection = NSRange(location: range.location, length: 0)
        return [.backspaces(1)]
    }

    mutating func replace(_ range: NSRange, with replacement: String) -> [TerminalInputEffect] {
        guard marked == nil else { return [] }
        let clamped = clamp(range)
        let removed = substring(clamped)
        splice(clamped, with: replacement)
        selection = NSRange(location: clamped.location + replacement.utf16.count, length: 0)
        return Self.effects(removing: removed, inserting: replacement)
    }

    mutating func setMarkedText(_ markedText: String?, selectedRange: NSRange) {
        let target = marked ?? selection
        let newText = markedText ?? ""
        splice(target, with: newText)
        guard !newText.isEmpty else {
            marked = nil
            selection = NSRange(location: target.location, length: 0)
            return
        }
        let newLength = newText.utf16.count
        marked = NSRange(location: target.location, length: newLength)
        let start = min(max(selectedRange.location, 0), newLength)
        let length = min(max(selectedRange.length, 0), newLength - start)
        selection = NSRange(location: target.location + start, length: length)
    }

    mutating func unmarkText() -> [TerminalInputEffect] {
        guard let marked else { return [] }
        let committed = substring(marked)
        self.marked = nil
        selection = NSRange(location: marked.location + marked.length, length: 0)
        return committed.isEmpty ? [] : [.text(committed)]
    }

    mutating func select(_ range: NSRange) {
        selection = clamp(range)
    }

    mutating func reset() {
        text = ""
        selection = NSRange(location: 0, length: 0)
        marked = nil
    }

    private mutating func splice(_ range: NSRange, with replacement: String) {
        text = (text as NSString).replacingCharacters(in: clamp(range), with: replacement)
    }

    private func clamp(_ range: NSRange) -> NSRange {
        let location = min(max(range.location, 0), length)
        let length = min(max(range.length, 0), self.length - location)
        return NSRange(location: location, length: length)
    }

    private static func effects(removing removed: String, inserting inserted: String) -> [TerminalInputEffect] {
        var effects: [TerminalInputEffect] = []
        if !removed.isEmpty {
            effects.append(.backspaces(removed.count))
        }
        if !inserted.isEmpty {
            effects.append(.text(inserted))
        }
        return effects
    }
}
