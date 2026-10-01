package com.muxy.app.features.terminal.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositionPolicyTest {
    @Test
    fun chineseJapaneseAndKoreanKeyboardsHoldTheirComposition() {
        listOf("ja-JP", "zh-CN", "ko").forEach { assertFalse(it, CompositionPolicy.composesEagerly(it, "")) }
    }

    @Test
    fun otherKeyboardsSendTheirCompositionAsTyped() {
        assertTrue(CompositionPolicy.composesEagerly("en-US", ""))
        assertTrue(CompositionPolicy.composesEagerly("", ""))
    }

    @Test
    fun aLegacyLocaleIsUsedWhenTheLanguageTagIsMissing() {
        assertFalse(CompositionPolicy.composesEagerly("", "ja_JP"))
        assertTrue(CompositionPolicy.composesEagerly("", "de_DE"))
    }
}
