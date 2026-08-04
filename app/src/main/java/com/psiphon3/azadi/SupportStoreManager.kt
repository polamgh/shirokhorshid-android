package com.psiphon3.azadi

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.psiphon3.BuildConfig
import com.psiphon3.log.MyLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SupportStoreManager private constructor(private val context: Context) : PurchasesUpdatedListener {

    companion object {
        @Volatile
        private var instance: SupportStoreManager? = null

        fun getInstance(context: Context): SupportStoreManager {
            return instance ?: synchronized(this) {
                instance ?: SupportStoreManager(context.applicationContext).also { instance = it }
            }
        }

        const val PRODUCT_TIP_SMALL = "tip_small"
        const val PRODUCT_TIP_MEDIUM = "tip_medium"
        const val PRODUCT_TIP_LARGE = "tip_large"
        const val PRODUCT_SUPPORT_MONTHLY = "support_monthly"
        const val PRODUCT_SUPPORT_YEARLY = "support_yearly"

        private val CONSUMABLE_TIPS = setOf(PRODUCT_TIP_SMALL, PRODUCT_TIP_MEDIUM, PRODUCT_TIP_LARGE)

        private fun logI(message: String) {
            MyLog.i("Billing_Event", "message", message)
        }

        private fun logW(message: String) {
            MyLog.w("Billing_Warning", "message", message)
        }

        private fun logE(message: String) {
            MyLog.e("Billing_Error", "message", message)
        }
    }

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _uiState = MutableStateFlow<BillingUiState>(BillingUiState.Idle)
    val uiState: StateFlow<BillingUiState> = _uiState.asStateFlow()

    private val _purchaseState = MutableStateFlow(PurchaseState.NOT_PURCHASED)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()

    sealed class BillingUiState {
        object Idle : BillingUiState()
        object Loading : BillingUiState()
        object Success : BillingUiState()
        data class Error(val message: String, val isUserCanceled: Boolean = false) : BillingUiState()
        data class Pending(val message: String) : BillingUiState()
    }

    enum class PurchaseState {
        UNKNOWN, NOT_PURCHASED, PURCHASED, SUBSCRIBED, EXPIRED
    }

    fun startConnection() {
        logI("==================================================")
        logI("SUPPORT STORE MANAGER: START CONNECTION")
        logI("Installed Package Name: ${context.packageName}")
        logI("App Version Name: ${BuildConfig.VERSION_NAME}")
        logI("App Version Code: ${BuildConfig.VERSION_CODE}")
        logI("BillingClient isReady: ${billingClient.isReady}")
        logI("==================================================")

        if (billingClient.isReady) {
            queryProducts()
            queryPurchases()
            return
        }

        _isLoading.value = true
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                logI("BillingClient connection result: responseCode=${billingResult.responseCode}, debugMessage='${billingResult.debugMessage}'")
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                    queryPurchases()
                } else {
                    _isLoading.value = false
                    _uiState.value = BillingUiState.Error("Billing setup failed: [Code ${billingResult.responseCode}] ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                logW("BillingClient service disconnected")
                _isLoading.value = false
            }
        })
    }

    private fun queryProducts() {
        logI("--- Beginning Query for INAPP and SUBS Products Separately ---")
        _isLoading.value = true

        val inAppProductList = listOf(
            PRODUCT_TIP_SMALL,
            PRODUCT_TIP_MEDIUM,
            PRODUCT_TIP_LARGE
        ).map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val subsProductList = listOf(
            PRODUCT_SUPPORT_MONTHLY,
            PRODUCT_SUPPORT_YEARLY
        ).map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val allReturnedProducts = mutableListOf<ProductDetails>()
        var remainingQueries = 2

        fun onQueryFinished() {
            remainingQueries--
            if (remainingQueries <= 0) {
                _isLoading.value = false
                _products.value = allReturnedProducts.toList()
                logI("--- Product Query Complete. Total returned products: ${allReturnedProducts.size} ---")
            }
        }

        // 1. Query INAPP products separately
        val inAppParams = QueryProductDetailsParams.newBuilder()
            .setProductList(inAppProductList)
            .build()

        billingClient.queryProductDetailsAsync(inAppParams) { billingResult, result ->
            val productDetailsList = result.productDetailsList
            logI("INAPP Query Result: responseCode=${billingResult.responseCode}, debugMessage='${billingResult.debugMessage}', count=${productDetailsList.size}")
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetailsList.forEach { details ->
                    logI("Returned INAPP Product: productId=${details.productId}, name='${details.name}', formattedPrice='${details.oneTimePurchaseOfferDetails?.formattedPrice}'")
                    allReturnedProducts.add(details)
                }

                // Log Unfetched Products using the new PBL 9 API
                result.unfetchedProductList.forEach { unfetched ->
                    logW("Unfetched INAPP Product: productId=${unfetched.productId}, statusCode=${unfetched.statusCode}")
                }
            } else {
                logE("Failed to query INAPP products: responseCode=${billingResult.responseCode}, debugMessage='${billingResult.debugMessage}'")
            }
            onQueryFinished()
        }

        // 2. Query SUBS products separately
        val subsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(subsProductList)
            .build()

        billingClient.queryProductDetailsAsync(subsParams) { billingResult, result ->
            val productDetailsList = result.productDetailsList
            logI("SUBS Query Result: responseCode=${billingResult.responseCode}, debugMessage='${billingResult.debugMessage}', count=${productDetailsList.size}")
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetailsList.forEach { details ->
                    logI("Returned SUBS Product: productId=${details.productId}, name='${details.name}'")
                    val offerDetailsList = details.subscriptionOfferDetails
                    if (!offerDetailsList.isNullOrEmpty()) {
                        offerDetailsList.forEachIndexed { idx, offer ->
                            val phase = offer.pricingPhases.pricingPhaseList.firstOrNull()
                            logI("SUBS Offer [$idx]: productId=${details.productId}, basePlanId='${offer.basePlanId}', offerId='${offer.offerId}', offerToken='${offer.offerToken}', formattedPrice='${phase?.formattedPrice}', billingPeriod='${phase?.billingPeriod}'")
                        }
                    } else {
                        logW("SUBS Product has NO subscriptionOfferDetails: productId=${details.productId}")
                    }
                    allReturnedProducts.add(details)
                }

                // Log Unfetched Products using the new PBL 9 API
                result.unfetchedProductList.forEach { unfetched ->
                    logW("Unfetched SUBS Product: productId=${unfetched.productId}, statusCode=${unfetched.statusCode}")
                }
            } else {
                logE("Failed to query SUBS products: responseCode=${billingResult.responseCode}, debugMessage='${billingResult.debugMessage}'")
            }
            onQueryFinished()
        }
    }

    fun purchase(activity: Activity, productDetails: ProductDetails) {
        logI("Purchase requested for productId=${productDetails.productId}, type=${productDetails.productType}")
        _uiState.value = BillingUiState.Loading

        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)

        if (productDetails.productType == BillingClient.ProductType.SUBS) {
            val offerToken = findBestOfferToken(productDetails)
            logI("Selected offerToken='$offerToken' for subscription '${productDetails.productId}'")

            if (offerToken == null) {
                logE("No valid offerToken found for subscription '${productDetails.productId}'")
                _uiState.value = BillingUiState.Error("No valid offer token found for subscription '${productDetails.productId}'.")
                return
            }
            productDetailsParamsBuilder.setOfferToken(offerToken)
        }

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
            .build()

        val result = billingClient.launchBillingFlow(activity, billingFlowParams)
        logI("launchBillingFlow result: responseCode=${result.responseCode}, debugMessage='${result.debugMessage}'")
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _uiState.value = BillingUiState.Error("Failed to launch billing flow: [Code ${result.responseCode}] ${result.debugMessage}")
        }
    }

    private fun findBestOfferToken(productDetails: ProductDetails): String? {
        val offers = productDetails.subscriptionOfferDetails
        if (offers.isNullOrEmpty()) {
            logW("findBestOfferToken: subscriptionOfferDetails is null or empty for ${productDetails.productId}")
            return null
        }
        val offer = offers.firstOrNull { it.offerTags.isNotEmpty() } ?: offers.firstOrNull()
        logI("findBestOfferToken: selected offer (basePlanId='${offer?.basePlanId}', offerId='${offer?.offerId}', offerToken='${offer?.offerToken}') for ${productDetails.productId}")
        return offer?.offerToken
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        logI("onPurchasesUpdated: responseCode=${billingResult.responseCode}, debugMessage='${billingResult.debugMessage}', count=${purchases?.size ?: 0}")
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { handlePurchase(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                logI("User canceled purchase flow")
                _uiState.value = BillingUiState.Error("Purchase canceled", isUserCanceled = true)
            }
            else -> {
                logE("Purchase failed: responseCode=${billingResult.responseCode}, debugMessage='${billingResult.debugMessage}'")
                _uiState.value = BillingUiState.Error("Purchase failed: ${billingResult.debugMessage}")
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        logI("Handling purchase: orderId=${purchase.orderId}, products=${purchase.products}, purchaseState=${purchase.purchaseState}, acknowledged=${purchase.isAcknowledged}")
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                val productId = purchase.products.firstOrNull() ?: return

                if (CONSUMABLE_TIPS.contains(productId)) {
                    consumePurchase(purchase)
                } else {
                    acknowledgePurchase(purchase)
                }
            }
            Purchase.PurchaseState.PENDING -> {
                _uiState.value = BillingUiState.Pending("Purchase is pending completion.")
            }
            else -> {
                _uiState.value = BillingUiState.Idle
            }
        }
    }

    private fun consumePurchase(purchase: Purchase) {
        logI("Consuming purchase token for orderId=${purchase.orderId}")
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.consumeAsync(consumeParams) { billingResult, _ ->
            logI("consumeAsync result: responseCode=${billingResult.responseCode}, debugMessage='${billingResult.debugMessage}'")
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                logI("Tip consumed successfully")
                _uiState.value = BillingUiState.Success
            } else {
                _uiState.value = BillingUiState.Error("Failed to finalize tip: ${billingResult.debugMessage}")
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        if (purchase.isAcknowledged) {
            logI("Purchase already acknowledged")
            _uiState.value = BillingUiState.Success
            queryPurchases()
            return
        }

        logI("Acknowledging purchase for orderId=${purchase.orderId}")
        val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(acknowledgeParams) { billingResult ->
            logI("acknowledgePurchase result: responseCode=${billingResult.responseCode}, debugMessage='${billingResult.debugMessage}'")
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                logI("Subscription acknowledged successfully")
                _uiState.value = BillingUiState.Success
                queryPurchases()
            } else {
                _uiState.value = BillingUiState.Error("Failed to finalize subscription: ${billingResult.debugMessage}")
            }
        }
    }

    fun queryPurchases() {
        logI("Querying existing purchases...")
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { billingResult, purchases ->
            logI("queryPurchases result: responseCode=${billingResult.responseCode}, count=${purchases.size}")
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val isSubscribed = purchases.any {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED && it.isAcknowledged
                }
                logI("User active subscription status: isSubscribed=$isSubscribed")
                _purchaseState.value = if (isSubscribed) PurchaseState.SUBSCRIBED else PurchaseState.NOT_PURCHASED
            }
        }
    }
}
