package com.personal.docvault.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.personal.docvault.MainActivity
import com.personal.docvault.VaultVm

const val MIN_PASSWORD = 8

@Composable
fun PasswordField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(false) }
    OutlinedTextField(
        value, onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton({ shown = !shown }) {
                Icon(
                    if (shown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    if (shown) "Hide password" else "Show password",
                )
            }
        },
    )
}

@Composable
private fun Crest() {
    Box(
        Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Shield, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun SetupScreen(vm: VaultVm) {
    val activity = LocalContext.current as MainActivity
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }

    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        vm.externalActivity = false
        restoreUri = uri
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Crest()
        Spacer(Modifier.height(20.dp))
        Text("DocVault", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your documents, encrypted and kept only on this phone.",
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(30.dp))

        val restoring = restoreUri
        if (restoring != null) {
            Text("Enter the recovery password for this backup.", Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            PasswordField("Recovery password", password, { password = it })
            vm.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                { vm.error = null; vm.restore(restoring, password.toCharArray()) },
                Modifier.fillMaxWidth(), enabled = password.isNotEmpty(),
            ) { Text("Restore my documents") }
            TextButton({ restoreUri = null; password = "" }) { Text("Cancel") }
        } else if (!vm.deviceSecure()) {
            Text(
                "Set a screen lock on your phone first. DocVault uses it, along with your fingerprint, " +
                    "to protect your documents. Go to Settings, then Security, then come back here.",
                textAlign = TextAlign.Center,
            )
        } else {
            Text("Create a recovery password", Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Day to day you'll use your fingerprint. You need this password to restore a backup on a " +
                    "new phone. Nobody can reset it, so write it down and keep it safe.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            PasswordField("Recovery password", password, { password = it })
            Spacer(Modifier.height(10.dp))
            PasswordField("Type it again", confirm, { confirm = it })
            val tooShort = password.isNotEmpty() && password.length < MIN_PASSWORD
            val mismatch = confirm.isNotEmpty() && confirm != password
            if (tooShort || mismatch) {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (tooShort) "Use at least $MIN_PASSWORD characters." else "The two passwords don't match.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(18.dp))
            Button(
                {
                    activity.authenticate(
                        "Set up DocVault",
                        onSuccess = { vm.createVault(password.toCharArray()) },
                        onError = { vm.toast = it },
                    )
                },
                Modifier.fillMaxWidth().height(52.dp),
                enabled = password.length >= MIN_PASSWORD && password == confirm,
            ) { Text("Create my vault") }
            Spacer(Modifier.height(8.dp))
            TextButton({
                vm.externalActivity = true
                pickBackup.launch(arrayOf("*/*"))
            }) { Text("I have a backup to restore") }
        }
    }
}

@Composable
fun LockedScreen(vm: VaultVm) {
    val activity = LocalContext.current as MainActivity
    var usePassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    fun promptDevice() = activity.authenticate(
        "Unlock DocVault",
        onSuccess = vm::unlockWithDevice,
        onError = { vm.toast = it },
    )

    LaunchedEffect(Unit) { promptDevice() }
    LaunchedEffect(vm.needsDevicePassword) { if (vm.needsDevicePassword) usePassword = true }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Crest()
        Spacer(Modifier.height(20.dp))
        Text("DocVault is locked", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(28.dp))
        if (usePassword) {
            PasswordField("Recovery password", password, { password = it })
            vm.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                { vm.error = null; vm.unlockWithPassword(password.toCharArray()); password = "" },
                Modifier.fillMaxWidth().height(52.dp), enabled = password.isNotEmpty(),
            ) { Text("Unlock") }
            if (!vm.needsDevicePassword) {
                TextButton({ usePassword = false }) { Text("Use fingerprint instead") }
            }
        } else {
            Button(::promptDevice, Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.Fingerprint, null)
                Spacer(Modifier.size(8.dp))
                Text("Unlock")
            }
            TextButton({ usePassword = true }) { Text("Use recovery password") }
        }
    }
}
