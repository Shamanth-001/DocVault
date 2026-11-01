package com.personal.docvault

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.PersistableBundle
import android.os.SystemClock
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.docvault.data.Attachment
import com.personal.docvault.data.Doc
import com.personal.docvault.data.KeystoreWrapper
import com.personal.docvault.data.Profile
import com.personal.docvault.data.Reminders
import com.personal.docvault.data.VaultStore
import com.personal.docvault.data.WrongPasswordException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.File

enum class Phase { Setup, Locked, Open }

sealed interface Route {
    data object Home : Route
    data object Settings : Route
    data class View(val id: String) : Route
    data class Edit(val id: String?) : Route
    data class Zoom(val blobId: String) : Route
}

class VaultVm(app: Application) : AndroidViewModel(app) {
    private val context get() = getApplication<Application>()
    private val store = VaultStore(File(app.noBackupFilesDir, "vault"), KeystoreWrapper())
    private val importer = Importer(app, store)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(1)

    var phase by mutableStateOf(if (store.exists) Phase.Locked else Phase.Setup)
        private set
    var docs by mutableStateOf(emptyList<Doc>())
        private set
    var profiles by mutableStateOf(listOf(Profile.default))
        private set

    /** The family member being viewed, or null for everyone. */
    var activeProfile by mutableStateOf<String?>(null)
    var busy by mutableStateOf<String?>(null)
        private set
    var toast by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    var needsDevicePassword by mutableStateOf(false)
        private set
    var relinkDevice by mutableStateOf(false)
        private set

    val stack = mutableStateListOf<Route>(Route.Home)

    private val thumbs = object : LruCache<String, Bitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    // Auto-lock
    private val lockAfterMs = 60_000L
    private val externalLockAfterMs = 5 * 60_000L
    private var backgroundedAt = 0L
    private var lockDueAfterMs = lockAfterMs
    private var lockJob: Job? = null

    /**
     * Set while a picker, camera or another app is open for us, which gives the user longer
     * before the vault locks. It is cleared on return, so the normal lock always comes back.
     */
    var externalActivity = false

    fun onForeground() {
        lockJob?.cancel()
        val wasExternal = externalActivity
        externalActivity = false
        if (phase == Phase.Open && backgroundedAt != 0L &&
            SystemClock.elapsedRealtime() - backgroundedAt > lockDueAfterMs
        ) {
            lock()
        }
        backgroundedAt = 0L
        if (!wasExternal) clearSharedFiles()
    }

    fun onBackground() {
        if (phase != Phase.Open) return
        backgroundedAt = SystemClock.elapsedRealtime()
        lockDueAfterMs = if (externalActivity) externalLockAfterMs else lockAfterMs
        lockJob = viewModelScope.launch {
            delay(lockDueAfterMs)
            lock()
        }
    }

    /** "Me" stays blank, so alerts about your own documents read as before. */
    fun refreshReminders() = Notifier.sync(
        context,
        docs.filter { it.expiry.isNotBlank() }.map {
            it.expiry to if (it.profile == Profile.ME) "" else profileName(it.profile)
        },
    )

    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** The reminder bot's WhatsApp number. It is not a secret, so it lives outside the vault. */
    var botNumber by mutableStateOf(prefs.getString("botNumber", "").orEmpty())
        private set

    fun saveBotNumber(input: String) {
        botNumber = Reminders.cleanNumber(input)
        prefs.edit().putString("botNumber", botNumber).apply()
        toast = if (botNumber.isEmpty()) "Bot number removed." else "Bot number saved."
    }

    fun setReminder(doc: Doc, launch: (Intent) -> Unit) {
        val message = Reminders.message(doc.expiry)
        if (message == null) {
            toast = "That expiry date is too close for a reminder."
            return
        }
        if (botNumber.isEmpty()) {
            toast = "Add your reminder bot's number in Settings first."
            navigate(Route.Settings)
            return
        }
        val link = Uri.parse("https://wa.me/$botNumber").buildUpon()
            .appendQueryParameter("text", message).build()
        launch(Intent(Intent.ACTION_VIEW, link))
        work(null) {
            store.upsert(doc.copy(reminderFor = doc.expiry))
            refresh()
        }
    }

    fun navigate(r: Route) {
        stack.add(r)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun replaceTop(r: Route) {
        stack[stack.lastIndex] = r
    }

    fun popTo(r: Route) {
        while (stack.size > 1 && stack.last() != r) stack.removeAt(stack.lastIndex)
    }

    private fun refresh() {
        profiles = store.profiles
        docs = store.docs.sortedWith(compareByDescending<Doc> { it.favorite }.thenByDescending { it.updatedAt })
        refreshReminders()
    }

    fun profileName(id: String) = profiles.firstOrNull { it.id == id }?.name ?: Profile.default.name

    fun addProfile(name: String) = work(null) {
        val created = store.addProfile(name)
        refresh()
        activeProfile = created.id
    }

    fun renameProfile(id: String, name: String) = work(null) {
        store.renameProfile(id, name)
        refresh()
    }

    fun deleteProfile(id: String) = work(null) {
        store.deleteProfile(id)
        if (activeProfile == id) activeProfile = null
        refresh()
    }

    private fun work(label: String?, block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch {
            busy = label
            try {
                withContext(io, block)
            } catch (e: Exception) {
                toast = e.message ?: "Something went wrong"
            } finally {
                busy = null
            }
        }
    }

    fun deviceSecure(): Boolean =
        (context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isDeviceSecure

    fun createVault(password: CharArray) = work("Creating your vault") {
        store.create(password)
        password.fill(' ')
        refresh()
        phase = Phase.Open
    }

    fun unlockWithDevice() = work("Unlocking") {
        try {
            store.unlockWithDevice()
            refresh()
            needsDevicePassword = false
            phase = Phase.Open
        } catch (_: KeyPermanentlyInvalidatedException) {
            useRecoveryPassword()
        } catch (_: JSONException) {
            useRecoveryPassword()
        }
    }

    private fun useRecoveryPassword() {
        needsDevicePassword = true
        relinkDevice = true
        toast = "Enter your recovery password to reconnect the fingerprint lock."
    }

    fun unlockWithPassword(password: CharArray) = work("Checking password") {
        try {
            store.unlockWithPassword(password)
            refresh()
            needsDevicePassword = false
            phase = Phase.Open
        } catch (_: WrongPasswordException) {
            error = "That password isn't right."
        } finally {
            password.fill(' ')
        }
    }

    fun relink() = work(null) {
        store.rewrapDevice()
        relinkDevice = false
    }

    fun lock() {
        lockJob?.cancel()
        backgroundedAt = 0L
        viewModelScope.launch(io) {
            store.lock()
            thumbs.evictAll()
        }
        clearSharedFiles()
        docs = emptyList()
        profiles = listOf(Profile.default)
        activeProfile = null
        stack.clear()
        stack.add(Route.Home)
        if (store.exists) phase = Phase.Locked
    }

    fun doc(id: String) = docs.firstOrNull { it.id == id }

    fun save(doc: Doc, onDone: () -> Unit) = work("Saving") {
        store.upsert(doc)
        refresh()
        withContext(Dispatchers.Main) { onDone() }
    }

    fun toggleFavorite(doc: Doc) = work(null) {
        store.upsert(doc.copy(favorite = !doc.favorite))
        refresh()
    }

    fun delete(doc: Doc, onDone: () -> Unit) = work("Deleting") {
        store.delete(doc.id)
        refresh()
        withContext(Dispatchers.Main) { onDone() }
    }

    fun discardBlobs(ids: List<String>) = work(null) { ids.forEach(store::deleteBlob) }

    fun importFiles(images: List<Uri>, pdfs: List<Uri>, onDone: (Imported) -> Unit) = work("Reading your document") {
        val atts = mutableListOf<Attachment>()
        val text = StringBuilder()
        var note: String? = null
        for (uri in images) {
            val r = importer.image(uri)
            atts += r.attachments
            text.appendLine(r.text)
        }
        for (uri in pdfs) {
            val r = importer.pdf(uri)
            atts += r.attachments
            text.appendLine(r.text)
            note = r.note ?: note
        }
        val result = Imported(atts, text.toString().trim(), note)
        withContext(Dispatchers.Main) { onDone(result) }
    }

    suspend fun bitmap(blobId: String, maxSide: Int): Bitmap? = withContext(io) {
        val key = "$blobId:$maxSide"
        thumbs.get(key) ?: runCatching {
            val bytes = store.getBlob(blobId)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()?.also { thumbs.put(key, it) }
    }

    fun copy(label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, value)
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        clipboard.setPrimaryClip(clip)
        toast = "$label copied. It clears in 60 seconds."
        viewModelScope.launch {
            delay(60_000)
            val current = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            if (current == value) clipboard.clearPrimaryClip()
        }
    }

    private val shareDir get() = File(context.cacheDir, "share")

    fun clearSharedFiles() {
        shareDir.deleteRecursively()
    }

    private fun safeName(name: String) = name.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(60).ifBlank { "document" }

    private fun exportTemp(a: Attachment, fallbackExt: String): Uri {
        shareDir.mkdirs()
        val base = safeName(a.name)
        val name = if (base.contains('.')) base else "$base.$fallbackExt"
        val file = File(shareDir, name)
        file.writeBytes(store.getBlob(a.blobId))
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    fun share(doc: Doc, launch: (Intent) -> Unit) = work("Preparing") {
        val files = doc.attachments.filter { it.kind == "file" }
            .ifEmpty { doc.images }
        if (files.isEmpty()) {
            toast = "This document has no files to share."
            return@work
        }
        val uris = ArrayList(files.map { exportTemp(it, if (it.kind == "file") "pdf" else "jpg") })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        intent.type = files.first().mime
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        withContext(Dispatchers.Main) { launch(Intent.createChooser(intent, "Share ${doc.title}")) }
    }

    fun openFile(a: Attachment, launch: (Intent) -> Unit) = work("Opening") {
        val uri = exportTemp(a, "pdf")
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, a.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        withContext(Dispatchers.Main) { launch(intent) }
    }

    fun backup(target: Uri) = work("Creating backup") {
        context.contentResolver.openOutputStream(target)?.use(store::exportBackup)
            ?: throw IllegalStateException("Could not write that file")
        toast = "Backup saved. Keep it somewhere safe, away from this phone."
    }

    fun restore(source: Uri, password: CharArray) = work("Restoring backup") {
        try {
            context.contentResolver.openInputStream(source)?.use { store.importBackup(it, password) }
                ?: throw IllegalStateException("Could not read that file")
            thumbs.evictAll()
            refresh()
            relinkDevice = true
            stack.clear()
            stack.add(Route.Home)
            phase = Phase.Open
            toast = "Backup restored."
        } catch (_: WrongPasswordException) {
            error = "That password doesn't match this backup."
        } finally {
            password.fill(' ')
        }
    }

    fun changePassword(current: CharArray, new: CharArray, onDone: () -> Unit) = work("Changing password") {
        try {
            store.changePassword(current, new)
            toast = "Recovery password changed."
            withContext(Dispatchers.Main) { onDone() }
        } catch (_: WrongPasswordException) {
            error = "Your current password isn't right."
        } finally {
            current.fill(' ')
            new.fill(' ')
        }
    }
}
