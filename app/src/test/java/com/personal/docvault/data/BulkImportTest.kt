package com.personal.docvault.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** All names and numbers are made up. */
class PersonMatcherTest {
    private val family = mapOf(
        "me" to listOf("Me", "Asha Rao"),
        "dad" to listOf("Father", "Ramesh Rao"),
        "mum" to listOf("Mother", "Sunita Rao"),
    )

    @Test fun nameOnDocumentPicksTheRightPerson() {
        assertEquals("dad", PersonMatcher.suggest("RAMESH RAO", "", family))
        assertEquals("mum", PersonMatcher.suggest("Sunita Rao", "", family))
    }

    @Test fun nameOrderDoesNotMatter() {
        assertEquals("dad", PersonMatcher.suggest("Rao Ramesh", "", family))
    }

    @Test fun sharedSurnameAloneIsNotEnough() {
        assertNull(PersonMatcher.suggest("Rao", "", family))
    }

    @Test fun unknownNameGetsNoSuggestion() {
        assertNull(PersonMatcher.suggest("Vikram Shetty", "", family))
    }

    @Test fun nameFoundInTextWhenNoHolderWasRead() {
        assertEquals("mum", PersonMatcher.suggest(null, "Issued to Sunita Rao at Bengaluru", family))
    }

    @Test fun cardNamingTwoRelativesStaysUndecided() {
        assertNull(PersonMatcher.suggest(null, "Asha Rao D/O Ramesh Rao", family))
    }

    @Test fun holderBeatsARelativeNamedOnTheCard() {
        assertEquals("me", PersonMatcher.suggest("Asha Rao", "Asha Rao D/O Ramesh Rao", family))
    }

    @Test fun profileCalledMotherDoesNotMatchTheWordOnACard() {
        val onlyLabels = mapOf("mum" to listOf("Mother"))
        assertNull(PersonMatcher.suggest(null, "Mother's Name: Lakshmi", onlyLabels))
    }

    @Test fun capitalNamesBecomeReadable() {
        assertEquals("Harish Kodgi M", PersonMatcher.displayName("HARISH  KODGI M"))
    }

    @Test fun mixedCaseNamesStayAsPrinted() {
        assertEquals("Harish Kodgi M", PersonMatcher.displayName("Harish Kodgi M"))
    }

    @Test fun differentSpellingsShareAGroup() {
        assertEquals(PersonMatcher.groupKey("RAMESH RAO"), PersonMatcher.groupKey("Rao, Ramesh"))
    }
}

class ClassifyTest {
    @Test fun knownCardIsConfident() {
        val c = Detector.classify("INCOME TAX DEPARTMENT\nPermanent Account Number\nABCDE1234F")!!
        assertEquals("pan", c.category)
        assertTrue(c.confident)
    }

    @Test fun billNeedsTwoSignals() {
        val c = Detector.classify("ACME STORE\nTAX INVOICE\nGSTIN 29ABCDE1234F1Z5\nGrand Total 1200")!!
        assertEquals("bills", c.category)
        assertFalse(c.confident)
    }

    @Test fun oneBillWordOnAScreenshotIsIgnored() {
        assertNull(Detector.classify("Please find the receipt for the party tomorrow"))
    }

    @Test fun marksCardGoesToEducation() {
        assertEquals("education", Detector.classify("Some University\nMarks Card\nName: Asha Rao")!!.category)
    }

    @Test fun prescriptionGoesToMedical() {
        assertEquals("medical", Detector.classify("City Clinic\nPrescription\nPatient Name: Asha Rao")!!.category)
    }

    @Test fun rentAgreementGoesToProperty() {
        assertEquals("property", Detector.classify("RENTAL AGREEMENT between the owner and tenant")!!.category)
    }

    @Test fun everydayTextIsIgnored() {
        assertNull(Detector.classify("Happy birthday! See you at the beach this weekend"))
        assertNull(Detector.classify("hi"))
    }

    @Test fun readsLabelledName() {
        assertEquals("Asha Rao", Detector.classify("Some University\nMarks Card\nName: Asha Rao")!!.holder)
        assertEquals("Ramesh Rao", Detector.classify("City Hospital\nPrescription\nPatient Name\nRamesh Rao")!!.holder)
    }

    @Test fun readsPassportName() {
        val c = Detector.classify("P<INDRAO<<ASHA<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<\nM1234567<0IND0001018M3105124<<<<<<<<<<<<<<<<")!!
        assertEquals("ASHA RAO", c.holder)
    }

    @Test fun fathersNameIsNotMistakenForTheHolder() {
        val c = Detector.classify("Some University\nMarks Card\nFather's Name: Ramesh Rao")!!
        assertNull(c.holder)
    }
}

class ScannedPhotosTest {
    @get:Rule val tmp = TemporaryFolder()

    private val deviceKey = ByteArray(32) { 9 }
    private val wrapper = object : KeyWrapper {
        override fun wrap(key: ByteArray) = Crypto.seal(deviceKey, key)
        override fun unwrap(wrapped: ByteArray) = Crypto.open(deviceKey, wrapped)
    }

    private fun store(name: String) = VaultStore(File(tmp.root, "$name/vault"), wrapper)
    private val pw = "a long password".toCharArray()

    @Test fun profilePhotoSurvivesLockingAndIsEncrypted() {
        val s = store("p")
        s.create(pw)
        val blob = s.putBlob("FACE-PHOTO-BYTES".toByteArray())
        s.setProfilePhoto(Profile.ME, blob)
        s.lock()
        s.unlockWithDevice()
        assertEquals(blob, s.profiles.single().photo)
        val disk = File(tmp.root, "p").walkTopDown().filter { it.isFile }.map { it.readBytes().toString(Charsets.ISO_8859_1) }
        assertFalse(disk.any { "FACE-PHOTO" in it })
    }

    @Test fun changingAPhotoDeletesTheOldOne() {
        val s = store("p")
        s.create(pw)
        val first = s.putBlob(byteArrayOf(1))
        s.setProfilePhoto(Profile.ME, first)
        s.setProfilePhoto(Profile.ME, s.putBlob(byteArrayOf(2)))
        assertFalse(File(tmp.root, "p/vault/blobs/$first.enc").exists())
    }

    @Test fun removingAPersonDeletesTheirPhoto() {
        val s = store("p")
        s.create(pw)
        val person = s.addProfile("Father")
        val blob = s.putBlob(byteArrayOf(1))
        s.setProfilePhoto(person.id, blob)
        s.deleteProfile(person.id)
        assertFalse(File(tmp.root, "p/vault/blobs/$blob.enc").exists())
    }

    @Test fun backupCarriesProfilePhotos() {
        val s = store("p")
        s.create(pw)
        val blob = s.putBlob(byteArrayOf(7, 7))
        s.setProfilePhoto(Profile.ME, blob)
        val backup = java.io.ByteArrayOutputStream().also(s::exportBackup).toByteArray()

        val other = store("q")
        other.importBackup(java.io.ByteArrayInputStream(backup), pw)
        assertEquals(blob, other.profiles.single().photo)
        assertTrue(other.getBlob(blob).contentEquals(byteArrayOf(7, 7)))
    }

    @Test fun scannedPhotosAreRememberedAfterLocking() {
        val s = VaultStore(File(tmp.root, "a/vault"), wrapper)
        s.create("a long password".toCharArray())
        s.markScanned(listOf("1:100", "2:200"))
        s.lock()
        s.unlockWithDevice()
        assertEquals(setOf("1:100", "2:200"), s.scannedPhotos)
    }
}
