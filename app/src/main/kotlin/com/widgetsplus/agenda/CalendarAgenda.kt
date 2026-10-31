package com.widgetsplus.agenda

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.widgetsplus.R
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
    val location: String = "",
    val description: String = "",
    val calendarId: Long = 0,
    /** The colour Calendar shows the event in (its own colour, or its calendar's); 0 if none. */
    val color: Int = 0,
)

/** One day of the agenda. [date] is the local calendar day; [events] may be empty. */
data class AgendaDay(val date: LocalDate, val events: List<AgendaEvent>) {
    val isToday: Boolean get() = date == LocalDate.now()
}

/** A calendar on the device, for the "which calendars" chooser. */
data class AgendaCalendar(val id: Long, val name: String, val account: String, val color: Int)

/** Which calendars the user has switched off. Shared by every agenda widget. */
object AgendaPrefs {
    private const val FILE = "widgetsplus_agenda"
    private const val KEY_EXCLUDED = "excluded_calendars"

    fun excluded(context: Context): Set<Long> =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getStringSet(KEY_EXCLUDED, emptySet())
            .orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    fun setExcluded(context: Context, ids: Set<Long>) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_EXCLUDED, ids.map { it.toString() }.toSet()).apply()
    }
}

/** Reads upcoming calendar instances and groups them by local day. */
object CalendarAgenda {
    /** Days always shown, even when empty: the widget and the "Next 30 days" sheet hold the same days. */
    const val MIN_DAYS = 30
    /** Horizon for days that have events. */
    const val MAX_DAYS = 30

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun load(context: Context): List<AgendaDay> {
        if (!hasPermission(context)) return emptyList()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(MAX_DAYS.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val excluded = AgendaPrefs.excluded(context)

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
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.DISPLAY_COLOR,
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
                val iLoc = c.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
                val iDesc = c.getColumnIndexOrThrow(CalendarContract.Instances.DESCRIPTION)
                val iCal = c.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
                val iColor = c.getColumnIndexOrThrow(CalendarContract.Instances.DISPLAY_COLOR)
                while (c.moveToNext()) {
                    val calendarId = c.getLong(iCal)
                    if (calendarId in excluded) continue
                    val allDay = c.getInt(iAllDay) == 1
                    val begin = c.getLong(iBegin)
                    // All-day instances are stored at UTC midnight; everything else is in the local zone.
                    val date = Instant.ofEpochMilli(begin).atZone(if (allDay) ZoneOffset.UTC else zone).toLocalDate()
                    if (date.isBefore(today)) continue
                    val title = c.getString(iTitle)?.takeIf { it.isNotBlank() } ?: context.getString(R.string.agenda_untitled)
                    byDay.getOrPut(date) { mutableListOf() }.add(
                        AgendaEvent(
                            id = c.getLong(iId), title = title, begin = begin, end = c.getLong(iEnd), allDay = allDay,
                            location = c.getString(iLoc)?.trim().orEmpty(),
                            description = c.getString(iDesc)?.trim().orEmpty(),
                            calendarId = calendarId,
                            color = if (c.isNull(iColor)) 0 else c.getInt(iColor),
                        ),
                    )
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

    fun loadDay(context: Context, date: LocalDate): AgendaDay =
        load(context).firstOrNull { it.date == date } ?: AgendaDay(date, emptyList())

    /** Calendars on the device, visible ones first. */
    fun calendars(context: Context): List<AgendaCalendar> {
        if (!hasPermission(context)) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
        )
        val out = mutableListOf<AgendaCalendar>()
        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, projection,
                "${CalendarContract.Calendars.VISIBLE} = 1", null,
                "${CalendarContract.Calendars.ACCOUNT_NAME} ASC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    out += AgendaCalendar(
                        id = c.getLong(0),
                        name = c.getString(1).orEmpty(),
                        account = c.getString(2).orEmpty(),
                        color = c.getInt(3),
                    )
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        }
        return out
    }

    /** Picks a badge icon from words in the title; the generic event icon otherwise. */
    fun iconFor(title: String): Int {
        val t = title.lowercase()
        return when {
            listOf("gym", "workout", "train", "run", "yoga", "fitness", "football", "five-a-side").any { it in t } -> R.drawable.ic_agenda_fitness
            listOf("coffee", "cafe", "café", "tea").any { it in t } -> R.drawable.ic_agenda_coffee
            listOf("birthday", "party", "celebrat", "anniversary").any { it in t } -> R.drawable.ic_agenda_cake
            listOf("flight", "fly", "airport", "travel", "trip").any { it in t } -> R.drawable.ic_agenda_flight
            listOf("call", "meeting", "meet", "standup", "sync", "review", "1:1", "planning").any { it in t } -> R.drawable.ic_agenda_videocam
            listOf("shop", "grocer", "buy", "order").any { it in t } -> R.drawable.ic_agenda_cart
            listOf("dentist", "doctor", "gp ", "clinic", "hospital", "appointment").any { it in t } -> R.drawable.ic_agenda_hospital
            listOf("lunch", "dinner", "brunch", "breakfast", "restaurant").any { it in t } -> R.drawable.ic_agenda_restaurant
            else -> R.drawable.ic_agenda_event
        }
    }
}
