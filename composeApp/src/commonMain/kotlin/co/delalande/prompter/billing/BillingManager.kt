package co.delalande.prompter.billing

interface BillingManager {
    fun connect()
    fun purchase(productId: String, onResult: (Boolean) -> Unit)

    /** Fetches store-formatted prices for the tip products; empty map if unavailable. */
    fun tipPrices(onPrices: (Map<String, String>) -> Unit)
}

const val SMALL_TIP_PRODUCT_ID = "co.delalande.prompter.smalltip"
const val LARGE_TIP_PRODUCT_ID = "co.delalande.prompter.largetip"
val TIP_PRODUCT_IDS = setOf(SMALL_TIP_PRODUCT_ID, LARGE_TIP_PRODUCT_ID)
