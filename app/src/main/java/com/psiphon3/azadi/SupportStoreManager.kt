package com.psiphon3.azadi

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.psiphon3.log.MyLog

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
        UNKNOWN, NOT_PURCHASED, SUBSCRIBED
    }

    fun startConnection() {
        if (billingClient.isReady) return

        _isLoading.value = true
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    MyLog.i("Billing", "Setup finished successfully")
                    queryProducts()
                    queryPurchases()
                } else {
                    _isLoading.value = false
                    _uiState.value = BillingUiState.Error("Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                MyLog.w("Billing", "Service disconnected")
                _isLoading.value = false
            }
        })
    }

    private fun queryProducts() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_TIP_SMALL)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_TIP_MEDIUM)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_TIP_LARGE)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_SUPPORT_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_SUPPORT_YEARLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            _isLoading.value = false
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _products.value = productDetailsList
            } else {
                _uiState.value = BillingUiState.Error("Failed to query products: ${billingResult.debugMessage}")
            }
        }
    }

    fun purchase(activity: Activity, productDetails: ProductDetails) {
        _uiState.value = BillingUiState.Loading

        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)

        if (productDetails.productType == BillingClient.ProductType.SUBS) {
            val basePlanId = if (productDetails.productId == PRODUCT_SUPPORT_MONTHLY) "monthly" else "yearly"
            val offerToken = findBestOfferToken(productDetails, basePlanId)
            
            if (offerToken == null) {
                _uiState.value = BillingUiState.Error("Required subscription plan '$basePlanId' not found.")
                return
            }
            productDetailsParamsBuilder.setOfferToken(offerToken)
        }

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
            .build()

        val result = billingClient.launchBillingFlow(activity, billingFlowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _uiState.value = BillingUiState.Error("Failed to launch billing flow: ${result.debugMessage}")
        }
    }

    private fun findBestOfferToken(productDetails: ProductDetails, basePlanId: String): String? {
        val offers = productDetails.subscriptionOfferDetails ?: return null
        
        // 1. Filter by basePlanId
        val matchingBasePlanOffers = offers.filter { it.basePlanId == basePlanId }
        if (matchingBasePlanOffers.isEmpty()) return null

        // 2. Prefer an offer with an offerTag (e.g. promo) if available
        val promotionalOffer = matchingBasePlanOffers.firstOrNull { it.offerTags.isNotEmpty() }
        
        // 3. Fallback to the simplest base plan offer
        return promotionalOffer?.offerToken ?: matchingBasePlanOffers.firstOrNull { it.offerTags.isEmpty() }?.offerToken 
            ?: matchingBasePlanOffers.first().offerToken
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { handlePurchase(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _uiState.value = BillingUiState.Error("Purchase canceled", isUserCanceled = true)
            }
            else -> {
                _uiState.value = BillingUiState.Error("Purchase failed: ${billingResult.debugMessage}")
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
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
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.consumeAsync(consumeParams) { billingResult, _ ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                MyLog.i("Billing", "Tip consumed locally")
                _uiState.value = BillingUiState.Success
            } else {
                _uiState.value = BillingUiState.Error("Failed to finalize tip: ${billingResult.debugMessage}")
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        if (purchase.isAcknowledged) {
            _uiState.value = BillingUiState.Success
            queryPurchases()
            return
        }

        val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(acknowledgeParams) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                MyLog.i("Billing", "Subscription acknowledged")
                _uiState.value = BillingUiState.Success
                queryPurchases()
            } else {
                _uiState.value = BillingUiState.Error("Failed to finalize subscription: ${billingResult.debugMessage}")
            }
        }
    }

    fun queryPurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val isSubscribed = purchases.any { 
                    it.purchaseState == Purchase.PurchaseState.PURCHASED && it.isAcknowledged 
                }
                _purchaseState.value = if (isSubscribed) PurchaseState.SUBSCRIBED else PurchaseState.NOT_PURCHASED
            }
        }
    }
}
