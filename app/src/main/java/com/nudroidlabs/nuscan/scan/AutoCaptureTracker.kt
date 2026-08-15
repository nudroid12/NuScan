package com.nudroidlabs.nuscan.scan

import android.graphics.PointF
import kotlin.math.hypot

internal data class AutoCaptureState(
    val stableFraction: Float,
    val sharpEnough: Boolean,
    val shouldCapture: Boolean
)

internal class AutoCaptureTracker {
    private var previous: List<PointF>? = null
    private var stableFrames = 0
    private var lastArea = 0.0
    private var cooldownUntil = 0L

    fun update(detection: EdgeDetection?, width: Int, height: Int, nowMs: Long): AutoCaptureState {
        if (detection == null || width <= 0 || height <= 0 || detection.areaRatio < 0.22) {
            previous = null
            stableFrames = 0
            lastArea = 0.0
            return AutoCaptureState(0f, false, false)
        }

        val normalized = detection.corners.map { PointF(it.x / width, it.y / height) }
        val sharpEnough = detection.sharpness >= 28.0
        val prior = previous
        val movement = if (prior != null && prior.size == 4) {
            normalized.zip(prior).map { (a, b) -> hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()) }.average()
        } else {
            Double.MAX_VALUE
        }
        val areaChange = if (lastArea > 0.0) kotlin.math.abs(detection.areaRatio - lastArea) / lastArea else 1.0

        if (movement < 0.018 && areaChange < 0.09 && sharpEnough) {
            stableFrames = (stableFrames + 1).coerceAtMost(REQUIRED_STABLE_FRAMES)
        } else {
            stableFrames = if (sharpEnough) 1 else 0
        }

        previous = normalized
        lastArea = detection.areaRatio
        val ready = stableFrames >= REQUIRED_STABLE_FRAMES && nowMs >= cooldownUntil
        if (ready) {
            cooldownUntil = nowMs + 1800L
            stableFrames = 0
        }
        return AutoCaptureState(
            stableFraction = (stableFrames / REQUIRED_STABLE_FRAMES.toFloat()).coerceIn(0f, 1f),
            sharpEnough = sharpEnough,
            shouldCapture = ready
        )
    }

    fun reset(cooldownMs: Long = 700L) {
        previous = null
        stableFrames = 0
        lastArea = 0.0
        cooldownUntil = System.currentTimeMillis() + cooldownMs
    }

    private companion object {
        const val REQUIRED_STABLE_FRAMES = 5
    }
}
