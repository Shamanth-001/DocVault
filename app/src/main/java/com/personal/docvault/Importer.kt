package com.personal.docvault

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Size
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

class GalleryPhoto(val key: String, val uri: Uri, val folder: String)

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

    // Kept for the app's lifetime, since a bulk scan would otherwise rebuild it for every photo
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun readText(bitmap: Bitmap): String = runCatching {
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
    }.getOrDefault("")

    /** Newest first. Tiny images such as icons and stickers are left out. */
    fun galleryPhotos(): List<GalleryPhoto> {
        val columns = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
        )
        val photos = mutableListOf<GalleryPhoto>()
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns, null, null,
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val size = c.getLong(3)
                val width = c.getInt(4)
                val height = c.getInt(5)
                if (size < 20_000 || (width in 1 until 300) || (height in 1 until 300)) continue
                val id = c.getLong(0)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                photos += GalleryPhoto("$id:${c.getLong(2)}", uri, c.getString(1) ?: "Other")
            }
        }
        return photos
    }

    /** Uses a smaller copy, which is far quicker and still reads printed cards. */
    suspend fun scanText(uri: Uri): String {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val largest = maxOf(info.size.width, info.size.height)
            if (largest > 1400) {
                val scale = 1400f / largest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
        }
        try {
            return readText(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    /** Saves a square, centre-cropped photo for a family profile and returns its blob id. */
    fun profilePhoto(uri: Uri): String {
        val full = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val smallest = minOf(info.size.width, info.size.height)
            if (smallest > 512) {
                val scale = 512f / smallest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
        }
        val side = minOf(full.width, full.height)
        val square = Bitmap.createBitmap(full, (full.width - side) / 2, (full.height - side) / 2, side, side)
        try {
            return store.putBlob(jpeg(square))
        } finally {
            if (square !== full) square.recycle()
            full.recycle()
        }
    }

    /** A screen-sized copy, shown while a photo is held down during bulk import. */
    fun preview(uri: Uri): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val largest = maxOf(info.size.width, info.size.height)
            if (largest > 1800) {
                val scale = 1800f / largest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
        }
    }.getOrNull()

    fun thumbnail(uri: Uri): Bitmap? = runCatching { resolver.loadThumbnail(uri, Size(240, 240), null) }.getOrNull()

    suspend fun image(uri: Uri, knownText: String? = null): Imported {
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
            val text = knownText ?: readText(bitmap)
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
