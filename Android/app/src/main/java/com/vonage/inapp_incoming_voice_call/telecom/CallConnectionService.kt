package com.vonage.inapp_incoming_voice_call.telecom

import android.content.Context
import android.net.Uri
import android.telecom.*
import android.util.Log
import com.vonage.inapp_incoming_voice_call.utils.Constants
import com.vonage.inapp_incoming_voice_call.utils.showToast

/**
 * A custom ConnectionService to handle incoming & outgoing calls.
 */
class CallConnectionService() : ConnectionService() {
    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        /*
        This gets the push info from the ClientManager
        and pulls out the 'from' number specified in your NCCO.
        A CallConnection Object is also created.
        This is how the system tells you
        the user has initiated an action with the System UI.
         */
        val bundle = request!!.extras
        val callId = bundle.getString(Constants.EXTRA_KEY_CALL_ID)!!
        val from = bundle.getString(Constants.EXTRA_KEY_FROM)

        val connection = CallConnection(applicationContext, callId, from).apply {
            setAddress(Uri.parse("Vonage"), TelecomManager.PRESENTATION_ALLOWED)
            setCallerDisplayName(from, TelecomManager.PRESENTATION_ALLOWED)
            setRinging()
        }
        return connection
    }

    override fun onCreateIncomingConnectionFailed(connectionManagerPhoneAccount: PhoneAccountHandle?, request: ConnectionRequest?
    ) {
        Log.e("onCreateIncomingFailed:",request.toString())
        showToast(applicationContext, "onCreateIncomingConnectionFailed")
    }
}