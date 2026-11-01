package com.personal.docvault.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class VaultStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val deviceKey = ByteArray(32) { 7 }
    private val wrapper = object : KeyWrapper {
        override fun wrap(key: ByteArray) = Crypto.seal(deviceKey, key)
        override fun unwrap(wrapped: ByteArray) = Crypto.open(deviceKey, wrapped)
    }
    private val pw = "correct horse".toCharArray()

    private fun store(name: String) = VaultStore(File(tmp.root, "$name/vault"), wrapper)

    private fun sample(blob: String = "") = Doc(
        id = "1", category = "pan", title = "My PAN", number = "ABCDE1234F",
        attachments = if (blob.isEmpty()) emptyList() else listOf(Attachment(blob, "image", "a.jpg", "image/jpeg")),
    )

    @Test fun nothingReadableIsWrittenToDisk() {
        val s = store("a")
        s.create(pw)
        val blob = s.putBlob("SECRET-PHOTO-BYTES".toByteArray())
        s.upsert(sample(blob))
        val all = File(tmp.root, "a").walkTopDown().filter { it.isFile }.map { it.readBytes().toString(Charsets.ISO_8859_1) }
        assertFalse(all.any { "ABCDE1234F" in it || "SECRET-PHOTO" in it || "My PAN" in it })
    }

    @Test fun reopensWithDeviceAndPassword() {
        val s = store("a")
        s.create(pw)
        val blob = s.putBlob(byteArrayOf(1, 2, 3))
        s.upsert(sample(blob))
        s.lock()
        assertFalse(s.isOpen)
        s.unlockWithDevice()
        assertEquals("My PAN", s.docs.single().title)
        assertArrayEquals(byteArrayOf(1, 2, 3), s.getBlob(blob))
        s.lock()
        s.unlockWithPassword(pw)
        assertEquals(1, s.docs.size)
    }

    @Test fun wrongPasswordIsRejected() {
        val s = store("a")
        s.create(pw)
        s.lock()
        assertThrows(WrongPasswordException::class.java) { s.unlockWithPassword("nope".toCharArray()) }
    }

    @Test fun backupRestoresOnAnotherPhone() {
        val s = store("a")
        s.create(pw)
        val blob = s.putBlob(byteArrayOf(9, 9))
        s.upsert(sample(blob))
        val backup = ByteArrayOutputStream().also(s::exportBackup).toByteArray()

        val other = store("b")
        other.importBackup(ByteArrayInputStream(backup), pw)
        other.rewrapDevice()
        assertEquals("ABCDE1234F", other.docs.single().number)
        assertArrayEquals(byteArrayOf(9, 9), other.getBlob(blob))
        other.lock()
        other.unlockWithDevice()
        assertEquals(1, other.docs.size)
    }

    @Test fun badRestoreLeavesCurrentVaultAlone() {
        val s = store("a")
        s.create(pw)
        s.upsert(sample())
        val backup = ByteArrayOutputStream().also(s::exportBackup).toByteArray()

        val other = store("b")
        other.create("other-password".toCharArray())
        assertThrows(WrongPasswordException::class.java) {
            other.importBackup(ByteArrayInputStream(backup), "wrong".toCharArray())
        }
        assertTrue(other.isOpen)
        assertTrue(other.docs.isEmpty())
        assertThrows(BadBackupException::class.java) {
            other.importBackup(ByteArrayInputStream("junk".toByteArray()), pw)
        }
    }

    @Test fun changedPasswordReplacesOldOne() {
        val s = store("a")
        s.create(pw)
        s.changePassword(pw, "new password".toCharArray())
        s.lock()
        assertThrows(WrongPasswordException::class.java) { s.unlockWithPassword(pw) }
        s.unlockWithPassword("new password".toCharArray())
        assertTrue(s.isOpen)
    }
}

class DetectorTest {
    // 12-digit number with a correct Verhoeff check digit, not a real Aadhaar
    private val validAadhaar = "234123412346"

    @Test fun aadhaarChecksum() {
        assertTrue(Detector.isValidAadhaar(validAadhaar))
        assertFalse(Detector.isValidAadhaar("234123412347"))
        assertFalse(Detector.isValidAadhaar("123412341234"))
    }

    @Test fun detectsAadhaarWithName() {
        val d = Detector.detect("Government of India\nTest Person\nDOB: 01/01/2000\nMALE\n2341 2341 2346")
        assertEquals("aadhaar", d.category)
        assertEquals("2341 2341 2346", d.number)
        assertEquals("Test Person", d.holder)
    }

    @Test fun detectsPan() {
        val d = Detector.detect("INCOME TAX DEPARTMENT\nPermanent Account Number\nABCDE1234F")
        assertEquals("pan", d.category)
        assertEquals("ABCDE1234F", d.number)
    }

    @Test fun detectsPassportExpiry() {
        val d = Detector.detect("REPUBLIC OF INDIA PASSPORT\nM1234567\nDate of Expiry\n12/05/2031")
        assertEquals("passport", d.category)
        assertEquals("M1234567", d.number)
        assertEquals("2031-05-12", d.expiry)
    }

    @Test fun unknownTextDetectsNothing() {
        assertNull(Detector.detect("grocery list: milk and eggs").category)
    }
}

class SearchTest {
    private val doc = Doc(id = "1", category = "aadhaar", title = "Aadhaar", number = "2341 2341 2346",
        photoText = "Address: 12 MG Road\nBengaluru")

    @Test fun findsTextInsidePhoto() {
        val hit = Search.run(listOf(doc), "bengaluru") { "Aadhaar card" }.single()
        assertEquals("Bengaluru", hit.snippet)
    }

    @Test fun findsNumberWithoutSpaces() {
        assertEquals(1, Search.run(listOf(doc), "234123412346") { "" }.size)
    }

    @Test fun allWordsMustMatch() {
        assertEquals(0, Search.run(listOf(doc), "bengaluru passport") { "" }.size)
    }
}
