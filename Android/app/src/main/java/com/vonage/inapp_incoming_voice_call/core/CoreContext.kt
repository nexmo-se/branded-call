package com.vonage.inapp_incoming_voice_call.core

import android.content.Context
import com.google.gson.Gson
import com.vonage.inapp_incoming_voice_call.models.CallInfo
import com.vonage.inapp_incoming_voice_call.telecom.CallConnection
import com.vonage.inapp_incoming_voice_call.telecom.TelecomHelper
import com.vonage.inapp_incoming_voice_call.utils.Constants
import com.vonage.inapp_incoming_voice_call.utils.PrivatePreferences



/**
 * A singleton class for storing and accessing Core Application Data
 */
class CoreContext private constructor(context: Context) {
    val applicationContext: Context = context.applicationContext
    val telecomHelper: TelecomHelper by lazy { TelecomHelper(applicationContext) }
    val clientManager: VoiceClientManager by lazy { VoiceClientManager(applicationContext) }
    private val gson = Gson()
    var activeCall: CallConnection? = null
        set(value) {
            PrivatePreferences.set(PrivatePreferences.CALL_ID, value?.callId, applicationContext)
            PrivatePreferences.set(PrivatePreferences.CALLER_DISPLAY_NAME, value?.callerDisplayName, applicationContext)
            field = value
        }

    /**
     * The Last Active Call's details.
     * We persist them for Call reconnection.
     */
    val lastActiveCall: CallInfo?
        get() = PrivatePreferences.get(PrivatePreferences.CALL_ID, applicationContext)?.let { callId ->
            CallInfo(callId, PrivatePreferences.get(PrivatePreferences.CALLER_DISPLAY_NAME, applicationContext) ?: Constants.DEFAULT_DIALED_NUMBER)
        }

    /**
     * The last valid Vonage API Token used to create a session.
     */
    var authToken: String? get() {
        return PrivatePreferences.get(PrivatePreferences.AUTH_TOKEN, applicationContext)
    } set(value) {
        PrivatePreferences.set(PrivatePreferences.AUTH_TOKEN, value, applicationContext)
    }

    /**
     * The Firebase Push Token obtained via PushNotificationService.
     */
    var pushToken: String? get() {
        return PrivatePreferences.get(PrivatePreferences.PUSH_TOKEN, applicationContext)
    } set(value) {
        PrivatePreferences.set(PrivatePreferences.PUSH_TOKEN, value, applicationContext)
    }
    /**
     * The Device ID bound to the Push Token once it will be registered.
     * It will be used to unregister the Push Token later on.
     */
    var deviceId: String? get() {
        return PrivatePreferences.get(PrivatePreferences.DEVICE_ID, applicationContext)
    } set(value) {
        PrivatePreferences.set(PrivatePreferences.DEVICE_ID, value, applicationContext)
    }

    /**
     * For auto-login.
     */
    var user: com.vonage.clientcore.core.api.models.User? get() {
        val userString = PrivatePreferences.get(PrivatePreferences.USER, applicationContext)
        return gson.fromJson(userString, com.vonage.clientcore.core.api.models.User::class.java)
    } set(value) {
        val userString = gson.toJson(value)
        PrivatePreferences.set(PrivatePreferences.USER, userString, applicationContext)
    }
    companion object {
        // Volatile will guarantee a thread-safe & up-to-date version of the instance
        @Volatile
        private var instance: CoreContext? = null

        fun getInstance(context: Context): CoreContext {
            return instance ?: synchronized(this) {
                instance ?: CoreContext(context).also { instance = it }
            }
        }
    }
}