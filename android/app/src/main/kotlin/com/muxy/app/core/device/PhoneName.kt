package com.muxy.app.core.device

import android.content.Context
import android.os.Build
import android.provider.Settings

fun interface PhoneName {
    fun current(): String

    companion object {
        const val FALLBACK = "Android"

        fun resolve(
            deviceName: String?,
            model: String?,
        ): String = deviceName?.trim()?.takeIf(String::isNotEmpty) ?: model?.trim()?.takeIf(String::isNotEmpty) ?: FALLBACK
    }
}

class SystemPhoneName(
    private val context: Context,
) : PhoneName {
    override fun current(): String =
        PhoneName.resolve(Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME), Build.MODEL)
}
