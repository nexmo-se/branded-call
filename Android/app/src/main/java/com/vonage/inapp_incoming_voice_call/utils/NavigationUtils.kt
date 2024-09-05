package com.vonage.inapp_incoming_voice_call.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.vonage.inapp_incoming_voice_call.services.AudioRecorderService
import com.vonage.inapp_incoming_voice_call.views.CallActivity
import com.vonage.inapp_incoming_voice_call.views.LoginActivity
import com.vonage.inapp_incoming_voice_call.views.MainActivity

internal fun LoginActivity.navigateToMainActivity(extras: Bundle? = null){
    val intent = Intent(this, MainActivity::class.java)
    extras?.let {
        intent.putExtras(it)
    }
    startActivity(intent)
    finish()
}

internal fun MainActivity.navigateToCallActivity(extras: Bundle? = null){
    val intent = Intent(this, CallActivity::class.java)
    extras?.let {
        intent.putExtras(it)
    }
    startActivity(intent)
}

internal fun MainActivity.navigateToLoginActivity(extras: Bundle? = null){
    val intent = Intent(this, LoginActivity::class.java)
    extras?.let {
        intent.putExtras(it)
    }
    startActivity(intent)
    finish()
}

internal fun navigateToMainActivity(context: Context, extras: Bundle? = null){
    val intent = Intent(context, MainActivity::class.java)
    extras?.let {
        intent.putExtras(it)
    }
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
    context.startActivity(intent)
}

internal fun sendMessageToCallActivity(context: Context, extras: Bundle? = null){
    val intent = Intent(CallActivity.MESSAGE_ACTION)
    extras?.let {
        intent.putExtras(it)
    }
    context.sendBroadcast(intent)
}

internal fun notifyIsMutedToCallActivity(context: Context, isMuted: Boolean){
    val extras = Bundle()
    extras.putBoolean(CallActivity.IS_MUTED, isMuted)
    sendMessageToCallActivity(context, extras)
}

internal fun notifyIsOnHoldToCallActivity(context: Context, isOnHold: Boolean){
    val extras = Bundle()
    val state = if(isOnHold) CallActivity.CALL_ON_HOLD else CallActivity.CALL_ANSWERED
    extras.putString(CallActivity.CALL_STATE, state)
    sendMessageToCallActivity(context, extras)
}

internal fun notifyCallAnsweredToCallActivity(context: Context) {
    val extras = Bundle()
    extras.putString(CallActivity.CALL_STATE, CallActivity.CALL_ANSWERED)
    sendMessageToCallActivity(context, extras)
}

internal fun notifyCallReconnectingToCallActivity(context: Context) {
    val extras = Bundle()
    extras.putString(CallActivity.CALL_STATE, CallActivity.CALL_RECONNECTING)
    sendMessageToCallActivity(context, extras)
}

internal fun notifyCallReconnectedToCallActivity(context: Context) {
    val extras = Bundle()
    extras.putString(CallActivity.CALL_STATE, CallActivity.CALL_RECONNECTED)
    sendMessageToCallActivity(context, extras)
}

internal fun notifyCallDisconnectedToCallActivity(context: Context, isRemote:Boolean) {
    val extras = Bundle()
    extras.putString(CallActivity.CALL_STATE, CallActivity.CALL_DISCONNECTED)
    extras.putBoolean(CallActivity.IS_REMOTE_DISCONNECT, isRemote)
    sendMessageToCallActivity(context, extras)
}

internal fun notifyCallRingingToMainActivity(context: Context) {
    val extras = Bundle()
    extras.putString(CallActivity.CALL_STATE, CallActivity.CALL_RINGING)
    sendMessageToCallActivity(context, extras)
}

internal fun startForegroundService(context: Context, extras: Bundle? = null){
    val intent = Intent(context, AudioRecorderService::class.java)
    extras?.let {
        intent.putExtras(it)
    }
    context.startForegroundService(intent)
}

internal fun stopForegroundService(context: Context, extras: Bundle? = null){
    val intent = Intent(context, AudioRecorderService::class.java)
    extras?.let {
        intent.putExtras(it)
    }
    context.stopService(intent)
}