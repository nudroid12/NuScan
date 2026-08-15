package com.nudroidlabs.nuscan

import android.content.Context

object AppPreferences {
    private const val FILE = "nuscan_preferences"
    private const val KEY_ONBOARDING = "onboarding_complete"
    private const val KEY_AUTO_UPDATE_CHECK = "auto_update_check"

    fun isOnboardingComplete(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_ONBOARDING, false)

    fun setOnboardingComplete(context: Context, complete: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING, complete)
            .apply()
    }

    fun isAutoUpdateCheckEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_UPDATE_CHECK, true)

    fun setAutoUpdateCheckEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_UPDATE_CHECK, enabled)
            .apply()
    }
}
