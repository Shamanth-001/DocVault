package com.personal.docvault.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate

class ProfilesTest {
    @get:Rule val tmp = TemporaryFolder()

    private val deviceKey = ByteArray(32) { 5 }
    private val wrapper = object : KeyWrapper {
        override fun wrap(key: ByteArray) = Crypto.seal(deviceKey, key)
        override fun unwrap(wrapped: ByteArray) = Crypto.open(deviceKey, wrapped)
    }
    private val pw = "family password".toCharArray()

    private fun store(name: String) = VaultStore(File(tmp.root, "$name/vault"), wrapper)

    @Test fun startsWithMe() {
        val s = store("a")
        s.create(pw)
        assertEquals(listOf(Profile.default), s.profiles)
    }

    @Test fun profilesAndOwnerSurviveLocking() {
        val s = store("a")
        s.create(pw)
        val mother = s.addProfile("  Mother ")
        s.upsert(Doc(id = "1", category = "pan", title = "PAN", profile = mother.id))
        s.lock()
        s.unlockWithDevice()
        assertEquals(listOf("Me", "Mother"), s.profiles.map { it.name })
        assertEquals(mother.id, s.docs.single().profile)
    }

    @Test fun existingVaultWithPlainListOpensUnderMe() {
        val s = store("a")
        s.create(pw)
        val key = wrapper.unwrap(
            java.util.Base64.getDecoder().decode(
                org.json.JSONObject(File(tmp.root, "a/vault/header.json").readText()).getString("device"),
            ),
        )
        val old = JSONArray().put(org.json.JSONObject().put("id", "1").put("title", "Old document"))
        File(tmp.root, "a/vault/index.enc").writeBytes(Crypto.seal(key, old.toString().toByteArray()))
        s.lock()
        s.unlockWithDevice()
        assertEquals(Profile.ME, s.docs.single().profile)
        assertEquals(listOf(Profile.default), s.profiles)
    }

    @Test fun removingAPersonKeepsTheirDocuments() {
        val s = store("a")
        s.create(pw)
        val father = s.addProfile("Father")
        s.upsert(Doc(id = "1", category = "pan", title = "PAN", profile = father.id))
        s.deleteProfile(father.id)
        assertEquals(Profile.ME, s.docs.single().profile)
        assertEquals(listOf("Me"), s.profiles.map { it.name })
    }

    @Test fun meCannotBeRemovedButCanBeRenamed() {
        val s = store("a")
        s.create(pw)
        assertThrows(IllegalArgumentException::class.java) { s.deleteProfile(Profile.ME) }
        s.renameProfile(Profile.ME, "Shamanth")
        assertEquals("Shamanth", s.profiles.single().name)
    }

    @Test fun blankAndDuplicateNamesAreRejected() {
        val s = store("a")
        s.create(pw)
        s.addProfile("Mother")
        assertThrows(IllegalArgumentException::class.java) { s.addProfile("   ") }
        assertThrows(IllegalArgumentException::class.java) { s.addProfile("mother") }
    }

    @Test fun backupCarriesProfiles() {
        val s = store("a")
        s.create(pw)
        val sister = s.addProfile("Sister")
        s.upsert(Doc(id = "1", category = "pan", title = "PAN", profile = sister.id))
        val backup = ByteArrayOutputStream().also(s::exportBackup).toByteArray()

        val other = store("b")
        other.importBackup(ByteArrayInputStream(backup), pw)
        assertEquals(listOf("Me", "Sister"), other.profiles.map { it.name })
        assertEquals(sister.id, other.docs.single().profile)
    }

    @Test fun searchFindsAPersonsDocuments() {
        val mine = Doc(id = "1", category = "pan", title = "PAN")
        val hers = Doc(id = "2", category = "pan", title = "PAN", profile = "m")
        val hits = Search.run(listOf(mine, hers), "mother", { if (it == "m") "Mother" else "Me" }) { "" }
        assertEquals("2", hits.single().doc.id)
    }

    @Test fun alertNamesThePerson() {
        val date = LocalDate.of(2031, 5, 12)
        val labels = ExpiryDue.decodeLabels(ExpiryDue.encodeLabels(listOf("2031-05-12" to "Mother")))
        assertEquals("Mother: A document expires in 7 days.", ExpiryDue.withName("A document expires in 7 days.", listOf(date), labels))
    }

    @Test fun myOwnAlertHasNoName() {
        val date = LocalDate.of(2031, 5, 12)
        val labels = ExpiryDue.decodeLabels(ExpiryDue.encodeLabels(listOf("2031-05-12" to "")))
        assertEquals("Text", ExpiryDue.withName("Text", listOf(date), labels))
    }

    @Test fun sameDateForTwoPeopleHasNoName() {
        val date = LocalDate.of(2031, 5, 12)
        val labels = ExpiryDue.decodeLabels(
            ExpiryDue.encodeLabels(listOf("2031-05-12" to "Mother", "2031-05-12" to "Father")),
        )
        assertEquals("Text", ExpiryDue.withName("Text", listOf(date), labels))
    }

    @Test fun differentDatesHaveNoName() {
        val labels = mapOf("2031-05-12" to "Mother", "2031-06-12" to "Father")
        assertTrue("Mother" !in ExpiryDue.withName("Text", listOf(LocalDate.of(2031, 5, 12), LocalDate.of(2031, 6, 12)), labels))
    }
}
