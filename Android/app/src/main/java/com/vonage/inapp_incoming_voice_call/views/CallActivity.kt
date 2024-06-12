package com.vonage.inapp_incoming_voice_call.views

import android.app.NotificationManager
import android.content.*
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.vonage.inapp_incoming_voice_call.App
import com.vonage.inapp_incoming_voice_call.R
import com.vonage.inapp_incoming_voice_call.api.APIRetrofit
import com.vonage.inapp_incoming_voice_call.api.DeleteInformation
import com.vonage.inapp_incoming_voice_call.databinding.ActivityCallBinding
import com.vonage.inapp_incoming_voice_call.utils.*
import com.vonage.inapp_incoming_voice_call.views.fragments.FragmentActiveCall
import com.vonage.inapp_incoming_voice_call.views.fragments.FragmentIdleCall
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class CallActivity : AppCompatActivity() {
    private val coreContext = App.coreContext
    private val clientManager = coreContext.clientManager
    private lateinit var binding: ActivityCallBinding

    private var currentState = CALL_DISCONNECTED
    private lateinit var logoutButton: Button

    /**
     * This Local BroadcastReceiver will be used
     * to receive messages from other activities
     */
    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Handle the messages here
            // Call Remotely Hangup
            intent?.getBooleanExtra(IS_REMOTE_REJECT, false)?.let {
                if (it && currentState == CALL_STARTED) {
                    showAlert(this@CallActivity, "Call Rejected", false)
                }
            }

            // Call Remotely Timeout
            intent?.getBooleanExtra(IS_REMOTE_TIMEOUT, false)?.let {
                if (it && currentState == CALL_STARTED) {
                    showAlert(this@CallActivity, "No Answer", false)
                }
            }

            intent?.getStringExtra(CALL_ACTION)?.let { it ->
                when(it) {
                    ANSWER_ACTION -> {
                        coreContext.activeCall?.let { call ->
                            clientManager.answerCall(call)
                        }
                    }
                    REJECT_ACTION -> {
                        coreContext.activeCall?.let { call ->
                            clientManager.rejectCall(call)
                        }
                    }
                }
                val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(123)
            }


            // Call State Updated
            intent?.getStringExtra(CALL_STATE)?.let {
                currentState = it

                when (it) {
                    CALL_DISCONNECTED -> {
                        replaceFragment(FragmentIdleCall())
                    }
                    CALL_STARTED -> {
                        replaceFragment(FragmentActiveCall())
                    }
                    CALL_ANSWERED -> {
                        replaceFragment(FragmentActiveCall())
                    }
                }
            }

            // Handle Call Error
            intent?.getStringExtra(CALL_ERROR)?.let {
                handleCallError(it)
            }

            // Handle Session Error
            intent?.getStringExtra(SESSION_ERROR)?.let {
                handleSessionError(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val user = coreContext.user
        if (user == null ) {
            navigateToLoginActivity()
            return
        }

        // Set toolbar View
        val toolbar = binding.tbCall
        logoutButton = toolbar.btLogout
        logoutButton.visibility = View.VISIBLE

        replaceFragment(FragmentIdleCall())

        logoutButton.setOnClickListener {
            logout()
        }

        registerReceiver(messageReceiver, IntentFilter(MESSAGE_ACTION))
    }

    override fun onResume() {
        super.onResume()
        val user = coreContext.user
        if (user == null ) {
            navigateToLoginActivity()
            return
        }
        coreContext.activeCall?.let {
            replaceFragment(FragmentActiveCall())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(messageReceiver)
    }


    private fun replaceFragment(fragment: Fragment) {

        if (currentState == CALL_ANSWERED || currentState == CALL_STARTED) {
            logoutButton.visibility = View.GONE
        }
        else {
            logoutButton.visibility = View.VISIBLE
        }

        val bundle = Bundle()
        if (currentState == CALL_ANSWERED || currentState == CALL_STARTED) {
            bundle.putString(CALL_STATE, currentState)
        }
        fragment.arguments = bundle
        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.fcCallStatus, fragment)
        fragmentTransaction.commitAllowingStateLoss()
    }

    private fun logout() {
        logoutButton.isEnabled = false
        logoutButton.setTextColor(Color.DKGRAY)
        val user = coreContext.user

        if (user != null) {
            APIRetrofit.instance.deleteUser(DeleteInformation(user.userId)).enqueue(object:
                Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    logoutButton.isEnabled = true
                    logoutButton.setTextColor(0x0100F5)
                }

                override fun onFailure(call: Call<Void>, t: Throwable) {
                    logoutButton.isEnabled = true
                    logoutButton.setTextColor(0x0100F5)
                }

            })
        }

        clientManager.logout {
            navigateToLoginActivity()
        }
    }

    private fun handleCallError(message: String) {
        showAlert(this@CallActivity, message, false)
        // Hangup call
        coreContext.activeCall?.let { call ->
            clientManager.hangupCall(call)
        }
        replaceFragment(FragmentIdleCall())
    }

    private fun handleSessionError(message: String) {
        showAlert(this@CallActivity, message, true)
        coreContext.sessionId = null
        logout()
    }

    companion object {
        const val MESSAGE_ACTION = "com.vonage.inapp_incoming_voice_call.MESSAGE_TO_CALL_ACTIVITY"
        const val CALL_STATE = "callState"
        const val CALL_ANSWERED = "answered"
        const val CALL_STARTED = "started"
        const val CALL_RINGING = "ringing"
        const val CALL_DISCONNECTED = "disconnected"
        const val CALL_ERROR = "callError"
        const val SESSION_ERROR = "sessionError"
        const val IS_REMOTE_REJECT = "isRemoteReject"
        const val IS_REMOTE_TIMEOUT = "isRemoteTimeout"
        const val CALL_ACTION = "callAction"
        const val ANSWER_ACTION = "answerAction"
        const val REJECT_ACTION = "cancelAction"
    }
}