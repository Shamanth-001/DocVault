package com.personal.docvault.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A real document shows several of its features. An article or screen that mentions one must not pass. */
class DocumentRulesTest {
    private fun category(text: String) = Detector.classify(text)?.category

    // PAN
    @Test fun panCardShape() {
        val c = Detector.classify("INCOME TAX DEPARTMENT\nGOVT. OF INDIA\nPermanent Account Number Card\nABCPE1234F")!!
        assertEquals("pan", c.category)
        assertTrue(c.confident)
    }

    @Test fun invalidFourthPanLetterIsRejected() {
        assertNull(category("INCOME TAX DEPARTMENT\nPermanent Account Number\nABCDE1234F"))
    }

    @Test fun incomeTaxArticleIsNotAPanCard() {
        assertNull(category("How to file your income tax return and link your permanent account number"))
    }

    // Aadhaar
    @Test fun aadhaarBackWithAddress() {
        assertEquals("aadhaar", category("Unique Identification Authority of India\nAddress: 12 MG Road\n2341 2341 2346\nwww.uidai.gov.in"))
    }

    @Test fun aadhaarFrontWithoutTheWordAadhaar() {
        assertEquals("aadhaar", category("Government of India\nTest Person\nDOB: 01/01/2000\nMALE\n2341 2341 2346"))
    }

    @Test fun oneHintIsNotAnAadhaar() {
        assertNull(category("Delivery address for order 2341 2341 2346 is confirmed"))
    }

    @Test fun aadhaarArticleWithNoNumberIsNotACard() {
        assertNull(category("Link your Aadhaar with UIDAI before the deadline, says the government"))
    }

    // Voter ID
    @Test fun voterCardWithIdentityWording() {
        assertEquals("voter", category("ELECTION COMMISSION OF INDIA\nElector Photo Identity Card\nName: Test Person"))
    }

    @Test fun oldVoterCardSeriesStillAccepted() {
        assertEquals("voter", category("ELECTION COMMISSION OF INDIA\nIDENTITY CARD\nDL/01/001/000000"))
    }

    @Test fun electionNewsIsNotAVoterId() {
        assertNull(category("The Election Commission announced the dates for the coming state polls"))
    }

    // Driving licence
    @Test fun licenceWithRealNumberShape() {
        val c = Detector.classify("Indian Union Driving Licence\nKA05 20190012345\nDOB 01-01-2000")!!
        assertEquals("licence", c.category)
        assertEquals("KA05 20190012345", c.number)
    }

    @Test fun drivingLicenceBlogIsNotALicence() {
        assertNull(category("Top tips for passing your driving licence test the first time"))
    }

    // Vehicle
    @Test fun rcCardGivesItsRegistrationNumber() {
        val c = Detector.classify("Certificate of Registration\nRegn. No KA 05 AB 1234\nChassis No MA1234567890\nFuel PETROL")!!
        assertEquals("vehicle", c.category)
        assertEquals("KA 05 AB 1234", c.number)
    }

    @Test fun rcWebpageIsNotAnRc() {
        assertNull(category("What is a registration certificate? Read our guide"))
    }

    @Test fun pollutionArticleIsNotAPucCertificate() {
        assertNull(category("Why every car needs a pollution under control check this winter"))
    }

    // Passport
    @Test fun passportPageWithoutCodedLine() {
        assertEquals("passport", category("REPUBLIC OF INDIA\nPASSPORT\nSurname KUMAR\nGiven Name TEST"))
    }

    @Test fun passportNewsIsNotAPassport() {
        assertNull(category("The Republic of India has improved its passport ranking this year"))
    }

    // Insurance
    @Test fun policyScheduleWithRealFields() {
        val c = Detector.classify("ACME Health Insurance\nPolicy Number: HL/2231/2025\nSum Insured Rs 500000\nPremium 12000")!!
        assertEquals("insurance", c.category)
        assertEquals("HL/2231/2025", c.number)
    }

    @Test fun motorFormFiftyOne() {
        assertEquals(
            "insurance",
            category("Motor Insurance Certificate\nFORM 51 OF THE CENTRAL MOTOR VEHICLE RULES\nUIN: IRDAN115RP0019V01202122"),
        )
    }

    @Test fun insuranceAdvertIsNotAPolicy() {
        assertNull(category("Compare insurance plans and pay a low premium today"))
    }

    // Unrelated photos
    @Test fun laptopScreenWithCodeIsIgnored() {
        assertNull(category("function getUser() {\n  const selector = '#user'\n  return document.querySelector(selector)\n}"))
    }

    @Test fun confidentResultsNeverComeFromAPayment() {
        val c = Detector.classify("PhonePe\nTransaction successful\nPaid to Test Shop\nUTR 234123412346\nABCPE1234F")!!
        assertEquals("bills", c.category)
        assertFalse(c.confident)
    }
}
