package com.muxy.app.features.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.muxy.app.core.logging.Log
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

class GooglePlayBilling(
    context: Context,
) : PlayBilling {
    private val purchaseUpdates = Channel<PlayPurchaseUpdate>(Channel.UNLIMITED)
    override val updates = purchaseUpdates.receiveAsFlow()
    private var product: ProductDetails? = null
    private var offer: ProductDetails.OneTimePurchaseOfferDetails? = null
    private val client =
        BillingClient
            .newBuilder(context.applicationContext)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .setListener { result, purchases ->
                Log.billing.debug("Purchase update: ${result.responseCode}")
                val update =
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        PlayPurchaseUpdate.Purchases(purchases.orEmpty().map(::mapPurchase))
                    } else {
                        PlayPurchaseUpdate.Failed(result.failure())
                    }
                purchaseUpdates.trySend(update)
            }.build()

    override suspend fun connect() {
        if (client.isReady) return
        Log.billing.debug("Connecting to Google Play")
        request {
            val result =
                suspendCancellableCoroutine { continuation ->
                    client.startConnection(
                        object : BillingClientStateListener {
                            override fun onBillingSetupFinished(result: BillingResult) {
                                if (continuation.isActive) continuation.resume(result)
                            }

                            override fun onBillingServiceDisconnected() {
                                Log.billing.debug("Google Play disconnected")
                            }
                        },
                    )
                }
            result.requireSuccess()
        }
    }

    override suspend fun fetchUnlockPrice(): String? =
        request {
            product = null
            offer = null
            val params =
                QueryProductDetailsParams
                    .newBuilder()
                    .setProductList(
                        listOf(
                            QueryProductDetailsParams.Product
                                .newBuilder()
                                .setProductId(UNLOCK_PRODUCT_ID)
                                .setProductType(BillingClient.ProductType.INAPP)
                                .build(),
                        ),
                    ).build()
            val result = client.queryProductDetails(params)
            result.billingResult.requireSuccess()
            val details = result.productDetailsList?.firstOrNull { it.productId == UNLOCK_PRODUCT_ID }
            val purchaseOffer = details?.oneTimePurchaseOfferDetailsList?.firstOrNull { it.rentalDetails == null }
            if (purchaseOffer == null) throw unavailableProduct()
            product = details
            offer = purchaseOffer
            purchaseOffer.formattedPrice
        }

    override suspend fun queryPurchases(): List<PlayPurchase> =
        request {
            val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
            val result = client.queryPurchasesAsync(params)
            result.billingResult.requireSuccess()
            result.purchasesList.map(::mapPurchase)
        }

    override fun launchPurchase(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) {
            throw PlayBillingException(PlayBillingFailure.UNAVAILABLE, "Couldn't open Google Play. Try again.")
        }
        val details = product ?: throw unavailableProduct()
        val purchaseOffer = offer ?: throw unavailableProduct()
        val productParams =
            BillingFlowParams.ProductDetailsParams
                .newBuilder()
                .setProductDetails(details)
                .apply { purchaseOffer.offerToken?.let(::setOfferToken) }
                .build()
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams)).build()
        client.launchBillingFlow(activity, params).requireSuccess()
    }

    override suspend fun acknowledge(purchase: PlayPurchase) {
        if (!purchase.isPurchased || purchase.acknowledged) return
        request {
            val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.token).build()
            client.acknowledgePurchase(params).requireSuccess()
        }
    }

    private fun mapPurchase(purchase: Purchase): PlayPurchase =
        PlayPurchase(
            products = purchase.products,
            state =
                when (purchase.purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> PlayPurchaseState.PURCHASED
                    Purchase.PurchaseState.PENDING -> PlayPurchaseState.PENDING
                    else -> PlayPurchaseState.UNKNOWN
                },
            token = purchase.purchaseToken,
            acknowledged = purchase.isAcknowledged,
        )

    private suspend fun <T> request(block: suspend () -> T): T =
        try {
            withTimeout(30_000) { block() }
        } catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            throw PlayBillingException(PlayBillingFailure.UNAVAILABLE, "Google Play didn't respond. Try again.")
        }

    private fun BillingResult.requireSuccess() {
        if (responseCode == BillingClient.BillingResponseCode.OK) return
        Log.billing.error("Google Play response: $responseCode")
        throw failure()
    }

    private fun BillingResult.failure(): PlayBillingException =
        when (responseCode) {
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                PlayBillingException(PlayBillingFailure.CANCELLED, "")
            }

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                PlayBillingException(PlayBillingFailure.ALREADY_OWNED, "This purchase is already owned. Tap Restore purchase.")
            }

            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> {
                unavailableProduct()
            }

            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                PlayBillingException(PlayBillingFailure.UNAVAILABLE, "In-app purchases are not available. Check your Google Play account.")
            }

            else -> {
                PlayBillingException(PlayBillingFailure.OTHER, "Couldn't complete the Google Play request. Try again.")
            }
        }

    private fun unavailableProduct(): PlayBillingException =
        PlayBillingException(PlayBillingFailure.UNAVAILABLE, "Muxy unlock is not available from Google Play. Try again later.")
}
