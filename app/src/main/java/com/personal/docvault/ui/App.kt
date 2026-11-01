package com.personal.docvault.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.dp
import com.personal.docvault.MainActivity
import com.personal.docvault.Notifier
import com.personal.docvault.Phase
import com.personal.docvault.Route
import com.personal.docvault.VaultVm
import kotlinx.coroutines.delay

@Composable
fun App(vm: VaultVm) {
    val activity = LocalContext.current as MainActivity

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            when (vm.phase) {
                Phase.Setup -> SetupScreen(vm)
                Phase.Locked -> LockedScreen(vm)
                Phase.Open -> {
                    val route = vm.stack.last()
                    BackHandler(enabled = vm.stack.size > 1 && route !is Route.Edit && route !is Route.BulkImport) {
                        vm.back()
                    }
                    when (route) {
                        Route.Home -> HomeScreen(vm)
                        Route.Settings -> SettingsScreen(vm)
                        Route.BulkImport -> BulkImportScreen(vm)
                        is Route.View -> ViewScreen(vm, route.id)
                        is Route.Edit -> EditScreen(vm, route.id)
                        is Route.Zoom -> ZoomScreen(vm, route.blobId)
                    }
                }
            }

            vm.busy?.let { label ->
                Box(
                    Modifier.fillMaxSize().background(Color(0xB30E1420)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(14.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            vm.toast?.let { message ->
                LaunchedEffect(message) {
                    delay(3500)
                    vm.toast = null
                }
                Snackbar(
                    Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding().padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) { Text(message) }
            }
        }
    }

    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.externalActivity = false
        if (granted) vm.refreshReminders()
    }
    LaunchedEffect(vm.phase) {
        if (vm.phase == Phase.Open && Build.VERSION.SDK_INT >= 33 && !Notifier.enabled(activity)) {
            vm.externalActivity = true
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // After restoring a backup, or if the phone's lock changed, link the fingerprint again
    LaunchedEffect(vm.relinkDevice, vm.phase) {
        if (vm.relinkDevice && vm.phase == Phase.Open) {
            activity.authenticate("Turn on fingerprint unlock", onSuccess = vm::relink)
        }
    }
}
