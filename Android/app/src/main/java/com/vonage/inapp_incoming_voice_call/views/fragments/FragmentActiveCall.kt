package com.vonage.inapp_incoming_voice_call.views.fragments

import android.app.NotificationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.vonage.inapp_incoming_voice_call.App
import com.vonage.inapp_incoming_voice_call.R
import com.vonage.inapp_incoming_voice_call.core.CoreContext
import com.vonage.inapp_incoming_voice_call.databinding.FragmentActivecallBinding
import com.vonage.inapp_incoming_voice_call.views.CallActivity.Companion.CALL_ANSWERED
import com.vonage.inapp_incoming_voice_call.views.CallActivity.Companion.CALL_RINGING
import com.vonage.inapp_incoming_voice_call.views.CallActivity.Companion.CALL_STARTED
import com.vonage.inapp_incoming_voice_call.views.CallActivity.Companion.CALL_STATE


class FragmentActiveCall: Fragment(R.layout.fragment_activecall) {
    private var _binding: FragmentActivecallBinding? = null
    private val binding get() = _binding!!

    private val coreContext = App.coreContext
    private val clientManager = coreContext.clientManager
    private lateinit var currentCallState: String

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActivecallBinding.inflate(inflater, container, false)
        // If no argument is provided, state will default to CALL_RINGING
        currentCallState = arguments?.getString(CALL_STATE) ?: CALL_RINGING
        when(currentCallState) {
            CALL_ANSWERED -> {
                binding.btAnswer.visibility = View.GONE
                binding.btMute.visibility = View.VISIBLE
                // Start Timer
                binding.cmCallTimer.visibility = View.VISIBLE
                binding.cmCallTimer.format = "Call duration - %s";
                binding.cmCallTimer.start()
            }
            CALL_STARTED -> {
                binding.btAnswer.visibility = View.GONE
                binding.btMute.visibility = View.VISIBLE
            }
            // Use RINGING_LABEL both for CALL_STARTED and CALL_RINGING
            else -> {
                binding.btAnswer.visibility = View.VISIBLE
                binding.btMute.visibility = View.GONE
            }
        }

        // Display incoming call name
        coreContext.activeCall?.let {
            binding.tvCallFrom.text = it.from
        }

        // Mute state listener
        CoreContext.CallMuteState.isMuted.observe(viewLifecycleOwner, Observer {
            updateCallMuteSTate(it)
        })

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.cmCallTimer.stop();
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btHangUp.setOnClickListener {
            onHangup()
        }
        binding.btAnswer.setOnClickListener {
            coreContext.activeCall?.let { call ->
                clientManager.answerCall(call)
            }
            val manager = activity?.getSystemService(AppCompatActivity.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(123)
        }
        binding.btMute.setOnClickListener {
            coreContext.activeCall?.let { call ->
                if (call.isMuted) {
                    clientManager.unmuteCall(call)
                }
                else {
                    clientManager.muteCall(call)
                }
            }
        }
    }
    private fun onHangup(){
        coreContext.activeCall?.let { call ->
            if (currentCallState == CALL_RINGING) {
                clientManager.rejectCall(call)
            }
            else {
                clientManager.hangupCall(call)
            }
        }
        val manager = activity?.getSystemService(AppCompatActivity.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(123)
    }

    private fun updateCallMuteSTate(isMuted: Boolean) {
        var icon = R.drawable.ic_baseline_mic_on
        if (isMuted) {
            icon = R.drawable.ic_baseline_mic_off
        }

        val top = ResourcesCompat.getDrawable(requireActivity().resources, icon , null)
        binding.btMute.setCompoundDrawablesWithIntrinsicBounds(null, top, null, null)
    }
}