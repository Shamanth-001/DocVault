package com.personal.docvault.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Layouts follow real Indian documents. All names, numbers and dates are made up. */
class ExpiryFormatsTest {
    private fun expiry(text: String) = Detector.detect(text).expiry

    // Insurance schedules
    @Test fun periodFromTimeToMidnight() = assertEquals(
        "2024-05-24",
        expiry("Health Insurance Policy\nPeriod of Insurance : From 00:01 on 25/05/2023 to mid-night on 24/05/2024"),
    )

    @Test fun periodWithHoursInBrackets() = assertEquals(
        "2025-07-03",
        expiry("Period of Insurance : From 04-07-2024 (00:00:00 Hrs) to 03-07-2025 (23:59:59 Hrs)"),
    )

    @Test fun startAndEndDateLines() = assertEquals(
        "2026-07-30",
        expiry("Policy Period - Start Date 00:00 hrs 31/Jul/2025\nPolicy Period - End Date Midnight 30/Jul/2026"),
    )

    @Test fun motorCertificateMidnightOn() = assertEquals(
        "2026-07-05",
        expiry("Date of Commencement of Insurance 06/07/2025\nDate of Expiry of the Insurance Midnight on 05/07/2026"),
    )

    @Test fun motorCertificateMidnightOfRange() = assertEquals(
        "2020-03-06",
        expiry("Period of cover from 00:00 Hrs of 07/03/2019 to Midnight of 06/03/2020"),
    )

    @Test fun renewalDueDate() = assertEquals("2027-02-14", expiry("Renewal / Payment Due Date: 14/02/2027"))

    @Test fun insuranceIsCategorised() {
        val d = Detector.detect("ACME General Insurance\nPolicy No: 25/110050/2310\nPolicy Period\nto 12-Jan-2028")
        assertEquals("insurance", d.category)
        assertEquals("25/110050/2310", d.number)
        assertEquals("2028-01-12", d.expiry)
    }

    // Driving licences
    @Test fun licenceWithTwoValidityColumns() {
        val d = Detector.detect("Indian Union Driving Licence\nIssue Date Validity ( NT ) Validity ( TR )\n27-06-2024 27-01-2046 NA")
        assertEquals("licence", d.category)
        assertEquals("2046-01-27", d.expiry)
    }

    @Test fun licenceStatusPage() = assertEquals(
        "2034-09-03",
        expiry("Driving Licence Details\nNon-Transport From: 04-Sep-2014 To: 03-Sep-2034"),
    )

    @Test fun rcValidity() {
        val d = Detector.detect("CERTIFICATE OF REGISTRATION\nRegn. Validity 11/03/2039")
        assertEquals("vehicle", d.category)
        assertEquals("2039-03-11", d.expiry)
    }

    @Test fun pollutionCertificate() {
        val d = Detector.detect("Pollution Under Control Certificate\nValid Upto: 20/10/2027")
        assertEquals("vehicle", d.category)
        assertEquals("2027-10-20", d.expiry)
    }

    // Bank cards
    @Test fun cardValidThru() {
        val d = Detector.detect("RUPAY DEBIT\n4111 1111 1111 1111\nVALID THRU 04/28")
        assertEquals("card", d.category)
        assertEquals("2028-04-30", d.expiry)
    }

    @Test fun cardLatestOfFromAndThru() = assertEquals(
        "2029-02-28",
        expiry("VISA CREDIT\n4111111111111111\nVALID FROM 02/24\nVALID THRU 02/29"),
    )

    @Test fun cardWithLabelSplitFromDate() = assertEquals(
        "2030-11-30",
        expiry("VISA\n4111 1111 1111 1111\nVALID\nTHRU\nSOME BANK\n11/30"),
    )

    @Test fun failedCardChecksumIsNotACard() {
        val d = Detector.detect("VISA\n4111 1111 1111 1112\nVALID THRU 04/28")
        assertNull(d.number)
    }

    // Passports
    private val code = "M1234567<0IND0001018M3105124<<<<<<<<<<<<<<<<"

    @Test fun passportCodedLine() {
        val d = Detector.detect("P<INDKUMAR<<TEST<<<<<<<<<<<<<<<<<<<<<<<<<<<\n$code")
        assertEquals("passport", d.category)
        assertEquals("2031-05-12", d.expiry)
        assertEquals("M1234567", d.number)
    }

    @Test fun passportCodedLineWithLetterOMisread() = assertEquals(
        "2031-05-12", expiry("P<INDKUMAR<<TEST\nM1234567<0IND0001018M31O5124<<<<<<<<<<<<<<<<"),
    )

    @Test fun passportWrongCheckDigitFallsBackToPrinted() = assertEquals(
        "2031-05-12",
        expiry("P<INDKUMAR<<TEST\nM1234567<0IND0001018M3105129<<<<<<<<<<<<<<<<\nDate of Expiry\n12/05/2031"),
    )

    // Date styles
    @Test fun writtenMonth() = assertEquals("2031-05-12", expiry("Expires on 12 May 2031"))
    @Test fun fullMonthWithComma() = assertEquals("2031-05-12", expiry("Expiry: 12th May, 2031"))
    @Test fun monthFirst() = assertEquals("2031-05-12", expiry("Valid until May 12, 2031"))
    @Test fun twoDigitYear() = assertEquals("2031-05-12", expiry("Valid till 12/05/31"))
    @Test fun dotted() = assertEquals("2031-05-12", expiry("Expiry Date: 12.05.2031"))
    @Test fun isoStyle() = assertEquals("2031-05-12", expiry("Expiry 2031-05-12"))
    @Test fun septemberLongForm() = assertEquals("2030-09-04", expiry("Valid upto 04-Sept-2030"))
    @Test fun dateOnNextLine() = assertEquals("2031-05-12", expiry("Date of Expiry\n12/05/2031"))

    // Must not guess
    @Test fun birthDateAloneIsIgnored() = assertNull(expiry("Date of Birth 01/01/2000"))
    @Test fun unlabelledDateIsIgnored() = assertNull(expiry("Bill dated 05/06/2029"))
    @Test fun issueDateIsIgnored() = assertNull(expiry("Date of Issue: 02/03/2024"))
    @Test fun impossibleDateIsIgnored() = assertNull(expiry("Expiry 31/02/2031"))
    @Test fun issueDateLosesToExpiry() = assertEquals(
        "2034-03-01",
        expiry("Date of Issue Date of Expiry\n02/03/2024 01/03/2034"),
    )
}
