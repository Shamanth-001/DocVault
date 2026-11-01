package com.personal.docvault

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.personal.docvault.ui.App
import com.personal.docvault.ui.VaultTheme

class MainActivity : FragmentActivity() {
    private val vm by viewModels<VaultVm>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Blocks screenshots, screen recording and the recent-apps preview
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        Notifier.start(this)
        setContent { VaultTheme { App(vm) } }
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }

    override fun onStop() {
        super.onStop()
        vm.onBackground()
    }

    fun authenticate(title: String, onSuccess: () -> Unit, onError: (String) -> Unit = {}) {
        val prompt = BiometricPrompt(
            this, mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) =
                    onError(errString.toString())
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle("Use your fingerprint or phone screen lock")
                .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                .build(),
        )
    }
}
