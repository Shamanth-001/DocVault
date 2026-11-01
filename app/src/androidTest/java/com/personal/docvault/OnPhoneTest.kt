package com.personal.docvault

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.platform.app.InstrumentationRegistry
import com.personal.docvault.data.Crypto
import com.personal.docvault.data.Detector
import com.personal.docvault.data.KeyWrapper
import com.personal.docvault.data.VaultStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Runs on the real phone with a throwaway vault, using only made-up card text. */
class OnPhoneTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun drawCard(vararg lines: String): Bitmap {
        val bmp = Bitmap.createBitmap(1400, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 64f }
        lines.forEachIndexed { i, line -> canvas.drawText(line, 80f, 130f + i * 110, paint) }
        return bmp
    }

    @Test fun readsTextFromAPhotoWithoutInternet() = runBlocking<Unit> {
        val root = File(context.cacheDir, "phone-test").apply { deleteRecursively() }
        val wrapperKey = ByteArray(32) { 3 }
        val wrapper = object : KeyWrapper {
            override fun wrap(key: ByteArray) = Crypto.seal(wrapperKey, key)
            override fun unwrap(wrapped: ByteArray) = Crypto.open(wrapperKey, wrapped)
        }
        val store = VaultStore(File(root, "vault"), wrapper)
        store.create("test password".toCharArray())

        val text = Importer(context, store).readText(
            drawCard("INCOME TAX DEPARTMENT", "Permanent Account Number", "ABCPE1234F"),
        )

        assertTrue("OCR read: $text", "INCOME TAX" in text.uppercase())
        val found = Detector.detect(text)
        assertEquals("pan", found.category)
        assertEquals("ABCPE1234F", found.number)
        root.deleteRecursively()
    }
}
