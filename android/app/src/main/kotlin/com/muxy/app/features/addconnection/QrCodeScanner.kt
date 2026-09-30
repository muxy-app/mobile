package com.muxy.app.features.addconnection

import android.content.Context
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.muxy.app.core.logging.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed interface QrScanResult {
    data class Scanned(
        val text: String,
    ) : QrScanResult

    data object Cancelled : QrScanResult

    data object Unavailable : QrScanResult
}

class QrCodeScanner(
    context: Context,
) {
    private val scanner =
        GmsBarcodeScanning.getClient(
            context,
            GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
        )

    suspend fun scan(): QrScanResult =
        suspendCancellableCoroutine { continuation ->
            scanner
                .startScan()
                .addOnSuccessListener { barcode ->
                    val text = barcode.rawValue
                    continuation.resume(if (text == null) QrScanResult.Cancelled else QrScanResult.Scanned(text))
                }.addOnCanceledListener { continuation.resume(QrScanResult.Cancelled) }
                .addOnFailureListener { error -> continuation.resume(result(error)) }
        }

    private fun result(error: Exception): QrScanResult {
        if ((error as? MlKitException)?.errorCode == MlKitException.CODE_SCANNER_CANCELLED) return QrScanResult.Cancelled
        Log.pairing.error("The QR code scanner failed", error)
        return QrScanResult.Unavailable
    }
}
