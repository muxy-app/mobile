import UIKit

enum TerminalHardwareKeyEncoder {
  static func encode(_ key: UIKey) -> [UInt8]? {
    let modifiers = key.modifierFlags
    if modifiers.contains(.command) {
      return nil
    }

    if let sequence = specialSequence(keyCode: Int(key.keyCode.rawValue), modifiers: modifiers) {
      return sequence
    }

    if modifiers.contains(.control), let sequence = controlSequence(key.charactersIgnoringModifiers) {
      return modifiers.contains(.alternate) ? [0x1b] + sequence : sequence
    }

    if modifiers.contains(.alternate),
       let bytes = key.charactersIgnoringModifiers.data(using: .utf8).map(Array.init),
       !bytes.isEmpty {
      return [0x1b] + bytes
    }

    return nil
  }

  private static func specialSequence(
    keyCode: Int,
    modifiers: UIKeyModifierFlags
  ) -> [UInt8]? {
    switch keyCode {
    case 0x28:
      return alternatePrefix(modifiers) + [0x0d]
    case 0x29:
      return [0x1b]
    case 0x2a:
      return alternatePrefix(modifiers) + [0x7f]
    case 0x2b:
      if modifiers.contains(.shift) {
        return [0x1b, 0x5b, 0x5a]
      }
      return alternatePrefix(modifiers) + [0x09]
    case 0x3a:
      return functionSequence(final: 0x50, modifiers: modifiers)
    case 0x3b:
      return functionSequence(final: 0x51, modifiers: modifiers)
    case 0x3c:
      return functionSequence(final: 0x52, modifiers: modifiers)
    case 0x3d:
      return functionSequence(final: 0x53, modifiers: modifiers)
    case 0x3e:
      return tildeSequence(code: "15", modifiers: modifiers)
    case 0x3f:
      return tildeSequence(code: "17", modifiers: modifiers)
    case 0x40:
      return tildeSequence(code: "18", modifiers: modifiers)
    case 0x41:
      return tildeSequence(code: "19", modifiers: modifiers)
    case 0x42:
      return tildeSequence(code: "20", modifiers: modifiers)
    case 0x43:
      return tildeSequence(code: "21", modifiers: modifiers)
    case 0x44:
      return tildeSequence(code: "23", modifiers: modifiers)
    case 0x45:
      return tildeSequence(code: "24", modifiers: modifiers)
    case 0x49:
      return tildeSequence(code: "2", modifiers: modifiers)
    case 0x4a:
      return csiSequence(final: 0x48, modifiers: modifiers)
    case 0x4b:
      return tildeSequence(code: "5", modifiers: modifiers)
    case 0x4c:
      return tildeSequence(code: "3", modifiers: modifiers)
    case 0x4d:
      return csiSequence(final: 0x46, modifiers: modifiers)
    case 0x4e:
      return tildeSequence(code: "6", modifiers: modifiers)
    case 0x4f:
      return csiSequence(final: 0x43, modifiers: modifiers)
    case 0x50:
      return csiSequence(final: 0x44, modifiers: modifiers)
    case 0x51:
      return csiSequence(final: 0x42, modifiers: modifiers)
    case 0x52:
      return csiSequence(final: 0x41, modifiers: modifiers)
    default:
      return nil
    }
  }

  private static func controlSequence(_ characters: String) -> [UInt8]? {
    guard characters.unicodeScalars.count == 1,
          let value = characters.unicodeScalars.first?.value else {
      return nil
    }

    switch value {
    case 0x40 ... 0x5f:
      return [UInt8(value - 0x40)]
    case 0x61 ... 0x7a:
      return [UInt8(value - 0x60)]
    case 0x20:
      return [0x00]
    case 0x3f:
      return [0x7f]
    default:
      return nil
    }
  }

  private static func functionSequence(
    final: UInt8,
    modifiers: UIKeyModifierFlags
  ) -> [UInt8] {
    let modifier = terminalModifier(modifiers)
    if modifier == 1 {
      return [0x1b, 0x4f, final]
    }
    return Array("\u{1b}[1;\(modifier)".utf8) + [final]
  }

  private static func csiSequence(
    final: UInt8,
    modifiers: UIKeyModifierFlags
  ) -> [UInt8] {
    let modifier = terminalModifier(modifiers)
    if modifier == 1 {
      return [0x1b, 0x5b, final]
    }
    return Array("\u{1b}[1;\(modifier)".utf8) + [final]
  }

  private static func tildeSequence(
    code: String,
    modifiers: UIKeyModifierFlags
  ) -> [UInt8] {
    let modifier = terminalModifier(modifiers)
    if modifier == 1 {
      return Array("\u{1b}[\(code)~".utf8)
    }
    return Array("\u{1b}[\(code);\(modifier)~".utf8)
  }

  private static func terminalModifier(_ modifiers: UIKeyModifierFlags) -> Int {
    var value = 1
    if modifiers.contains(.shift) {
      value += 1
    }
    if modifiers.contains(.alternate) {
      value += 2
    }
    if modifiers.contains(.control) {
      value += 4
    }
    return value
  }

  private static func alternatePrefix(_ modifiers: UIKeyModifierFlags) -> [UInt8] {
    modifiers.contains(.alternate) ? [0x1b] : []
  }
}
