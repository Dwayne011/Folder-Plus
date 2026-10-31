package com.widgetsplus.agenda

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.widget.Toast
import com.widgetsplus.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Text and intents shared by the widget and the day sheet. */
object AgendaFormat {
    /** Our own URI for "open the day sheet for this date", carried by the widget's "+N more" chip. */
    private const val SCHEME = "widgetsplus"

    fun dow(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).uppercase(Locale.getDefault())
    fun mon(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault())).uppercase(Locale.getDefault())

    /** e.g. "Mon 2 Nov". */
    fun dayTitle(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))

    private fun timeFormatter(context: Context) =
        DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "hh:mm a", Locale.getDefault())

    fun time(context: Context, millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(timeFormatter(context)).uppercase(Locale.getDefault())

    /** Widget row: "FRI 30 - 09:00 AM" or "FRI 30 - ALL DAY". */
    fun whenLabel(context: Context, date: LocalDate, event: AgendaEvent): String {
        val locale = Locale.getDefault()
        val dayPart = date.format(DateTimeFormatter.ofPattern("EEE d", locale)).uppercase(locale)
        val time = if (event.allDay) context.getString(R.string.agenda_all_day).uppercase(locale) else time(context, event.begin)
        return "$dayPart - $time"
    }

    /** Day sheet: "09:30 AM – 09:45 AM" or "All day". */
    fun timeRange(context: Context, event: AgendaEvent): String =
        if (event.allDay) context.getString(R.string.agenda_all_day)
        else time(context, event.begin) + " – " + time(context, event.end)

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

    fun moreUri(date: LocalDate): Uri = Uri.parse("$SCHEME://agenda/day/${date.toEpochDay()}")

    /** The date card's Today pill: show the first card (today) on the widget. */
    fun todayUri(): Uri = Uri.parse("$SCHEME://agenda/today")

    fun isTodayUri(uri: Uri?): Boolean =
        uri != null && uri.scheme == SCHEME && uri.host == "agenda" && uri.pathSegments == listOf("today")

    /** The date card's month pill: open the "Next 30 days" sheet scrolled to [date]. */
    fun monthUri(date: LocalDate): Uri = Uri.parse("$SCHEME://agenda/month/${date.toEpochDay()}")

    fun dateFromMonthUri(uri: Uri?): LocalDate? {
        if (uri == null || uri.scheme != SCHEME || uri.host != "agenda") return null
        val segments = uri.pathSegments
        if (segments.size != 2 || segments[0] != "month") return null
        return segments[1].toLongOrNull()?.let(LocalDate::ofEpochDay)
    }

    /** e.g. "November". */
    fun monthName(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("LLLL", Locale.getDefault()))

    /** e.g. "30 Oct". */
    fun shortDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

    /** The date carried by a [moreUri], or null for any other URI. */
    fun dateFromMoreUri(uri: Uri?): LocalDate? {
        if (uri == null || uri.scheme != SCHEME || uri.host != "agenda") return null
        val segments = uri.pathSegments
        if (segments.size != 2 || segments[0] != "day") return null
        return segments[1].toLongOrNull()?.let(LocalDate::ofEpochDay)
    }

    private val URL = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    /**
     * Location shortcut: a meeting link opens as a link; anything else opens as a
     * Maps search for that place.
     */
    fun locationIntent(location: String): Intent? {
        val text = location.trim()
        if (text.isEmpty()) return null
        val url = URL.find(text)?.value?.trimEnd('.', ',', ')')
        return if (url != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(text))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun isLink(location: String): Boolean = URL.containsMatchIn(location)

    fun open(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // No maps app: fall back to the web for geo searches; otherwise say so.
            val data = intent.data
            if (data?.scheme == "geo") {
                val q = data.getQueryParameter("q").orEmpty()
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(q)))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    return
                } catch (e2: ActivityNotFoundException) { /* fall through */ }
            }
            Toast.makeText(context, R.string.agenda_no_calendar_app, Toast.LENGTH_SHORT).show()
        }
    }
}
