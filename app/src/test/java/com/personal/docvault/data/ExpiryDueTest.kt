package com.personal.docvault.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExpiryDueTest {
    private val expiry = LocalDate.of(2031, 5, 12)

    @Test fun firesOnceAtEachPoint() {
        var sent = emptyList<String>()
        val alerts = mutableListOf<Long>()
        var day = expiry.minusDays(45)
        while (!day.isAfter(expiry.plusDays(3))) {
            val r = ExpiryDue.check(listOf(expiry), sent, day)
            alerts += r.daysLeft
            sent = r.sent
            day = day.plusDays(1)
        }
        assertEquals(listOf(30L, 7L, 1L), alerts)
    }

    @Test fun silentBeforeThirtyDays() {
        assertTrue(ExpiryDue.check(listOf(expiry), emptyList(), expiry.minusDays(31)).daysLeft.isEmpty())
    }

    @Test fun documentAddedExactlyOnAnAlertDayAlerts() {
        assertEquals(listOf(7L), ExpiryDue.check(listOf(expiry), emptyList(), expiry.minusDays(7)).daysLeft)
    }

    @Test fun documentAddedBetweenPointsAlertsWithTrueDaysLeft() {
        assertEquals(listOf(20L), ExpiryDue.check(listOf(expiry), emptyList(), expiry.minusDays(20)).daysLeft)
    }

    @Test fun phoneOffOnTheDayCatchesUp() {
        val sent = ExpiryDue.check(listOf(expiry), emptyList(), expiry.minusDays(30)).sent
        assertEquals(listOf(5L), ExpiryDue.check(listOf(expiry), sent, expiry.minusDays(5)).daysLeft)
    }

    @Test fun doesNotRepeatTheSameDay() {
        val today = expiry.minusDays(7)
        val sent = ExpiryDue.check(listOf(expiry), emptyList(), today).sent
        assertTrue(ExpiryDue.check(listOf(expiry), sent, today).daysLeft.isEmpty())
    }

    @Test fun deletedThenAddedAgainAlertsAgain() {
        val today = expiry.minusDays(7)
        val first = ExpiryDue.check(listOf(expiry), emptyList(), today)
        assertEquals(1, first.daysLeft.size)
        val deleted = ExpiryDue.check(emptyList(), first.sent, today)
        assertTrue(deleted.sent.isEmpty())
        assertEquals(1, ExpiryDue.check(listOf(expiry), deleted.sent, today).daysLeft.size)
    }

    @Test fun secondDocumentWithSameDateStillAlerts() {
        val today = expiry.minusDays(7)
        val first = ExpiryDue.check(listOf(expiry), emptyList(), today)
        assertEquals(1, ExpiryDue.check(listOf(expiry, expiry), first.sent, today).daysLeft.size)
    }

    @Test fun unopenedAlertKeepsCountingDown() {
        val today = expiry.minusDays(6)
        val pending = ExpiryDue.stillPending(listOf(expiry), listOf(expiry), today)
        assertEquals(listOf(6L), ExpiryDue.daysLeft(pending, today))
    }

    @Test fun deletedOrExpiredDocumentStopsRepeating() {
        assertTrue(ExpiryDue.stillPending(listOf(expiry), emptyList(), expiry.minusDays(6)).isEmpty())
        assertTrue(ExpiryDue.stillPending(listOf(expiry), listOf(expiry), expiry.plusDays(1)).isEmpty())
    }

    @Test fun nothingAfterExpiry() {
        assertTrue(ExpiryDue.check(listOf(expiry), emptyList(), expiry.plusDays(1)).daysLeft.isEmpty())
    }

    @Test fun messagesNeverNameADocument() {
        assertNull(ExpiryDue.message(emptyList()))
        assertEquals("A document expires in 7 days. Open DocVault to check it.", ExpiryDue.message(listOf(7L)))
        assertEquals("A document expires tomorrow. Open DocVault to check it.", ExpiryDue.message(listOf(1L)))
        assertEquals("2 documents are expiring soon. Open DocVault to check them.", ExpiryDue.message(listOf(7L, 30L)))
    }
}
