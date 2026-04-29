package com.forenserecovery.android.monetization

import android.app.Activity
import com.reparafotos.ai.BuildConfig
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class MonetizationProduct(
    val productId: String,
    val title: String,
    val description: String,
    val price: String,
    val productType: String
)

data class MonetizationState(
    val isReady: Boolean = false,
    val isPremiumUnlocked: Boolean = false,
    val products: List<MonetizationProduct> = emptyList()
)

object MonetizationConfig {
    const val ADMOB_APP_ID_TEST = "ca-app-pub-3940256099942544~3347511713"
    const val BANNER_AD_UNIT_TEST = "ca-app-pub-3940256099942544/6300978111"
    const val INTERSTITIAL_AD_UNIT_TEST = "ca-app-pub-3940256099942544/1033173712"
    const val ADMOB_APP_ID = BuildConfig.ADMOB_APP_ID
    const val BANNER_AD_UNIT_ID = BuildConfig.ADMOB_BANNER_AD_UNIT_ID
    const val INTERSTITIAL_AD_UNIT_ID = BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID

    const val PREMIUM_SUB_MONTHLY = BuildConfig.BILLING_PREMIUM_SUB_MONTHLY_ID
    const val PREMIUM_SUB_YEARLY = BuildConfig.BILLING_PREMIUM_SUB_YEARLY_ID
    const val PREMIUM_LIFETIME = BuildConfig.BILLING_PREMIUM_LIFETIME_ID

    fun resolvedAdMobAppId(): String = ADMOB_APP_ID.ifBlank { ADMOB_APP_ID_TEST }
    fun resolvedBannerAdUnitId(): String = BANNER_AD_UNIT_ID.ifBlank { BANNER_AD_UNIT_TEST }
    fun resolvedInterstitialAdUnitId(): String =
        INTERSTITIAL_AD_UNIT_ID.ifBlank { INTERSTITIAL_AD_UNIT_TEST }
    val adMobAppId: String = resolvedAdMobAppId()
    val bannerAdUnitId: String = resolvedBannerAdUnitId()
    val interstitialAdUnitId: String = resolvedInterstitialAdUnitId()

    val subscriptionProductIds = listOf(PREMIUM_SUB_MONTHLY, PREMIUM_SUB_YEARLY)
        .filter { it.isNotBlank() }
    val inAppProductIds = listOf(PREMIUM_LIFETIME)
        .filter { it.isNotBlank() }
    val premiumProductIds = (subscriptionProductIds + inAppProductIds).toSet()
}

class MonetizationManager(
    private val appContext: android.content.Context
) {
    private val _state = MutableStateFlow(MonetizationState())
    val state: StateFlow<MonetizationState> = _state

    private val productDetails = mutableMapOf<String, ProductDetails>()

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases)
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    fun start() {
        if (billingClient.isReady) {
            _state.update { it.copy(isReady = true) }
            queryCatalog()
            queryActivePurchases()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                _state.update { it.copy(isReady = false) }
            }

            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.update { it.copy(isReady = true) }
                    queryCatalog()
                    queryActivePurchases()
                } else {
                    _state.update { it.copy(isReady = false) }
                }
            }
        })
    }

    fun launchPurchase(activity: Activity, productId: String): Boolean {
        if (!billingClient.isReady) return false
        val details = productDetails[productId] ?: return false
        val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        if (details.productType == BillingClient.ProductType.SUBS) {
            val offerToken = details.subscriptionOfferDetails
                ?.firstOrNull()
                ?.offerToken
                ?: return false
            productParamsBuilder.setOfferToken(offerToken)
        }
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParamsBuilder.build()))
            .build()
        val result = billingClient.launchBillingFlow(activity, flowParams)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    fun restorePurchases() {
        queryActivePurchases()
    }

    fun stop() {
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }

    private fun queryCatalog() {
        queryProductsOfType(BillingClient.ProductType.SUBS, MonetizationConfig.subscriptionProductIds)
        queryProductsOfType(BillingClient.ProductType.INAPP, MonetizationConfig.inAppProductIds)
    }

    private fun queryProductsOfType(type: String, ids: List<String>) {
        if (ids.isEmpty() || !billingClient.isReady) return
        val products = ids.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(type)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()
        billingClient.queryProductDetailsAsync(params) { result, detailsList ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            detailsList.forEach { details -> productDetails[details.productId] = details }
            val mapped = productDetails.values.mapNotNull { mapProductDetails(it) }
                .sortedBy { it.price }
            _state.update { it.copy(products = mapped) }
        }
    }

    private fun mapProductDetails(details: ProductDetails): MonetizationProduct? {
        val price = when (details.productType) {
            BillingClient.ProductType.SUBS -> details.subscriptionOfferDetails
                ?.firstOrNull()
                ?.pricingPhases
                ?.pricingPhaseList
                ?.firstOrNull()
                ?.formattedPrice

            BillingClient.ProductType.INAPP -> details.oneTimePurchaseOfferDetails?.formattedPrice
            else -> null
        } ?: return null

        return MonetizationProduct(
            productId = details.productId,
            title = details.title,
            description = details.description,
            price = price,
            productType = details.productType
        )
    }

    private fun queryActivePurchases() {
        if (!billingClient.isReady) return
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { inappResult, inappPurchases ->
            val safeInApp = if (inappResult.responseCode == BillingClient.BillingResponseCode.OK) {
                inappPurchases
            } else {
                emptyList()
            }
            billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            ) { subsResult, subsPurchases ->
                val safeSubs = if (subsResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    subsPurchases
                } else {
                    emptyList()
                }
                val all = safeInApp + safeSubs
                updatePremiumFromPurchases(all)
                handlePurchases(all)
            }
        }
    }

    private fun updatePremiumFromPurchases(purchases: List<Purchase>) {
        val unlocked = purchases.any { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                purchase.products.any { it in MonetizationConfig.premiumProductIds }
        }
        _state.update { it.copy(isPremiumUnlocked = unlocked) }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        purchases.forEach { purchase ->
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return@forEach
            if (purchase.products.any { it in MonetizationConfig.premiumProductIds }) {
                _state.update { it.copy(isPremiumUnlocked = true) }
            }
            if (!purchase.isAcknowledged) {
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(params) {}
            }
        }
    }
}
