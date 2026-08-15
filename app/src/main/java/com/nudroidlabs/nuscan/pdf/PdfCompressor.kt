package com.nudroidlabs.nuscan.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.nudroidlabs.nuscan.data.DocumentRepository
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlin.math.max
import kotlin.math.roundToInt

object PdfCompressor {
    enum class Preset(
        val label: String,
        val description: String,
        internal val maxRenderDimension: Int,
        internal val jpegQuality: Float
    ) {
        HIGH("High quality", "Sharper pages with lighter compression", 2600, 0.90f),
        BALANCED("Balanced", "Good quality with useful size reduction", 1900, 0.80f),
        SMALL("Small file", "Stronger compression for sharing", 1350, 0.68f)
    }

    data class Result(
        val file: java.io.File,
        val pageCount: Int,
        val originalBytes: Long,
        val outputBytes: Long
    )

    fun compress(
        context: Context,
        source: Uri,
        requestedName: String,
        preset: Preset
    ): Result {
        val output = DocumentRepository.uniquePdfFile(
            DocumentRepository.outputDirectory(context),
            requestedName
        )
        val originalBytes = sourceSize(context, source)
        val descriptor = context.contentResolver.openFileDescriptor(source, "r")
            ?: error("Unable to open PDF.")

        try {
            descriptor.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    require(renderer.pageCount > 0) { "This PDF has no pages." }
                    PDDocument().use { document ->
                        for (index in 0 until renderer.pageCount) {
                            renderer.openPage(index).use { sourcePage ->
                                val pageWidth = sourcePage.width.toFloat().coerceAtLeast(1f)
                                val pageHeight = sourcePage.height.toFloat().coerceAtLeast(1f)
                                val longest = max(sourcePage.width, sourcePage.height).coerceAtLeast(1)
                                val scale = (preset.maxRenderDimension.toFloat() / longest.toFloat())
                                    .coerceAtMost(3f)
                                    .coerceAtLeast(0.25f)
                                val bitmapWidth = (sourcePage.width * scale).roundToInt().coerceAtLeast(1)
                                val bitmapHeight = (sourcePage.height * scale).roundToInt().coerceAtLeast(1)
                                val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)

                                try {
                                    bitmap.eraseColor(Color.WHITE)
                                    sourcePage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                    val page = PDPage(PDRectangle(pageWidth, pageHeight))
                                    document.addPage(page)
                                    val image = JPEGFactory.createFromImage(document, bitmap, preset.jpegQuality)
                                    PDPageContentStream(document, page).use { stream ->
                                        stream.drawImage(image, 0f, 0f, pageWidth, pageHeight)
                                    }
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                        }
                        document.save(output)
                    }
                    require(output.exists() && output.length() > 0L) { "Compressed PDF was not created." }
                    return Result(
                        file = output,
                        pageCount = renderer.pageCount,
                        originalBytes = originalBytes,
                        outputBytes = output.length()
                    )
                }
            }
        } catch (error: Throwable) {
            output.delete()
            throw error
        }
    }

    private fun sourceSize(context: Context, source: Uri): Long {
        return context.contentResolver.query(
            source,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) cursor.getLong(index) else 0L
        } ?: 0L
    }
}
