package co.delalande.prompter.billing

import co.delalande.prompter.data.PlatformStorage

interface BillingManager {
    fun connect(onPremiumChanged: (Boolean) -> Unit)
    fun purchase(productId: String, onResult: (Boolean) -> Unit)
    fun restorePurchases(onResult: (Boolean) -> Unit)
    fun isPremium(): Boolean

    /** Fetches store-formatted prices for the tip products; empty map if unavailable. */
    fun tipPrices(onPrices: (Map<String, String>) -> Unit)
}

const val SMALL_TIP_PRODUCT_ID = "co.delalande.prompter.smalltip"
const val LARGE_TIP_PRODUCT_ID = "co.delalande.prompter.largetip"
val TIP_PRODUCT_IDS = setOf(SMALL_TIP_PRODUCT_ID, LARGE_TIP_PRODUCT_ID)
const val PREMIUM_FILE = "premium.json"

fun savePremium(storage: PlatformStorage, premium: Boolean) {
    storage.writeFile(PREMIUM_FILE, if (premium) "true" else "false")
}

fun loadPremium(storage: PlatformStorage): Boolean =
    storage.readFile(PREMIUM_FILE)?.toBoolean() ?: false
