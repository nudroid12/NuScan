package com.nudroidlabs.nuscan

import android.content.Context

object AppPreferences {
    private const val FILE = "nuscan_preferences"
    private const val KEY_ONBOARDING = "onboarding_complete"

    fun isOnboardingComplete(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_ONBOARDING, false)

    fun setOnboardingComplete(context: Context, complete: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING, complete)
            .apply()
    }
}
