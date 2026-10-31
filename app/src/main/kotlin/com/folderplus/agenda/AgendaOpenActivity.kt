package com.folderplus.agenda

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.folderplus.R

/**
 * Invisible trampoline behind every tap on the agenda widget. Android 14 forbids a
 * mutable PendingIntent with an implicit target, and widget collections need a
 * mutable template for fill-in intents, so the template points here and this
 * activity forwards the filled-in data (an event or a day) to the Calendar app.
 */
class AgendaOpenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent.data
        if (data != null) {
            val view = Intent(Intent.ACTION_VIEW, data).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.extras?.let { view.putExtras(it) }
            try {
                startActivity(view)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, R.string.agenda_no_calendar_app, Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}
