package com.vonage.inapp_incoming_voice_call.views

import android.annotation.SuppressLint
import android.content.*
import android.os.Bundle
import android.os.SystemClock
import android.telecom.Connection
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.vonage.clientcore.core.api.models.Username
import com.vonage.inapp_incoming_voice_call.App
import com.vonage.inapp_incoming_voice_call.R
import com.vonage.inapp_incoming_voice_call.databinding.ActivityCallBinding
import com.vonage.inapp_incoming_voice_call.utils.*


class CallActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCallBinding
    private val coreContext = App.coreContext
    private val clientManager = coreContext.clientManager

    /**
     * When an Active Call gets disconnected
     * (either remotely or locally) it will be null.
     * Hence, we use these variables to manually update the UI in that case
     */
    private var fallbackState: Int? = null

    private var fallbackUsername: Username? = null

    /**
     * This Local BroadcastReceiver will be used
     * to receive messages from other activities
     */
    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Handle the messages here

            // Call Is Muted Update
            intent?.getBooleanExtra(IS_MUTED, false)?.let {
                updateMuteUI(it)
            }
            // Call Remotely Disconnected
            intent?.getBooleanExtra(IS_REMOTE_DISCONNECT, false)?.let {
                fallbackState = if(it) Connection.STATE_DISCONNECTED else null
            }

            // Call State Updated
            intent?.getStringExtra(CALL_STATE)?.let { callStateExtra ->
                setStateUI(callStateExtra)
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        handleIntent(intent)
        setBindings()

        // Set toolbar View
        val toolbar = binding.tbCall
        toolbar.btLogout.visibility = View.GONE

        ContextCompat.registerReceiver(
            this,
            messageReceiver,
            IntentFilter(MESSAGE_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onResume() {
        super.onResume()
        if (coreContext.activeCall == null) {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.cmCallTimer.stop();
        unregisterReceiver(messageReceiver)
    }

    /**
     * An Intent with extras will be received if
     * the App received an incoming call while device was locked.
     */
    private fun handleIntent(intent: Intent?){
        intent ?: return
        val from = intent.getStringExtra(Constants.EXTRA_KEY_FROM) ?: return
        fallbackUsername = from
        fallbackState = Connection.STATE_RINGING
    }

    private fun setBindings(){
        setButtonListeners()
        setStateUI()
        setUserUI()
    }

    private fun setButtonListeners() = binding.run{
        // Button Listeners
        btAnswer.setOnClickListener { onAnswer() }
        btReject.setOnClickListener { onReject() }
        btHangUp.setOnClickListener { onHangup() }
        btMute.setOnClickListener { onMute() }
    }

    private fun setUserUI() = binding.run{
        // Username Label
        tvCallFrom.text = coreContext.activeCall?.callerDisplayName ?: fallbackUsername
    }

    @SuppressLint("SetTextI18n")
    private fun setStateUI(callStateExtra: String? = null) = binding.run {
        val callState = coreContext.activeCall?.state ?: fallbackState
        //Buttons Visibility
        if(callState == Connection.STATE_RINGING){
            btAnswer.visibility = View.VISIBLE
            btReject.visibility = View.VISIBLE
            btHangUp.visibility = View.GONE
            btMute.visibility = View.GONE
        }
        else if (callState == Connection.STATE_ACTIVE || callState == Connection.STATE_DIALING) {
            btAnswer.visibility = View.GONE
            btReject.visibility = View.GONE
            btHangUp.visibility = View.VISIBLE
            btMute.visibility = View.VISIBLE
        }
        // Buttons Toggled
        coreContext.activeCall?.run {
            updateMuteUI(isMuted)
        }
        if (callState == Connection.STATE_ACTIVE) {
            // Start Timer
            binding.cmCallTimer.base = SystemClock.elapsedRealtime()
            binding.cmCallTimer.visibility = View.VISIBLE
            binding.cmCallTimer.format = "Call duration - %s";
            binding.cmCallTimer.start()
        }
        if (callStateExtra == CALL_RECONNECTING) {
            tvCallState.text = "Call Reconnecting..."
        } else {
            tvCallState.text = ""
        }

        if(callStateExtra == CALL_DISCONNECTED){
            finish()
        }
    }

    private fun onAnswer(){
        coreContext.activeCall?.let { call ->
            clientManager.answerCall(call)
        }
    }

    private fun onReject(){
        coreContext.activeCall?.let { call ->
            clientManager.rejectCall(call)
        }
    }

    private fun onHangup(){
        coreContext.activeCall?.let { call ->
            clientManager.hangupCall(call)
        }
    }

    private fun onMute(){
        coreContext.activeCall?.let { call ->
            if(call.isMuted){
                clientManager.unmuteCall(call)
            } else {
                clientManager.muteCall(call)
            }
        }
    }


    private fun updateMuteUI(isMuted: Boolean){

        var icon = R.drawable.ic_baseline_mic_on
        if (isMuted) {
            icon = R.drawable.ic_baseline_mic_off
        }

        val top = ResourcesCompat.getDrawable(resources, icon , null)
        binding.btMute.setCompoundDrawablesWithIntrinsicBounds(null, top, null, null)
    }

    companion object {
        const val MESSAGE_ACTION = "com.vonage.inapp_incoming_voice_call.MESSAGE_TO_CALL_ACTIVITY"
        const val IS_MUTED = "isMuted"
        const val CALL_STATE = "callState"
        const val CALL_RINGING = "ringing"
        const val CALL_ANSWERED = "answered"
        const val CALL_ON_HOLD = "holding"
        const val CALL_RECONNECTING = "reconnecting"
        const val CALL_RECONNECTED = "reconnected"
        const val CALL_DISCONNECTED = "disconnected"
        const val CALL_ACTION_ANSWER = "actionAnswer"
        const val CALL_ACTION_REJECT = "actionReject"
        const val CALL_ACTION = "callAction"
        const val IS_REMOTE_DISCONNECT = "isRemoteDisconnect"

    }
}