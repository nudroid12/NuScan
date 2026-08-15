package com.nudroidlabs.nuscan.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

internal data class CorrectedScan(
    val outputFile: File,
    val normalizedCorners: List<PointF>,
    val autoCorrected: Boolean,
    val sharpness: Double
)

internal object PerspectiveCorrector {
    private const val MAX_SOURCE_DIMENSION = 4200
    private const val MAX_OUTPUT_DIMENSION = 3800

    fun correct(input: File, output: File): CorrectedScan {
        val bitmap = decodeUpright(input)
        try {
            val detection = DocumentEdgeDetector.detect(bitmap)
            val normalized = detection?.corners?.map {
                PointF(
                    (it.x / bitmap.width.toFloat()).coerceIn(0f, 1f),
                    (it.y / bitmap.height.toFloat()).coerceIn(0f, 1f)
                )
            } ?: defaultCorners()

            val corrected = warpAndEnhance(bitmap, normalized)
            try {
                saveJpeg(corrected, output)
            } finally {
                corrected.recycle()
            }

            return CorrectedScan(
                outputFile = output,
                normalizedCorners = normalized,
                autoCorrected = detection != null,
                sharpness = detection?.sharpness ?: 0.0
            )
        } finally {
            bitmap.recycle()
        }
    }

    fun applyCorners(input: File, normalizedCorners: List<PointF>, output: File): CorrectedScan {
        require(normalizedCorners.size == 4) { "Four corners are required." }
        val bitmap = decodeUpright(input)
        try {
            val safe = normalizedCorners.map { PointF(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)) }
            val corrected = warpAndEnhance(bitmap, safe)
            try {
                saveJpeg(corrected, output)
            } finally {
                corrected.recycle()
            }
            return CorrectedScan(output, safe, true, 0.0)
        } finally {
            bitmap.recycle()
        }
    }

    fun decodePreview(file: File, maxDimension: Int = 1800): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        var largest = max(bounds.outWidth, bounds.outHeight)
        while (largest / 2 >= maxDimension) {
            sample *= 2
            largest /= 2
        }
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: error("Unable to read captured image.")
        return rotateFromExif(file, decoded)
    }

    private fun decodeUpright(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unable to read captured image." }

        var sample = 1
        var largest = max(bounds.outWidth, bounds.outHeight)
        while (largest / 2 >= MAX_SOURCE_DIMENSION) {
            sample *= 2
            largest /= 2
        }
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: error("Unable to read captured image.")
        return rotateFromExif(file, decoded)
    }

    private fun rotateFromExif(file: File, bitmap: Bitmap): Bitmap {
        val rotation = runCatching {
            when (ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (rotation == 0f) return bitmap

        val matrix = Matrix().apply { postRotate(rotation) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun warpAndEnhance(bitmap: Bitmap, normalized: List<PointF>): Bitmap {
        check(OpenCvRuntime.ensureLoaded()) { "OpenCV could not be initialized." }
        val ordered = normalized.map { Point((it.x * bitmap.width).toDouble(), (it.y * bitmap.height).toDouble()) }

        val widthTop = distance(ordered[0], ordered[1])
        val widthBottom = distance(ordered[3], ordered[2])
        val heightLeft = distance(ordered[0], ordered[3])
        val heightRight = distance(ordered[1], ordered[2])

        var targetWidth = max(widthTop, widthBottom).coerceAtLeast(320.0)
        var targetHeight = max(heightLeft, heightRight).coerceAtLeast(320.0)
        val maxTarget = max(targetWidth, targetHeight)
        if (maxTarget > MAX_OUTPUT_DIMENSION) {
            val s = MAX_OUTPUT_DIMENSION / maxTarget
            targetWidth *= s
            targetHeight *= s
        }

        val source = Mat()
        val warped = Mat()
        val blurred = Mat()
        val sharpened = Mat()
        val srcPoints = MatOfPoint2f(*ordered.toTypedArray())
        val dstPoints = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(targetWidth - 1.0, 0.0),
            Point(targetWidth - 1.0, targetHeight - 1.0),
            Point(0.0, targetHeight - 1.0)
        )
        val transform = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)

        try {
            Utils.bitmapToMat(bitmap, source)
            Imgproc.warpPerspective(
                source,
                warped,
                transform,
                Size(targetWidth, targetHeight),
                Imgproc.INTER_CUBIC,
                Core.BORDER_CONSTANT,
                Scalar(255.0, 255.0, 255.0, 255.0)
            )

            // Mild unsharp mask. Perspective is corrected first, enhancement is applied second.
            Imgproc.GaussianBlur(warped, blurred, Size(0.0, 0.0), 1.05)
            Core.addWeighted(warped, 1.18, blurred, -0.18, 1.5, sharpened)

            val out = Bitmap.createBitmap(sharpened.cols(), sharpened.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(sharpened, out)
            return out
        } finally {
            source.release()
            warped.release()
            blurred.release()
            sharpened.release()
            srcPoints.release()
            dstPoints.release()
            transform.release()
        }
    }

    private fun saveJpeg(bitmap: Bitmap, output: File) {
        output.parentFile?.mkdirs()
        FileOutputStream(output).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 97, stream)) { "Unable to save corrected scan." }
        }
    }

    private fun distance(a: Point, b: Point): Double = hypot(a.x - b.x, a.y - b.y)

    private fun defaultCorners(): List<PointF> = listOf(
        PointF(0.035f, 0.035f),
        PointF(0.965f, 0.035f),
        PointF(0.965f, 0.965f),
        PointF(0.035f, 0.965f)
    )
}
