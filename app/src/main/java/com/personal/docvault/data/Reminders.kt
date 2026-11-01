package com.personal.docvault.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Builds the reminder message for a WhatsApp bot. It carries dates only, never document details. */
object Reminders {
    private val dayBefore = listOf(30, 7, 1)
    private val format = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)

    fun message(expiryIso: String, today: LocalDate = LocalDate.now()): String? {
        val expiry = try {
            if (expiryIso.isBlank()) return null
            LocalDate.parse(expiryIso)
        } catch (_: DateTimeParseException) {
            return null
        }
        val lines = dayBefore
            .map { it to expiry.minusDays(it.toLong()) }
            .filter { it.second.isAfter(today) }
            .map { (days, date) ->
                val left = if (days == 1) "1 day" else "$days days"
                "- ${date.format(format)}: a document expires in $left"
            }
        if (lines.isEmpty()) return null
        return "Please remind me on these dates:\n" + lines.joinToString("\n")
    }

    /** WhatsApp links need the country code. Ten-digit numbers are treated as Indian. */
    fun cleanNumber(input: String): String {
        val digits = input.filter(Char::isDigit).removePrefix("00")
        return if (digits.length == 10) "91$digits" else digits
    }
}
