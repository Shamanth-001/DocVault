package com.personal.docvault.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.personal.docvault.MainActivity
import com.personal.docvault.Notifier
import com.personal.docvault.VaultVm
import com.personal.docvault.data.Profile
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: VaultVm) {
    val activity = LocalContext.current as MainActivity
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var changingPassword by remember { mutableStateOf(false) }
    var addingProfile by remember { mutableStateOf(false) }
    var renamingProfile by remember { mutableStateOf<Profile?>(null) }
    var removingProfile by remember { mutableStateOf<Profile?>(null) }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        vm.externalActivity = false
        uri?.let(vm::backup)
    }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        vm.externalActivity = false
        restoreUri = uri
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Action(
                Icons.Filled.Notifications,
                "Test expiry notifications",
                "You'll get an alert 30, 7 and 1 days before a document expires. Tap to send a test.",
            ) {
                if (Notifier.enabled(activity)) {
                    Notifier.sendTest(activity)
                } else {
                    vm.toast = "Turn on notifications for DocVault."
                    vm.externalActivity = true
                    activity.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName),
                    )
                }
            }
            Action(
                Icons.Filled.BatteryChargingFull,
                "Let alerts run on this phone",
                "Opens DocVault's app page. Under battery or background settings, allow it to run in the background, or vivo may delay alerts.",
            ) {
                vm.externalActivity = true
                activity.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")),
                )
            }
            Action(Icons.Filled.Backup, "Back up my documents", "Saves one encrypted file. Keep a copy off this phone, such as in Drive or on your laptop.") {
                vm.externalActivity = true
                createBackup.launch("DocVault-backup-${LocalDate.now()}.dvault")
            }
            Action(Icons.Filled.Restore, "Restore from a backup", "Replaces everything in DocVault with the backup.") {
                vm.externalActivity = true
                pickBackup.launch(arrayOf("*/*"))
            }
            Action(Icons.Filled.Key, "Change recovery password", "Backups you already made still use the old password.") {
                changingPassword = true
            }

            Spacer(Modifier.height(16.dp))
            Text("Family profiles", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            vm.profiles.forEach { p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, Modifier.weight(1f))
                    TextButton({ renamingProfile = p }) { Text("Rename") }
                    if (p.id != Profile.ME) {
                        TextButton({ removingProfile = p }) { Text("Remove") }
                    }
                }
            }
            TextButton({ addingProfile = true }) { Text("Add person") }

            Spacer(Modifier.height(16.dp))
            Text("WhatsApp reminder bot", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Enter your reminder bot's WhatsApp number. Only reminder dates are sent to it, and only after you tap Send in WhatsApp.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            var bot by remember { mutableStateOf(vm.botNumber) }
            OutlinedTextField(
                bot, { bot = it },
                label = { Text("Bot's number with country code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton({ vm.saveBotNumber(bot) }) { Text("Save bot number") }

            Spacer(Modifier.height(16.dp))
            Text("How your documents are protected", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            listOf(
                "This app has no internet permission, so it can't send anything anywhere.",
                "Everything is encrypted with AES-256, and the key is locked by your phone's secure hardware.",
                "It locks 60 seconds after you leave, and screenshots are blocked.",
                "Only expiry dates and family profile names are kept outside the encryption, so alerts work while locked. No document names, numbers or photos are.",
                "Photos you add stay in your gallery too. Delete them there if you want them only here.",
                "Uninstalling DocVault erases everything in it. Only a backup can bring it back.",
            ).forEach {
                Text(
                    "•  $it", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp),
                )
            }
        }
    }

    if (addingProfile) {
        PersonDialog("Add person", "", onSave = { vm.addProfile(it); addingProfile = false }) {
            addingProfile = false
        }
    }

    renamingProfile?.let { p ->
        PersonDialog("Rename ${p.name}", p.name, onSave = { vm.renameProfile(p.id, it); renamingProfile = null }) {
            renamingProfile = null
        }
    }

    removingProfile?.let { p ->
        AlertDialog(
            onDismissRequest = { removingProfile = null },
            title = { Text("Remove ${p.name}?") },
            text = { Text("Only the profile is removed. Their documents move to ${vm.profileName(Profile.ME)}, and nothing is deleted.") },
            confirmButton = {
                TextButton({
                    vm.deleteProfile(p.id)
                    removingProfile = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton({ removingProfile = null }) { Text("Cancel") } },
        )
    }

    restoreUri?.let { uri ->
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("Replace everything?") },
            text = {
                Column {
                    Text("Your current documents will be replaced by this backup.")
                    Spacer(Modifier.height(12.dp))
                    PasswordField("Backup's recovery password", password, { password = it })
                    vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton({
                    vm.error = null
                    vm.restore(uri, password.toCharArray())
                }, enabled = password.isNotEmpty()) { Text("Restore") }
            },
            dismissButton = { TextButton({ restoreUri = null; vm.error = null }) { Text("Cancel") } },
        )
    }

    if (changingPassword) {
        var current by remember { mutableStateOf("") }
        var new by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { changingPassword = false },
            title = { Text("Change recovery password") },
            text = {
                Column {
                    PasswordField("Current password", current, { current = it })
                    Spacer(Modifier.height(8.dp))
                    PasswordField("New password, $MIN_PASSWORD or more characters", new, { new = it })
                    vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton({
                    vm.error = null
                    vm.changePassword(current.toCharArray(), new.toCharArray()) { changingPassword = false }
                }, enabled = current.isNotEmpty() && new.length >= MIN_PASSWORD) { Text("Change") }
            },
            dismissButton = { TextButton({ changingPassword = false; vm.error = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Action(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Gold)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
