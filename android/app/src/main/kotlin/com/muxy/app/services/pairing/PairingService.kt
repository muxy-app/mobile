package com.muxy.app.services.pairing

import com.muxy.app.core.logging.Log
import com.muxy.app.models.Pairing
import com.muxy.app.networking.muxy1.MuxyClient
import com.muxy.app.networking.muxy1.protocol.AuthParams
import com.muxy.app.networking.muxy1.protocol.ErrorCode
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.networking.muxy1.protocol.PairingResult
import com.muxy.app.networking.muxy1.protocol.ProtocolException
import com.muxy.app.networking.muxy1.protocol.ProtocolJson
import com.muxy.app.networking.muxy1.protocol.RawTagged
import com.muxy.app.networking.muxy1.protocol.ResultType
import com.muxy.app.networking.muxy1.transport.TransportException
import com.muxy.app.networking.muxy1.transport.TransportFailure
import kotlinx.serialization.SerializationException
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed interface PairingError {
    data object ConnectionFailed : PairingError

    data object ApprovalDenied : PairingError

    data object ApprovalTimedOut : PairingError

    data object WrongToken : PairingError

    data object InvalidResponse : PairingError

    data class Server(
        val code: Int,
        val message: String,
    ) : PairingError
}

sealed interface PairingStatus {
    data object Idle : PairingStatus

    data object Connecting : PairingStatus

    data object Authenticating : PairingStatus

    data object AwaitingApproval : PairingStatus

    data class Paired(
        val pairing: Pairing,
    ) : PairingStatus

    data class Failed(
        val error: PairingError,
    ) : PairingStatus
}

fun interface PairingService {
    suspend fun pair(
        client: MuxyClient,
        params: AuthParams,
        onStatus: (PairingStatus) -> Unit,
    ): PairingStatus
}

class LivePairingService(
    private val approvalTimeout: Duration = APPROVAL_TIMEOUT,
) : PairingService {
    override suspend fun pair(
        client: MuxyClient,
        params: AuthParams,
        onStatus: (PairingStatus) -> Unit,
    ): PairingStatus {
        onStatus(PairingStatus.Authenticating)
        val status =
            try {
                PairingStatus.Paired(pairing(client.request(Method.AUTHENTICATE_DEVICE, encoded(params))))
            } catch (error: ProtocolException) {
                afterAuthenticationError(error, client, params, onStatus)
            } catch (error: IOException) {
                Log.pairing.error("Authentication failed", error)
                PairingStatus.Failed(PairingError.ConnectionFailed)
            } catch (error: InvalidPairingResponse) {
                Log.pairing.error("Authentication failed", error)
                PairingStatus.Failed(PairingError.InvalidResponse)
            }
        Log.pairing.debug("Pairing finished: ${status.javaClass.simpleName}")
        onStatus(status)
        return status
    }

    private suspend fun afterAuthenticationError(
        error: ProtocolException,
        client: MuxyClient,
        params: AuthParams,
        onStatus: (PairingStatus) -> Unit,
    ): PairingStatus {
        if (error.code == ErrorCode.FORBIDDEN) return PairingStatus.Failed(PairingError.WrongToken)
        if (error.code != ErrorCode.UNAUTHORIZED) return PairingStatus.Failed(mapped(error))
        onStatus(PairingStatus.AwaitingApproval)
        return try {
            PairingStatus.Paired(pairing(client.request(Method.PAIR_DEVICE, encoded(params), approvalTimeout)))
        } catch (pairError: ProtocolException) {
            PairingStatus.Failed(mapped(pairError))
        } catch (pairError: IOException) {
            Log.pairing.error("Pairing request failed", pairError)
            PairingStatus.Failed(approvalFailure(pairError))
        } catch (pairError: InvalidPairingResponse) {
            Log.pairing.error("Pairing request failed", pairError)
            PairingStatus.Failed(PairingError.InvalidResponse)
        }
    }

    private fun approvalFailure(error: IOException): PairingError {
        val timedOut = error is TransportException && error.failure == TransportFailure.TIMED_OUT
        return if (timedOut) PairingError.ApprovalTimedOut else PairingError.ConnectionFailed
    }

    private fun encoded(params: AuthParams) = ProtocolJson.encodeToJsonElement(AuthParams.serializer(), params)

    private fun pairing(result: RawTagged): Pairing {
        if (result.type != ResultType.PAIRING) throw InvalidPairingResponse()
        return try {
            result.decode(PairingResult.serializer()).pairing
        } catch (error: SerializationException) {
            throw InvalidPairingResponse(error)
        } catch (error: IllegalArgumentException) {
            throw InvalidPairingResponse(error)
        }
    }

    private fun mapped(error: ProtocolException): PairingError =
        when (error.code) {
            ErrorCode.FORBIDDEN -> PairingError.ApprovalDenied
            ErrorCode.PAIRING_TIMEOUT -> PairingError.ApprovalTimedOut
            else -> PairingError.Server(error.body.code, error.body.message)
        }

    private class InvalidPairingResponse(
        cause: Throwable? = null,
    ) : Exception("Unexpected pairing response", cause)

    private companion object {
        val APPROVAL_TIMEOUT = 120.seconds
    }
}
