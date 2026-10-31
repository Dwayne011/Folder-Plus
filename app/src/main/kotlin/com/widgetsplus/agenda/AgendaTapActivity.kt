package com.widgetsplus.agenda

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Invisible trampoline behind every tap on the agenda widget. The widget's click
 * template must be a mutable PendingIntent (so rows can fill in what was tapped) and
 * Android 14 requires such intents to be explicit, so they all land here and are
 * forwarded: our own "+N more" URI opens the day sheet, anything else goes to the
 * Calendar app.
 */
class AgendaTapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent.data
        val date = AgendaFormat.dateFromMoreUri(data)
        when {
            date != null -> {
                startActivity(
                    Intent(this, AgendaDayActivity::class.java)
                        .putExtra(AgendaDayActivity.EXTRA_EPOCH_DAY, date.toEpochDay())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            data != null -> {
                val view = Intent(Intent.ACTION_VIEW, data).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.extras?.let { view.putExtras(it) }
                AgendaFormat.open(this, view)
            }
        }
        finish()
    }
}
