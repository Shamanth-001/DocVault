package com.personal.docvault.ui

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.personal.docvault.Imported
import com.personal.docvault.Route
import com.personal.docvault.VaultVm
import com.personal.docvault.data.Attachment
import com.personal.docvault.data.CustomField
import com.personal.docvault.data.Detector
import com.personal.docvault.data.Doc
import com.personal.docvault.data.Profile
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

private val displayDate = DateTimeFormatter.ofPattern("d MMM yyyy")

fun formatDate(iso: String): String = runCatching { LocalDate.parse(iso).format(displayDate) }.getOrDefault(iso)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(vm: VaultVm, id: String?) {
    val context = LocalContext.current
    val existing = remember(id) { id?.let(vm::doc) }

    var profile by remember { mutableStateOf(existing?.profile ?: vm.activeProfile ?: Profile.ME) }
    var category by remember { mutableStateOf(existing?.category ?: "aadhaar") }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var holder by remember { mutableStateOf(existing?.holder ?: "") }
    var number by remember { mutableStateOf(existing?.number ?: "") }
    var expiry by remember { mutableStateOf(existing?.expiry ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var photoText by remember { mutableStateOf(existing?.photoText ?: "") }
    val attachments = remember { mutableStateListOf<Attachment>().apply { addAll(existing?.attachments.orEmpty()) } }
    val fields = remember { mutableStateListOf<CustomField>().apply { addAll(existing?.fields.orEmpty()) } }
    val addedBlobs = remember { mutableStateListOf<String>() }
    val removedBlobs = remember { mutableStateListOf<String>() }
    var titleEdited by remember { mutableStateOf(existing != null) }
    var confirmLeave by remember { mutableStateOf(false) }
    var addFieldOpen by remember { mutableStateOf(false) }

    val cat = categoryOf(category)

    fun applyCategory(key: String) {
        category = key
        val c = categoryOf(key)
        if (!titleEdited) title = c.label
        c.extraFields.forEach { label ->
            if (fields.none { it.label == label }) fields.add(CustomField(label, ""))
        }
    }

    fun onImported(r: Imported) {
        attachments.addAll(r.attachments)
        addedBlobs.addAll(r.attachments.map { it.blobId })
        if (r.text.isNotBlank()) photoText = (photoText + "\n" + r.text).trim()
        val found = Detector.detect(r.text)
        if (existing == null) found.category?.let(::applyCategory)
        if (number.isBlank()) found.number?.let { number = it }
        if (holder.isBlank()) found.holder?.let { holder = it }
        if (expiry.isBlank()) found.expiry?.let { expiry = it }
        vm.toast = r.note ?: when {
            r.text.isNotBlank() && found.number != null -> "Details filled in from the photo. Please check them."
            r.text.isNotBlank() -> "Text read. You can search for it later."
            else -> null
        }
    }

    fun endExternal() {
        vm.externalActivity = false
    }

    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        endExternal()
        if (uris.isNotEmpty()) vm.importFiles(uris, emptyList(), ::onImported)
    }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        endExternal()
        uri?.let { vm.importFiles(emptyList(), listOf(it), ::onImported) }
    }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        endExternal()
        val file = cameraFile
        if (ok && file != null) {
            vm.importFiles(listOf(Uri.fromFile(file)), emptyList()) {
                file.delete()
                onImported(it)
            }
        } else {
            file?.delete()
        }
    }

    fun openCamera() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        cameraFile = file
        vm.externalActivity = true
        camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
    }

    val changed = addedBlobs.isNotEmpty() || removedBlobs.isNotEmpty() ||
        profile != (existing?.profile ?: profile) || title != (existing?.title ?: "") || number != (existing?.number ?: "") ||
        holder != (existing?.holder ?: "") || notes != (existing?.notes ?: "")

    fun cancel() {
        vm.discardBlobs(addedBlobs.toList())
        vm.back()
    }

    fun tryLeave() {
        if (changed) confirmLeave = true else cancel()
    }

    BackHandler { tryLeave() }

    fun save() {
        val now = System.currentTimeMillis()
        val doc = Doc(
            id = existing?.id ?: UUID.randomUUID().toString(),
            category = category,
            profile = profile,
            title = title.trim().ifBlank { cat.label },
            holder = holder.trim(),
            number = number.trim(),
            expiry = expiry,
            notes = notes.trim(),
            fields = fields.filter { it.value.isNotBlank() }.map { CustomField(it.label, it.value.trim()) },
            attachments = attachments.toList(),
            photoText = photoText,
            favorite = existing?.favorite ?: false,
            reminderFor = existing?.reminderFor.orEmpty(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        vm.save(doc) {
            vm.discardBlobs(removedBlobs.toList())
            // A new document with an expiry opens straight away, so the reminder button is right there
            if (existing == null && doc.expiry.isNotBlank()) vm.replaceTop(Route.View(doc.id)) else vm.back()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Add document" else "Edit document") },
                navigationIcon = {
                    IconButton(::tryLeave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (vm.profiles.size > 1) {
                Text("Whose document?", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.profiles.forEach { p ->
                        FilterChip(
                            profile == p.id, { profile = p.id }, { Text(p.name) },
                            leadingIcon = { Avatar(vm, p, 24) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Text("Add photos or a PDF", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SourceButton(Icons.Filled.CameraAlt, "Camera", Modifier.weight(1f), ::openCamera)
                SourceButton(Icons.Filled.PhotoLibrary, "Photos", Modifier.weight(1f)) {
                    vm.externalActivity = true
                    pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                SourceButton(Icons.Filled.PictureAsPdf, "PDF", Modifier.weight(1f)) {
                    vm.externalActivity = true
                    pickPdf.launch(arrayOf("application/pdf"))
                }
            }

            if (attachments.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    attachments.toList().forEach { a ->
                        Box(Modifier.size(96.dp)) {
                            if (a.kind == "image") {
                                BlobImage(vm, a.blobId, 300, Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)))
                            } else {
                                Box(
                                    Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surface),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Filled.PictureAsPdf, a.name, tint = Coral) }
                            }
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp).clip(CircleShape)
                                    .background(Color(0xCC0E1420))
                                    .clickable {
                                        attachments.remove(a)
                                        if (a.blobId in addedBlobs) {
                                            addedBlobs.remove(a.blobId)
                                            vm.discardBlobs(listOf(a.blobId))
                                        } else {
                                            removedBlobs.add(a.blobId)
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Close, "Remove", Modifier.size(16.dp)) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Type of document", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { c ->
                    FilterChip(category == c.key, { applyCategory(c.key) }, { Text(c.label) })
                }
            }

            Spacer(Modifier.height(16.dp))
            Field("Title", title, { title = it; titleEdited = true })
            Field("Name on document", holder, { holder = it })
            Field(cat.numberLabel, number, { number = it })

            fields.forEachIndexed { i, f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        f.value, { fields[i] = f.copy(value = it) },
                        label = { Text(f.label) }, singleLine = true,
                        modifier = Modifier.weight(1f).padding(vertical = 5.dp),
                    )
                    IconButton({ fields.removeAt(i) }) { Icon(Icons.Filled.Delete, "Remove ${f.label}") }
                }
            }
            TextButton({ addFieldOpen = true }) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add another detail")
            }

            if (cat.hasExpiry || expiry.isNotBlank()) {
                OutlinedButton(
                    {
                        val start = runCatching { LocalDate.parse(expiry) }.getOrDefault(LocalDate.now())
                        DatePickerDialog(context, { _, y, m, d ->
                            expiry = LocalDate.of(y, m + 1, d).toString()
                        }, start.year, start.monthValue - 1, start.dayOfMonth).show()
                    },
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) { Text(if (expiry.isBlank()) "Set expiry date" else "Expires ${formatDate(expiry)}") }
                if (expiry.isNotBlank()) {
                    TextButton({ expiry = "" }) { Text("Remove expiry date") }
                }
            }

            OutlinedTextField(
                notes, { notes = it }, label = { Text("Notes") }, minLines = 3,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )

            Spacer(Modifier.height(12.dp))
            Button(::save, Modifier.fillMaxWidth().height(52.dp)) { Text("Save") }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Discard changes?") },
            text = { Text("What you've added here won't be saved.") },
            confirmButton = { TextButton(::cancel) { Text("Discard") } },
            dismissButton = { TextButton({ confirmLeave = false }) { Text("Keep editing") } },
        )
    }

    if (addFieldOpen) {
        var label by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addFieldOpen = false },
            title = { Text("Add a detail") },
            text = {
                OutlinedTextField(
                    label, { label = it }, label = { Text("Name, like Blood group") }, singleLine = true,
                )
            },
            confirmButton = {
                TextButton({
                    fields.add(CustomField(label.trim(), ""))
                    addFieldOpen = false
                }, enabled = label.isNotBlank()) { Text("Add") }
            },
            dismissButton = { TextButton({ addFieldOpen = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SourceButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = Gold)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
    )
}
