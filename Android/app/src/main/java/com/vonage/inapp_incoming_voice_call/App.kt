package com.vonage.inapp_incoming_voice_call

import android.app.Application
import com.vonage.inapp_incoming_voice_call.core.CoreContext

class App: Application() {

    companion object {
        lateinit var coreContext: CoreContext
    }

    override fun onCreate() {
        super.onCreate()
        coreContext = CoreContext.getInstance(applicationContext)
    }

}
