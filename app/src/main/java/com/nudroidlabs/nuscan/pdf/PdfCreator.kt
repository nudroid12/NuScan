package com.nudroidlabs.nuscan.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.nudroidlabs.nuscan.data.DocumentRepository
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

object PdfCreator {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val PAGE_MARGIN = 32f
    private const val MAX_IMAGE_DIMENSION = 2600

    fun create(context: Context, images: List<Uri>, requestedName: String): File {
        require(images.isNotEmpty()) { "Select at least one image." }

        val safeName = requestedName
            .trim()
            .removeSuffix(".pdf")
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .take(80)
            .ifBlank { "NuScan_Document" }

        val output = uniqueFile(DocumentRepository.outputDirectory(context), safeName)
        val pdf = PdfDocument()

        try {
            images.forEachIndexed { index, uri ->
                val bitmap = decodeForPdf(context, uri)
                    ?: error("Unable to read image ${index + 1}.")

                try {
                    val pageInfo = PdfDocument.PageInfo.Builder(
                        PAGE_WIDTH,
                        PAGE_HEIGHT,
                        index + 1
                    ).create()
                    val page = pdf.startPage(pageInfo)
                    drawImage(page.canvas, bitmap)
                    pdf.finishPage(page)
                } finally {
                    bitmap.recycle()
                }
            }

            FileOutputStream(output).use(pdf::writeTo)
            return output
        } catch (error: Throwable) {
            output.delete()
            throw error
        } finally {
            pdf.close()
        }
    }

    private fun uniqueFile(directory: File, baseName: String): File {
        var candidate = File(directory, "$baseName.pdf")
        var suffix = 2
        while (candidate.exists()) {
            candidate = File(directory, "$baseName ($suffix).pdf")
            suffix++
        }
        return candidate
    }

    private fun decodeForPdf(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val rotation = resolver.openInputStream(uri)?.use { stream ->
            runCatching {
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }.getOrDefault(0f)
        } ?: 0f

        if (rotation == 0f) return decoded

        val matrix = Matrix().apply { postRotate(rotation) }
        val rotated = Bitmap.createBitmap(
            decoded,
            0,
            0,
            decoded.width,
            decoded.height,
            matrix,
            true
        )
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        var largest = max(width, height)
        while (largest / 2 >= MAX_IMAGE_DIMENSION) {
            sample *= 2
            largest /= 2
        }
        return sample
    }

    private fun drawImage(canvas: Canvas, bitmap: Bitmap) {
        canvas.drawColor(android.graphics.Color.WHITE)
        val availableWidth = PAGE_WIDTH - PAGE_MARGIN * 2
        val availableHeight = PAGE_HEIGHT - PAGE_MARGIN * 2
        val scale = min(
            availableWidth / bitmap.width.toFloat(),
            availableHeight / bitmap.height.toFloat()
        )
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val left = (PAGE_WIDTH - drawWidth) / 2f
        val top = (PAGE_HEIGHT - drawHeight) / 2f
        val target = RectF(left, top, left + drawWidth, top + drawHeight)
        canvas.drawBitmap(bitmap, null, target, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }
}
