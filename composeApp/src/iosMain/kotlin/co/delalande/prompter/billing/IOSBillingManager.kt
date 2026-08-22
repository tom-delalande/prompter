package co.delalande.prompter.billing

import co.delalande.prompter.data.PlatformStorage
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.NSError
import platform.StoreKit.SKPayment
import platform.StoreKit.SKPaymentQueue
import platform.StoreKit.SKPaymentTransaction
import platform.StoreKit.SKPaymentTransactionObserverProtocol
import platform.StoreKit.SKPaymentTransactionState
import platform.StoreKit.SKProduct
import platform.StoreKit.SKProductsRequest
import platform.StoreKit.SKProductsRequestDelegateProtocol
import platform.StoreKit.SKProductsResponse
import platform.StoreKit.SKRequest
import platform.StoreKit.SKRequestDelegateProtocol
import platform.darwin.NSObject

class IOSBillingManager(
    private val storage: PlatformStorage
) : BillingManager {

    private var premium = loadPremium(storage)
    private var onPremiumChanged: ((Boolean) -> Unit)? = null
    private var purchaseCallback: ((Boolean) -> Unit)? = null
    private var restoreCallback: ((Boolean) -> Unit)? = null
    private var productsRequest: SKProductsRequest? = null
    private var productsRequestDelegate: ProductsRequestDelegate? = null
    private var transactionObserver: TransactionObserver? = null

    override fun connect(onPremiumChanged: (Boolean) -> Unit) {
        this.onPremiumChanged = onPremiumChanged
        if (transactionObserver == null) {
            val observer = TransactionObserver(
                onTransaction = ::handleTransaction,
                onRestoreFinished = {
                    restoreCallback?.invoke(premium)
                    restoreCallback = null
                },
                onRestoreFailed = {
                    restoreCallback?.invoke(false)
                    restoreCallback = null
                }
            )
            transactionObserver = observer
            SKPaymentQueue.defaultQueue().addTransactionObserver(observer)
        }
        val existing = SKPaymentQueue.defaultQueue().transactions
            .firstOrNull { isPremiumTransaction(it as SKPaymentTransaction) }
        if (existing != null) {
            setPremium(true)
            onPremiumChanged(true)
        } else {
            onPremiumChanged(premium)
        }
    }

    override fun purchase(productId: String, onResult: (Boolean) -> Unit) {
        purchaseCallback = onResult
        val delegate = ProductsRequestDelegate(
            onResponse = { products ->
                val product = products.firstOrNull()
                if (product != null) {
                    SKPaymentQueue.defaultQueue().addPayment(SKPayment.paymentWithProduct(product))
                } else {
                    purchaseCallback?.invoke(false)
                    purchaseCallback = null
                }
            },
            onError = {
                purchaseCallback?.invoke(false)
                purchaseCallback = null
            }
        )
        productsRequestDelegate = delegate
        val request = SKProductsRequest(setOf<Any?>(productId))
        request.delegate = delegate
        productsRequest = request
        request.start()
    }

    override fun restorePurchases(onResult: (Boolean) -> Unit) {
        restoreCallback = onResult
        SKPaymentQueue.defaultQueue().restoreCompletedTransactions()
    }

    override fun isPremium(): Boolean = premium

    override fun tipPrices(onPrices: (Map<String, String>) -> Unit) {
        productsRequestDelegate = ProductsRequestDelegate(
            onResponse = { products ->
                val formatter = NSNumberFormatter().apply {
                    numberStyle = NSNumberFormatterCurrencyStyle
                }
                onPrices(
                    products.mapNotNull { product ->
                        val productId = product.productIdentifier
                        if (productId in TIP_PRODUCT_IDS) {
                            formatter.locale = product.priceLocale
                            formatter.stringFromNumber(product.price)?.let { productId to it }
                        } else {
                            null
                        }
                    }.toMap()
                )
                productsRequestDelegate = null
                productsRequest = null
            },
            onError = { onPrices(emptyMap()) }
        )
        val request = SKProductsRequest(TIP_PRODUCT_IDS)
        request.delegate = productsRequestDelegate!!
        productsRequest = request
        request.start()
    }

    private fun handleTransaction(transaction: SKPaymentTransaction) {
        when (transaction.transactionState) {
            SKPaymentTransactionState.SKPaymentTransactionStatePurchased -> {
                if (isPremiumTransaction(transaction)) {
                    setPremium(true)
                }
                purchaseCallback?.invoke(isPremiumTransaction(transaction))
                purchaseCallback = null
                SKPaymentQueue.defaultQueue().finishTransaction(transaction)
            }
            SKPaymentTransactionState.SKPaymentTransactionStateRestored -> {
                if (isPremiumTransaction(transaction)) {
                    setPremium(true)
                    restoreCallback?.invoke(true)
                    restoreCallback = null
                }
                SKPaymentQueue.defaultQueue().finishTransaction(transaction)
            }
            SKPaymentTransactionState.SKPaymentTransactionStateFailed -> {
                purchaseCallback?.invoke(false)
                purchaseCallback = null
                restoreCallback?.invoke(false)
                restoreCallback = null
                SKPaymentQueue.defaultQueue().finishTransaction(transaction)
            }
            else -> Unit
        }
    }

    private fun isPremiumTransaction(transaction: SKPaymentTransaction): Boolean =
        transaction.payment.productIdentifier in TIP_PRODUCT_IDS

    private fun setPremium(value: Boolean) {
        premium = value
        savePremium(storage, value)
    }
}

private class TransactionObserver(
    private val onTransaction: (SKPaymentTransaction) -> Unit,
    private val onRestoreFinished: () -> Unit,
    private val onRestoreFailed: () -> Unit
) : NSObject(), SKPaymentTransactionObserverProtocol {

    override fun paymentQueue(queue: SKPaymentQueue, updatedTransactions: List<*>) {
        updatedTransactions.forEach { transaction ->
            onTransaction(transaction as SKPaymentTransaction)
        }
    }

    override fun paymentQueueRestoreCompletedTransactionsFinished(queue: SKPaymentQueue) {
        onRestoreFinished()
    }

    override fun paymentQueue(
        queue: SKPaymentQueue,
        restoreCompletedTransactionsFailedWithError: NSError
    ) {
        onRestoreFailed()
    }
}

private class ProductsRequestDelegate(
    private val onResponse: (List<SKProduct>) -> Unit,
    private val onError: () -> Unit
) : NSObject(), SKProductsRequestDelegateProtocol {

    override fun productsRequest(request: SKProductsRequest, didReceiveResponse: SKProductsResponse) {
        onResponse(didReceiveResponse.products.map { it as SKProduct })
    }

    override fun request(request: SKRequest, didFailWithError: NSError) {
        onError()
    }
}
