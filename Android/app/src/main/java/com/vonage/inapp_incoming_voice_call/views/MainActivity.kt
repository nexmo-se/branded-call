package com.vonage.inapp_incoming_voice_call.views

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.vonage.inapp_incoming_voice_call.App
import com.vonage.inapp_incoming_voice_call.api.APIRetrofit
import com.vonage.inapp_incoming_voice_call.api.DeleteInformation
import com.vonage.inapp_incoming_voice_call.databinding.ActivityMainBinding
import com.vonage.inapp_incoming_voice_call.utils.Constants
import com.vonage.inapp_incoming_voice_call.utils.navigateToCallActivity
import com.vonage.inapp_incoming_voice_call.utils.navigateToLoginActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity  : AppCompatActivity() {
    private val coreContext = App.coreContext
    private val clientManager = coreContext.clientManager
    private lateinit var binding: ActivityMainBinding
    private lateinit var logoutButton: Button
    private val callDepartments = arrayOf("Customer Support", "API Query", "Zendesk Report", "Refund Request")
    private var selectedDepartment = ""

    /**
     * This Local BroadcastReceiver will be used
     * to receive messages from other activities
     */
    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Call State Updated
            intent?.getStringExtra(CallActivity.CALL_STATE)?.let { callStateExtra ->
                if (callStateExtra == CallActivity.CALL_RINGING) {
                    navigateToCallActivity()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val user = coreContext.user
        if (user == null ) {
            navigateToLoginActivity()
            return
        }

        val toolbar = binding.tbMain
        logoutButton = toolbar.btLogout
        logoutButton.visibility = View.VISIBLE

        binding.btCall.isFocusableInTouchMode = true
        binding.btCall.requestFocus()

        val departmentsAdaptor = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, callDepartments)
        binding.spDepartment.adapter = departmentsAdaptor

        // Set Bindings
        setBindings()

        ContextCompat.registerReceiver(
            this,
            messageReceiver,
            IntentFilter(CallActivity.MESSAGE_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onResume() {
        super.onResume()
        clientManager.sessionId ?: navigateToLoginActivity()
        coreContext.activeCall?.let {
            navigateToCallActivity()
        }
        binding.btCall.isEnabled = true
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(messageReceiver)
    }

    private fun setBindings(){
        binding.apply {
            clientManager.currentUser?.let {
                tvLoggedInDisplayName.text = it.displayName ?: it.name
                tvLoggedInPhone.text = it.name
            }
            logoutButton.setOnClickListener {
                logout()
            }

            spDepartment.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    selectedDepartment = callDepartments[p2]
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    TODO("Not yet implemented")
                }

            }

            btCall.setOnClickListener {
                // prevent double submit
                binding.btCall.isEnabled = false
                val callContext = mapOf(
                    Constants.EXTRA_KEY_TO to selectedDepartment,
                    Constants.CONTEXT_KEY_TYPE to Constants.APP_TYPE
                )
                clientManager.startOutboundCall(callContext)
            }
        }
    }

    private fun logout() {
        logoutButton.isEnabled = false
        logoutButton.setTextColor(Color.DKGRAY)
        val user = clientManager.currentUser

        if (user != null) {
            APIRetrofit.instance.deleteUser(DeleteInformation(user.id)).enqueue(object:
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

}