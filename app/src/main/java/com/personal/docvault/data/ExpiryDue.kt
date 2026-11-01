package com.personal.docvault.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Decides which expiry alerts are due. Knows only dates, so nothing here identifies a document. */
object ExpiryDue {
    private val alertDays = listOf(30L, 7L, 1L)

    class Result(val daysLeft: List<Long>, val sent: List<String>, val dates: List<LocalDate>)

    /**
     * Each document gets one alert for the latest of the 30, 7 and 1 day points it has reached.
     * That covers a document added late, or a phone that was off on the day.
     *
     * [sent] records which alerts already went out, once per document. Entries for dates no longer
     * in the vault are dropped, so a deleted document that is added again alerts again.
     */
    fun check(expiries: List<LocalDate>, sent: List<String>, today: LocalDate): Result {
        val daysLeft = mutableListOf<Long>()
        val nowSent = mutableListOf<String>()
        val dates = mutableListOf<LocalDate>()
        expiries.groupBy { it }.forEach { (expiry, documents) ->
            val left = ChronoUnit.DAYS.between(today, expiry)
            val reached = alertDays.filter { !expiry.minusDays(it).isAfter(today) }.minOrNull()
            if (left >= 0 && reached != null) {
                val key = "$expiry|$reached"
                val alreadySent = sent.count { it == key }
                repeat(documents.size) { nowSent += key }
                repeat(maxOf(0, documents.size - alreadySent)) {
                    daysLeft += left
                    dates += expiry
                }
            }
        }
        return Result(daysLeft, nowSent, dates)
    }

    /** Days left for alerts the user hasn't opened yet, leaving out anything no longer relevant. */
    fun stillPending(pending: List<LocalDate>, current: List<LocalDate>, today: LocalDate): List<LocalDate> =
        pending.distinct().filter { it in current && !it.isBefore(today) }

    fun daysLeft(dates: List<LocalDate>, today: LocalDate) = dates.map { ChronoUnit.DAYS.between(today, it) }

    /**
     * Saves whose document each date belongs to, for alerts sent while the vault is locked. A date
     * shared by two people gets no name, and "Me" is passed as a blank name.
     */
    fun encodeLabels(entries: List<Pair<String, String>>): String =
        entries.groupBy({ it.first }, { it.second }).map { (date, names) ->
            "$date=${names.distinct().singleOrNull().orEmpty().replace('\n', ' ')}"
        }.joinToString("\n")

    fun decodeLabels(raw: String): Map<String, String> = raw.lines().filter { '=' in it }
        .associate { it.substringBefore('=') to it.substringAfter('=') }

    fun withName(text: String, dates: List<LocalDate>, labels: Map<String, String>): String {
        val name = dates.distinct().singleOrNull()?.let { labels[it.toString()] }.orEmpty()
        return if (name.isBlank()) text else "$name: $text"
    }

    fun message(daysLeft: List<Long>): String? {
        if (daysLeft.isEmpty()) return null
        if (daysLeft.size > 1) return "${daysLeft.size} documents are expiring soon. Open DocVault to check them."
        return when (val days = daysLeft.single()) {
            0L -> "A document expires today. Open DocVault to check it."
            1L -> "A document expires tomorrow. Open DocVault to check it."
            else -> "A document expires in $days days. Open DocVault to check it."
        }
    }
}
