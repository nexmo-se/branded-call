package com.vonage.inapp_incoming_voice_call.telecom

import android.net.Uri
import android.telecom.*
import android.util.Log
import com.vonage.inapp_incoming_voice_call.utils.Constants
import com.vonage.inapp_incoming_voice_call.utils.TimerManager
import com.vonage.inapp_incoming_voice_call.utils.showToast
/**
 * A custom ConnectionService to handle incoming & outgoing calls.
 */
class CallConnectionService : ConnectionService() {
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
        val connection = CallConnection(callId, from ?: "").apply {
            setAddress(Uri.parse(from), TelecomManager.PRESENTATION_ALLOWED)
            setCallerDisplayName(from, TelecomManager.PRESENTATION_ALLOWED)
            setRinging()
        }
        return connection
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        TimerManager.cancelTimer(TimerManager.CONNECTION_SERVICE_TIMER)
        val bundle = request!!.extras
        val callId = bundle.getString(Constants.EXTRA_KEY_CALL_ID)!!
        val to = bundle.getString(Constants.EXTRA_KEY_TO)
        val isReconnected = bundle.getBoolean(Constants.EXTRA_KEY_RECONNECTED)
        val connection = CallConnection(callId, to ?: "").apply {
            setAddress(Uri.parse(to), TelecomManager.PRESENTATION_ALLOWED)
            setCallerDisplayName(to, TelecomManager.PRESENTATION_ALLOWED)
            setDialing()
            if(isReconnected){ setActive() }
        }
        return connection
    }

    override fun onCreateIncomingConnectionFailed(connectionManagerPhoneAccount: PhoneAccountHandle?, request: ConnectionRequest?
    ) {
        Log.e("onCreateIncomingFailed:",request.toString())
        showToast(applicationContext, "onCreateIncomingConnectionFailed")
    }

    override fun onCreateOutgoingConnectionFailed(connectionManagerPhoneAccount: PhoneAccountHandle?, request: ConnectionRequest?) {
        TimerManager.cancelTimer(TimerManager.CONNECTION_SERVICE_TIMER)
        Log.e("onCreateOutgoingFailed:",request.toString())
        showToast(applicationContext, "onCreateOutgoingConnectionFailed")
    }
}