package com.muxy.app.design

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTokensParityTest {
    @Test
    fun everyPaletteDerivesTheTokensIosDerives() {
        ThemeCatalog.all.forEach { palette ->
            assertEquals(palette.name, iosTokens.getValue(palette.name), ThemeTokens.from(palette).asHexMap())
        }
    }

    @Test
    fun goldenValuesCoverEveryPalette() {
        assertEquals(ThemeCatalog.all.map { it.name }.toSet(), iosTokens.keys)
    }

    private fun ThemeTokens.asHexMap(): Map<String, String> =
        mapOf(
            "isDark" to isDark.toString(),
            "background" to background.hex(),
            "secondaryBackground" to secondaryBackground.hex(),
            "groupedBackground" to groupedBackground.hex(),
            "secondaryGroupedBackground" to secondaryGroupedBackground.hex(),
            "separator" to separator.hex(),
            "foreground" to foreground.hex(),
            "secondaryForeground" to secondaryForeground.hex(),
            "accent" to accent.hex(),
            "onAccent" to onAccent.hex(),
            "red" to red.hex(),
            "green" to green.hex(),
            "yellow" to yellow.hex(),
            "cyan" to cyan.hex(),
        )

    private fun ThemeColor.hex(): String = rgb.toString(16).padStart(6, '0').uppercase()

    private val iosTokens: Map<String, Map<String, String>> =
        mapOf(
            "Muxy" to
                mapOf(
                    "isDark" to "true",
                    "background" to "19171F",
                    "secondaryBackground" to "26242D",
                    "groupedBackground" to "100E14",
                    "secondaryGroupedBackground" to "26242D",
                    "separator" to "413E49",
                    "foreground" to "C9C2D9",
                    "secondaryForeground" to "8E899A",
                    "accent" to "C370D3",
                    "onAccent" to "19171F",
                    "red" to "E9539F",
                    "green" to "34D399",
                    "yellow" to "E0AF68",
                    "cyan" to "22D3EE",
                ),
            "Muxy Light" to
                mapOf(
                    "isDark" to "false",
                    "background" to "F0F0F5",
                    "secondaryBackground" to "E3E3E9",
                    "groupedBackground" to "E3E3E9",
                    "secondaryGroupedBackground" to "F9F9FB",
                    "separator" to "D0D0D5",
                    "foreground" to "1E1E2E",
                    "secondaryForeground" to "646471",
                    "accent" to "3767A4",
                    "onAccent" to "F0F0F5",
                    "red" to "A32D68",
                    "green" to "1A744C",
                    "yellow" to "826026",
                    "cyan" to "0B6F87",
                ),
            "Atom One Dark" to
                mapOf(
                    "isDark" to "true",
                    "background" to "21252B",
                    "secondaryBackground" to "2D3238",
                    "groupedBackground" to "191C21",
                    "secondaryGroupedBackground" to "2D3238",
                    "separator" to "484E55",
                    "foreground" to "ABB2BF",
                    "secondaryForeground" to "9299A4",
                    "accent" to "61AFEF",
                    "onAccent" to "21252B",
                    "red" to "CE848E",
                    "green" to "98C379",
                    "yellow" to "E5C07B",
                    "cyan" to "56B6C2",
                ),
            "Catppuccin Latte" to
                mapOf(
                    "isDark" to "false",
                    "background" to "EFF1F5",
                    "secondaryBackground" to "E2E4EA",
                    "groupedBackground" to "E2E4EA",
                    "secondaryGroupedBackground" to "F8F9FB",
                    "separator" to "CFD0D8",
                    "foreground" to "4C4F69",
                    "secondaryForeground" to "61647B",
                    "accent" to "2B60CE",
                    "onAccent" to "EFF1F5",
                    "red" to "CA133C",
                    "green" to "476F50",
                    "yellow" to "786252",
                    "cyan" to "346D7F",
                ),
            "Catppuccin Mocha" to
                mapOf(
                    "isDark" to "true",
                    "background" to "1E1E2E",
                    "secondaryBackground" to "2A2B3C",
                    "groupedBackground" to "161623",
                    "secondaryGroupedBackground" to "2A2B3C",
                    "separator" to "434659",
                    "foreground" to "CDD6F4",
                    "secondaryForeground" to "8C92AB",
                    "accent" to "89B4FA",
                    "onAccent" to "1E1E2E",
                    "red" to "F38BA8",
                    "green" to "A6E3A1",
                    "yellow" to "F9E2AF",
                    "cyan" to "94E2D5",
                ),
            "Dracula" to
                mapOf(
                    "isDark" to "true",
                    "background" to "282A36",
                    "secondaryBackground" to "363742",
                    "groupedBackground" to "20212B",
                    "secondaryGroupedBackground" to "363742",
                    "separator" to "52535C",
                    "foreground" to "F8F8F2",
                    "secondaryForeground" to "A5A6A7",
                    "accent" to "BD93F9",
                    "onAccent" to "282A36",
                    "red" to "FE7674",
                    "green" to "50FA7B",
                    "yellow" to "F1FA8C",
                    "cyan" to "8BE9FD",
                ),
            "GitHub Dark Default" to
                mapOf(
                    "isDark" to "true",
                    "background" to "0D1117",
                    "secondaryBackground" to "1A1E24",
                    "groupedBackground" to "030305",
                    "secondaryGroupedBackground" to "1A1E24",
                    "separator" to "34383E",
                    "foreground" to "E6EDF3",
                    "secondaryForeground" to "8F959B",
                    "accent" to "58A6FF",
                    "onAccent" to "0D1117",
                    "red" to "FF7B72",
                    "green" to "3FB950",
                    "yellow" to "D29922",
                    "cyan" to "39C5CF",
                ),
            "GitHub Light Default" to
                mapOf(
                    "isDark" to "false",
                    "background" to "FFFFFF",
                    "secondaryBackground" to "EAEAEB",
                    "groupedBackground" to "EAEAEB",
                    "secondaryGroupedBackground" to "FFFFFF",
                    "separator" to "D6D6D7",
                    "foreground" to "1F2328",
                    "secondaryForeground" to "686A6E",
                    "accent" to "0A66D3",
                    "onAccent" to "FFFFFF",
                    "red" to "CD222E",
                    "green" to "116329",
                    "yellow" to "4D2D00",
                    "cyan" to "1B747B",
                ),
            "Gruvbox Dark" to
                mapOf(
                    "isDark" to "true",
                    "background" to "282828",
                    "secondaryBackground" to "363532",
                    "groupedBackground" to "1F1F1F",
                    "secondaryGroupedBackground" to "363532",
                    "separator" to "555148",
                    "foreground" to "EBDBB2",
                    "secondaryForeground" to "A79C82",
                    "accent" to "82A497",
                    "onAccent" to "282828",
                    "red" to "DD866D",
                    "green" to "A4A12F",
                    "yellow" to "D79921",
                    "cyan" to "7EA876",
                ),
            "Gruvbox Light" to
                mapOf(
                    "isDark" to "false",
                    "background" to "FBF1C7",
                    "secondaryBackground" to "EEE4BD",
                    "groupedBackground" to "EEE4BD",
                    "secondaryGroupedBackground" to "FDF9E6",
                    "separator" to "D4D0C1",
                    "foreground" to "3C3836",
                    "secondaryForeground" to "6A6559",
                    "accent" to "426D6F",
                    "onAccent" to "FBF1C7",
                    "red" to "C5251E",
                    "green" to "6B6828",
                    "yellow" to "7E612D",
                    "cyan" to "536C51",
                ),
            "Nord" to
                mapOf(
                    "isDark" to "true",
                    "background" to "2E3440",
                    "secondaryBackground" to "3C424E",
                    "groupedBackground" to "262B35",
                    "secondaryGroupedBackground" to "3C424E",
                    "separator" to "595F6B",
                    "foreground" to "D8DEE9",
                    "secondaryForeground" to "A8AEB9",
                    "accent" to "96B0CB",
                    "onAccent" to "2E3440",
                    "red" to "CCA3AD",
                    "green" to "A3BE8C",
                    "yellow" to "EBCB8B",
                    "cyan" to "88C0D0",
                ),
            "TokyoNight" to
                mapOf(
                    "isDark" to "true",
                    "background" to "1A1B26",
                    "secondaryBackground" to "262836",
                    "groupedBackground" to "12131B",
                    "secondaryGroupedBackground" to "262836",
                    "separator" to "3F4356",
                    "foreground" to "C0CAF5",
                    "secondaryForeground" to "878EAE",
                    "accent" to "7AA2F7",
                    "onAccent" to "1A1B26",
                    "red" to "F7768E",
                    "green" to "9ECE6A",
                    "yellow" to "E0AF68",
                    "cyan" to "7DCFFF",
                ),
        )
}
