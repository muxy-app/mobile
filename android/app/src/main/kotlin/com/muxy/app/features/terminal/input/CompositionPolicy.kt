package com.muxy.app.features.terminal.input

import java.util.Locale

object CompositionPolicy {
    private val deferredLanguages = setOf("ja", "zh", "ko")

    fun composesEagerly(
        languageTag: String,
        legacyLocale: String,
    ): Boolean {
        val tag = languageTag.ifEmpty { legacyLocale.replace('_', '-') }
        return Locale.forLanguageTag(tag).language !in deferredLanguages
    }
}
