package com.muxy.app.features.billing

import android.app.Activity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

internal class FakePlayBilling(
    val calls: MutableList<String> = mutableListOf(),
) : PlayBilling {
    private val events = Channel<PlayPurchaseUpdate>(Channel.UNLIMITED)
    override val updates = events.receiveAsFlow()
    var price: String? = "$5.00"
    var purchases = emptyList<PlayPurchase>()
    val acknowledged = mutableListOf<PlayPurchase>()
    var connect: suspend () -> Unit = {}
    var fetch: suspend () -> Unit = {}
    var query: suspend () -> Unit = {}
    var launch: () -> Unit = {}
    var acknowledge: suspend () -> Unit = {}

    override suspend fun connect() {
        calls += "connect"
        connect.invoke()
    }

    override suspend fun fetchUnlockPrice(): String? {
        calls += "price"
        fetch.invoke()
        return price
    }

    override suspend fun queryPurchases(): List<PlayPurchase> {
        calls += "query"
        query.invoke()
        return purchases.toList()
    }

    override fun launchPurchase(activity: Activity) {
        calls += "launch"
        launch.invoke()
    }

    override suspend fun acknowledge(purchase: PlayPurchase) {
        calls += "acknowledge"
        acknowledge.invoke()
        acknowledged += purchase
    }

    suspend fun emit(update: PlayPurchaseUpdate) {
        events.send(update)
    }
}

internal class FakeTrialStore(
    private val calls: MutableList<String> = mutableListOf(),
) : TrialStore {
    var startedAt: Long? = null
    var error: Exception? = null
    var starts = 0

    override suspend fun startIfAbsent(now: Long): Long {
        calls += "trial"
        error?.let { throw it }
        startedAt?.let { return it }
        starts += 1
        startedAt = now
        return now
    }
}

internal fun unlockPurchase(
    state: PlayPurchaseState = PlayPurchaseState.PURCHASED,
    acknowledged: Boolean = false,
): PlayPurchase = PlayPurchase(listOf(UNLOCK_PRODUCT_ID), state, "test-token", acknowledged)

internal fun billingFailure(failure: PlayBillingFailure = PlayBillingFailure.OTHER): PlayBillingException =
    PlayBillingException(failure, "Billing failed")
