package com.personal.docvault.data

import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class Detected(
    val category: String? = null,
    val number: String? = null,
    val holder: String? = null,
    val expiry: String? = null,
)

/** A gallery photo that looks like a document. [confident] means a known card or paper was found. */
data class Classified(
    val category: String,
    val number: String?,
    val holder: String?,
    val expiry: String?,
    val confident: Boolean,
)

/** Works out what kind of Indian document some recognised text came from. */
object Detector {
    private val BILL_WORDS = listOf(
        "TAX INVOICE", "INVOICE", "BILL NO", "RECEIPT", "GSTIN", "TOTAL AMOUNT", "GRAND TOTAL",
        "AMOUNT PAID", "CASH MEMO", "WARRANTY", "HSN",
    )
    private val EDUCATION_WORDS = listOf(
        "UNIVERSITY", "MARKS CARD", "MARKSHEET", "MARK SHEET", "DEGREE CERTIFICATE", "ROLL NO",
        "REGISTER NO", "BOARD OF SECONDARY", "HALL TICKET", "TRANSFER CERTIFICATE", "BONAFIDE",
    )
    private val MEDICAL_WORDS = listOf(
        "PRESCRIPTION", "PATIENT NAME", "DIAGNOSIS", "LAB REPORT", "DISCHARGE SUMMARY",
        "BLOOD GROUP", "VACCINATION CERTIFICATE",
    )
    private val PROPERTY_WORDS = listOf(
        "SALE DEED", "RENTAL AGREEMENT", "RENT AGREEMENT", "LEASE AGREEMENT", "KHATA", "ENCUMBRANCE",
    )
    private val NAME_LABEL = Regex(
        "(?i)^(?:name|insured\\s*name|name\\s*of\\s*(?:the\\s*)?(?:insured|student|candidate|patient|holder)|" +
            "patient\\s*name|student\\s*name|candidate\\s*name|customer\\s*name|holder'?s?\\s*name)" +
            "\\s*[:\\-]?\\s*(.*)$",
    )

    /** Decides whether a gallery photo is worth showing for import, or null for everyday photos. */
    fun classify(text: String): Classified? {
        if (text.length < 20) return null
        val d = detect(text)
        val holder = d.holder ?: passportName(text) ?: labelledName(text)
        d.category?.let { return Classified(it, d.number, holder, d.expiry, confident = true) }

        val upper = text.uppercase()
        fun hits(words: List<String>) = words.count { it in upper }
        val guess = when {
            hits(BILL_WORDS) >= 2 -> "bills"
            hits(EDUCATION_WORDS) >= 1 -> "education"
            hits(MEDICAL_WORDS) >= 1 -> "medical"
            hits(PROPERTY_WORDS) >= 1 -> "property"
            else -> return null
        }
        return Classified(guess, null, holder, d.expiry, confident = false)
    }

    private fun labelledName(text: String): String? {
        val lines = text.lines().map { it.trim() }
        lines.forEachIndexed { i, line ->
            val rest = NAME_LABEL.find(line)?.groupValues?.get(1)?.trim() ?: return@forEachIndexed
            val candidate = rest.ifBlank { lines.getOrNull(i + 1).orEmpty() }
            if (looksLikeName(candidate)) return candidate
        }
        return null
    }

    private fun passportName(text: String): String? {
        val line = text.lines().map { it.filterNot(Char::isWhitespace).uppercase() }
            .firstOrNull { it.startsWith("P<IND") } ?: return null
        val parts = line.removePrefix("P<IND").split("<<").filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        val surname = parts[0].replace('<', ' ')
        val given = parts.getOrNull(1)?.replace('<', ' ').orEmpty()
        return "$given $surname".trim().takeIf(::looksLikeName)
    }

    private val PAN = Regex("\\b[A-Z]{5}[0-9]{4}[A-Z]\\b")
    private val AADHAAR = Regex("(?<![0-9])([2-9][0-9]{3})[ -]?([0-9]{4})[ -]?([0-9]{4})(?![0-9])")
    private val PASSPORT = Regex("\\b[A-Z][0-9]{7}\\b")
    private val LICENCE = Regex("\\b[A-Z]{2}[0-9]{2}[ -]?[0-9]{4}[ -]?[0-9]{7}\\b|\\b[A-Z]{2}[0-9]{2}[ -]?[0-9]{11}\\b")
    private val VOTER = Regex("\\b[A-Z]{3}[0-9]{7}\\b")
    private val CARD_NUMBER = Regex("(?<![0-9])([0-9]{4})[ -]?([0-9]{4})[ -]?([0-9]{4})[ -]?([0-9]{4})(?![0-9])")
    private val POLICY_NUMBER = Regex("(?i)policy\\s*(?:no|number)\\.?\\s*[:\\-]?\\s*([A-Z0-9][A-Z0-9/\\-]{5,})")
    private val CARD_WORDS = Regex("VALID\\s*(THRU|THROUGH)|GOOD\\s*(THRU|THROUGH)|DEBIT|CREDIT|RUPAY|VISA|MASTERCARD")

    // Wording seen on Indian insurance schedules, licences, RCs and bank cards
    private val STRONG_LABEL = Regex(
        "(?i)expir|\\bexp\\.?\\s*date|date\\s*of\\s*exp|" +
            "valid\\s*(till|til|upto|up\\s*to|thru|through|until)|good\\s*(thru|through)|" +
            "end\\s*date|to\\s*mid-?\\s*night|mid-?\\s*night\\s*(of|on)|\\bnon-?transport\\b|" +
            "renewal\\s*(/\\s*payment\\s*)?(due\\s*)?date",
    )
    private val WEAK_LABEL = Regex(
        "(?i)validity|policy\\s*period|period\\s*of\\s*(insurance|cover)|fit(ness)?\\s*(valid\\s*)?up\\s*to|" +
            "tax\\s*up\\s*to|maturity",
    )

    private const val MONTH = "(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*"
    private val ISO_DATE = Regex("\\b((?:19|20)[0-9]{2})-([01]?[0-9])-([0-3]?[0-9])\\b")
    private val DAY_MONTH_NAME = Regex("(?i)\\b([0-3]?[0-9])(?:st|nd|rd|th)?[\\s/.,-]*$MONTH[\\s/.,-]*((?:19|20)?[0-9]{2})\\b")
    private val MONTH_NAME_DAY = Regex("(?i)\\b$MONTH[\\s.]+([0-3]?[0-9])(?:st|nd|rd|th)?[\\s,]+((?:19|20)[0-9]{2})\\b")
    private val NUMERIC_DATE = Regex("(?<![0-9])([0-3]?[0-9])[/.-]([01]?[0-9])[/.-]((?:19|20)[0-9]{2})(?![0-9])")
    private val SHORT_DATE = Regex("(?<![0-9/.-])([0-3]?[0-9])[/.-]([01]?[0-9])[/.-]([0-9]{2})(?![0-9/.-])")
    private val MONTH_YEAR = Regex("(?<![0-9/.-])(0[1-9]|1[0-2])[/-]((?:20)[0-9]{2})(?![0-9/.-])")
    private val CARD_EXPIRY = Regex("(?<![0-9/.-])(0[1-9]|1[0-2])[/-]([0-9]{2})(?![0-9/.-])")

    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    fun detect(text: String): Detected {
        val upper = text.uppercase()
        val pan = PAN.find(upper)?.value
        val aadhaar = AADHAAR.findAll(text)
            .map { it.groupValues[1] + it.groupValues[2] + it.groupValues[3] }
            .firstOrNull(::isValidAadhaar)
        val card = CARD_NUMBER.findAll(text)
            .map { it.groupValues.drop(1).joinToString("") }
            .firstOrNull(::passesLuhn)
        val isCard = card != null && CARD_WORDS.containsMatchIn(upper)
        val passportExpiry = passportCodeExpiry(text)

        return when {
            passportExpiry != null || ("REPUBLIC OF INDIA" in upper && "PASSPORT" in upper) || "P<IND" in upper ->
                Detected("passport", PASSPORT.find(upper)?.value, null, passportExpiry ?: expiry(text))
            "DRIVING LICEN" in upper ->
                Detected("licence", LICENCE.find(upper)?.value, null, expiry(text))
            "ELECTION COMMISSION" in upper || "ELECTOR" in upper ->
                Detected("voter", VOTER.find(upper)?.value)
            "REGISTRATION CERTIFICATE" in upper || "CERTIFICATE OF REGISTRATION" in upper ||
                "POLLUTION UNDER CONTROL" in upper ->
                Detected("vehicle", null, null, expiry(text))
            isCard -> Detected("card", card!!.chunked(4).joinToString(" "), null, expiry(text, isCard = true))
            "INSURANCE" in upper && ("POLICY" in upper || "CERTIFICATE" in upper) ->
                Detected("insurance", POLICY_NUMBER.find(text)?.groupValues?.get(1), null, expiry(text))
            pan != null && ("INCOME TAX" in upper || "PERMANENT ACCOUNT" in upper) ->
                Detected("pan", pan, panName(text))
            aadhaar != null -> Detected("aadhaar", formatAadhaar(aadhaar), aadhaarName(text))
            pan != null -> Detected("pan", pan, panName(text))
            else -> Detected(expiry = expiry(text))
        }
    }

    fun formatAadhaar(digits: String) = digits.chunked(4).joinToString(" ")

    /** Aadhaar numbers end in a Verhoeff check digit, which rejects most misread numbers. */
    fun isValidAadhaar(number: String): Boolean {
        if (number.length != 12 || !number.all(Char::isDigit) || number[0] < '2') return false
        var c = 0
        number.reversed().forEachIndexed { i, ch ->
            c = VERHOEFF_D[c][VERHOEFF_P[i % 8][ch - '0']]
        }
        return c == 0
    }

    private fun passesLuhn(number: String): Boolean {
        var sum = 0
        number.reversed().forEachIndexed { i, ch ->
            val d = ch - '0'
            sum += if (i % 2 == 1) (d * 2).let { if (it > 9) it - 9 else it } else d
        }
        return sum % 10 == 0
    }

    private fun aadhaarName(text: String): String? {
        val lines = text.lines().map { it.trim() }
        val dob = lines.indexOfFirst { Regex("(?i)dob|date of birth|year of birth").containsMatchIn(it) }
        if (dob <= 0) return null
        return lines[dob - 1].takeIf(::looksLikeName)
    }

    private fun panName(text: String): String? {
        val lines = text.lines().map { it.trim() }
        val label = lines.indexOfFirst { it.equals("Name", true) || it.endsWith("/ Name", true) }
        if (label >= 0 && label + 1 < lines.size && looksLikeName(lines[label + 1])) return lines[label + 1]
        return null
    }

    private fun looksLikeName(s: String) =
        s.length in 3..50 && s.all { it.isLetter() || it == ' ' || it == '.' } && s.count(Char::isLetter) >= 3

    /**
     * The coded line at the bottom of a passport holds the expiry as YYMMDD with a check digit,
     * which makes it more reliable than the printed date.
     */
    private fun passportCodeExpiry(text: String): String? {
        for (raw in text.lines()) {
            val line = raw.filterNot(Char::isWhitespace).uppercase()
            if (line.length < 28 || '<' !in line) continue
            val nationality = line.substring(10, 13)
            if (!nationality.all { it.isLetter() || it == '<' }) continue
            val digits = line.substring(21, 28).replace('O', '0')
            if (!digits.all(Char::isDigit) || line[20] !in "MFX<") continue
            if (codeCheckDigit(digits.take(6)) != digits[6] - '0') continue
            val date = validDate(2000 + digits.substring(0, 2).toInt(), digits.substring(2, 4).toInt(), digits.substring(4, 6).toInt())
            return date?.format(DateTimeFormatter.ISO_LOCAL_DATE)
        }
        return null
    }

    private fun codeCheckDigit(digits: String): Int {
        val weights = intArrayOf(7, 3, 1)
        return digits.foldIndexed(0) { i, sum, ch -> sum + (ch - '0') * weights[i % 3] } % 10
    }

    /** Only trusts dates next to a label, so a birth or issue date alone is never used. */
    private fun expiry(text: String, isCard: Boolean = false): String? {
        val lines = text.lines()

        fun near(label: Regex): List<LocalDate> {
            val found = mutableListOf<LocalDate>()
            lines.forEachIndexed { i, line ->
                if (label.containsMatchIn(line)) {
                    for (j in i..minOf(i + 2, lines.lastIndex)) found += datesIn(lines[j], short = true)
                }
            }
            return found
        }

        val best = near(STRONG_LABEL).ifEmpty { near(WEAK_LABEL) }.ifEmpty {
            // Card layouts often split "VALID THRU" apart, so any month/year on a card counts
            if (isCard) lines.flatMap { datesIn(it, short = true) } else emptyList()
        }
        return best.maxOrNull()?.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    private fun validDate(year: Int, month: Int, day: Int): LocalDate? = try {
        LocalDate.of(year, month, day).takeIf { year in 1990..2100 }
    } catch (_: DateTimeException) {
        null
    }

    private fun fullYear(s: String) = if (s.length == 2) 2000 + s.toInt() else s.toInt()

    private fun monthNumber(name: String) = MONTHS.indexOf(name.take(3).lowercase()) + 1

    private fun endOfMonth(year: Int, month: Int): LocalDate? = try {
        YearMonth.of(year, month).atEndOfMonth()
    } catch (_: DateTimeException) {
        null
    }

    /** Reads every date in a line. Each match is blanked out so it can't be read twice. */
    private fun datesIn(line: String, short: Boolean): List<LocalDate> {
        var rest = line
        val found = mutableListOf<LocalDate>()

        fun take(pattern: Regex, read: (MatchResult) -> LocalDate?) {
            pattern.findAll(rest).toList().forEach { m -> read(m)?.let(found::add) }
            rest = pattern.replace(rest) { " ".repeat(it.value.length) }
        }

        take(ISO_DATE) { validDate(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt()) }
        take(DAY_MONTH_NAME) {
            validDate(fullYear(it.groupValues[3]), monthNumber(it.groupValues[2]), it.groupValues[1].toInt())
        }
        take(MONTH_NAME_DAY) {
            validDate(it.groupValues[3].toInt(), monthNumber(it.groupValues[1]), it.groupValues[2].toInt())
        }
        take(NUMERIC_DATE) {
            validDate(it.groupValues[3].toInt(), it.groupValues[2].toInt(), it.groupValues[1].toInt())
        }
        if (short) {
            take(SHORT_DATE) {
                validDate(2000 + it.groupValues[3].toInt(), it.groupValues[2].toInt(), it.groupValues[1].toInt())
            }
            // A card is valid through the last day of its printed month
            take(MONTH_YEAR) { endOfMonth(it.groupValues[2].toInt(), it.groupValues[1].toInt()) }
            take(CARD_EXPIRY) { endOfMonth(2000 + it.groupValues[2].toInt(), it.groupValues[1].toInt()) }
        }
        return found
    }

    private val VERHOEFF_D = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
        intArrayOf(1, 2, 3, 4, 0, 6, 7, 8, 9, 5),
        intArrayOf(2, 3, 4, 0, 1, 7, 8, 9, 5, 6),
        intArrayOf(3, 4, 0, 1, 2, 8, 9, 5, 6, 7),
        intArrayOf(4, 0, 1, 2, 3, 9, 5, 6, 7, 8),
        intArrayOf(5, 9, 8, 7, 6, 0, 4, 3, 2, 1),
        intArrayOf(6, 5, 9, 8, 7, 1, 0, 4, 3, 2),
        intArrayOf(7, 6, 5, 9, 8, 2, 1, 0, 4, 3),
        intArrayOf(8, 7, 6, 5, 9, 3, 2, 1, 0, 4),
        intArrayOf(9, 8, 7, 6, 5, 4, 3, 2, 1, 0),
    )
    private val VERHOEFF_P = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
        intArrayOf(1, 5, 7, 6, 2, 8, 3, 0, 9, 4),
        intArrayOf(5, 8, 0, 3, 7, 9, 6, 1, 4, 2),
        intArrayOf(8, 9, 1, 6, 0, 4, 3, 5, 2, 7),
        intArrayOf(9, 4, 5, 3, 1, 2, 6, 8, 7, 0),
        intArrayOf(4, 2, 8, 6, 5, 7, 3, 9, 0, 1),
        intArrayOf(2, 7, 9, 3, 8, 0, 6, 4, 1, 5),
        intArrayOf(7, 0, 4, 6, 9, 1, 3, 2, 5, 8),
    )
}
