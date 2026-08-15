package com.nudroidlabs.nuscan.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.max
import kotlin.math.roundToInt

object OcrEngine {
    data class Result(val text: String, val pageCount: Int)

    suspend fun recognise(context: Context, source: Uri, mimeType: String?): Result {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            if (mimeType.equals("application/pdf", ignoreCase = true)) {
                recognisePdf(context, source, recognizer)
            } else {
                val image = InputImage.fromFilePath(context, source)
                val result = recognizer.process(image).await()
                Result(result.text.trim(), 1)
            }
        } finally {
            recognizer.close()
        }
    }

    private suspend fun recognisePdf(
        context: Context,
        source: Uri,
        recognizer: com.google.mlkit.vision.text.TextRecognizer
    ): Result {
        val descriptor = context.contentResolver.openFileDescriptor(source, "r")
            ?: error("Unable to open PDF.")

        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                require(renderer.pageCount > 0) { "This PDF has no pages." }
                val output = StringBuilder()

                for (index in 0 until renderer.pageCount) {
                    renderer.openPage(index).use { page ->
                        val longest = max(page.width, page.height).coerceAtLeast(1)
                        val scale = (1800f / longest.toFloat()).coerceAtMost(2.5f).coerceAtLeast(0.5f)
                        val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                        val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val recognised = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text.trim()
                            if (renderer.pageCount > 1) {
                                if (output.isNotEmpty()) output.append("\n\n")
                                output.append("--- Page ${index + 1} ---\n")
                            }
                            output.append(recognised)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }

                return Result(output.toString().trim(), renderer.pageCount)
            }
        }
    }
}
