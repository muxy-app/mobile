package com.muxy.app.features.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.muxy.app.app.MuxyApplication
import kotlinx.coroutines.runBlocking

class MuxyMessagingService : FirebaseMessagingService() {
    private val push: PushCenter
        get() = (application as MuxyApplication).container.push

    override fun onNewToken(token: String) {
        push.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        runBlocking { push.deliver(message.data) }
    }
}
