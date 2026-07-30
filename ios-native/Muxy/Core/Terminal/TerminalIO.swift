import Foundation
import SwiftTerm

@MainActor
protocol TerminalIO: AnyObject {
    var theme: TerminalTheme { get }
    var isFollowingBottom: Bool { get }
    var activeModifier: TerminalModifier { get }
    var modifierArmed: Bool { get }
    var canCopySelection: Bool { get }
    var onModifierStateChange: ((TerminalModifier, Bool) -> Void)? { get set }

    func attach(_ view: TerminalView)
    func terminalDidLayout()
    func terminalDidResize(cols: Int, rows: Int)
    func sendBytes(_ slice: ArraySlice<UInt8>)
    func sendText(_ text: String)
    func paste()
    func copySelection()
    func setModifierArmed(_ armed: Bool)
    func selectModifier(_ modifier: TerminalModifier)
    func setTitle(_ title: String)
    func userScrolled(toPosition position: Double)
    func requestJumpToBottom()
    @discardableResult
    func dismissKeyboard() -> Bool
}
