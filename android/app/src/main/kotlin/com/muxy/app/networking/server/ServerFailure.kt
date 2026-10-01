package com.muxy.app.networking.server

import uniffi.muxy_mobile.MobileException

sealed interface ServerFailure {
    data object InvalidLink : ServerFailure

    data object InvalidCredential : ServerFailure

    data object Unreachable : ServerFailure

    data object IdentityMismatch : ServerFailure

    data object Unauthorized : ServerFailure

    data object IncompatibleVersion : ServerFailure

    data object Unsupported : ServerFailure

    data object Timeout : ServerFailure

    data object Disconnected : ServerFailure

    data class Server(
        val reason: String,
    ) : ServerFailure

    data object Unknown : ServerFailure

    enum class Context {
        PAIRING,
        CONNECTING,
        REQUEST,
    }

    val isFatal: Boolean
        get() =
            when (this) {
                InvalidCredential, IdentityMismatch, Unauthorized, IncompatibleVersion, Unsupported -> true
                InvalidLink, Unreachable, Timeout, Disconnected, is Server, Unknown -> false
            }

    val requiresPairing: Boolean
        get() =
            when (this) {
                InvalidCredential, IdentityMismatch, Unauthorized -> true
                InvalidLink, Unreachable, IncompatibleVersion, Unsupported, Timeout, Disconnected, is Server, Unknown -> false
            }

    fun message(
        context: Context,
        serverName: String,
    ): String =
        when (this) {
            InvalidLink -> "This isn't a Muxy pairing code."
            InvalidCredential -> "The saved pairing for $serverName is damaged. Pair this phone again."
            Unreachable -> unreachableMessage(context, serverName)
            IdentityMismatch -> identityMismatchMessage(context)
            Unauthorized -> unauthorizedMessage(context, serverName)
            IncompatibleVersion -> "Update Muxy on your phone or computer."
            Unsupported -> "Update Muxy on your phone and computer to use this."
            Timeout -> "Muxy isn't responding."
            Disconnected -> "Disconnected from $serverName."
            is Server -> reason
            Unknown -> "Something went wrong. Try again."
        }

    companion object {
        fun from(error: Throwable): ServerFailure =
            when (error) {
                is MobileException.InvalidLink -> InvalidLink
                is MobileException.InvalidCredential -> InvalidCredential
                is MobileException.Unreachable -> Unreachable
                is MobileException.IdentityMismatch -> IdentityMismatch
                is MobileException.Unauthorized -> Unauthorized
                is MobileException.IncompatibleVersion -> IncompatibleVersion
                is MobileException.Unsupported -> Unsupported
                is MobileException.Timeout -> Timeout
                is MobileException.Disconnected -> Disconnected
                is MobileException.Server -> Server(error.reason)
                else -> Unknown
            }

        private fun unreachableMessage(
            context: Context,
            serverName: String,
        ): String {
            if (context != Context.PAIRING) {
                return "Can't reach $serverName. Check that it's awake, that mobile access is on, and that it's on the same network or VPN."
            }
            return "Can't reach the computer. Check that it's awake and on the same network or VPN."
        }

        private fun identityMismatchMessage(context: Context): String {
            if (context != Context.PAIRING) return "This computer's identity changed. Pair again."
            return "The computer that answered isn't the one showing this code."
        }

        private fun unauthorizedMessage(
            context: Context,
            serverName: String,
        ): String {
            if (context != Context.PAIRING) return "This phone isn't paired with $serverName anymore."
            return "This code expired, was already used, or was replaced. Show a new code on your computer."
        }
    }
}
