package com.vonage.inapp_incoming_voice_call.services

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vonage.inapp_incoming_voice_call.App

class PushNotificationService : FirebaseMessagingService() {
    companion object {
        /**
         * Request FCM Token Explicitly.
         */
        fun requestToken(onSuccessCallback: ((String) -> Unit)? = null){
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    task.result?.let { token ->
                        println("FCM Device Push Token: $token")
                        App.coreContext.pushToken = token
                        onSuccessCallback?.invoke(token)
                    }
                }
            }
        }
    }
    override fun onNewToken(token: String) {
        println("New FCM Device Push Token: $token")
        App.coreContext.pushToken = token
    }
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Whenever a Push Notification comes in
        // If there is no active session then
        // Create one using the latest valid Auth Token and notify the ClientManager
        // Else notify the ClientManager directly

        App.coreContext.run {
            if (clientManager.sessionId == null) {
                val token = authToken ?: return@run
                clientManager.login(token) {
                    clientManager.processIncomingPush(remoteMessage)
                }
            } else {
                clientManager.processIncomingPush(remoteMessage)
            }
        }
    }
}