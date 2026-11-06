package com.widgetsplus.core.agenda

/**
 * Google's stand-in descriptions. Events Google Calendar creates by itself from Gmail (tickets,
 * bookings, reservations) keep their details to the Google Calendar app: other apps reading the
 * phone's calendar get a placeholder description instead ("To see detailed information for
 * automatically created events like this one…", "This event was created from an email that you
 * received in Gmail…"), which the Google Calendar app itself never shows. Those are dropped, so the
 * agenda only shows descriptions that are really part of the event.
 */
object EventText {
    /** The organizer Google gives events it created from Gmail. */
    const val GMAIL_ORGANIZER = "unknownorganizer@calendar.google.com"

    /** Links in Google's placeholders; the same in every language. */
    private val placeholderLinks = listOf("g.co/calendar", "mail.google.com/mail?extsrc=cal")

    /** The English wording, in case the links change. */
    private val placeholderPhrases = listOf(
        "automatically created events like this one",
        "this event was created from an email that you received in gmail",
    )

    fun isPlaceholder(description: String, organizer: String?): Boolean {
        if (organizer != null && organizer.trim().equals(GMAIL_ORGANIZER, ignoreCase = true)) return true
        val text = description.lowercase()
        return placeholderLinks.any { it in text } || placeholderPhrases.any { it in text }
    }

    /** The description to show: empty for Google's placeholders, otherwise as it is. */
    fun shown(description: String, organizer: String?): String =
        if (description.isNotEmpty() && isPlaceholder(description, organizer)) "" else description
}
