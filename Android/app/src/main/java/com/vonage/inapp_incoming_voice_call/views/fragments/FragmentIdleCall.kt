package com.vonage.inapp_incoming_voice_call.views.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import com.vonage.inapp_incoming_voice_call.App
import com.vonage.inapp_incoming_voice_call.R
import com.vonage.inapp_incoming_voice_call.databinding.FragmentIdlecallBinding
import com.vonage.inapp_incoming_voice_call.utils.Constants

class FragmentIdleCall: Fragment(R.layout.fragment_idlecall) {
    private var _binding: FragmentIdlecallBinding? = null
    private val binding get() = _binding!!

    private val coreContext = App.coreContext
    private val clientManager = coreContext.clientManager

    private val callDepartments = arrayOf("Customer Support", "API Query", "Zendesk Report", "Refund Request")

    private var selectedDepartment = ""
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIdlecallBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val user = coreContext.user ?: return
        binding.tvLoggedInDisplayName.text = user.displayName
        binding.tvLoggedInPhone.text = user.username

        binding.btCall.isFocusableInTouchMode = true;
        binding.btCall.requestFocus()

        val departmentsAdaptor = ArrayAdapter(context!!, android.R.layout.simple_spinner_dropdown_item, callDepartments)
        binding.spDepartment.adapter = departmentsAdaptor

        binding.spDepartment.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                selectedDepartment = callDepartments[p2]
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
                TODO("Not yet implemented")
            }

        }

        binding.btCall.setOnClickListener {
            // prevent double submit
            binding.btCall.isEnabled = false
            val callContext = mapOf(
                Constants.CONTEXT_KEY_RECIPIENT to selectedDepartment,
                Constants.CONTEXT_KEY_TYPE to Constants.APP_TYPE
            )
            clientManager.startOutboundCall(callContext)
        }
    }
}