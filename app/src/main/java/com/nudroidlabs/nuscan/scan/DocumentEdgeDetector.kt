package com.nudroidlabs.nuscan.scan

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.camera.core.ImageProxy
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

internal data class EdgeDetection(
    val corners: List<PointF>,
    val areaRatio: Double,
    val sharpness: Double
)

internal object DocumentEdgeDetector {
    private const val DETECTION_MAX_DIMENSION = 1200

    fun detect(bitmap: Bitmap): EdgeDetection? {
        if (!OpenCvRuntime.ensureLoaded() || bitmap.width < 32 || bitmap.height < 32) return null

        val scale = min(1.0, DETECTION_MAX_DIMENSION.toDouble() / max(bitmap.width, bitmap.height).toDouble())
        val working = if (scale < 0.999) {
            Bitmap.createScaledBitmap(
                bitmap,
                max(1, (bitmap.width * scale).toInt()),
                max(1, (bitmap.height * scale).toInt()),
                true
            )
        } else {
            bitmap
        }

        val rgba = Mat()
        try {
            Utils.bitmapToMat(working, rgba)
            return detectRgba(rgba)
        } finally {
            rgba.release()
            if (working !== bitmap) working.recycle()
        }
    }

    fun detect(image: ImageProxy): EdgeDetection? {
        if (!OpenCvRuntime.ensureLoaded()) return null
        val plane = image.planes.firstOrNull() ?: return null
        val width = image.width
        val height = image.height
        if (width < 32 || height < 32) return null

        val buffer = plane.buffer
        buffer.rewind()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        if (pixelStride < 4 || rowStride < width * pixelStride) return null

        val raw = ByteArray(buffer.remaining())
        buffer.get(raw)
        val packed = ByteArray(width * height * 4)

        var dst = 0
        for (y in 0 until height) {
            var src = y * rowStride
            for (x in 0 until width) {
                // CameraX RGBA output is packed A,R,G,B. OpenCV expects R,G,B,A.
                packed[dst] = raw[src + 1]
                packed[dst + 1] = raw[src + 2]
                packed[dst + 2] = raw[src + 3]
                packed[dst + 3] = raw[src]
                dst += 4
                src += pixelStride
            }
        }

        val rgba = Mat(height, width, CvType.CV_8UC4)
        try {
            rgba.put(0, 0, packed)
            return detectRgba(rgba)
        } finally {
            rgba.release()
        }
    }

    private fun detectRgba(rgba: Mat): EdgeDetection? {
        if (rgba.empty()) return null

        val scale = min(1.0, DETECTION_MAX_DIMENSION.toDouble() / max(rgba.cols(), rgba.rows()).toDouble())
        val work = Mat()
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        val closed = Mat()
        val hierarchy = Mat()
        var kernel: Mat? = null
        val contours = mutableListOf<MatOfPoint>()

        try {
            if (scale < 0.999) {
                Imgproc.resize(rgba, work, Size(), scale, scale, Imgproc.INTER_AREA)
            } else {
                rgba.copyTo(work)
            }

            Imgproc.cvtColor(work, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 55.0, 165.0)

            val morphologyKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
            kernel = morphologyKernel
            Imgproc.morphologyEx(edges, closed, Imgproc.MORPH_CLOSE, morphologyKernel)
            Imgproc.dilate(closed, closed, morphologyKernel)

            val sharpness = calculateSharpness(gray)
            Imgproc.findContours(closed, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

            val frameArea = work.cols().toDouble() * work.rows().toDouble()
            var best: List<Point>? = null
            var bestScore = 0.0
            var bestAreaRatio = 0.0

            contours.sortedByDescending { Imgproc.contourArea(it) }.take(40).forEach { contour ->
                    val area = Imgproc.contourArea(contour)
                    val areaRatio = area / frameArea
                    if (areaRatio !in 0.12..0.975) return@forEach

                    val contour2f = MatOfPoint2f(*contour.toArray())
                    try {
                        val perimeter = Imgproc.arcLength(contour2f, true)
                        val epsilons = doubleArrayOf(0.015, 0.02, 0.025, 0.03)
                        for (epsilon in epsilons) {
                            val approx = MatOfPoint2f()
                            try {
                                Imgproc.approxPolyDP(contour2f, approx, perimeter * epsilon, true)
                                val pts = approx.toArray().toList()
                                if (pts.size != 4) continue

                                val poly = MatOfPoint(*pts.toTypedArray())
                                try {
                                    if (!Imgproc.isContourConvex(poly)) continue
                                    val ordered = orderCorners(pts) ?: continue
                                    if (looksLikeFrameBorder(ordered, work.cols(), work.rows())) continue
                                    if (!hasReasonableGeometry(ordered)) continue

                                    val rectangularity = rectangularityScore(ordered)
                                    val centreScore = centreScore(ordered, work.cols(), work.rows())
                                    val score = areaRatio * 0.76 + rectangularity * 0.16 + centreScore * 0.08
                                    if (score > bestScore) {
                                        bestScore = score
                                        best = ordered
                                        bestAreaRatio = areaRatio
                                    }
                                } finally {
                                    poly.release()
                                }
                            } finally {
                                approx.release()
                            }
                        }
                    } finally {
                        contour2f.release()
                    }
            }

            val selected = best ?: return null
            val invScaleX = rgba.cols().toDouble() / work.cols().toDouble()
            val invScaleY = rgba.rows().toDouble() / work.rows().toDouble()
            val corners = selected.map {
                PointF((it.x * invScaleX).toFloat(), (it.y * invScaleY).toFloat())
            }
            return EdgeDetection(corners, bestAreaRatio, sharpness)
        } finally {
            work.release()
            gray.release()
            blurred.release()
            edges.release()
            closed.release()
            kernel?.release()
            hierarchy.release()
            contours.forEach { it.release() }
        }
    }

    private fun calculateSharpness(gray: Mat): Double {
        val laplacian = Mat()
        val mean = MatOfDouble()
        val stddev = MatOfDouble()
        return try {
            Imgproc.Laplacian(gray, laplacian, CvType.CV_64F)
            Core.meanStdDev(laplacian, mean, stddev)
            val sigma = stddev.toArray().firstOrNull() ?: 0.0
            sigma * sigma
        } finally {
            laplacian.release()
            mean.release()
            stddev.release()
        }
    }

    private fun orderCorners(points: List<Point>): List<Point>? {
        if (points.size != 4) return null
        val tl = points.minByOrNull { it.x + it.y } ?: return null
        val br = points.maxByOrNull { it.x + it.y } ?: return null
        val tr = points.maxByOrNull { it.x - it.y } ?: return null
        val bl = points.minByOrNull { it.x - it.y } ?: return null
        val ordered = listOf(tl, tr, br, bl)
        if (ordered.distinctBy { "${it.x.toInt()}:${it.y.toInt()}" }.size != 4) return null
        return ordered
    }

    private fun looksLikeFrameBorder(points: List<Point>, width: Int, height: Int): Boolean {
        val marginX = width * 0.025
        val marginY = height * 0.025
        val nearBorder = points.count { p ->
            p.x < marginX || p.x > width - marginX || p.y < marginY || p.y > height - marginY
        }
        return nearBorder >= 4
    }

    private fun hasReasonableGeometry(p: List<Point>): Boolean {
        val top = distance(p[0], p[1])
        val right = distance(p[1], p[2])
        val bottom = distance(p[2], p[3])
        val left = distance(p[3], p[0])
        val shortest = min(min(top, bottom), min(left, right))
        val longest = max(max(top, bottom), max(left, right))
        if (shortest < 40.0 || longest / shortest > 8.0) return false

        val diagA = distance(p[0], p[2])
        val diagB = distance(p[1], p[3])
        return min(diagA, diagB) / max(diagA, diagB) > 0.35
    }

    private fun rectangularityScore(p: List<Point>): Double {
        val cosines = (0..3).map { i ->
            val a = p[(i + 3) % 4]
            val b = p[i]
            val c = p[(i + 1) % 4]
            val abx = a.x - b.x
            val aby = a.y - b.y
            val cbx = c.x - b.x
            val cby = c.y - b.y
            val denom = hypot(abx, aby) * hypot(cbx, cby)
            if (denom <= 1e-6) 1.0 else abs((abx * cbx + aby * cby) / denom)
        }
        val averageCos = cosines.average().coerceIn(0.0, 1.0)
        return 1.0 - averageCos
    }

    private fun centreScore(p: List<Point>, width: Int, height: Int): Double {
        val cx = p.map { it.x }.average() / width
        val cy = p.map { it.y }.average() / height
        val dx = abs(cx - 0.5) * 2.0
        val dy = abs(cy - 0.5) * 2.0
        return (1.0 - (dx + dy) / 2.0).coerceIn(0.0, 1.0)
    }

    private fun distance(a: Point, b: Point): Double = hypot(a.x - b.x, a.y - b.y)
}
