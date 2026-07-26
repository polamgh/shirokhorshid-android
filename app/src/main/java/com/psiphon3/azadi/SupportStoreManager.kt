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
    }

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
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
        MyLog.i("Billing", "==================================================")
        MyLog.i("Billing", "SUPPORT STORE MANAGER: START CONNECTION")
        MyLog.i("Billing", "Installed Package Name: ${context.packageName}")
        MyLog.i("Billing", "App Version Name: ${BuildConfig.VERSION_NAME}")
        MyLog.i("Billing", "App Version Code: ${BuildConfig.VERSION_CODE}")
        MyLog.i("Billing", "BillingClient isReady: ${billingClient.isReady}")
        MyLog.i("Billing", "==================================================")

        if (billingClient.isReady) {
            queryProducts()
            queryPurchases()
            return
        }

        _isLoading.value = true
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                MyLog.i(
                    "Billing",
                    "BillingClient connection result: responseCode=${billingResult.responseCode}, debugMessage='${billingResult.debugMessage}'"
                )
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                    queryPurchases()
                } else {
                    _isLoading.value = false
                    _uiState.value = BillingUiState.Error("Billing setup failed: [Code ${billingResult.responseCode}] ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                MyLog.w("Billing", "BillingClient service disconnected")
                _isLoading.value = false
            }
        })
    }

    private fun queryProducts() {
        MyLog.i("Billing", "--- Beginning Query for INAPP and SUBS Products Separately ---")
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
                MyLog.i("Billing", "--- Product Query Complete. Total returned products: ${allReturnedProducts.size} ---")
            }
        }

        // 1. Query INAPP products separately
        val inAppParams = QueryProductDetailsParams.newBuilder()
            .setProductList(inAppProductList)
            .build()

        billingClient.queryProductDetailsAsync(inAppParams) { billingResult, productDetailsList ->
            MyLog.i(
                "Billing",
                "INAPP Query Result: responseCode=${billingResult.responseCode}, debugMessage='${billingResult.debugMessage}', count=${productDetailsList.size}"
            )
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val returnedIds = productDetailsList.map { it.productId }.toSet()
                productDetailsList.forEach { details ->
                    MyLog.i(
                        "Billing",
                        "  Returned INAPP Product: productId=${details.productId}, name='${details.name}', formattedPrice='${details.oneTimePurchaseOfferDetails?.formattedPrice}'"
                    )
                    allReturnedProducts.add(details)
                }

                // Check and print Unfetched INAPP Products
                val requestedInAppIds = listOf(PRODUCT_TIP_SMALL, PRODUCT_TIP_MEDIUM, PRODUCT_TIP_LARGE)
                requestedInAppIds.filter { it !in returnedIds }.forEach { unfetchedId ->
                    MyLog.w(
                        "Billing",
                        "  Unfetched INAPP Product: productId=$unfetchedId, statusCode=${billingResult.responseCode}"
                    )
                }
            } else {
                MyLog.e("Billing", "Failed to query INAPP products: responseCode=${billingResult.responseCode}, debugMessage='${billingResult.debugMessage}'")
            }
            onQueryFinished()
        }

        // 2. Query SUBS products separately
        val subsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(subsProductList)
            .build()

        billingClient.queryProductDetailsAsync(subsParams) { billingResult, productDetailsList ->
            MyLog.i(
                "Billing",
                "SUBS Query Result: responseCode=${billingResult.responseCode}, debugMessage='${billingResult.debugMessage}', count=${productDetailsList.size}"
            )
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val returnedIds = productDetailsList.map { it.productId }.toSet()
                productDetailsList.forEach { details ->
                    MyLog.i("Billing", "  Returned SUBS Product: productId=${details.productId}, name='${details.name}'")
                    val offerDetailsList = details.subscriptionOfferDetails
                    if (!offerDetailsList.isNullOrEmpty()) {
                        offerDetailsList.forEachIndexed { idx, offer ->
                            val phase = offer.pricingPhases.pricingPhaseList.firstOrNull()
                            MyLog.i(
                                "Billing",
                                "    SUBS Offer [$idx]: productId=${details.productId}, basePlanId='${offer.basePlanId}', offerId='${offer.offerId}', offerToken='${offer.offerToken}', formattedPrice='${phase?.formattedPrice}', billingPeriod='${phase?.billingPeriod}'"
                            )
                        }
                    } else {
                        MyLog.w("Billing", "    SUBS Product has NO subscriptionOfferDetails: productId=${details.productId}")
                    }
                    allReturnedProducts.add(details)
                }

                // Check and print Unfetched SUBS Products
                val requestedSubsIds = listOf(PRODUCT_SUPPORT_MONTHLY, PRODUCT_SUPPORT_YEARLY)
                requestedSubsIds.filter { it !in returnedIds }.forEach { unfetchedId ->
                    MyLog.w(
                        "Billing",
                        "  Unfetched SUBS Product: productId=$unfetchedId, statusCode=${billingResult.responseCode}"
                    )
                }
            } else {
                MyLog.e("Billing", "Failed to query SUBS products: responseCode=${billingResult.responseCode}, debugMessage='${billingResult.debugMessage}'")
            }
            onQueryFinished()
        }
    }

    fun purchase(activity: Activity, productDetails: ProductDetails) {
        MyLog.i("Billing", "Purchase requested for productId=${productDetails.productId}, type=${productDetails.productType}")
        _uiState.value = BillingUiState.Loading

        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)

        if (productDetails.productType == BillingClient.ProductType.SUBS) {
            val offerToken = findBestOfferToken(productDetails)
            MyLog.i("Billing", "Selected offerToken='$offerToken' for subscription '${productDetails.productId}'")

            if (offerToken == null) {
                MyLog.e("Billing", "No valid offerToken found for subscription '${productDetails.productId}'")
                _uiState.value = BillingUiState.Error("No valid offer token found for subscription '${productDetails.productId}'.")
                return
            }
            productDetailsParamsBuilder.setOfferToken(offerToken)
        }

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
            .build()

        val result = billingClient.launchBillingFlow(activity, billingFlowParams)
        MyLog.i("Billing", "launchBillingFlow result: responseCode=${result.responseCode}, debugMessage='${result.debugMessage}'")
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _uiState.value = BillingUiState.Error("Failed to launch billing flow: [Code ${result.responseCode}] ${result.debugMessage}")
        }
    }

    private fun findBestOfferToken(productDetails: ProductDetails): String? {
        val offers = productDetails.subscriptionOfferDetails
        if (offers.isNullOrEmpty()) {
            MyLog.w("Billing", "findBestOfferToken: subscriptionOfferDetails is null or empty for ${productDetails.productId}")
            return null
        }
        // Do not require offerId; do not filter support_yearly by basePlanId "yearly". Select the active eligible offer returned by Google Play.
        val offer = offers.firstOrNull { it.offerTags.isNotEmpty() } ?: offers.firstOrNull()
        MyLog.i(
            "Billing",
            "findBestOfferToken: selected offer (basePlanId='${offer?.basePlanId}', offerId='${offer?.offerId}', offerToken='${offer?.offerToken}') for ${productDetails.productId}"
        )
        return offer?.offerToken
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        MyLog.i("Billing", "onPurchasesUpdated: responseCode=${billingResult.responseCode}, debugMessage='${billingResult.debugMessage}', count=${purchases?.size ?: 0}")
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { handlePurchase(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                MyLog.i("Billing", "User canceled purchase flow")
                _uiState.value = BillingUiState.Error("Purchase canceled", isUserCanceled = true)
            }
            else -> {
                MyLog.e("Billing", "Purchase failed: responseCode=${billingResult.responseCode}, debugMessage='${billingResult.debugMessage}'")
                _uiState.value = BillingUiState.Error("Purchase failed: ${billingResult.debugMessage}")
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        MyLog.i("Billing", "Handling purchase: orderId=${purchase.orderId}, products=${purchase.products}, purchaseState=${purchase.purchaseState}, acknowledged=${purchase.isAcknowledged}")
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
        MyLog.i("Billing", "Consuming purchase token for orderId=${purchase.orderId}")
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.consumeAsync(consumeParams) { billingResult, _ ->
            MyLog.i("Billing", "consumeAsync result: responseCode=${billingResult.responseCode}, debugMessage='${billingResult.debugMessage}'")
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                MyLog.i("Billing", "Tip consumed successfully")
                _uiState.value = BillingUiState.Success
            } else {
                _uiState.value = BillingUiState.Error("Failed to finalize tip: ${billingResult.debugMessage}")
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        if (purchase.isAcknowledged) {
            MyLog.i("Billing", "Purchase already acknowledged")
            _uiState.value = BillingUiState.Success
            queryPurchases()
            return
        }

        MyLog.i("Billing", "Acknowledging purchase for orderId=${purchase.orderId}")
        val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(acknowledgeParams) { billingResult ->
            MyLog.i("Billing", "acknowledgePurchase result: responseCode=${billingResult.responseCode}, debugMessage='${billingResult.debugMessage}'")
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                MyLog.i("Billing", "Subscription acknowledged successfully")
                _uiState.value = BillingUiState.Success
                queryPurchases()
            } else {
                _uiState.value = BillingUiState.Error("Failed to finalize subscription: ${billingResult.debugMessage}")
            }
        }
    }

    fun queryPurchases() {
        MyLog.i("Billing", "Querying existing purchases...")
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { billingResult, purchases ->
            MyLog.i("Billing", "queryPurchases result: responseCode=${billingResult.responseCode}, count=${purchases.size}")
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val isSubscribed = purchases.any {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED && it.isAcknowledged
                }
                MyLog.i("Billing", "User active subscription status: isSubscribed=$isSubscribed")
                _purchaseState.value = if (isSubscribed) PurchaseState.SUBSCRIBED else PurchaseState.NOT_PURCHASED
            }
        }
    }
}
