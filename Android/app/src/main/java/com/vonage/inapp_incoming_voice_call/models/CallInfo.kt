package com.vonage.inapp_incoming_voice_call.models

import com.vonage.voice.api.CallId

data class CallInfo(
    val callId: CallId,
    val callerDisplayName: String
)