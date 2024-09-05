package com.vonage.inapp_incoming_voice_call.receivers

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import androidx.core.os.postDelayed
import com.vonage.inapp_incoming_voice_call.App
import com.vonage.inapp_incoming_voice_call.utils.navigateToMainActivity
import com.vonage.inapp_incoming_voice_call.views.CallActivity

class CallBroadcastReceiver: BroadcastReceiver() {
    private val coreContext = App.coreContext
    private val clientManager = coreContext.clientManager
    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        val callAction = intent?.getStringExtra(CallActivity.CALL_ACTION)

        if (callAction == CallActivity.CALL_ACTION_ANSWER) {
            coreContext.activeCall?.run {
                clientManager.answerCall(this)
            }
        }
        else if (callAction == CallActivity.CALL_ACTION_REJECT) {
            coreContext.activeCall?.run {
                clientManager.rejectCall(this)
            }
        }

        if(intent?.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED){ return }
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        when(state){
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                //Add 500ms delay to make transition smoother
                if (coreContext.user != null) {
                    Handler(Looper.getMainLooper()).postDelayed(500) {
                        navigateToMainActivity(context)
                    }
                }
            }
            TelephonyManager.EXTRA_STATE_RINGING -> {
                val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

                if (keyguardManager.isKeyguardLocked) {
                    // device locked logic goes here
                    Handler(Looper.getMainLooper()).postDelayed(500) {
                        navigateToMainActivity(context)
                    }
                }
            }
        }
    }
}