package com.teleprompter.billing

import com.teleprompter.data.PlatformStorage

interface BillingManager {
    fun connect(onPremiumChanged: (Boolean) -> Unit)
    fun purchase(productId: String, onResult: (Boolean) -> Unit)
    fun restorePurchases(onResult: (Boolean) -> Unit)
    fun isPremium(): Boolean
}

const val TIP_10_PRODUCT_ID = "com.teleprompter.tip10"
const val TIP_25_PRODUCT_ID = "com.teleprompter.tip25"
val TIP_PRODUCT_IDS = setOf(TIP_10_PRODUCT_ID, TIP_25_PRODUCT_ID)
const val PREMIUM_FILE = "premium.json"

fun savePremium(storage: PlatformStorage, premium: Boolean) {
    storage.writeFile(PREMIUM_FILE, if (premium) "true" else "false")
}

fun loadPremium(storage: PlatformStorage): Boolean =
    storage.readFile(PREMIUM_FILE)?.toBoolean() ?: false
