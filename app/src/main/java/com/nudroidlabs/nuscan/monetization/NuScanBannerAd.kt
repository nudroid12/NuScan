package com.nudroidlabs.nuscan.monetization

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.nudroidlabs.nuscan.BuildConfig

@Composable
fun NuScanBannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val adView = remember {
        AdView(context).apply {
            adUnitId = BuildConfig.ADMOB_BANNER_AD_UNIT_ID
            setAdSize(AdSize.BANNER)
            loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        AndroidView(factory = { adView }, modifier = Modifier.fillMaxWidth())
    }
}
