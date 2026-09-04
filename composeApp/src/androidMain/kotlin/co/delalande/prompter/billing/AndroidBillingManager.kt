package co.delalande.prompter.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
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
    private val activity: Activity
) : BillingManager {

    private var client: BillingClient? = null
    private var connecting = false
    private val pendingAfterConnect = mutableListOf<() -> Unit>()
    private var purchaseCallback: ((Boolean) -> Unit)? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        Log.i(TAG) { "onPurchasesUpdated: ${resultCodeName(result.responseCode)} debug=${result.debugMessage} purchases=${purchases?.map { it.products to it.purchaseState }}" }
        val success = result.responseCode == BillingClient.BillingResponseCode.OK &&
            purchases?.any { it.purchaseState == Purchase.PurchaseState.PURCHASED } == true
        if (success) {
            consumeAll(purchases)
        }
        purchaseCallback?.invoke(success)
        purchaseCallback = null
    }

    override fun connect() {
        if (client == null) {
            client = BillingClient.newBuilder(activity.applicationContext)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
                )
                .enableAutoServiceReconnection()
                .build()
        }
        ensureConnected {
            consumeOwnedTips { }
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
                Log.i(TAG) { "queryProductDetails($productId): ${resultCodeName(billingResult.responseCode)} debug=${billingResult.debugMessage} found=${result.productDetailsList.map { it.productId }}" }
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
                        .mapNotNull { details ->
                            details.oneTimePurchaseOfferDetails?.formattedPrice
                                ?.let { price -> details.productId to price }
                        }
                        .toMap()
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
        pendingAfterConnect += action
        if (connecting) {
            return
        }
        connecting = true
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                connecting = false
                Log.i(TAG) { "onBillingSetupFinished: ${resultCodeName(billingResult.responseCode)} debug=${billingResult.debugMessage}" }
                val actions = pendingAfterConnect.toList()
                pendingAfterConnect.clear()
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    actions.forEach { it() }
                } else {
                    purchaseCallback?.invoke(false)
                    purchaseCallback = null
                }
            }

            override fun onBillingServiceDisconnected() {
                // Auto reconnection is enabled; nothing to do here.
            }
        })
    }

    /** Consumes any owned tip purchases so they can be bought again. */
    private fun consumeOwnedTips(onDone: (found: Boolean) -> Unit) {
        val billingClient = client ?: return onDone(false)
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            Log.i(TAG) { "queryPurchasesAsync: ${resultCodeName(billingResult.responseCode)} debug=${billingResult.debugMessage} purchases=${purchases.map { it.products to it.purchaseState }}" }
            val ownedTips = purchases.filter { purchase ->
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    purchase.products.any { it in TIP_PRODUCT_IDS }
            }
            consumeAll(ownedTips)
            onDone(ownedTips.isNotEmpty())
        }
    }

    private fun consumeAll(purchases: List<Purchase>?) {
        val billingClient = client ?: return
        purchases?.forEach { purchase ->
            val consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.consumeAsync(consumeParams) { billingResult, purchaseToken ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.i(TAG) { "consumed: $purchaseToken" }
                } else {
                    Log.w(TAG) { "consume failed(${resultCodeName(billingResult.responseCode)}): ${billingResult.debugMessage}" }
                }
            }
        }
    }
}
