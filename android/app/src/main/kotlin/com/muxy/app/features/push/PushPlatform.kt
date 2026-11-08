package com.muxy.app.features.push

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.muxy.app.core.logging.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

interface NotificationPermission {
    val isGranted: Boolean
    val needsRuntimeRequest: Boolean
}

class SystemNotificationPermission(
    private val context: Context,
) : NotificationPermission {
    override val isGranted: Boolean
        get() = NotificationManagerCompat.from(context).areNotificationsEnabled()

    override val needsRuntimeRequest: Boolean
        get() =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
}

interface PushTokenSource {
    suspend fun token(): String?

    suspend fun replace(): String?
}

class FirebasePushTokenSource : PushTokenSource {
    override suspend fun token(): String? =
        suspendCancellableCoroutine { continuation ->
            val messaging = FirebaseMessaging.getInstance()
            messaging.isAutoInitEnabled = true
            messaging.token.addOnCompleteListener { task ->
                if (!task.isSuccessful) Log.push.error("Getting the FCM token failed", task.exception)
                continuation.resume(if (task.isSuccessful) task.result else null)
            }
        }

    override suspend fun replace(): String? {
        val deleted =
            suspendCancellableCoroutine { continuation ->
                FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener { task ->
                    if (!task.isSuccessful) Log.push.error("Replacing the FCM token failed", task.exception)
                    continuation.resume(task.isSuccessful)
                }
            }
        if (!deleted) return null
        return token()
    }
}
