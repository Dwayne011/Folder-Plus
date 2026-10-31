package com.folderplus.agenda

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.widget.Toast
import com.folderplus.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Text and intents shared by the widget and the agenda popup. */
object AgendaFormat {
    fun dow(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).uppercase(Locale.getDefault())
    fun mon(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault())).uppercase(Locale.getDefault())

    /** e.g. "FRI 30 - 09:00 AM" or "FRI 30 - ALL DAY". */
    fun whenLabel(context: Context, date: LocalDate, event: AgendaEvent): String {
        val locale = Locale.getDefault()
        val dayPart = date.format(DateTimeFormatter.ofPattern("EEE d", locale)).uppercase(locale)
        val time = if (event.allDay) context.getString(R.string.agenda_all_day).uppercase(locale)
        else Instant.ofEpochMilli(event.begin).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "hh:mm a", locale)).uppercase(locale)
        return "$dayPart - $time"
    }

    fun eventIntent(event: AgendaEvent): Intent =
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, event.allDay)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun dayIntent(date: LocalDate): Intent {
        val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/$millis")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun open(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.agenda_no_calendar_app, Toast.LENGTH_SHORT).show()
        }
    }
}
