import Foundation

nonisolated enum ThemeCatalog {
    static let all: [ThemePalette] = [
        .muxy,
        .muxyLight,
        .atomOneDark,
        .catppuccinLatte,
        .catppuccinMocha,
        .dracula,
        .githubDarkDefault,
        .githubLightDefault,
        .gruvboxDark,
        .gruvboxLight,
        .nord,
        .tokyoNight,
    ]

    static func named(_ name: String) -> ThemePalette {
        all.first { $0.name == name } ?? .muxy
    }
}

nonisolated extension ThemePalette {
    static let muxy = ThemePalette(
        name: "Muxy",
        foreground: 0xC9C2D9,
        background: 0x19171F,
        ansi: [
            0x464056, 0xEC4899, 0x34D399, 0xE0AF68,
            0xC370D3, 0x6366F1, 0x22D3EE, 0xA9B1D6,
            0x7C7393, 0xF472B6, 0x6EE7B7, 0xFBBF24,
            0xD99BE5, 0x818CF8, 0x67E8F9, 0xC9C2D9,
        ],
        cursor: 0xC370D3,
        cursorText: 0x19171F,
        selectionBackground: 0xC370D3,
        selectionForeground: 0x19171F
    )

    static let muxyLight = ThemePalette(
        name: "Muxy Light",
        foreground: 0x1E1E2E,
        background: 0xF0F0F5,
        ansi: [
            0xD5D6DB, 0xA32D68, 0x1A7A4E, 0x9A7024,
            0x4796F0, 0x7C3AED, 0x0B7189, 0x3B3F5C,
            0x7A7E94, 0xEC4899, 0x34D399, 0xE0AF68,
            0x6BABF5, 0xA78BFA, 0x22D3EE, 0x1E1E2E,
        ],
        cursor: 0x4796F0,
        cursorText: 0xF0F0F5,
        selectionBackground: 0x4796F0,
        selectionForeground: 0xF0F0F5
    )

    static let atomOneDark = ThemePalette(
        name: "Atom One Dark",
        foreground: 0xABB2BF,
        background: 0x21252B,
        ansi: [
            0x21252B, 0xE06C75, 0x98C379, 0xE5C07B,
            0x61AFEF, 0xC678DD, 0x56B6C2, 0xABB2BF,
            0x767676, 0xE06C75, 0x98C379, 0xE5C07B,
            0x61AFEF, 0xC678DD, 0x56B6C2, 0xABB2BF,
        ],
        cursor: 0xABB2BF,
        cursorText: 0x21252B,
        selectionBackground: 0x323844,
        selectionForeground: 0xABB2BF
    )

    static let catppuccinLatte = ThemePalette(
        name: "Catppuccin Latte",
        foreground: 0x4C4F69,
        background: 0xEFF1F5,
        ansi: [
            0x5C5F77, 0xD20F39, 0x40A02B, 0xDF8E1D,
            0x1E66F5, 0xEA76CB, 0x179299, 0xACB0BE,
            0x6C6F85, 0xDE293E, 0x49AF3D, 0xEEA02D,
            0x456EFF, 0xFE85D8, 0x2D9FA8, 0xBCC0CC,
        ],
        cursor: 0xDC8A78,
        cursorText: 0xEFF1F5,
        selectionBackground: 0xACB0BE,
        selectionForeground: 0x4C4F69
    )

    static let catppuccinMocha = ThemePalette(
        name: "Catppuccin Mocha",
        foreground: 0xCDD6F4,
        background: 0x1E1E2E,
        ansi: [
            0x45475A, 0xF38BA8, 0xA6E3A1, 0xF9E2AF,
            0x89B4FA, 0xF5C2E7, 0x94E2D5, 0xA6ADC8,
            0x585B70, 0xF37799, 0x89D88B, 0xEBD391,
            0x74A8FC, 0xF2AEDE, 0x6BD7CA, 0xBAC2DE,
        ],
        cursor: 0xF5E0DC,
        cursorText: 0x1E1E2E,
        selectionBackground: 0x585B70,
        selectionForeground: 0xCDD6F4
    )

    static let dracula = ThemePalette(
        name: "Dracula",
        foreground: 0xF8F8F2,
        background: 0x282A36,
        ansi: [
            0x21222C, 0xFF5555, 0x50FA7B, 0xF1FA8C,
            0xBD93F9, 0xFF79C6, 0x8BE9FD, 0xF8F8F2,
            0x6272A4, 0xFF6E6E, 0x69FF94, 0xFFFFA5,
            0xD6ACFF, 0xFF92DF, 0xA4FFFF, 0xFFFFFF,
        ],
        cursor: 0xF8F8F2,
        cursorText: 0x282A36,
        selectionBackground: 0x44475A,
        selectionForeground: 0xFFFFFF
    )

    static let githubDarkDefault = ThemePalette(
        name: "GitHub Dark Default",
        foreground: 0xE6EDF3,
        background: 0x0D1117,
        ansi: [
            0x484F58, 0xFF7B72, 0x3FB950, 0xD29922,
            0x58A6FF, 0xBC8CFF, 0x39C5CF, 0xB1BAC4,
            0x6E7681, 0xFFA198, 0x56D364, 0xE3B341,
            0x79C0FF, 0xD2A8FF, 0x56D4DD, 0xFFFFFF,
        ],
        cursor: 0x2F81F7,
        cursorText: 0x6FC1FF,
        selectionBackground: 0xE6EDF3,
        selectionForeground: 0x0D1117
    )

    static let githubLightDefault = ThemePalette(
        name: "GitHub Light Default",
        foreground: 0x1F2328,
        background: 0xFFFFFF,
        ansi: [
            0x24292F, 0xCF222E, 0x116329, 0x4D2D00,
            0x0969DA, 0x8250DF, 0x1B7C83, 0x6E7781,
            0x57606A, 0xA40E26, 0x1A7F37, 0x633C01,
            0x218BFF, 0xA475F9, 0x3192AA, 0x8C959F,
        ],
        cursor: 0x0969DA,
        cursorText: 0x3C9CFF,
        selectionBackground: 0x1F2328,
        selectionForeground: 0xFFFFFF
    )

    static let gruvboxDark = ThemePalette(
        name: "Gruvbox Dark",
        foreground: 0xEBDBB2,
        background: 0x282828,
        ansi: [
            0x282828, 0xCC241D, 0x98971A, 0xD79921,
            0x458588, 0xB16286, 0x689D6A, 0xA89984,
            0x928374, 0xFB4934, 0xB8BB26, 0xFABD2F,
            0x83A598, 0xD3869B, 0x8EC07C, 0xEBDBB2,
        ],
        cursor: 0xEBDBB2,
        cursorText: 0x282828,
        selectionBackground: 0x665C54,
        selectionForeground: 0xEBDBB2
    )

    static let gruvboxLight = ThemePalette(
        name: "Gruvbox Light",
        foreground: 0x3C3836,
        background: 0xFBF1C7,
        ansi: [
            0xFBF1C7, 0xCC241D, 0x98971A, 0xD79921,
            0x458588, 0xB16286, 0x689D6A, 0x7C6F64,
            0x928374, 0x9D0006, 0x79740E, 0xB57614,
            0x076678, 0x8F3F71, 0x427B58, 0x3C3836,
        ],
        cursor: 0x3C3836,
        cursorText: 0xFBF1C7,
        selectionBackground: 0x3C3836,
        selectionForeground: 0xFBF1C7
    )

    static let nord = ThemePalette(
        name: "Nord",
        foreground: 0xD8DEE9,
        background: 0x2E3440,
        ansi: [
            0x3B4252, 0xBF616A, 0xA3BE8C, 0xEBCB8B,
            0x81A1C1, 0xB48EAD, 0x88C0D0, 0xE5E9F0,
            0x596377, 0xBF616A, 0xA3BE8C, 0xEBCB8B,
            0x81A1C1, 0xB48EAD, 0x8FBCBB, 0xECEFF4,
        ],
        cursor: 0xECEFF4,
        cursorText: 0x282828,
        selectionBackground: 0xECEFF4,
        selectionForeground: 0x4C566A
    )

    static let tokyoNight = ThemePalette(
        name: "TokyoNight",
        foreground: 0xC0CAF5,
        background: 0x1A1B26,
        ansi: [
            0x15161E, 0xF7768E, 0x9ECE6A, 0xE0AF68,
            0x7AA2F7, 0xBB9AF7, 0x7DCFFF, 0xA9B1D6,
            0x414868, 0xF7768E, 0x9ECE6A, 0xE0AF68,
            0x7AA2F7, 0xBB9AF7, 0x7DCFFF, 0xC0CAF5,
        ],
        cursor: 0xC0CAF5,
        cursorText: 0x15161E,
        selectionBackground: 0x33467C,
        selectionForeground: 0xC0CAF5
    )
}
