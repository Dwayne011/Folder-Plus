package com.widgetsplus.core

import com.widgetsplus.core.agenda.EventText
import org.junit.Assert.assertEquals
import org.junit.Test

class EventTextTest {
    private val automatic = "To see detailed information for automatically created events like this one, " +
        "use the official Google Calendar app. https://g.co/calendar"
    private val fromGmail = "This event was created from an email that you received in Gmail. " +
        "https://mail.google.com/mail?extsrc=cal&plid=ACUX6DPExample"

    @Test fun googlePlaceholdersAreDropped() {
        assertEquals("", EventText.shown(automatic, organizer = "someone@example.com"))
        assertEquals("", EventText.shown(fromGmail, organizer = null))
    }

    @Test fun anythingFromTheGmailOrganizerIsDropped() {
        assertEquals("", EventText.shown("Ceci est un texte de remplacement", EventText.GMAIL_ORGANIZER))
        assertEquals("", EventText.shown("Placeholder", " UnknownOrganizer@calendar.google.com "))
    }

    @Test fun placeholderLinksWorkInAnyLanguage() {
        assertEquals("", EventText.shown("Dieser Termin wurde aus einer E-Mail erstellt. https://mail.google.com/mail?extsrc=cal&plid=x", null))
        assertEquals("", EventText.shown("Para ver más detalles, usa la app oficial. https://g.co/calendar", null))
    }

    @Test fun realDescriptionsStay() {
        val gym = "Incline Dumbbell Press: 3 sets of 8-12 reps (2-3 min rest)"
        assertEquals(gym, EventText.shown(gym, "me@gmail.com"))
        val holiday = "Observance\nTo hide observances, go to Google Calendar Settings > Holidays in United Kingdom"
        assertEquals(holiday, EventText.shown(holiday, "en.uk#holiday@group.v.calendar.google.com"))
        assertEquals("", EventText.shown("", null))
    }
}
