package com.vonage.inapp_incoming_voice_call.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.vonage.clientcore.core.api.HangupReason
import com.vonage.inapp_incoming_voice_call.views.CallActivity
import com.vonage.inapp_incoming_voice_call.views.LoginActivity

internal fun LoginActivity.navigateToCallActivity(extras: Bundle? = null){
    val intent = Intent(this, CallActivity::class.java)
    extras?.let {
        intent.putExtras(it)
    }
    startActivity(intent)
    finish()
}

internal fun CallActivity.navigateToLoginActivity(extras: Bundle? = null){
    val intent = Intent(this, LoginActivity::class.java)
    extras?.let {
        intent.putExtras(it)
    }
    startActivity(intent)
    finish()
}

internal fun navigateToCallActivity(context: Context, extras: Bundle? = null){
    val intent = Intent(context, CallActivity::class.java)
    extras?.let {
        intent.putExtras(it)
    }
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
    context.startActivity(intent)
}

internal fun notifyCallStartedToCallActivity(context: Context) {
    val extras = Bundle()
    extras.putString(CallActivity.CALL_STATE, CallActivity.CALL_STARTED)
    sendMessageToCallActivity(context, extras)
}

internal fun sendMessageToCallActivity(context: Context, extras: Bundle? = null){
    val intent = Intent(CallActivity.MESSAGE_ACTION)
    extras?.let {
        intent.putExtras(it)
    }
    context.sendBroadcast(intent)
}

internal fun notifyCallAnsweredToCallActivity(context: Context) {
    val extras = Bundle()
    extras.putString(CallActivity.CALL_STATE, CallActivity.CALL_ANSWERED)
    sendMessageToCallActivity(context, extras)
}

internal fun notifyCallDisconnectedToCallActivity(context: Context, isRemote:Boolean, reason: HangupReason? = null) {
    val extras = Bundle()
    extras.putString(CallActivity.CALL_STATE, CallActivity.CALL_DISCONNECTED)
    if (reason == HangupReason.remoteReject) {
        extras.putBoolean(CallActivity.IS_REMOTE_REJECT, true)
    }
    else if (reason == HangupReason.remoteNoAnswerTimeout) {
        extras.putBoolean(CallActivity.IS_REMOTE_TIMEOUT, true)
    }
    sendMessageToCallActivity(context, extras)
}

internal fun notifyCallErrorToCallActivity(context: Context, message:String) {
    val extras = Bundle()
    extras.putString(CallActivity.CALL_ERROR, message)
    sendMessageToCallActivity(context, extras)
}

internal fun notifySessionErrorToCallActivity(context: Context, message:String) {
    val extras = Bundle()
    extras.putString(CallActivity.SESSION_ERROR, message)
    sendMessageToCallActivity(context, extras)
}