package com.muxy.app.features.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.muxy.app.R
import com.muxy.app.app.MainActivity

interface PushNotifications {
    fun post(notification: PushNotification)

    fun cancel(tag: String)
}

class PushNotifier(
    private val context: Context,
) : PushNotifications {
    private val manager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(ATTENTION_CHANNEL, "Needs attention", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "An agent is waiting for you."
                },
                NotificationChannel(COMPLETED_CHANNEL, "Finished", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "An agent finished working."
                },
            ),
        )
    }

    override fun post(notification: PushNotification) {
        if (!canPost()) return
        val tag = notification.tag ?: notification.target?.let { "event:${it.eventId}" } ?: "event:${System.nanoTime()}"
        val channel = channel(notification.kind)
        val built =
            NotificationCompat
                .Builder(context, channel)
                .setSmallIcon(R.drawable.ic_terminal)
                .setContentTitle(notification.alert.title)
                .setContentText(notification.alert.body)
                .setSubText(notification.alert.subText)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(contentIntent(tag, notification.target))
                .apply { notification.group?.let(::setGroup) }
                .build()
        manager.notify(tag, NOTIFICATION_ID, built)
        notification.group?.let { postSummary(it, channel) }
    }

    override fun cancel(tag: String) {
        val group =
            manager.activeNotifications
                .firstOrNull { it.tag == tag && it.id == NOTIFICATION_ID }
                ?.notification
                ?.group
        manager.cancel(tag, NOTIFICATION_ID)
        group?.let { cancelSummaryIfEmpty(it, cancelledTag = tag) }
    }

    private fun postSummary(
        group: String,
        channel: String,
    ) {
        val summary =
            NotificationCompat
                .Builder(context, channel)
                .setSmallIcon(R.drawable.ic_terminal)
                .setGroup(group)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(contentIntent(summaryTag(group), null))
                .build()
        manager.notify(summaryTag(group), SUMMARY_ID, summary)
    }

    private fun cancelSummaryIfEmpty(
        group: String,
        cancelledTag: String,
    ) {
        val remaining =
            manager.activeNotifications.any {
                it.id == NOTIFICATION_ID && it.tag != cancelledTag && it.notification.group == group
            }
        if (remaining) return
        manager.cancel(summaryTag(group), SUMMARY_ID)
    }

    private fun contentIntent(
        tag: String,
        target: PushTarget?,
    ): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java)
                .setAction(PushIntents.ACTION_OPEN)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        target?.let { PushIntents.extras(it).forEach { (name, value) -> intent.putExtra(name, value) } }
        return PendingIntent.getActivity(
            context,
            tag.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return manager.areNotificationsEnabled()
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun channel(kind: PushKind): String =
        when (kind) {
            PushKind.ATTENTION -> ATTENTION_CHANNEL
            PushKind.COMPLETED -> COMPLETED_CHANNEL
        }

    private fun summaryTag(group: String): String = "group:$group"

    private companion object {
        const val ATTENTION_CHANNEL = "attention"
        const val COMPLETED_CHANNEL = "completed"
        const val NOTIFICATION_ID = 1
        const val SUMMARY_ID = 2
    }
}
