package com.vonage.inapp_incoming_voice_call.core

import android.content.Context
import android.net.Uri
import android.telecom.DisconnectCause
import android.telecom.TelecomManager
import com.google.firebase.messaging.RemoteMessage
import com.vonage.android_core.PushType
import com.vonage.android_core.VGClientConfig
import com.vonage.clientcore.core.api.*
import com.vonage.inapp_incoming_voice_call.models.User
import com.vonage.inapp_incoming_voice_call.telecom.CallConnection
import com.vonage.inapp_incoming_voice_call.App
import com.vonage.inapp_incoming_voice_call.push.PushNotificationService
import com.vonage.inapp_incoming_voice_call.utils.*
import com.vonage.inapp_incoming_voice_call.utils.notifyCallDisconnectedToCallActivity
import com.vonage.inapp_incoming_voice_call.utils.showToast
import com.vonage.voice.api.VoiceClient

/**
 * This Class will act as an interface
 * between the App and the Voice Client SDK
 */
class VoiceClientManager(private val context: Context) {
    private lateinit var client : VoiceClient
    private val coreContext = App.coreContext

    private fun initClient(){
        setDefaultLoggingLevel(LoggingLevel.Info)

        val config = VGClientConfig(ClientConfigRegion.AP)

        client = VoiceClient(context)
        client.setConfig(config)
        setClientListeners()
    }

    private fun setClientListeners(){

        client.setSessionErrorListener { err ->
            coreContext.activeCall?.run {
                selfDestroy()
                clearActiveCall()
            }
            when(err){
                SessionErrorReason.TransportClosed -> notifySessionErrorToCallActivity(context, "Session Error: TransportClosed")
                SessionErrorReason.TokenExpired -> notifySessionErrorToCallActivity(context, "Session Error: TokenExpired")
                SessionErrorReason.PingTimeout -> notifySessionErrorToCallActivity(context, "Session Error: PingTimeout")
            }
        }

        client.setCallInviteListener { callId, from, type ->
            // Temp Push notification bug:
            // reject incoming calls when there is an active one
            coreContext.activeCall?.let { return@setCallInviteListener }
            try {
                coreContext.telecomHelper.startIncomingCall(callId, from, type)
            } catch (e: Exception){
                abortInboundCall(callId, e.message)
            }
        }

        client.setOnCallHangupListener { callId, callQuality, reason ->
            takeIfActive(callId)?.apply {
                val (cause, isRemote) = when(reason) {
                    HangupReason.remoteReject -> DisconnectCause.REJECTED to true
                    HangupReason.remoteHangup -> DisconnectCause.REMOTE to true
                    HangupReason.localHangup -> DisconnectCause.LOCAL to false
                    HangupReason.mediaTimeout -> DisconnectCause.BUSY to true
                    HangupReason.remoteNoAnswerTimeout -> DisconnectCause.CANCELED to true
                }
                setDisconnected(DisconnectCause(cause))
                clearActiveCall()
                notifyCallDisconnectedToCallActivity(context, isRemote, reason)
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
                    else -> { return@apply }
                }
                setDisconnected(cause)
                clearActiveCall()
                notifyCallDisconnectedToCallActivity(context, true)
            }
        }

        client.setOnLegStatusUpdate { callId, legId, status ->
            println("Call $callId has received status update $status for leg $legId")
            takeIfActive(callId)?.apply {
                if(status == LegStatus.answered){
                    setActive()
                    notifyCallAnsweredToCallActivity(context)
                }
            }
        }

        client.setOnMutedListener { callId, legId, isMuted ->
            println("LegId $legId for Call $callId has been ${if(isMuted) "muted" else "unmuted"}")
            takeIf { callId == legId } ?: return@setOnMutedListener
            // Update Active Call Mute State
            takeIfActive(callId)?.isMuted = isMuted
        }

        client.setOnDTMFListener { callId, legId, digits ->
            println("LegId $legId has sent DTMF digits '$digits' to Call $callId")
        }
    }

    fun login(user: User, onSuccessCallback: ((String) -> Unit)? = null, onErrorCallback: (() -> Unit)? = null){
        initClient()
        client.createSession(user.token){ error, sessionId ->
            sessionId?.let {
                showToast(context, "Connected")
                registerDevicePushToken(user)
                coreContext.sessionId = it
                coreContext.user = user
                onSuccessCallback?.invoke(it)
            } ?: error?.let {
                onErrorCallback?.invoke()
                showToast(context, "Login Failed: ${error.message}")
            }
        }
    }

    fun logout(onSuccessCallback: (() -> Unit)? = null){
        if (coreContext.sessionId != null) {
            unregisterDevicePushToken(coreContext.user)
            coreContext.sessionId = null
            client.deleteSession { error ->
                error?.let {
                    showToast(context, "Error Logging Out: ${error.message}")
                } ?: run {
                    onSuccessCallback?.invoke()
                }
            }
        }
        coreContext.user = null
    }

    fun startOutboundCall(callContext: Map<String, String>? = null){
        client.serverCall(callContext) { err, callId ->
            err?.let {
                notifyCallErrorToCallActivity(context, "Error starting outbound call: $it")
                println("Error starting outbound call: $it")
            } ?: callId?.let {
                println("Outbound Call successfully started with Call ID: $it")
                val to = callContext?.get(Constants.CONTEXT_KEY_RECIPIENT) ?: Constants.DEFAULT_DIALED_NUMBER
                try {
                    coreContext.telecomHelper.startOutgoingCall(it, to)
                    notifyCallStartedToCallActivity(context)
                    mockOutgoingConnection(it, to)
                } catch (e: Exception){
                    abortOutboundCall(it, e.message)
                }
            }
        }
    }

    private fun abortOutboundCall(callId: CallId, message: String?){
        showToast(context, "Outgoing Call Error: $message")
        client.hangup(callId){}
        notifyCallDisconnectedToCallActivity(context, false, HangupReason.localHangup)
    }

    internal fun abortInboundCall(callId: CallId, message: String?){
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
    private fun mockOutgoingConnection(callId: CallId, to: String) : CallConnection {
        val connection = CallConnection(context, callId, to).apply {
            setAddress(Uri.parse(to), TelecomManager.PRESENTATION_ALLOWED)
            setCallerDisplayName(to, TelecomManager.PRESENTATION_ALLOWED)
            setDialing()
        }
        return connection
    }
    private fun registerDevicePushToken(user: User){
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

    private fun unregisterDevicePushToken(user: User?){
        coreContext.deviceId?.let {
            client.unregisterDevicePushToken(it) { err ->
                err?.let {
                    notifyCallErrorToCallActivity(context, "Error in unregistering Device Push Token: $err")
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

    fun answerCall(call: CallConnection, attempt: Int = 3){
        call.takeIfActive()?.apply {
            client.answer(callId) { err ->
                if (err != null) {
                    if (attempt > 0) {
                        answerCall(call, attempt -1)
                    }else {
                        println("Error Answering Call, Attempt ${attempt}: $err")
                        setDisconnected(DisconnectCause(DisconnectCause.ERROR))
                        clearActiveCall()
                        notifyCallErrorToCallActivity(context, "Unable to Answer the Call: $err")
                    }

                } else {
                    println("Answered call with id: $callId")
                    setActive()
                    notifyCallAnsweredToCallActivity(context)
                }
            }
        } ?: call.selfDestroy()
    }

    fun rejectCall(call: CallConnection, attempt: Int = 3){
        call.takeIfActive()?.apply {
            client.reject(callId){ err ->
                if (err != null) {
                    if (attempt > 0) {
                        rejectCall(call, attempt -1)
                    }else {
                        notifyCallErrorToCallActivity(context, "Unable to Reject the Call: $err")
                        println("Error Rejecting Call: $err")
                        setDisconnected(DisconnectCause(DisconnectCause.ERROR))
                        clearActiveCall()
                    }
                } else {
                    println("Rejected call with id: $callId")
                    setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
                    clearActiveCall()
                    notifyCallDisconnectedToCallActivity(context, false)
                }
            }
        } ?: call.selfDestroy()
    }

    fun hangupCall(call: CallConnection, attempt: Int = 3){
        call.takeIfActive()?.apply {
            client.hangup(callId) { err ->
                if (err != null) {
                    if (attempt > 0) {
                        hangupCall(call, attempt -1)
                    }else {
                        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
                        clearActiveCall()
                        notifyCallDisconnectedToCallActivity(context, false)
                        println("Error Hanging Up Call: $err")
                    }
                } else {
                    println("Hung up call with id: $callId")
                    setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
                    clearActiveCall()
                    notifyCallDisconnectedToCallActivity(context, false)
                }

            }
        } ?: call.selfDestroy()
    }

    fun muteCall(call: CallConnection){
        call.takeIfActive()?.apply {
            client.mute(callId) { err ->
                if (err != null) {
                    notifyCallErrorToCallActivity(context, "Error Muting Call: $err")
                    println("Error Muting Call: $err")
                } else {
                    println("Muted call with id: $callId")
                    CoreContext.CallMuteState.isMuted.postValue(true)
                }
            }
        }
    }

    fun unmuteCall(call: CallConnection){
        call.takeIfActive()?.apply {
            client.unmute(callId) { err ->
                if (err != null) {
                    notifyCallErrorToCallActivity(context, "Error Un-muting Call: $err")
                    println("Error Un-muting Call: $err")
                } else {
                    println("Un-muted call with id: $callId")
                    CoreContext.CallMuteState.isMuted.postValue(false)
                }
            }
        }
    }

    fun sendDtmf(call: CallConnection, digit: String){
        call.takeIfActive()?.apply {
            client.sendDTMF(callId, digit){ err ->
                if (err != null) {
                    notifyCallErrorToCallActivity(context, "Error in Sending DTMF '$digit': $err")
                    println("Error in Sending DTMF '$digit': $err")
                } else {
                    println("Sent DTMF '$digit' on call with id: $callId")
                }
            }
        }
    }

    // Utilities to filter active calls
    private fun takeIfActive(callId: CallId) : CallConnection? {
        return coreContext.activeCall?.takeIf { it.callId == callId }
    }
    private fun CallConnection.takeIfActive() : CallConnection? {
        return takeIfActive(callId)
    }
}