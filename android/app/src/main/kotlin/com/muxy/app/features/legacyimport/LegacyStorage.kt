package com.muxy.app.features.legacyimport

enum class LegacyImportStage {
    PENDING,
    IMPORTED,
    COMPLETE,
}

interface LegacyStorage {
    var stage: LegacyImportStage

    fun value(key: String): String?

    fun secret(key: String): String?

    fun cleanup(): Boolean
}
