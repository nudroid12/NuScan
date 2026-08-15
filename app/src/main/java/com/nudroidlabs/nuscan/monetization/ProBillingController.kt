package com.nudroidlabs.nuscan.monetization

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

class ProBillingController(context: Context) : PurchasesUpdatedListener {
    companion object {
        const val PRODUCT_ID = "nuscan_pro_lifetime"
    }

    var isPro by mutableStateOf(false)
        private set
    var priceText by mutableStateOf<String?>(null)
        private set
    var statusText by mutableStateOf("Connecting to Google Play…")
        private set
    var purchaseReady by mutableStateOf(false)
        private set

    private var productDetails: ProductDetails? = null

    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    init {
        connect()
    }

    private fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    statusText = "Connected to Google Play"
                    refreshPurchases()
                    queryProduct()
                } else {
                    purchaseReady = false
                    statusText = billingResult.debugMessage.ifBlank { "Google Play Billing unavailable" }
                }
            }

            override fun onBillingServiceDisconnected() {
                purchaseReady = false
                statusText = "Google Play Billing disconnected"
            }
        })
    }

    private fun queryProduct() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        billingClient.queryProductDetailsAsync(params) { result, detailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = detailsResult.productDetailsList.firstOrNull()
                val offer = productDetails?.oneTimePurchaseOfferDetailsList?.firstOrNull()
                priceText = offer?.formattedPrice
                purchaseReady = productDetails != null && offer != null
                statusText = if (purchaseReady) {
                    "NuScan Pro is available"
                } else {
                    "Create the '$PRODUCT_ID' product in Play Console to enable purchases"
                }
            } else {
                purchaseReady = false
                statusText = result.debugMessage.ifBlank { "Unable to load Pro product" }
            }
        }
    }

    fun refreshPurchases() {
        if (!billingClient.isReady) return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val owned = purchases.any { purchase ->
                    PRODUCT_ID in purchase.products && purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                isPro = owned
                purchases.filter { PRODUCT_ID in it.products }.forEach(::processPurchase)
            }
        }
    }

    fun launchPurchase(activity: Activity): String? {
        if (isPro) return "NuScan Pro is already active."
        val details = productDetails ?: return "NuScan Pro is not configured in Google Play yet."
        // NuScan Pro is a non-consumable one-time product. Google Play Billing
        // does not require an offer token for the standard one-time purchase flow.
        val detailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(detailsParams))
            .build()
        val result = billingClient.launchBillingFlow(activity, flowParams)
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) null
        else result.debugMessage.ifBlank { "Unable to open Google Play purchase screen." }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach(::processPurchase)
            BillingClient.BillingResponseCode.USER_CANCELED -> statusText = "Purchase cancelled"
            else -> statusText = billingResult.debugMessage.ifBlank { "Purchase failed" }
        }
    }

    private fun processPurchase(purchase: Purchase) {
        if (PRODUCT_ID !in purchase.products || purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        isPro = true
        statusText = "NuScan Pro active"
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { result ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    statusText = "Pro active. Purchase acknowledgement will retry later."
                }
            }
        }
    }

    fun close() {
        billingClient.endConnection()
    }
}
