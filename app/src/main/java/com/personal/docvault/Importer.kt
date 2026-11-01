package com.personal.docvault

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.personal.docvault.data.Attachment
import com.personal.docvault.data.VaultStore
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class Imported(val attachments: List<Attachment>, val text: String, val note: String? = null)

/** Turns picked photos and PDFs into encrypted attachments and reads their text on the phone. */
class Importer(private val context: Context, private val store: VaultStore) {
    private val resolver: ContentResolver get() = context.contentResolver
    private val maxSide = 2400
    private val maxFileBytes = 40L * 1024 * 1024
    private val maxPdfPages = 30

    private fun displayName(uri: Uri): String = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: "document"

    private fun jpeg(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().also {
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
    }.toByteArray()

    suspend fun readText(bitmap: Bitmap): String = runCatching {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
        } finally {
            recognizer.close()
        }
    }.getOrDefault("")

    suspend fun image(uri: Uri): Imported {
        val source = ImageDecoder.createSource(resolver, uri)
        // ImageDecoder also applies the photo's rotation, unlike BitmapFactory
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val largest = maxOf(info.size.width, info.size.height)
            if (largest > maxSide) {
                val scale = maxSide.toFloat() / largest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
        }
        try {
            val text = readText(bitmap)
            val id = store.putBlob(jpeg(bitmap))
            return Imported(listOf(Attachment(id, "image", displayName(uri), "image/jpeg")), text)
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun pdf(uri: Uri): Imported {
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > maxFileBytes) throw IllegalArgumentException("That PDF is larger than 40 MB")
            }
            out.toByteArray()
        } ?: throw IllegalArgumentException("Could not open that file")

        val name = displayName(uri)
        val attachments = mutableListOf(
            Attachment(store.putBlob(bytes), "file", name, "application/pdf"),
        )
        val text = StringBuilder()
        var note: String? = null

        val temp = File(context.cacheDir, "import-${UUID.randomUUID()}.pdf")
        try {
            temp.writeBytes(bytes)
            ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    val count = minOf(renderer.pageCount, maxPdfPages)
                    if (renderer.pageCount > maxPdfPages) {
                        note = "Showing the first $maxPdfPages pages. The full PDF is saved."
                    }
                    for (i in 0 until count) {
                        renderer.openPage(i).use { page ->
                            val scale = 1800f / maxOf(page.width, 1)
                            val bmp = Bitmap.createBitmap(
                                1800, (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888,
                            )
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            text.appendLine(readText(bmp))
                            attachments += Attachment(
                                store.putBlob(jpeg(bmp)), "image", "$name page ${i + 1}", "image/jpeg",
                            )
                            bmp.recycle()
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
            // Password-protected PDFs, such as e-Aadhaar, cannot be previewed but are kept intact
            note = "This PDF has a password, so it can't be previewed or searched. It's saved safely, and you can open it from here."
        } catch (_: java.io.IOException) {
            note = "This PDF can't be previewed, but it's saved safely."
        } finally {
            temp.delete()
        }
        return Imported(attachments, text.toString(), note)
    }
}
