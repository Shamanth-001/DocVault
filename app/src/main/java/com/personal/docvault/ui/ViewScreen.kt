package com.personal.docvault.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.docvault.MainActivity
import com.personal.docvault.Route
import com.personal.docvault.VaultVm
import com.personal.docvault.data.Reminders

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ViewScreen(vm: VaultVm, id: String) {
    val activity = LocalContext.current as MainActivity
    val doc = vm.doc(id)
    if (doc == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val cat = categoryOf(doc.category)
    var revealed by remember(id) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showText by remember(id) { mutableStateOf(false) }

    fun launchOut(intent: Intent) {
        try {
            vm.externalActivity = true
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            vm.externalActivity = false
            vm.toast = "No app on this phone can open that file."
        }
    }

    fun launchWhatsApp(intent: Intent) {
        try {
            vm.externalActivity = true
            activity.startActivity(Intent(intent).setPackage("com.whatsapp"))
        } catch (_: ActivityNotFoundException) {
            launchOut(intent)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton({ vm.toggleFavorite(doc) }) {
                        Icon(
                            if (doc.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            if (doc.favorite) "Remove from favourites" else "Add to favourites",
                            tint = if (doc.favorite) Gold else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton({ vm.share(doc, ::launchOut) }) { Icon(Icons.Filled.Share, "Share") }
                    IconButton({ vm.navigate(Route.Edit(doc.id)) }) { Icon(Icons.Filled.Edit, "Edit") }
                    IconButton({ confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            val images = doc.images
            if (images.isNotEmpty()) {
                val pager = rememberPagerState { images.size }
                HorizontalPager(pager, Modifier.fillMaxWidth().height(240.dp), pageSpacing = 12.dp) { page ->
                    BlobImage(
                        vm, images[page].blobId, 1400,
                        Modifier.fillMaxSize().clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { vm.navigate(Route.Zoom(images[page].blobId)) },
                        ContentScale.Fit,
                    )
                }
                if (images.size > 1) {
                    Text(
                        "${pager.currentPage + 1} of ${images.size}",
                        Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(doc.category)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(doc.title, style = MaterialTheme.typography.headlineSmall)
                    Text(cat.label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                doc.daysToExpiry()?.takeIf { it < 90 }?.let { ExpiryPill(it) }
            }

            Spacer(Modifier.height(16.dp))
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    if (vm.profiles.size > 1) {
                        val owner = vm.profiles.firstOrNull { it.id == doc.profile }
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            owner?.let { Avatar(vm, it, 36) }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    "Belongs to", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(vm.profileName(doc.profile), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    if (doc.number.isNotBlank()) {
                        DetailRow(
                            cat.numberLabel,
                            if (revealed) doc.number else maskNumber(doc.number),
                            onCopy = { vm.copy(cat.numberLabel, doc.number) },
                            reveal = { revealed = !revealed },
                            revealed = revealed,
                        )
                    }
                    if (doc.holder.isNotBlank()) {
                        DetailRow("Name", doc.holder, onCopy = { vm.copy("Name", doc.holder) })
                    }
                    doc.fields.forEach { f ->
                        DetailRow(f.label, f.value, onCopy = { vm.copy(f.label, f.value) })
                    }
                    if (doc.expiry.isNotBlank()) DetailRow("Expires", formatDate(doc.expiry))
                    if (doc.notes.isNotBlank()) DetailRow("Notes", doc.notes)
                    if (doc.number.isBlank() && doc.holder.isBlank() && doc.fields.isEmpty() &&
                        doc.expiry.isBlank() && doc.notes.isBlank()
                    ) {
                        DetailRow("Details", "None added yet")
                    }
                }
            }

            if (Reminders.message(doc.expiry) != null) {
                Spacer(Modifier.height(12.dp))
                val done = doc.reminderFor == doc.expiry
                androidx.compose.material3.OutlinedButton(
                    { vm.setReminder(doc, ::launchWhatsApp) },
                    Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Icon(Icons.Filled.NotificationsActive, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (done) "Send WhatsApp reminders again" else "Set WhatsApp reminders")
                }
                Text(
                    if (done) {
                        "Opened in WhatsApp for this expiry date. If you tapped Send there, it's set."
                    } else {
                        "Opens your bot's chat with the dates typed in. Tap Send there once. No document details are included."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp),
                )
            }

            val files = doc.attachments.filter { it.kind == "file" }
            if (files.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                files.forEach { f ->
                    Surface(
                        onClick = { vm.openFile(f, ::launchOut) },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PictureAsPdf, null, tint = Coral)
                            Spacer(Modifier.width(12.dp))
                            Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            if (doc.photoText.isNotBlank()) {
                TextButton({ showText = !showText }) {
                    Text(if (showText) "Hide text found in photos" else "Show text found in photos")
                }
                if (showText) {
                    Text(
                        doc.photoText, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${doc.title}?") },
            text = { Text("This removes it and its photos from DocVault. This can't be undone.") },
            confirmButton = {
                TextButton({
                    confirmDelete = false
                    vm.delete(doc) { vm.back() }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    onCopy: (() -> Unit)? = null,
    reveal: (() -> Unit)? = null,
    revealed: Boolean = false,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        if (reveal != null) {
            IconButton(reveal) {
                Icon(
                    if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    if (revealed) "Hide $label" else "Show $label",
                )
            }
        }
        if (onCopy != null) {
            IconButton(onCopy) { Icon(Icons.Filled.ContentCopy, "Copy $label", Modifier.size(20.dp)) }
        }
    }
}

@Composable
fun ZoomScreen(vm: VaultVm, blobId: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        BlobImage(
            vm, blobId, 2400,
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            ContentScale.Fit,
        )
        IconButton(vm::back, Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)) {
            Icon(Icons.Filled.Close, "Close", tint = Color.White)
        }
    }
}
