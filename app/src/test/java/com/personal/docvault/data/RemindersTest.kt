package com.personal.docvault.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.time.LocalDate

class RemindersTest {
    private val today = LocalDate.of(2026, 11, 1)

    @Test fun listsAllThreeDates() {
        val m = Reminders.message("2031-05-12", today)!!
        assertTrue("12 April 2031: a document expires in 30 days" in m)
        assertTrue("5 May 2031: a document expires in 7 days" in m)
        assertTrue("11 May 2031: a document expires in 1 day" in m)
    }

    @Test fun leavesOutDatesAlreadyPassed() {
        val m = Reminders.message("2026-11-20", today)!!
        assertFalse("30 days" in m)
        assertTrue("7 days" in m)
        assertTrue("1 day" in m)
    }

    @Test fun nothingToSendWhenExpiredOrBlank() {
        assertNull(Reminders.message("2026-11-02", today))
        assertNull(Reminders.message("2020-01-01", today))
        assertNull(Reminders.message("", today))
    }

    @Test fun cleansNumbers() {
        assertEquals("919876543210", Reminders.cleanNumber("98765 43210"))
        assertEquals("919876543210", Reminders.cleanNumber("+91 98765-43210"))
        assertEquals("14155550100", Reminders.cleanNumber("+1 415 555 0100"))
    }

    @Test fun olderSavedDocumentsStillLoad() {
        val old = JSONObject().put("id", "1").put("title", "Saved before this update")
        assertEquals("", Doc.fromJson(old).reminderFor)
    }

    @Test fun reminderDateSurvivesSaving() {
        val d = Doc(id = "1", category = "passport", title = "P", reminderFor = "2031-05-12")
        assertEquals("2031-05-12", Doc.fromJson(JSONObject(d.toJson().toString())).reminderFor)
    }
}
