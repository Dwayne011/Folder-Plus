package com.folderplus.agenda

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.folderplus.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

data class AgendaEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
)

/** One day of the agenda. [date] is the local calendar day; [events] may be empty. */
data class AgendaDay(val date: LocalDate, val events: List<AgendaEvent>) {
    val isToday: Boolean get() = date == LocalDate.now()
}

/** Reads upcoming calendar instances and groups them by local day. */
object CalendarAgenda {
    /** Days always shown, even when empty. */
    const val MIN_DAYS = 7
    /** Horizon for days that have events. */
    const val MAX_DAYS = 14

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun load(context: Context): List<AgendaDay> {
        if (!hasPermission(context)) return emptyList()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(MAX_DAYS.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .let { ContentUris.appendId(it, start) }
            .let { ContentUris.appendId(it, end) }
            .build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )
        val selection = "${CalendarContract.Instances.VISIBLE} = 1 AND " +
            "${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED}"
        val byDay = HashMap<LocalDate, MutableList<AgendaEvent>>()
        try {
            context.contentResolver.query(uri, projection, selection, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                val iId = c.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                val iTitle = c.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val iBegin = c.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val iEnd = c.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val iAllDay = c.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                while (c.moveToNext()) {
                    val allDay = c.getInt(iAllDay) == 1
                    val begin = c.getLong(iBegin)
                    // All-day instances are stored at UTC midnight; everything else is in the local zone.
                    val date = Instant.ofEpochMilli(begin).atZone(if (allDay) ZoneOffset.UTC else zone).toLocalDate()
                    if (date.isBefore(today)) continue
                    val title = c.getString(iTitle)?.takeIf { it.isNotBlank() } ?: context.getString(R.string.agenda_untitled)
                    byDay.getOrPut(date) { mutableListOf() }.add(AgendaEvent(c.getLong(iId), title, begin, c.getLong(iEnd), allDay))
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        }
        return (0 until MAX_DAYS).mapNotNull { offset ->
            val date = today.plusDays(offset.toLong())
            val events = byDay[date].orEmpty().sortedWith(compareBy<AgendaEvent> { !it.allDay }.thenBy { it.begin })
            if (offset < MIN_DAYS || events.isNotEmpty()) AgendaDay(date, events) else null
        }
    }

    /** Picks a badge icon from words in the title; the generic event icon otherwise. */
    fun iconFor(title: String): Int {
        val t = title.lowercase()
        return when {
            listOf("gym", "workout", "train", "run", "yoga", "fitness").any { it in t } -> R.drawable.ic_agenda_fitness
            listOf("coffee", "cafe", "café", "tea").any { it in t } -> R.drawable.ic_agenda_coffee
            listOf("birthday", "party", "celebrat", "anniversary").any { it in t } -> R.drawable.ic_agenda_cake
            listOf("flight", "fly", "airport", "travel", "trip").any { it in t } -> R.drawable.ic_agenda_flight
            listOf("call", "meeting", "meet", "standup", "sync", "review", "1:1").any { it in t } -> R.drawable.ic_agenda_videocam
            listOf("shop", "grocer", "buy", "order").any { it in t } -> R.drawable.ic_agenda_cart
            listOf("dentist", "doctor", "gp ", "clinic", "hospital", "appointment").any { it in t } -> R.drawable.ic_agenda_hospital
            listOf("lunch", "dinner", "brunch", "breakfast", "restaurant").any { it in t } -> R.drawable.ic_agenda_restaurant
            else -> R.drawable.ic_agenda_event
        }
    }
}
