package com.vonage.inapp_incoming_voice_call.utils

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.vonage.inapp_incoming_voice_call.views.LoginActivity
import java.util.*

fun ArrayList<String>.contains(s: String, ignoreCase: Boolean = false): Boolean {

    return any { it.equals(s, ignoreCase) }
}

internal fun showToast(context: Context, text: String, duration: Int = Toast.LENGTH_LONG){
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(context, text, duration).show()
    }
}


internal fun showAlert(context: Context, text: String, forceExit: Boolean){
    Handler(Looper.getMainLooper()).post {
        val builder: AlertDialog.Builder = AlertDialog.Builder(context)

        // Set Alert Title
        val titleView = TextView(context)
        titleView.setPadding(20,10,20,10);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(Color.BLACK);

        titleView.text = text
        builder.setCustomTitle(titleView)
        builder.setCancelable(false);

        // Set the positive button with yes name Lambda OnClickListener method is use of DialogInterface interface.
        builder.setPositiveButton("Ok",
            DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                // When the user click yes button then app will close
                if (dialog !== null) {
                    dialog.cancel();
                }
                if (forceExit) {
                    val intent = Intent(context, LoginActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    context.startActivity (intent);
                }

            } as DialogInterface.OnClickListener)
        val alertDialog = builder.create()
        alertDialog.show()

    }
}


internal fun scrollVerticalTo(y: Int, scrollview: ScrollView) {
    Handler(Looper.getMainLooper()).post {
        Timer().schedule(object : TimerTask() {
            override fun run() {
                scrollview.smoothScrollTo(0, y)
            }
        }, 200)
    }
}
