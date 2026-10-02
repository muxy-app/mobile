package com.muxy.app.features.billing

import android.app.Activity
import com.muxy.app.core.concurrency.attempt
import com.muxy.app.core.logging.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BillingRepository(
    private val play: PlayBilling,
    private val trials: TrialStore,
    val enforcement: BillingEnforcement,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutableState = MutableStateFlow(BillingState())
    val state = mutableState.asStateFlow()
    private val operations = Mutex()
    private var trialStartedAt: Long? = null
    private var purchased = false
    private var purchaseFlowLaunched = false
    private val unacknowledged = mutableMapOf<String, PlayPurchase>()

    init {
        scope.launch {
            play.updates.collect { update ->
                operations.withLock {
                    when (update) {
                        is PlayPurchaseUpdate.Purchases -> {
                            purchaseFlowLaunched = false
                            mutableState.update { it.copy(purchasing = false, error = null) }
                            processPurchases(update.purchases)
                        }

                        is PlayPurchaseUpdate.Failed -> {
                            handlePurchaseFailure(update.error)
                        }
                    }
                }
            }
        }
        scope.launch {
            while (isActive) {
                delay(60_000)
                refreshEntitlement()
                operations.withLock {
                    if (unacknowledged.isNotEmpty()) {
                        attempt {
                            play.connect()
                            acknowledgePurchases()
                        }.onFailure(::logFailure)
                    }
                }
            }
        }
    }

    suspend fun refresh() {
        refreshEntitlement()
        operations.withLock {
            if (!state.value.ready) {
                initialize()
                return@withLock
            }
            if (trialStartedAt == null) loadTrial()
            attempt {
                play.connect()
                if (state.value.productPrice == null) attempt { loadPrice() }.onFailure(::showFailure)
                processPurchases(play.queryPurchases())
            }.onFailure(::showFailure)
        }
    }

    fun onActivityResumed() {
        if (!purchaseFlowLaunched) return
        scope.launch {
            operations.withLock {
                if (!purchaseFlowLaunched) return@withLock
                purchaseFlowLaunched = false
                try {
                    attempt {
                        play.connect()
                        processPurchases(play.queryPurchases())
                    }.onFailure(::showFailure)
                } finally {
                    mutableState.update { it.copy(purchasing = false) }
                }
            }
        }
    }

    fun buy(activity: Activity) {
        if (state.value.busy || purchased) return
        mutableState.update { it.copy(purchasing = true, error = null) }
        scope.launch {
            operations.withLock {
                attempt {
                    if (!state.value.ready) initialize()
                    if (purchased) {
                        mutableState.update { it.copy(purchasing = false) }
                        return@attempt
                    }
                    play.connect()
                    loadPrice()
                    play.launchPurchase(activity)
                    purchaseFlowLaunched = true
                }.onFailure { handlePurchaseFailure(it) }
            }
        }
    }

    fun restore() {
        if (state.value.busy) return
        mutableState.update { it.copy(restoring = true, error = null) }
        scope.launch {
            try {
                operations.withLock {
                    attempt {
                        if (!state.value.ready) initialize()
                        play.connect()
                        processPurchases(play.queryPurchases())
                    }.onFailure(::showFailure)
                }
            } finally {
                mutableState.update { it.copy(restoring = false) }
            }
        }
    }

    private fun currentEntitlement(): Entitlement {
        if (purchased) return Entitlement.Unlocked
        if (state.value.trialLoaded && trialStartedAt == null) return Entitlement.Expired
        return computeEntitlement(purchased, trialStartedAt, now(), enforcement.trialDurationMs)
    }

    private suspend fun initialize() {
        loadTrial()
        attempt {
            play.connect()
            attempt { loadPrice() }.onFailure(::showFailure)
            processPurchases(play.queryPurchases())
        }.onFailure(::showFailure)
        mutableState.update { it.copy(ready = true) }
    }

    private suspend fun loadTrial() {
        attempt { trials.startIfAbsent(now()) }
            .onSuccess { trialStartedAt = it }
            .onFailure {
                logFailure(it)
                mutableState.update { current ->
                    current.copy(error = "Couldn't start the trial securely. Restart Muxy to try again.")
                }
            }
        mutableState.update { it.copy(trialLoaded = true) }
        refreshEntitlement()
    }

    private suspend fun loadPrice() {
        val price = play.fetchUnlockPrice()
        mutableState.update { it.copy(productPrice = price) }
    }

    private suspend fun processPurchases(purchases: List<PlayPurchase>) {
        val valid = purchases.filter { it.isPurchased }
        if (valid.isEmpty()) return
        purchased = true
        purchaseFlowLaunched = false
        mutableState.update { it.copy(entitlement = Entitlement.Unlocked, purchasing = false, error = null) }
        valid.forEach {
            if (it.acknowledged) unacknowledged.remove(it.token) else unacknowledged[it.token] = it
        }
        acknowledgePurchases()
    }

    private suspend fun acknowledgePurchases() {
        unacknowledged.values.toList().forEach { purchase ->
            attempt { play.acknowledge(purchase) }
                .onSuccess { unacknowledged.remove(purchase.token) }
                .onFailure(::logFailure)
        }
    }

    private suspend fun handlePurchaseFailure(error: Throwable) {
        purchaseFlowLaunched = false
        mutableState.update { it.copy(purchasing = false) }
        val failure = (error as? PlayBillingException)?.failure
        if (failure == PlayBillingFailure.CANCELLED) {
            mutableState.update { it.copy(error = null) }
            return
        }
        if (failure == PlayBillingFailure.ALREADY_OWNED) {
            attempt {
                play.connect()
                processPurchases(play.queryPurchases())
                if (!purchased) showFailure(error)
            }.onFailure(::showFailure)
            return
        }
        showFailure(error)
    }

    fun refreshEntitlement(): Entitlement {
        val entitlement = currentEntitlement()
        mutableState.update { it.copy(entitlement = entitlement) }
        return entitlement
    }

    private fun showFailure(error: Throwable) {
        logFailure(error)
        val message = (error as? PlayBillingException)?.message ?: "Couldn't connect to Google Play. Try again."
        mutableState.update { it.copy(error = message) }
    }

    private fun logFailure(error: Throwable) {
        Log.billing.error("Billing operation failed: ${error.javaClass.simpleName}")
    }
}
