package com.personal.docvault.data

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** AES-256-GCM helpers. Every sealed blob is its random 12-byte IV followed by ciphertext and tag. */
object Crypto {
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128
    private const val IV_BYTES = 12
    const val PBKDF2_ITERATIONS = 310_000

    private val rng = SecureRandom()

    fun random(size: Int): ByteArray = ByteArray(size).also { rng.nextBytes(it) }

    fun seal(key: ByteArray, plain: ByteArray): ByteArray {
        val iv = random(IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        return iv + cipher.doFinal(plain)
    }

    fun open(key: ByteArray, sealed: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES),
        )
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }

    fun derive(password: CharArray, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
