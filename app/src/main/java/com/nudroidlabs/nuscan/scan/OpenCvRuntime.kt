package com.nudroidlabs.nuscan.scan

import org.opencv.android.OpenCVLoader

object OpenCvRuntime {
    @Volatile
    private var loaded: Boolean? = null

    fun ensureLoaded(): Boolean {
        loaded?.let { return it }
        return synchronized(this) {
            loaded ?: runCatching { OpenCVLoader.initLocal() }
                .getOrDefault(false)
                .also { loaded = it }
        }
    }
}
