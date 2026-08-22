package co.delalande.prompter.billing

import android.app.Activity
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
import co.delalande.prompter.data.PlatformStorage
import co.delalande.prompter.log.Log

private const val TAG = "Billing"

private fun resultCodeName(code: Int): String = when (code) {
    BillingClient.BillingResponseCode.OK -> "OK"
    BillingClient.BillingResponseCode.USER_CANCELED -> "USER_CANCELED"
    BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE"
    BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "BILLING_UNAVAILABLE"
    BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "ITEM_UNAVAILABLE"
    BillingClient.BillingResponseCode.DEVELOPER_ERROR -> "DEVELOPER_ERROR"
    BillingClient.BillingResponseCode.ERROR -> "ERROR"
    BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> "ITEM_ALREADY_OWNED"
    BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> "ITEM_NOT_OWNED"
    else -> "UNKNOWN($code)"
}

class AndroidBillingManager(
    private val activity: Activity,
    private val storage: PlatformStorage
) : BillingManager {

    private var purchased = loadPremium(storage)
    private var client: BillingClient? = null
    private var connecting = false
    private var pendingAfterConnect: (() -> Unit)? = null
    private var onPremiumChanged: ((Boolean) -> Unit)? = null
    private var purchaseCallback: ((Boolean) -> Unit)? = null
    private var restoreCallback: ((Boolean) -> Unit)? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        Log.i(TAG) { "onPurchasesUpdated: ${resultCodeName(result.responseCode)} debug=${result.debugMessage} purchases=${purchases?.map { it.products to it.purchaseState }}" }
        val success = result.responseCode == BillingClient.BillingResponseCode.OK && hasPremium(purchases)
        if (success) setPremium(true)
        purchaseCallback?.invoke(success)
        purchaseCallback = null
    }

    override fun connect(onPremiumChanged: (Boolean) -> Unit) {
        this.onPremiumChanged = onPremiumChanged
        val billingClient = BillingClient.newBuilder(activity.applicationContext)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .enableAutoServiceReconnection()
            .build()
        client = billingClient
        ensureConnected {
            queryPremium { premium ->
                onPremiumChanged(premium)
            }
        }
    }

    override fun purchase(productId: String, onResult: (Boolean) -> Unit) {
        purchaseCallback = onResult
        ensureConnected {
            val billingClient = client ?: return@ensureConnected
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(productId)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    )
                )
                .build()
            billingClient.queryProductDetailsAsync(params) { billingResult, result ->
                Log.i(TAG) { "queryProductDetails($productId): ${resultCodeName(billingResult.responseCode)} debug=${billingResult.debugMessage} found=${result.productDetailsList?.map { it.productId }}" }
                val details = result.productDetailsList.firstOrNull { it.productId == productId }
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && details != null) {
                    val flowParams = BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(
                            listOf(
                                BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(details)
                                    .build()
                            )
                        )
                        .build()
                    val flowResult = billingClient.launchBillingFlow(activity, flowParams)
                    Log.i(TAG) { "launchBillingFlow($productId): ${resultCodeName(flowResult.responseCode)} debug=${flowResult.debugMessage}" }
                    if (flowResult.responseCode != BillingClient.BillingResponseCode.OK) {
                        purchaseCallback?.invoke(false)
                        purchaseCallback = null
                    }
                } else {
                    purchaseCallback?.invoke(false)
                    purchaseCallback = null
                }
            }
        }
    }

    override fun restorePurchases(onResult: (Boolean) -> Unit) {
        restoreCallback = onResult
        ensureConnected {
            queryPremium { premium ->
                restoreCallback?.invoke(premium)
                restoreCallback = null
            }
        }
    }

    override fun isPremium(): Boolean = purchased

    override fun tipPrices(onPrices: (Map<String, String>) -> Unit) {
        ensureConnected {
            val billingClient = client
            if (billingClient == null) {
                onPrices(emptyMap())
                return@ensureConnected
            }
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    TIP_PRODUCT_IDS.map { productId ->
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(productId)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    }
                )
                .build()
            billingClient.queryProductDetailsAsync(params) { billingResult, result ->
                val prices = if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    result.productDetailsList
                        ?.mapNotNull { details ->
                            details.oneTimePurchaseOfferDetails?.formattedPrice
                                ?.let { price -> details.productId to price }
                        }
                        ?.toMap() ?: emptyMap()
                } else {
                    Log.i(TAG) { "tipPrices: ${resultCodeName(billingResult.responseCode)} debug=${billingResult.debugMessage}" }
                    emptyMap()
                }
                onPrices(prices)
            }
        }
    }

    private fun ensureConnected(action: () -> Unit) {
        val billingClient = client
        if (billingClient == null) {
            action()
            return
        }
        if (billingClient.isReady) {
            action()
            return
        }
        if (connecting) {
            pendingAfterConnect = action
            return
        }
        connecting = true
        pendingAfterConnect = action
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                connecting = false
                Log.i(TAG) { "onBillingSetupFinished: ${resultCodeName(billingResult.responseCode)} debug=${billingResult.debugMessage}" }
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    pendingAfterConnect?.invoke()
                } else {
                    pendingAfterConnect = null
                    purchaseCallback?.invoke(false)
                    purchaseCallback = null
                    restoreCallback?.invoke(false)
                    restoreCallback = null
                    onPremiumChanged?.invoke(purchased)
                }
            }

            override fun onBillingServiceDisconnected() {
                // Auto reconnection is enabled; nothing to do here.
            }
        })
    }

    private fun queryPremium(callback: (Boolean) -> Unit) {
        val billingClient = client ?: return callback(purchased)
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            Log.i(TAG) { "queryPurchasesAsync: ${resultCodeName(billingResult.responseCode)} debug=${billingResult.debugMessage} purchases=${purchases.map { it.products to it.purchaseState }}" }
            val premium = hasPremium(purchases)
            if (premium != purchased) {
                setPremium(premium)
            }
            callback(premium)
        }
    }

    private fun setPremium(premium: Boolean) {
        purchased = premium
        savePremium(storage, premium)
    }

    private fun hasPremium(purchases: List<Purchase>?): Boolean =
        purchases?.any {
            it.products.any { productId -> productId in TIP_PRODUCT_IDS } &&
                it.purchaseState == Purchase.PurchaseState.PURCHASED
        } == true
}
