package com.personal.docvault.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.docvault.BulkItem
import com.personal.docvault.BulkStage
import com.personal.docvault.VaultVm
import com.personal.docvault.data.PersonMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkImportScreen(vm: VaultVm) {
    val stage = vm.bulkStage

    fun leave() {
        vm.bulkReset()
        vm.back()
    }

    BackHandler {
        when (stage) {
            BulkStage.Scanning -> vm.bulkStopScan()
            BulkStage.Importing -> Unit
            else -> leave()
        }
    }

    // A long scan shouldn't be interrupted by the screen switching off
    val view = LocalView.current
    DisposableEffect(stage) {
        view.keepScreenOn = stage == BulkStage.Scanning || stage == BulkStage.Importing
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Bulk import") },
                navigationIcon = {
                    if (stage != BulkStage.Scanning && stage != BulkStage.Importing) {
                        IconButton(::leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (stage) {
                BulkStage.Intro -> Intro(vm)
                BulkStage.Folders -> Folders(vm)
                BulkStage.Scanning -> Progress(
                    "Looking through your photos",
                    vm.bulkDone, vm.bulkTotal,
                    "${vm.bulkItems.size} possible documents found. Keep DocVault open. It stays on your phone.",
                ) {
                    OutlinedButton(vm::bulkStopScan) { Text("Stop and review what's found") }
                }
                BulkStage.Review -> Review(vm)
                BulkStage.Importing -> Progress("Saving your documents", vm.bulkDone, vm.bulkTotal, "Keep DocVault open.") {}
                BulkStage.Done -> Done(vm, ::leave)
            }
        }
    }

    // Drawn in this window rather than a dialog, so the screenshot block still covers it
    vm.bulkPreview?.let { HeldPreview(vm, it) }
}

@Composable
private fun HeldPreview(vm: VaultVm, uri: Uri) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, uri) { value = vm.bulkPreviewBitmap(uri) }
    Box(Modifier.fillMaxSize().background(Color(0xF2000000)), contentAlignment = Alignment.Center) {
        val shown = bitmap
        if (shown != null) {
            Image(shown.asImageBitmap(), "Full photo", Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
        } else {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun Intro(vm: VaultVm) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        vm.externalActivity = false
        if (uris.isNotEmpty()) vm.bulkScanPicked(uris)
    }
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.externalActivity = false
        if (granted) {
            vm.bulkLoadFolders()
        } else {
            vm.toast = "Photo access wasn't allowed. You can still choose photos yourself."
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Bring in many documents at once", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "This is optional. DocVault reads your photos on this phone, shows what looks like a document, and saves only what you choose. Nothing leaves your phone.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Surface(
            onClick = {
                vm.externalActivity = true
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Choose photos myself", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Pick up to 100 photos. No permission needed.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Surface(
            onClick = {
                vm.externalActivity = true
                ask.launch(permission)
            },
            shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Scan my gallery", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Finds documents for you. Android will ask to allow photo access, and you can remove that any time in phone settings.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Your photos stay in your gallery. Text reading covers English letters and numbers.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Folders(vm: VaultVm) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "Choose which folders to scan. Fewer folders finish faster.",
            Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) {
            items(vm.bulkFolders, key = { it.name }) { f ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.bulkToggleFolder(f.name) }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(f.selected, { vm.bulkToggleFolder(f.name) })
                    Text(f.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${f.count}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
        Column(Modifier.padding(16.dp).navigationBarsPadding()) {
            Row(
                Modifier.clickable { vm.bulkIncludeOld = !vm.bulkIncludeOld },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(vm.bulkIncludeOld, { vm.bulkIncludeOld = it })
                Text("Include photos scanned before", style = MaterialTheme.typography.bodyMedium)
            }
            val total = vm.bulkFolders.filter { it.selected }.sumOf { it.count }
            Button(vm::bulkScanSelectedFolders, Modifier.fillMaxWidth().height(52.dp), enabled = total > 0) {
                Text("Scan up to $total photos")
            }
        }
    }
}

@Composable
private fun Progress(title: String, done: Int, total: Int, note: String, actions: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(18.dp))
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done.toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text("$done of $total")
        Spacer(Modifier.height(10.dp))
        Text(note, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        actions()
    }
}

/** Items sharing a printed name stay together, so one tap can assign them all to one person. */
@Composable
private fun Review(vm: VaultVm) {
    var addingPerson by remember { mutableStateOf(false) }
    val groups = vm.bulkItems.groupBy { PersonMatcher.groupKey(it.holder.orEmpty()) }.toList()
        .sortedBy { (key, _) -> key.isEmpty() }
    val selected = vm.bulkItems.count { it.selected }

    if (vm.bulkItems.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("No documents found", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Nothing in those photos looked like a document. Blurry photos and other languages can be missed.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
            item {
                Text(
                    "Check each one, and tick only what you want to keep. Hold a photo to see it full size.",
                    Modifier.padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton({ addingPerson = true }) { Text("Add a family member") }
            }
            groups.forEach { (key, items) ->
                item(key = "group-$key") { GroupHeader(vm, key, items) }
                items(items, key = { it.key }) { ItemRow(vm, it) }
            }
        }
        Column(Modifier.padding(16.dp).navigationBarsPadding()) {
            Button(vm::bulkImport, Modifier.fillMaxWidth().height(52.dp), enabled = selected > 0) {
                Text(if (selected == 1) "Import 1 document" else "Import $selected documents")
            }
        }
    }

    if (addingPerson) {
        PersonDialog("Add person", "", onSave = { vm.addProfile(it); addingPerson = false }) {
            addingPerson = false
        }
    }
}

@Composable
private fun GroupHeader(vm: VaultVm, key: String, items: List<BulkItem>) {
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 14.dp, bottom = 6.dp)) {
        val name = items.first().holder.takeUnless { key.isEmpty() } ?: "No name found"
        Text(name, style = MaterialTheme.typography.titleMedium)
        val shared = items.map { it.profile }.distinct().singleOrNull()
        val hint = when {
            items.any { it.matched } && shared != null -> "Matched to ${vm.profileName(shared)}"
            key.isEmpty() -> "DocVault couldn't read a name. Choose whose these are."
            else -> "${items.size} found. Choose who this is."
        }
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (key.isNotEmpty() && items.none { it.matched } && !vm.bulkNameIsProfile(key)) {
            val person = PersonMatcher.displayName(name)
            TextButton({ vm.bulkAddPerson(key, person) }) { Text("Add $person as a new person") }
        }
        if (vm.profiles.size > 1) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.profiles.forEach { p ->
                    FilterChip(
                        shared == p.id, { vm.bulkAssign(key, p.id) }, { Text(p.name) },
                        leadingIcon = { Avatar(vm, p, 24) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemRow(vm: VaultVm, item: BulkItem) {
    Surface(
        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(item.selected, { checked -> vm.bulkUpdate(item.key) { it.copy(selected = checked) } })
            Thumb(vm, item.uri)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                TypePicker(item) { key -> vm.bulkUpdate(item.key) { it.copy(category = key) } }
                if (vm.profiles.size > 1) {
                    PersonPicker(vm, item) { id -> vm.bulkUpdate(item.key) { it.copy(profile = id, matched = false) } }
                }
                val details = listOfNotNull(
                    item.number?.let(::maskNumber),
                    item.expiry?.let { "Expires ${formatDate(it)}" },
                    item.note,
                ).joinToString("  ·  ")
                if (details.isNotBlank()) {
                    Text(
                        details, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun Thumb(vm: VaultVm, uri: Uri) {
    val thumb by produceState<android.graphics.Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { vm.bulkThumbnail(uri) }
    }
    val modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)).pointerInput(uri) {
        // Shows the photo for as long as the finger stays down
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val held = awaitLongPressOrCancellation(down.id)
            if (held != null) {
                vm.bulkPreview = uri
                try {
                    held.consume()
                    waitForUpOrCancellation()
                } finally {
                    vm.bulkPreview = null
                }
            }
        }
    }
    thumb?.let { Image(it.asImageBitmap(), "Document photo", modifier, contentScale = ContentScale.Crop) }
        ?: Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
}

@Composable
private fun TypePicker(item: BulkItem, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            categoryOf(item.category).label + " ▾",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.clickable { open = true }.padding(vertical = 3.dp),
        )
        DropdownMenu(open, { open = false }) {
            categories.forEach { c ->
                DropdownMenuItem({ Text(c.label) }, { onPick(c.key); open = false })
            }
        }
    }
}

@Composable
private fun PersonPicker(vm: VaultVm, item: BulkItem, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            "For ${vm.profileName(item.profile)} ▾",
            style = MaterialTheme.typography.bodyMedium, color = Gold,
            modifier = Modifier.clickable { open = true }.padding(vertical = 3.dp),
        )
        DropdownMenu(open, { open = false }) {
            vm.profiles.forEach { p ->
                DropdownMenuItem({ Text(p.name) }, { onPick(p.id); open = false })
            }
        }
    }
}

@Composable
private fun Done(vm: VaultVm, onFinish: () -> Unit) {
    val saved = vm.bulkDone - vm.bulkFailed
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(if (saved == 1) "1 document saved" else "$saved documents saved", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        if (vm.bulkFailed > 0) {
            Text("${vm.bulkFailed} couldn't be saved. You can add those by hand.", color = Coral)
            Spacer(Modifier.height(8.dp))
        }
        Text(
            "Your originals are still in your gallery. Delete them there if you want them only in DocVault. Make a new backup too.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onFinish) { Text("Done") }
    }
}
