package com.nudroidlabs.nuscan.monetization

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

class PrivacyConsentController(context: Context) {
    private val appContext = context.applicationContext
    private val consentInformation = UserMessagingPlatform.getConsentInformation(appContext)
    private var mobileAdsInitialised = false

    var canRequestAds by mutableStateOf(false)
        private set

    var privacyOptionsRequired by mutableStateOf(false)
        private set

    var statusText by mutableStateOf("Checking privacy settings")
        private set

    fun requestConsent(activity: Activity) {
        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                updateState()
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    statusText = formError?.message ?: "Privacy settings ready"
                    updateState()
                    initialiseAdsIfAllowed()
                }
                initialiseAdsIfAllowed()
            },
            { requestError ->
                statusText = requestError.message
                updateState()
                initialiseAdsIfAllowed()
            }
        )
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            statusText = formError?.message ?: "Privacy choices updated"
            updateState()
            initialiseAdsIfAllowed()
        }
    }

    private fun updateState() {
        canRequestAds = consentInformation.canRequestAds()
        privacyOptionsRequired =
            consentInformation.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun initialiseAdsIfAllowed() {
        if (!canRequestAds || mobileAdsInitialised) return
        mobileAdsInitialised = true
        MobileAds.initialize(appContext) { }
    }
}
