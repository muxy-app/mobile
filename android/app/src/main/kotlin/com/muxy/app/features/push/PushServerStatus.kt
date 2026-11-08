package com.muxy.app.features.push

import uniffi.muxy_mobile.PushStatus

enum class PushServerStatus {
    ACTIVE,
    PENDING,
    REFUSED,
    DISABLED,
    UNSUPPORTED,
    OTHER,
    ;

    companion object {
        fun from(status: PushStatus): PushServerStatus =
            when (status) {
                PushStatus.ACTIVE -> ACTIVE
                PushStatus.PENDING -> PENDING
                PushStatus.REFUSED -> REFUSED
                PushStatus.DISABLED -> DISABLED
                PushStatus.OTHER -> OTHER
            }
    }
}

enum class PushAuthorization {
    NOT_DETERMINED,
    DENIED,
    ALLOWED,
}

object PushServerSummary {
    fun text(
        authorization: PushAuthorization,
        isEnabled: Boolean,
        status: PushServerStatus?,
        serverName: String,
    ): String {
        if (!isEnabled) return "Off"
        if (status == PushServerStatus.UNSUPPORTED) return "Update Muxy on $serverName to get notifications."
        if (status == PushServerStatus.DISABLED) return "Notifications are turned off on $serverName."
        return when (authorization) {
            PushAuthorization.DENIED -> "Notifications are off for Muxy in Android settings."
            PushAuthorization.NOT_DETERMINED -> "Allow notifications to hear from $serverName."
            PushAuthorization.ALLOWED -> allowedText(status, serverName)
        }
    }

    private fun allowedText(
        status: PushServerStatus?,
        serverName: String,
    ): String =
        when (status) {
            PushServerStatus.ACTIVE, PushServerStatus.OTHER -> "On"
            PushServerStatus.PENDING -> "On. $serverName is still reaching muxy.app."
            PushServerStatus.REFUSED -> "Google refused this device's notification token. Muxy will try again."
            PushServerStatus.DISABLED, PushServerStatus.UNSUPPORTED, null -> "Turns on the next time Muxy connects to $serverName."
        }
}
