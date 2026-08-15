package com.nudroidlabs.nuscan.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import com.nudroidlabs.nuscan.data.DocumentRepository
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object PdfSigner {
    enum class Position(val label: String) {
        BOTTOM_LEFT("Bottom left"),
        BOTTOM_CENTRE("Bottom centre"),
        BOTTOM_RIGHT("Bottom right"),
        CENTRE("Centre")
    }

    data class Stroke(val points: List<Offset>)

    fun sign(
        context: Context,
        source: Uri,
        requestedName: String,
        pageNumber: Int,
        position: Position,
        widthPercent: Int,
        strokes: List<Stroke>
    ): File {
        require(strokes.any { it.points.size >= 2 }) { "Draw a signature first." }
        require(widthPercent in 15..50) { "Signature size is outside the supported range." }

        val temp = copyUriToTempPdf(context, source)
        val output = DocumentRepository.uniquePdfFile(
            DocumentRepository.outputDirectory(context),
            requestedName
        )
        val bitmap = renderSignature(strokes)

        try {
            PDDocument.load(temp, MemoryUsageSetting.setupTempFileOnly()).use { document ->
                require(!document.isEncrypted) { "Password-protected PDFs must be unlocked before signing." }
                require(pageNumber in 1..document.numberOfPages) { "Page must be between 1 and ${document.numberOfPages}." }

                val page = document.getPage(pageNumber - 1)
                val box = page.mediaBox
                val targetWidth = box.width * (widthPercent / 100f)
                val targetHeight = targetWidth * bitmap.height.toFloat() / bitmap.width.toFloat()
                val margin = 28f
                val x = when (position) {
                    Position.BOTTOM_LEFT -> margin
                    Position.BOTTOM_CENTRE, Position.CENTRE -> (box.width - targetWidth) / 2f
                    Position.BOTTOM_RIGHT -> box.width - targetWidth - margin
                }.coerceAtLeast(0f)
                val y = when (position) {
                    Position.CENTRE -> (box.height - targetHeight) / 2f
                    else -> margin
                }.coerceAtLeast(0f)

                val image = LosslessFactory.createFromImage(document, bitmap)
                PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                    stream.drawImage(image, x, y, targetWidth, targetHeight)
                }
                document.save(output)
            }
            require(output.exists() && output.length() > 0L) { "Signed PDF was not created." }
            return output
        } catch (error: Throwable) {
            output.delete()
            throw error
        } finally {
            bitmap.recycle()
            temp.delete()
        }
    }

    private fun renderSignature(strokes: List<Stroke>): Bitmap {
        val all = strokes.flatMap { it.points }
        val minX = all.minOf { it.x }
        val maxX = all.maxOf { it.x }
        val minY = all.minOf { it.y }
        val maxY = all.maxOf { it.y }
        val sourceWidth = max(1f, maxX - minX)
        val sourceHeight = max(1f, maxY - minY)
        val padding = 24f
        val scale = 900f / max(sourceWidth, sourceHeight)
        val width = (sourceWidth * scale + padding * 2).toInt().coerceAtLeast(120)
        val height = (sourceHeight * scale + padding * 2).toInt().coerceAtLeast(60)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 8f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        strokes.forEach { stroke ->
            val points = stroke.points
            for (index in 1 until points.size) {
                val a = points[index - 1]
                val b = points[index]
                canvas.drawLine(
                    (a.x - minX) * scale + padding,
                    (a.y - minY) * scale + padding,
                    (b.x - minX) * scale + padding,
                    (b.y - minY) * scale + padding,
                    paint
                )
            }
        }
        return bitmap
    }

    private fun copyUriToTempPdf(context: Context, source: Uri): File {
        val file = File.createTempFile("nuscan_sign_", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            } ?: error("Unable to read PDF.")
            return file
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }
}
