package com.vonage.inapp_incoming_voice_call.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.DisconnectCause
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.vonage.clientcore.core.api.CallId
import com.vonage.inapp_incoming_voice_call.App
import com.vonage.inapp_incoming_voice_call.R
import com.vonage.inapp_incoming_voice_call.views.CallActivity


/**
 * A Connection class used to initiate a connection
 * when a User receives an incoming or outgoing call
 */
class CallConnection(val context: Context, val callId: CallId, val from: String?) : Connection() {
    private val coreContext = App.coreContext
    private val clientManager = coreContext.clientManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager


    var isMuted = false
    init {
        // Update active call only if current is null
        coreContext.activeCall = coreContext.activeCall ?: this

        val properties = connectionProperties or PROPERTY_SELF_MANAGED
        connectionProperties = properties

        val capabilities = connectionCapabilities or CAPABILITY_MUTE or CAPABILITY_SUPPORT_HOLD or CAPABILITY_HOLD
        connectionCapabilities = capabilities

        audioModeIsVoip = true
    }
    override fun onAnswer() {
        clientManager.answerCall(this)
    }

    override fun onReject() {
        clientManager.rejectCall(this)
    }

    override fun onDisconnect() {
        clientManager.hangupCall(this)
    }

    override fun onAbort() {
        clientManager.hangupCall(this)
    }

    override fun onCallAudioStateChanged(state: CallAudioState?) {
        state ?: return
        // Trigger mute/unmute only if states are not consistent
        val shouldMute = state.isMuted
        if (shouldMute != this.isMuted) {
            val muteAction = if (shouldMute) clientManager::muteCall else clientManager::unmuteCall
            muteAction(this)
        }
        val route = state.route
        println("isMuted: $isMuted, route: $route")
    }

    override fun onPlayDtmfTone(c: Char) {
        println("Dtmf Char received: $c")
        clientManager.sendDtmf(this, c.toString())
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onShowIncomingCallUi() {
        super.onShowIncomingCallUi()

        // Create the notification channel with a unique ID
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel("notification-channel", "notification-name", importance)
        // We'll use the default system ringtone for our incoming call notification channel.  You can
        // use your own audio resource here.
        // We'll use the default system ringtone for our incoming call notification channel.  You can
        // use your own audio resource here.
        val ringtoneUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        channel.setSound(
            ringtoneUri,
            AudioAttributes.Builder() // Setting the AudioAttributes is important as it identifies the purpose of your
                // notification sound.
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        notificationManager.createNotificationChannel(channel)

        // Create Notification Channel if it doesn't exist
        notificationManager.getNotificationChannel("notification-channel")

        // Answer Action
        val answerIntent = Intent(CallActivity.MESSAGE_ACTION).apply {
            putExtra(CallActivity.CALL_ACTION, CallActivity.ANSWER_ACTION)
        }

        val answerPendingIntent: PendingIntent =
            PendingIntent.getBroadcast(context, 0, answerIntent, PendingIntent.FLAG_MUTABLE)

        // Reject Action
        val rejectIntent = Intent(CallActivity.MESSAGE_ACTION).apply {
            putExtra(CallActivity.CALL_ACTION, CallActivity.REJECT_ACTION)
        }

        val rejectPendingIntent: PendingIntent =
            PendingIntent.getBroadcast(context, 1, rejectIntent, PendingIntent.FLAG_MUTABLE)

        // Create the notification builder
        val bm = BitmapFactory.decodeResource(context.resources, R.drawable.vonage_logo)

        val builder = NotificationCompat.Builder(context, "notification-channel")
            .setSmallIcon(R.drawable.ic_stat_vonage_logo)
            .setLargeIcon(bm)
            .setContentTitle("Vonage")
            .setContentText(from)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .addAction(R.drawable.vonage_logo, "Answer", answerPendingIntent)
            .addAction(R.drawable.vonage_logo, "Reject", rejectPendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)

        val notification = builder.build()
        notification.flags = notification.flags or Notification.FLAG_NO_CLEAR
        // Show the notification
        notificationManager.notify(123, notification)
    }

    fun selfDestroy(){
        println("[$callId] Connection  is no more useful, destroying it")
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
    }

    fun clearActiveCall(){
        // Reset active call only if it was the current one
        coreContext.activeCall?.takeIf { it == this }?.let { coreContext.activeCall = null }
    }


}