package com.nudroidlabs.nuscan.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.nudroidlabs.nuscan.data.DocumentRepository
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * NuScan's post-capture document enhancement pipeline.
 *
 * ML Kit remains responsible for capture, crop and perspective correction. This engine processes
 * the returned page JPEGs before NuScan creates the final PDF. It is intentionally conservative:
 * it targets uneven lighting, soft shadows, colour casts and weak text contrast without attempting
 * a second perspective transform.
 */
object ScanEnhancementEngine {
    private const val MAX_LONG_EDGE = 2200
    private const val BACKGROUND_LONG_EDGE = 72
    private const val JPEG_QUALITY = 0.93f
    private const val PDF_LONG_EDGE_POINTS = 842f

    data class Result(
        val file: File,
        val pageCount: Int
    )

    fun createEnhancedPdf(
        context: Context,
        pageUris: List<Uri>,
        requestedName: String,
        onProgress: ((currentPage: Int, totalPages: Int) -> Unit)? = null
    ): Result {
        require(pageUris.isNotEmpty()) { "The scanner returned no page images." }

        val output = DocumentRepository.uniquePdfFile(
            DocumentRepository.outputDirectory(context),
            requestedName
        )

        try {
            PDDocument().use { document ->
                pageUris.forEachIndexed { index, uri ->
                    onProgress?.invoke(index + 1, pageUris.size)
                    val source = decodePage(context, uri)
                        ?: error("Unable to read scanned page ${index + 1}.")

                    val enhanced = try {
                        enhance(source)
                    } finally {
                        // enhance() returns a new bitmap. The source can always be released here.
                        source.recycle()
                    }

                    try {
                        appendPage(document, enhanced)
                    } finally {
                        enhanced.recycle()
                    }
                }

                document.save(output)
            }

            require(output.exists() && output.length() > 0L) { "Enhanced PDF was not created." }
            return Result(output, pageUris.size)
        } catch (error: Throwable) {
            output.delete()
            throw error
        }
    }

    private fun decodePage(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
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

        val oriented = if (rotation == 0f) {
            decoded
        } else {
            val matrix = Matrix().apply { postRotate(rotation) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                if (it !== decoded) decoded.recycle()
            }
        }

        val longest = max(oriented.width, oriented.height)
        if (longest <= MAX_LONG_EDGE) return oriented

        val scale = MAX_LONG_EDGE.toFloat() / longest.toFloat()
        val resized = Bitmap.createScaledBitmap(
            oriented,
            (oriented.width * scale).roundToInt().coerceAtLeast(1),
            (oriented.height * scale).roundToInt().coerceAtLeast(1),
            true
        )
        if (resized !== oriented) oriented.recycle()
        return resized
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sample = 1
        var longest = max(width, height)
        while (longest / 2 >= MAX_LONG_EDGE) {
            sample *= 2
            longest /= 2
        }
        return sample
    }

    private fun enhance(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixelCount = width * height
        val pixels = IntArray(pixelCount)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val gains = estimateWhiteBalance(pixels, width, height)
        val background = buildBackgroundMap(pixels, width, height, gains)
        val correctedLuma = ByteArray(pixelCount)

        var saturationAccumulator = 0.0
        var saturationSamples = 0
        val saturationStep = max(1, max(width, height) / 500)

        for (y in 0 until height) {
            val bgY = background.coordinateY(y, height)
            for (x in 0 until width) {
                val index = y * width + x
                val color = pixels[index]
                var r = (Color.red(color) * gains.red).roundToInt().coerceIn(0, 255)
                var g = (Color.green(color) * gains.green).roundToInt().coerceIn(0, 255)
                var b = (Color.blue(color) * gains.blue).roundToInt().coerceIn(0, 255)

                val lum = luminance(r, g, b)
                val bg = background.sample(x, width, bgY)

                // A bright, smooth local background is likely paper. Dark local backgrounds are
                // treated more cautiously so photos, logos and coloured blocks are not washed out.
                val paperConfidence = ((bg - 115f) / 50f).coerceIn(0f, 1f)
                val targetBackground = bg + (248f - bg) * paperConfidence
                val contrast = 1.10f + 0.08f * paperConfidence
                var targetLum = targetBackground + (lum - bg) * contrast
                targetLum = targetLum.coerceIn(0f, 255f)

                val delta = targetLum - lum
                r = (r + delta).roundToInt().coerceIn(0, 255)
                g = (g + delta).roundToInt().coerceIn(0, 255)
                b = (b + delta).roundToInt().coerceIn(0, 255)

                // A small desaturation makes white paper more neutral while keeping stamps,
                // signatures and diagrams recognisably coloured.
                val correctedLum = luminance(r, g, b)
                val desaturate = 0.10f * paperConfidence
                r = lerpChannel(r, correctedLum, desaturate)
                g = lerpChannel(g, correctedLum, desaturate)
                b = lerpChannel(b, correctedLum, desaturate)

                pixels[index] = Color.rgb(r, g, b)
                correctedLuma[index] = luminance(r, g, b).roundToInt().coerceIn(0, 255).toByte()

                if (x % saturationStep == 0 && y % saturationStep == 0) {
                    val maxChannel = max(r, max(g, b))
                    val minChannel = min(r, min(g, b))
                    if (maxChannel > 0) {
                        saturationAccumulator += (maxChannel - minChannel).toDouble() / maxChannel.toDouble()
                        saturationSamples++
                    }
                }
            }
        }

        val averageSaturation = if (saturationSamples == 0) 0.0 else saturationAccumulator / saturationSamples
        val looksMonochrome = averageSaturation < 0.035
        applyControlledSharpen(pixels, correctedLuma, width, height, looksMonochrome)

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun estimateWhiteBalance(pixels: IntArray, width: Int, height: Int): ChannelGains {
        val histogram = IntArray(256)
        val step = max(1, max(width, height) / 650)
        var samples = 0

        for (y in 0 until height step step) {
            var index = y * width
            for (x in 0 until width step step) {
                val color = pixels[index + x]
                histogram[luminance(Color.red(color), Color.green(color), Color.blue(color)).roundToInt().coerceIn(0, 255)]++
                samples++
            }
        }

        if (samples == 0) return ChannelGains(1f, 1f, 1f)
        val brightThreshold = percentile(histogram, samples, 0.86f)

        var redSum = 0L
        var greenSum = 0L
        var blueSum = 0L
        var brightSamples = 0L
        for (y in 0 until height step step) {
            val row = y * width
            for (x in 0 until width step step) {
                val color = pixels[row + x]
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                if (luminance(r, g, b) >= brightThreshold) {
                    redSum += r
                    greenSum += g
                    blueSum += b
                    brightSamples++
                }
            }
        }

        if (brightSamples == 0L) return ChannelGains(1f, 1f, 1f)
        val redAverage = redSum.toFloat() / brightSamples
        val greenAverage = greenSum.toFloat() / brightSamples
        val blueAverage = blueSum.toFloat() / brightSamples
        val neutral = (redAverage + greenAverage + blueAverage) / 3f

        return ChannelGains(
            red = safeGain(neutral, redAverage),
            green = safeGain(neutral, greenAverage),
            blue = safeGain(neutral, blueAverage)
        )
    }

    private fun safeGain(target: Float, channel: Float): Float {
        if (channel < 1f) return 1f
        return (target / channel).coerceIn(0.90f, 1.15f)
    }

    private fun buildBackgroundMap(
        pixels: IntArray,
        width: Int,
        height: Int,
        gains: ChannelGains
    ): BackgroundMap {
        val longest = max(width, height).coerceAtLeast(1)
        val scale = BACKGROUND_LONG_EDGE.toFloat() / longest.toFloat()
        val mapWidth = max(8, (width * scale).roundToInt())
        val mapHeight = max(8, (height * scale).roundToInt())
        val luma = FloatArray(mapWidth * mapHeight)

        // Estimate each tiny background cell from the brighter samples in its source region.
        // Using an upper percentile rather than a plain average reduces the influence of text.
        val sampleValues = IntArray(25)
        for (mapY in 0 until mapHeight) {
            val top = mapY.toFloat() * height.toFloat() / mapHeight.toFloat()
            val bottom = (mapY + 1).toFloat() * height.toFloat() / mapHeight.toFloat()
            for (mapX in 0 until mapWidth) {
                val left = mapX.toFloat() * width.toFloat() / mapWidth.toFloat()
                val right = (mapX + 1).toFloat() * width.toFloat() / mapWidth.toFloat()

                var sampleIndex = 0
                for (sy in 0 until 5) {
                    val y = (top + (sy + 0.5f) * (bottom - top) / 5f)
                        .roundToInt().coerceIn(0, height - 1)
                    val row = y * width
                    for (sx in 0 until 5) {
                        val x = (left + (sx + 0.5f) * (right - left) / 5f)
                            .roundToInt().coerceIn(0, width - 1)
                        val color = pixels[row + x]
                        val r = (Color.red(color) * gains.red).coerceIn(0f, 255f)
                        val g = (Color.green(color) * gains.green).coerceIn(0f, 255f)
                        val b = (Color.blue(color) * gains.blue).coerceIn(0f, 255f)
                        sampleValues[sampleIndex++] = luminance(r, g, b).roundToInt().coerceIn(0, 255)
                    }
                }

                sampleValues.sort()
                luma[mapY * mapWidth + mapX] = sampleValues[18].toFloat()
            }
        }

        // Repeated blur on the tiny map keeps broad lighting gradients and soft shadows while
        // suppressing individual text strokes.
        repeat(3) { boxBlur3x3(luma, mapWidth, mapHeight) }
        return BackgroundMap(mapWidth, mapHeight, luma)
    }

    private fun boxBlur3x3(values: FloatArray, width: Int, height: Int) {
        val copy = values.copyOf()
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0f
                var count = 0
                for (dy in -1..1) {
                    val yy = (y + dy).coerceIn(0, height - 1)
                    for (dx in -1..1) {
                        val xx = (x + dx).coerceIn(0, width - 1)
                        sum += copy[yy * width + xx]
                        count++
                    }
                }
                values[y * width + x] = sum / count.toFloat()
            }
        }
    }

    private fun applyControlledSharpen(
        pixels: IntArray,
        luma: ByteArray,
        width: Int,
        height: Int,
        monochrome: Boolean
    ) {
        if (width < 3 || height < 3) return
        val strength = if (monochrome) 0.30f else 0.22f

        for (y in 1 until height - 1) {
            val row = y * width
            for (x in 1 until width - 1) {
                val index = row + x
                val center = luma[index].toInt() and 0xFF
                if (center >= 246) continue

                val neighbours = (
                    (luma[index - 1].toInt() and 0xFF) +
                        (luma[index + 1].toInt() and 0xFF) +
                        (luma[index - width].toInt() and 0xFF) +
                        (luma[index + width].toInt() and 0xFF)
                    ) / 4f
                val detail = center - neighbours
                if (abs(detail) < 2.5f) continue

                val sharpened = (center + detail * strength).coerceIn(0f, 255f)
                val delta = sharpened - center
                val color = pixels[index]
                val r = (Color.red(color) + delta).roundToInt().coerceIn(0, 255)
                val g = (Color.green(color) + delta).roundToInt().coerceIn(0, 255)
                val b = (Color.blue(color) + delta).roundToInt().coerceIn(0, 255)
                pixels[index] = if (monochrome) {
                    val gray = luminance(r, g, b).roundToInt().coerceIn(0, 255)
                    Color.rgb(gray, gray, gray)
                } else {
                    Color.rgb(r, g, b)
                }
            }
        }
    }

    private fun appendPage(document: PDDocument, bitmap: Bitmap) {
        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
        val pageWidth: Float
        val pageHeight: Float
        if (ratio <= 1f) {
            pageHeight = PDF_LONG_EDGE_POINTS
            pageWidth = (pageHeight * ratio).coerceAtLeast(144f)
        } else {
            pageWidth = PDF_LONG_EDGE_POINTS
            pageHeight = (pageWidth / ratio).coerceAtLeast(144f)
        }

        val page = PDPage(PDRectangle(pageWidth, pageHeight))
        document.addPage(page)
        val image = JPEGFactory.createFromImage(document, bitmap, JPEG_QUALITY)
        PDPageContentStream(document, page).use { stream ->
            stream.drawImage(image, 0f, 0f, pageWidth, pageHeight)
        }
    }

    private fun percentile(histogram: IntArray, samples: Int, fraction: Float): Int {
        val target = ceil(samples * fraction.coerceIn(0f, 1f)).toInt().coerceAtLeast(1)
        var cumulative = 0
        for (value in histogram.indices) {
            cumulative += histogram[value]
            if (cumulative >= target) return value
        }
        return 255
    }

    private fun luminance(r: Int, g: Int, b: Int): Float =
        r * 0.2126f + g * 0.7152f + b * 0.0722f

    private fun luminance(r: Float, g: Float, b: Float): Float =
        r * 0.2126f + g * 0.7152f + b * 0.0722f

    private fun lerpChannel(channel: Int, target: Float, amount: Float): Int =
        (channel + (target - channel) * amount).roundToInt().coerceIn(0, 255)

    private data class ChannelGains(
        val red: Float,
        val green: Float,
        val blue: Float
    )

    private data class BackgroundMap(
        val width: Int,
        val height: Int,
        val values: FloatArray
    ) {
        data class YCoordinate(val y0: Int, val y1: Int, val fraction: Float)

        fun coordinateY(sourceY: Int, sourceHeight: Int): YCoordinate {
            val fy = if (sourceHeight <= 1) 0f else sourceY.toFloat() * (height - 1).toFloat() / (sourceHeight - 1).toFloat()
            val y0 = floor(fy).toInt().coerceIn(0, height - 1)
            val y1 = min(y0 + 1, height - 1)
            return YCoordinate(y0, y1, fy - y0)
        }

        fun sample(sourceX: Int, sourceWidth: Int, y: YCoordinate): Float {
            val fx = if (sourceWidth <= 1) 0f else sourceX.toFloat() * (width - 1).toFloat() / (sourceWidth - 1).toFloat()
            val x0 = floor(fx).toInt().coerceIn(0, width - 1)
            val x1 = min(x0 + 1, width - 1)
            val tx = fx - x0

            val top = values[y.y0 * width + x0] + (values[y.y0 * width + x1] - values[y.y0 * width + x0]) * tx
            val bottom = values[y.y1 * width + x0] + (values[y.y1 * width + x1] - values[y.y1 * width + x0]) * tx
            return top + (bottom - top) * y.fraction
        }
    }
}
