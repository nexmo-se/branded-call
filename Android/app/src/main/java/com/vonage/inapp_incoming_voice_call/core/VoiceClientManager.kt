package com.vonage.inapp_incoming_voice_call.core

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.telecom.DisconnectCause
import android.telecom.TelecomManager
import com.google.firebase.messaging.RemoteMessage
import com.vonage.android_core.PushType
import com.vonage.android_core.VGClientConfig
import com.vonage.clientcore.core.api.*
import com.vonage.clientcore.core.conversation.VoiceChannelType
import com.vonage.inapp_incoming_voice_call.telecom.CallConnection
import com.vonage.inapp_incoming_voice_call.App
import com.vonage.inapp_incoming_voice_call.services.PushNotificationService
import com.vonage.inapp_incoming_voice_call.utils.*
import com.vonage.inapp_incoming_voice_call.utils.notifyCallDisconnectedToCallActivity
import com.vonage.inapp_incoming_voice_call.utils.showToast
import com.vonage.voice.api.VoiceClient
import java.lang.Exception


/**
 * This Class will act as an interface
 * between the App and the Voice Client SDK
 */
/**
 * This Class will act as an interface
 * between the App and the Voice Client SDK
 */
class VoiceClientManager(private val context: Context) {
    private lateinit var client : VoiceClient
    private val coreContext = App.coreContext
    var sessionId: String? = null
        private set
    var currentUser: com.vonage.clientcore.core.api.models.User? = null
        private set
    init {
        initClient()
        setClientListeners()
    }

    private fun initClient(){
        setDefaultLoggingLevel(LoggingLevel.Info)
        val config = VGClientConfig(ClientConfigRegion.AP)
        client = VoiceClient(context)
        client.setConfig(config)
    }

    private fun setClientListeners(){

        client.setSessionErrorListener { err ->
            val message = when(err){
                SessionErrorReason.TokenExpired -> "Token has expired"
                SessionErrorReason.TransportClosed -> "Socket connection has been closed"
                SessionErrorReason.PingTimeout -> "Ping timeout"
            }
            showToast(context, "Session Error: $message")
            // When the Socket Connection is closed
            // Reset sessionId & current user
            sessionId = null
            currentUser = null
            // And try to log in again using last valid credentials
            val token = coreContext.authToken ?: return@setSessionErrorListener
            login(token,
                onErrorCallback = {
                    // Cleanup any active call upon login failure
                    coreContext.activeCall?.run {
                        cleanUp(DisconnectCause(DisconnectCause.MISSED), false)
                    } ?: navigateToMainActivity(context)
                }
            )
        }

        client.setCallInviteListener { callId, from, type ->
            // Reject incoming calls when there is already an active one
            coreContext.activeCall?.let { return@setCallInviteListener }
            placeIncomingCall(callId, from, type)
            // NOTE: a foreground service needs to be started to record the audio when app is in the background
            startForegroundService(context)

            if(foregrounded()) {
                notifyCallRingingToMainActivity(context)
            }
        }

        client.setOnLegStatusUpdate { callId, legId, status ->
            println("Call $callId has received status update $status for leg $legId")
            takeIfActive(callId)?.apply {
                if(status == LegStatus.answered){
                    setAnswered()
                }
            }
        }

        client.setOnCallHangupListener { callId, callQuality, reason ->
            println("Call $callId has been hung up with reason: ${reason.name} and quality: $callQuality")
            takeIfActive(callId)?.apply {
                val (cause, isRemote) = when(reason) {
                    HangupReason.remoteReject -> DisconnectCause.REJECTED to true
                    HangupReason.remoteHangup -> DisconnectCause.REMOTE to true
                    HangupReason.localHangup -> DisconnectCause.LOCAL to false
                    HangupReason.mediaTimeout -> DisconnectCause.BUSY to true
                    HangupReason.remoteNoAnswerTimeout -> DisconnectCause.CANCELED to true
                }
                cleanUp(DisconnectCause(cause), isRemote)
            }
        }

        client.setOnCallMediaDisconnectListener { callId, reason ->
            println("Call $callId has been disconnected with reason: ${reason.name}")
            takeIfActive(callId)?.apply {
                cleanUp(DisconnectCause(DisconnectCause.ERROR), isRemote = false)
            }
        }

        client.setOnCallMediaReconnectingListener { callId ->
            println("Call $callId is reconnecting")
            takeIfActive(callId)?.apply {
                notifyCallReconnectingToCallActivity(context)
            }
        }

        client.setOnCallMediaReconnectionListener { callId ->
            println("Call $callId has successfully reconnected")
            takeIfActive(callId)?.apply {
                notifyCallReconnectedToCallActivity(context)
            }
        }

        client.setCallInviteCancelListener { callId, reason ->
            println("Invite to Call $callId has been canceled with reason: ${reason.name}")
            takeIfActive(callId)?.apply {
                val cause = when(reason){
                    VoiceInviteCancelReason.AnsweredElsewhere -> DisconnectCause(DisconnectCause.ANSWERED_ELSEWHERE)
                    VoiceInviteCancelReason.RejectedElsewhere -> DisconnectCause(DisconnectCause.REJECTED)
                    VoiceInviteCancelReason.RemoteCancel -> DisconnectCause(DisconnectCause.CANCELED)
                    VoiceInviteCancelReason.RemoteTimeout -> DisconnectCause(DisconnectCause.MISSED)
                }
                cleanUp(cause, true)
            } ?: stopForegroundService(context)
        }

        client.setCallTransferListener { callId, conversationId ->
            println("Call $callId has been transferred to conversation $conversationId")
            takeIfActive(callId)?.apply {
                setAnswered()
            }
        }

        client.setOnMutedListener { callId, legId, isMuted ->
            println("LegId $legId for Call $callId has been ${if(isMuted) "muted" else "unmuted"}")
            takeIf { callId == legId } ?: return@setOnMutedListener
            takeIfActive(callId)?.run {
                // Update Active Call Mute State
                toggleMuteState()
                takeUnless { it.isOnHold }?.run {
                    // Notify Call Activity
                    notifyIsMutedToCallActivity(context, isMuted)
                }
            }
        }

        client.setOnDTMFListener { callId, legId, digits ->
            println("LegId $legId has sent DTMF digits '$digits' to Call $callId")
        }
    }
    fun login(token: String, onErrorCallback: ((Exception) -> Unit)? = null, onSuccessCallback: ((String) -> Unit)? = null){
        client.createSession(token){ error, sessionId ->
            sessionId?.let {
                if (coreContext.deviceId == null) {
                    registerDevicePushToken()
                }
                this.sessionId = it
                coreContext.authToken = token
                getCurrentUser {
                    reconnectCall()
                    onSuccessCallback?.invoke(it)
                }
            } ?: error?.let {
                onErrorCallback?.invoke(it)
            }
        }
    }

    private fun getCurrentUser(completionHandler: (() -> Unit)? = null){
        client.getUser("me"){ _, user ->
            currentUser = user
            coreContext.user = user
            completionHandler?.invoke()
        }
    }

    fun logout(onSuccessCallback: (() -> Unit)? = null){
        unregisterDevicePushToken()
        client.deleteSession { error ->
            error?.let {
                showToast(context, "Error Logging Out: ${error.message}")
            } ?: run {
                sessionId = null
                currentUser = null
                coreContext.authToken = null
                coreContext.user = null
                coreContext.deviceId = null
                onSuccessCallback?.invoke()
            }
        }
    }

    fun startOutboundCall(callContext: Map<String, String>? = null){
        client.serverCall(callContext) { err, callId ->
            err?.let {
                println("Error starting outbound call: $it")
            } ?: callId?.let {
                println("Outbound Call successfully started with Call ID: $it")
                val callee = callContext?.get(Constants.EXTRA_KEY_TO)
                if (callee != null) {
                    placeOutgoingCall(it, callee)
                }
            }
        }
    }

    private fun reconnectCall(){
        coreContext.lastActiveCall?.run {
            client.reconnectCall(this.callId){ err ->
                err?.let {
                    showToast(context, "Error reconnecting call with $callerDisplayName: $it")
                } ?: run {
                    showToast(context, "Call with $callerDisplayName successfully reconnected")
                    coreContext.activeCall ?:
                    // Start a new Outgoing Call if there is not an active one
                    placeOutgoingCall(this.callId, this.callerDisplayName, isReconnected = true)
                }
            }
        }
    }

    private fun registerDevicePushToken(){
        val registerTokenCallback : (String) -> Unit = { token ->
            client.registerDevicePushToken(token) { err, deviceId ->
                err?.let {
                    println("Error in registering Device Push Token: $err")
                } ?: deviceId?.let {
                    coreContext.deviceId = deviceId
                    println("Device Push Token successfully registered with Device ID: $deviceId")
                }
            }
        }
        coreContext.pushToken?.let {
            registerTokenCallback(it)
        } ?: PushNotificationService.requestToken {
            registerTokenCallback(it)
        }
    }

    private fun unregisterDevicePushToken(){
        coreContext.deviceId?.let {
            client.unregisterDevicePushToken(it) { err ->
                err?.let {
                    println("Error in unregistering Device Push Token: $err")
                }
            }
        }
    }

    fun processIncomingPush(remoteMessage: RemoteMessage) {
        val dataString = remoteMessage.data.toString()
        val type: PushType = VoiceClient.getPushNotificationType(dataString)
        if (type == PushType.INCOMING_CALL) {
            // This method will trigger the Client's Call Invite Listener
            client.processPushCallInvite(dataString)
        }
    }

    fun answerCall(call: CallConnection){
        call.takeIfActive()?.apply {
            client.answer(callId) { err ->
                if (err != null) {
                    println("Error Answering Call: $err")
                    cleanUp(DisconnectCause(DisconnectCause.ERROR), false)
                } else {
                    println("Answered call with id: $callId")
                    setAnswered()
                }
            }
        } ?: call.selfDestroy()
    }

    fun rejectCall(call: CallConnection){
        call.takeIfActive()?.apply {
            client.reject(callId){ err ->
                if (err != null) {
                    println("Error Rejecting Call: $err")
                    cleanUp(DisconnectCause(DisconnectCause.ERROR), false)
                } else {
                    println("Rejected call with id: $callId")
                    cleanUp(DisconnectCause(DisconnectCause.REJECTED), false)
                }
            }
        } ?: call.selfDestroy()
    }

    fun hangupCall(call: CallConnection){
        call.takeIfActive()?.apply {
            client.hangup(callId) { err ->
                if (err != null) {
                    println("Error Hanging Up Call: $err")
                    // If there has been an error
                    // the onCallHangupListener will not be invoked,
                    // hence the Call needs to be explicitly disconnected
                    cleanUp(DisconnectCause(DisconnectCause.LOCAL), false)
                } else {
                    println("Hung up call with id: $callId")
                }
            }
        } ?: call.selfDestroy()
    }

    fun muteCall(call: CallConnection){
        call.takeIfActive()?.apply {
            client.mute(callId) { err ->
                if (err != null) {
                    println("Error Muting Call: $err")
                } else {
                    println("Muted call with id: $callId")
                }
            }
        }
    }

    fun unmuteCall(call: CallConnection){
        call.takeIfActive()?.apply {
            client.unmute(callId) { err ->
                if (err != null) {
                    println("Error Un-muting Call: $err")
                } else {
                    println("Un-muted call with id: $callId")
                }
            }
        }
    }

    fun enableNoiseSuppression(call: CallConnection){
        call.takeIfActive()?.apply {
            client.enableNoiseSuppression(callId) { err ->
                err?.let {
                    println("Error enabling noise suppression on Call: $it")
                } ?: println("Enabled noise suppression on Call with id: $callId")
            }
        }
    }

    fun disableNoiseSuppression(call: CallConnection){
        call.takeIfActive()?.apply {
            client.disableNoiseSuppression(callId) { err ->
                err?.let {
                    println("Error disabling noise suppression on Call: $it")
                } ?: println("Disabled noise suppression on Call with id: $callId")
            }
        }
    }

    fun sendDtmf(call: CallConnection, digit: String){
        call.takeIfActive()?.apply {
            client.sendDTMF(callId, digit){ err ->
                if (err != null) {
                    println("Error in Sending DTMF '$digit': $err")
                } else {
                    println("Sent DTMF '$digit' on call with id: $callId")
                }
            }
        }
    }

    fun holdCall(call: CallConnection){
        call.takeIfActive()?.apply{
            client.mute(callId){ error ->
                error?.let {
                    println("Error muting in holdCall with id: $callId")
                } ?: run {
                    client.enableEarmuff(callId){ error ->
                        error?.let {
                            println("Error enabling earmuff in holdCall with id: $callId")
                        } ?: run {
                            println("Call $callId successfully put on hold")
                            toggleHoldState()
                            notifyIsOnHoldToCallActivity(context, true)
                        }
                    }
                }
            }
        }
    }

    fun unholdCall(call: CallConnection){
        call.takeIfActive()?.apply{
            client.unmute(callId){ error ->
                error?.let {
                    println("Error unmuting in unholdCall with id: $callId")
                } ?: run {
                    client.disableEarmuff(callId){ error ->
                        error?.let {
                            println("Error disabling earmuff in unholdCall with id: $callId")
                        } ?: run {
                            println("Call $callId successfully removed from hold")
                            toggleHoldState()
                            notifyIsOnHoldToCallActivity(context, false)
                        }
                    }
                }
            }
        }
    }

    /*
     * Utilities to handle errors on telecomHelper
     */
    private fun placeOutgoingCall(callId:CallId, callee: String, isReconnected:Boolean = false){
        try {
            coreContext.telecomHelper.startOutgoingCall(callId, callee, isReconnected)
            // If ConnectionService does not respond within 3 seconds
            // then mock an outgoing connection
            TimerManager.startTimer(TimerManager.CONNECTION_SERVICE_TIMER, 3000){
                mockOutgoingConnection(callId, callee, isReconnected)
            }
        } catch (e: Exception){
            abortOutboundCall(callId, e.message)
        }
    }

    private fun placeIncomingCall(callId: CallId, caller: String, type: VoiceChannelType){
        try {
            coreContext.telecomHelper.startIncomingCall(callId, caller, type)
        } catch (e: Exception){
            abortInboundCall(callId, e.message)
        }
    }

    private fun abortOutboundCall(callId: CallId, message: String?){
        showToast(context, "Outgoing Call Error: $message")
        client.hangup(callId){}
        notifyCallDisconnectedToCallActivity(context, false)
    }

    private fun abortInboundCall(callId: CallId, message: String?){
        showToast(context, "Incoming Call Error: $message")
        client.reject(callId){}
        notifyCallDisconnectedToCallActivity(context, false)
    }

    /**
     *  ConnectionService not working on some devices (e.g. Samsung)
     *  is a known issue.
     *
     *  This method will mock
     *  `ConnectionService#onCreateOutgoingConnection`
     *  and allow outgoing calls without interacting with the Telecom framework.
     */
    private fun mockOutgoingConnection(callId: CallId, to: String, isReconnected: Boolean) : CallConnection {
        showToast(context, "ConnectionService Not Available")
        val connection = CallConnection(callId, to).apply {
            setAddress(Uri.parse(to), TelecomManager.PRESENTATION_ALLOWED)
            setCallerDisplayName(to, TelecomManager.PRESENTATION_ALLOWED)
            setDialing()
            if(isReconnected){ setAnswered() }
        }
        return connection
    }

    /*
     * Utilities to filter active calls
     */
    private fun takeIfActive(callId: CallId) : CallConnection? {
        return coreContext.activeCall?.takeIf { it.callId == callId }
    }
    private fun CallConnection.takeIfActive() : CallConnection? {
        return takeIfActive(callId)
    }

    private fun CallConnection.setAnswered(){
        this.setActive()
        notifyCallAnsweredToCallActivity(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(Constants.NOTIFICATION_ID)
    }

    private fun CallConnection.cleanUp(disconnectCause: DisconnectCause, isRemote: Boolean){
        this.disconnect(disconnectCause)
        notifyCallDisconnectedToCallActivity(context, isRemote)
        stopForegroundService(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(Constants.NOTIFICATION_ID)
    }

    private fun foregrounded(): Boolean {
        val appProcessInfo = ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(appProcessInfo);
        return (appProcessInfo.importance == IMPORTANCE_FOREGROUND || appProcessInfo.importance == IMPORTANCE_VISIBLE)
    }
}