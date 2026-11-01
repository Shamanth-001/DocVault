package com.personal.docvault.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps a hardware-backed key that only works for 30 seconds after a fingerprint or screen-lock
 * check. Adding a new fingerprint does not invalidate it; removing the screen lock does, which is
 * why the recovery password exists.
 */
class KeystoreWrapper : KeyWrapper {
    private val alias = "docvault_device_key"
    private val store get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun generate(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(
                30, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
            )
            .setInvalidatedByBiometricEnrollment(false)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec) }
            .generateKey()
    }

    private fun existing() = store.getKey(alias, null) as SecretKey?

    private fun encryptCipher(): Cipher {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        try {
            cipher.init(Cipher.ENCRYPT_MODE, existing() ?: generate())
        } catch (_: KeyPermanentlyInvalidatedException) {
            store.deleteEntry(alias)
            cipher.init(Cipher.ENCRYPT_MODE, generate())
        }
        return cipher
    }

    override fun wrap(key: ByteArray): ByteArray {
        val cipher = encryptCipher()
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(key)
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        val ivSize = wrapped[0].toInt()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = existing() ?: throw KeyPermanentlyInvalidatedException()
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, wrapped, 1, ivSize))
        return cipher.doFinal(wrapped, 1 + ivSize, wrapped.size - 1 - ivSize)
    }
}
