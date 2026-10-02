package com.muxy.app.features.billing

import android.app.Activity
import kotlinx.coroutines.flow.Flow

const val UNLOCK_PRODUCT_ID = "muxy_unlock"

interface PlayBilling {
    val updates: Flow<PlayPurchaseUpdate>

    suspend fun connect()

    suspend fun fetchUnlockPrice(): String?

    suspend fun queryPurchases(): List<PlayPurchase>

    fun launchPurchase(activity: Activity)

    suspend fun acknowledge(purchase: PlayPurchase)
}

data class PlayPurchase(
    val products: List<String>,
    val state: PlayPurchaseState,
    val token: String,
    val acknowledged: Boolean,
) {
    val isPurchased: Boolean
        get() = UNLOCK_PRODUCT_ID in products && state == PlayPurchaseState.PURCHASED && token.isNotBlank()
}

enum class PlayPurchaseState {
    PENDING,
    PURCHASED,
    UNKNOWN,
}

sealed interface PlayPurchaseUpdate {
    data class Purchases(
        val purchases: List<PlayPurchase>,
    ) : PlayPurchaseUpdate

    data class Failed(
        val error: PlayBillingException,
    ) : PlayPurchaseUpdate
}

enum class PlayBillingFailure {
    CANCELLED,
    ALREADY_OWNED,
    UNAVAILABLE,
    OTHER,
}

class PlayBillingException(
    val failure: PlayBillingFailure,
    message: String,
) : Exception(message)
