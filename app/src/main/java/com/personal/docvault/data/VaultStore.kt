package com.personal.docvault.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class WrongPasswordException : Exception("Wrong password")
class BadBackupException(message: String) : Exception(message)

/** Wraps the vault key with something outside this class, normally the Android Keystore. */
interface KeyWrapper {
    fun wrap(key: ByteArray): ByteArray
    fun unwrap(wrapped: ByteArray): ByteArray
}

/**
 * Encrypted document store.
 *
 * A random 256-bit vault key encrypts the index and every file. On disk that key exists only
 * wrapped twice: by the hardware Keystore (fingerprint or screen lock) and by a key derived from
 * the recovery password. A backup is the same encrypted files plus the password-wrapped key, so
 * it stays unreadable without the password.
 */
class VaultStore(private val root: File, private val wrapper: KeyWrapper) {
    private val headerFile get() = File(root, "header.json")
    private val indexFile get() = File(root, "index.enc")
    private val blobDir get() = File(root, "blobs")

    private var vaultKey: ByteArray? = null
    private var items = mutableListOf<Doc>()
    private var people = mutableListOf(Profile.default)

    val exists get() = headerFile.exists()
    val isOpen get() = vaultKey != null
    val docs: List<Doc> get() = items.toList()
    val profiles: List<Profile> get() = people.toList()

    private class Index(val docs: MutableList<Doc>, val profiles: MutableList<Profile>)

    /** Older vaults and backups saved a plain list of documents, with no family profiles. */
    private fun parseIndex(json: String): Index {
        val text = json.trim()
        val docs: JSONArray
        val saved: JSONArray?
        if (text.startsWith("[")) {
            docs = JSONArray(text)
            saved = null
        } else {
            val o = JSONObject(text)
            docs = o.getJSONArray("docs")
            saved = o.optJSONArray("profiles")
        }
        val list = MutableList(saved?.length() ?: 0) { Profile.fromJson(saved!!.getJSONObject(it)) }
        if (list.none { it.id == Profile.ME }) list.add(0, Profile.default)
        return Index(MutableList(docs.length()) { Doc.fromJson(docs.getJSONObject(it)) }, list)
    }

    private fun enc(b: ByteArray) = Base64.getEncoder().encodeToString(b)
    private fun dec(s: String) = Base64.getDecoder().decode(s)

    private fun writeAtomic(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".tmp")
        tmp.outputStream().use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun readHeader() = JSONObject(headerFile.readText())

    private fun writeHeader(h: JSONObject) = writeAtomic(headerFile, h.toString().toByteArray())

    private fun passwordBlock(password: CharArray, key: ByteArray): JSONObject {
        val salt = Crypto.random(16)
        val kek = Crypto.derive(password, salt)
        try {
            return JSONObject()
                .put("salt", enc(salt))
                .put("iterations", Crypto.PBKDF2_ITERATIONS)
                .put("wrapped", enc(Crypto.seal(kek, key)))
        } finally {
            kek.fill(0)
        }
    }

    private fun unwrapPassword(block: JSONObject, password: CharArray): ByteArray {
        val kek = Crypto.derive(password, dec(block.getString("salt")), block.getInt("iterations"))
        try {
            return Crypto.open(kek, dec(block.getString("wrapped")))
        } catch (_: javax.crypto.AEADBadTagException) {
            throw WrongPasswordException()
        } finally {
            kek.fill(0)
        }
    }

    /** Requires a recent device authentication, because it uses the Keystore key. */
    fun create(password: CharArray) {
        check(!exists) { "A vault already exists" }
        root.mkdirs()
        blobDir.mkdirs()
        val key = Crypto.random(32)
        val header = JSONObject()
            .put("version", 1)
            .put("device", enc(wrapper.wrap(key)))
            .put("password", passwordBlock(password, key))
        vaultKey = key
        items = mutableListOf()
        people = mutableListOf(Profile.default)
        saveIndex()
        writeHeader(header)
    }

    fun unlockWithDevice() {
        val key = wrapper.unwrap(dec(readHeader().getString("device")))
        open(key)
    }

    fun unlockWithPassword(password: CharArray) {
        open(unwrapPassword(readHeader().getJSONObject("password"), password))
    }

    private fun open(key: ByteArray) {
        val index = parseIndex(String(Crypto.open(key, indexFile.readBytes())))
        items = index.docs
        people = index.profiles
        vaultKey = key
    }

    /** Re-links the vault to a fresh Keystore key, for example after restoring on a new phone. */
    fun rewrapDevice() {
        val key = requireKey()
        val h = readHeader()
        h.put("device", enc(wrapper.wrap(key)))
        writeHeader(h)
    }

    fun lock() {
        vaultKey?.fill(0)
        vaultKey = null
        items = mutableListOf()
        people = mutableListOf(Profile.default)
    }

    private fun requireKey() = vaultKey ?: throw IllegalStateException("Vault is locked")

    private fun saveIndex() {
        val docs = JSONArray()
        items.forEach { docs.put(it.toJson()) }
        val profiles = JSONArray()
        people.forEach { profiles.put(it.toJson()) }
        val index = JSONObject().put("docs", docs).put("profiles", profiles)
        writeAtomic(indexFile, Crypto.seal(requireKey(), index.toString().toByteArray()))
    }

    private fun checkedName(name: String, ignoring: String? = null): String {
        val clean = name.trim().replace(Regex("\\s+"), " ").take(Profile.MAX_NAME)
        require(clean.isNotEmpty()) { "Enter a name." }
        require(people.none { it.id != ignoring && it.name.equals(clean, ignoreCase = true) }) {
            "$clean is already in your family profiles."
        }
        return clean
    }

    fun addProfile(name: String): Profile {
        requireKey()
        val profile = Profile(UUID.randomUUID().toString(), checkedName(name))
        people.add(profile)
        saveIndex()
        return profile
    }

    fun renameProfile(id: String, name: String) {
        val i = people.indexOfFirst { it.id == id }
        if (i < 0) return
        people[i] = people[i].copy(name = checkedName(name, ignoring = id))
        saveIndex()
    }

    /** Removes a person only. Their documents move to Me, so nothing is ever lost this way. */
    fun deleteProfile(id: String) {
        require(id != Profile.ME) { "Me can't be removed." }
        people.removeAll { it.id == id }
        items = items.map { if (it.profile == id) it.copy(profile = Profile.ME) else it }.toMutableList()
        saveIndex()
    }

    fun upsert(doc: Doc) {
        val i = items.indexOfFirst { it.id == doc.id }
        if (i >= 0) items[i] = doc else items.add(doc)
        saveIndex()
    }

    fun delete(id: String) {
        val doc = items.firstOrNull { it.id == id } ?: return
        items.remove(doc)
        saveIndex()
        doc.attachments.forEach { deleteBlob(it.blobId) }
    }

    fun putBlob(bytes: ByteArray): String {
        val id = UUID.randomUUID().toString().replace("-", "")
        writeAtomic(File(blobDir, "$id.enc"), Crypto.seal(requireKey(), bytes))
        return id
    }

    fun getBlob(id: String): ByteArray = Crypto.open(requireKey(), File(blobDir, "$id.enc").readBytes())

    fun deleteBlob(id: String) {
        File(blobDir, "$id.enc").delete()
    }

    fun changePassword(current: CharArray, new: CharArray) {
        val h = readHeader()
        val key = unwrapPassword(h.getJSONObject("password"), current)
        try {
            h.put("password", passwordBlock(new, key))
            writeHeader(h)
        } finally {
            key.fill(0)
        }
    }

    fun exportBackup(out: OutputStream) {
        requireKey()
        val header = readHeader()
        ZipOutputStream(out).use { zip ->
            fun add(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            val backupHeader = JSONObject().put("version", 1).put("password", header.getJSONObject("password"))
            add("header.json", backupHeader.toString().toByteArray())
            add("index.enc", indexFile.readBytes())
            val referenced = items.flatMap { d -> d.attachments.map { it.blobId } }
            referenced.forEach { add("blobs/$it.enc", File(blobDir, "$it.enc").readBytes()) }
        }
    }

    /**
     * Checks the backup completely before touching the current vault, then swaps it in.
     * The vault is left open but still needs [rewrapDevice] to work with the fingerprint.
     */
    fun importBackup(input: InputStream, password: CharArray) {
        val staging = File(root.parentFile, "restore-staging").apply { deleteRecursively(); mkdirs() }
        try {
            File(staging, "blobs").mkdirs()
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    val allowed = name == "header.json" || name == "index.enc" ||
                        Regex("blobs/[0-9a-f]{32}\\.enc").matches(name)
                    if (!allowed) throw BadBackupException("This is not a DocVault backup")
                    val buffer = ByteArrayOutputStream()
                    zip.copyTo(buffer)
                    File(staging, name).writeBytes(buffer.toByteArray())
                }
            }
            val stagedHeader = File(staging, "header.json")
            val stagedIndex = File(staging, "index.enc")
            if (!stagedHeader.exists() || !stagedIndex.exists()) {
                throw BadBackupException("This is not a DocVault backup")
            }
            val h = JSONObject(stagedHeader.readText())
            val key = unwrapPassword(h.getJSONObject("password"), password)
            parseIndex(String(Crypto.open(key, stagedIndex.readBytes()))).docs.forEach { doc ->
                doc.attachments.forEach {
                    if (!File(staging, "blobs/${it.blobId}.enc").exists()) {
                        throw BadBackupException("This backup is incomplete")
                    }
                }
            }

            lock()
            // Keep the old vault until the new one is in place, so a failure cannot lose both
            val previous = File(root.parentFile, "restore-previous").apply { deleteRecursively() }
            val hadVault = root.exists()
            if (hadVault && !root.renameTo(previous)) throw IllegalStateException("Could not restore the backup")
            if (!staging.renameTo(root)) {
                if (hadVault) previous.renameTo(root)
                throw IllegalStateException("Could not restore the backup")
            }
            previous.deleteRecursively()
            open(key)
        } finally {
            staging.deleteRecursively()
        }
    }
}
