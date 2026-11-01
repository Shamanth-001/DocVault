package com.personal.docvault.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.docvault.Route
import com.personal.docvault.VaultVm
import com.personal.docvault.data.Doc
import com.personal.docvault.data.Profile
import com.personal.docvault.data.SearchHit
import com.personal.docvault.data.Search

fun maskNumber(n: String): String {
    val clean = n.filterNot(Char::isWhitespace)
    return if (clean.length <= 4) n else "•••• " + clean.takeLast(4)
}

fun expiryLabel(days: Long): String = when {
    days < 0 -> "Expired"
    days == 0L -> "Expires today"
    days < 60 -> "$days days left"
    days < 365 -> "${days / 30} months left"
    else -> "${days / 365} years left"
}

@Composable
fun BlobImage(
    vm: VaultVm,
    blobId: String,
    maxSide: Int,
    modifier: Modifier = Modifier,
    scale: ContentScale = ContentScale.Crop,
) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, blobId, maxSide) { value = vm.bitmap(blobId, maxSide) }
    bitmap?.let { Image(it.asImageBitmap(), "Document photo", modifier, contentScale = scale) }
        ?: Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
}

@Composable
fun CategoryBadge(key: String, size: Int = 48) {
    val c = categoryOf(key)
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(14.dp)).background(c.color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(c.icon, null, Modifier.size((size * 0.52f).dp), tint = c.color)
    }
}

@Composable
private fun DocRow(vm: VaultVm, hit: SearchHit) {
    val d = hit.doc
    Surface(
        onClick = { vm.navigate(Route.View(d.id)) },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val image = d.images.firstOrNull()
            if (image != null) {
                BlobImage(vm, image.blobId, 200, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
            } else {
                CategoryBadge(d.category, 56)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        d.title, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false),
                    )
                    if (d.favorite) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.Star, "Favourite", Modifier.size(15.dp), tint = Gold)
                    }
                }
                val person = if (vm.activeProfile == null && vm.profiles.size > 1) vm.profileName(d.profile) else ""
                val sub = listOf(person, d.holder, if (d.number.isBlank()) "" else maskNumber(d.number))
                    .filter(String::isNotBlank).joinToString("  ·  ")
                Text(
                    sub.ifBlank { categoryOf(d.category).label },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                hit.snippet?.let {
                    Text(
                        it, style = MaterialTheme.typography.bodyMedium, color = Gold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            d.daysToExpiry()?.takeIf { it < 90 }?.let { days ->
                Spacer(Modifier.width(8.dp))
                ExpiryPill(days)
            }
        }
    }
}

@Composable
fun ExpiryPill(days: Long) {
    val color = if (days < 30) Coral else Gold
    Text(
        expiryLabel(days),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
fun HomeScreen(vm: VaultVm) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }

    var addingPerson by remember { mutableStateOf(false) }

    val active = vm.activeProfile
    val all = remember(vm.docs, active) { vm.docs.filter { active == null || it.profile == active } }
    val present = remember(all) { categories.filter { c -> all.any { it.category == c.key } } }
    val hits = remember(all, query, filter, vm.profiles) {
        Search.run(all.filter { filter == null || it.category == filter }, query, vm::profileName) { categoryOf(it).label }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.navigate(Route.Edit(null)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Add document") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()).statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("DocVault", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        if (all.size == 1) "1 document" else "${all.size} documents",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(vm::lock) { Icon(Icons.Filled.Lock, "Lock now") }
                IconButton({ vm.navigate(Route.Settings) }) { Icon(Icons.Filled.Settings, "Settings") }
            }

            Spacer(Modifier.height(8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(active == null, { vm.activeProfile = null; filter = null }, { Text("Everyone") })
                }
                items(vm.profiles, key = { it.id }) { p ->
                    FilterChip(active == p.id, { vm.activeProfile = p.id; filter = null }, { Text(p.name) })
                }
                item {
                    AssistChip(
                        onClick = { addingPerson = true },
                        label = { Text("Add person") },
                        leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) },
                    )
                }
            }

            TextField(
                query, { query = it },
                placeholder = { Text("Search names, numbers, text in photos") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton({ query = "" }) { Icon(Icons.Filled.Close, "Clear search") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )

            if (present.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { FilterChip(filter == null, { filter = null }, { Text("All") }) }
                    items(present, key = { it.key }) { c ->
                        FilterChip(
                            filter == c.key,
                            { filter = if (filter == c.key) null else c.key },
                            { Text(c.label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            when {
                all.isEmpty() -> EmptyState(
                    "Nothing here yet",
                    "Add your Aadhaar, PAN or any document. Photos are read on your phone, so you can search what's in them.",
                )
                hits.isEmpty() -> EmptyState("No matches", "Try a different word or number.")
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(hits, key = { it.doc.id }) { DocRow(vm, it) }
                }
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
fun PersonDialog(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    name, { name = it.take(Profile.MAX_NAME) },
                    label = { Text("Name, like Mother") }, singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "This name appears on expiry alerts for their documents, even when DocVault is locked. A nickname works too.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton({ onSave(name) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            body, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
