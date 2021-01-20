package io.github.mattpvaughn.chronicle.application

import android.app.Activity
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.BillingResponseCode.OK
import com.android.billingclient.api.BillingClient.SkuType.INAPP
import com.android.billingclient.api.Purchase.PurchaseState.PURCHASED
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.NO_PREMIUM_TOKEN
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton wrapper around the Google Playing Billing library. Handles the initialization of
 * Billing, restores previous purchases, and exposes a method to launch billing flow.
 *
 * TODO: use a more sophisticated method to prevent cheats
 */
@Singleton
class ChronicleBillingManager @Inject constructor(
    private val prefsRepo: PrefsRepo,
    private val unhandledExceptionHandler: CoroutineExceptionHandler
) : PurchasesUpdatedListener {

    private lateinit var premiumUpgradeSku: SkuDetails

    var billingClient: BillingClient? = null
        set(value) {
            field = value
            Timber.i("Billing client set: $billingClient")
            GlobalScope.launch(unhandledExceptionHandler) {
                querySkuDetails(requireNotNull(billingClient))
                getPurchaseHistory()
            }
        }

    private fun getPurchaseHistory() {
        if (billingClient == null) {
            return
        }
        val purchase = requireNotNull(billingClient).queryPurchases(INAPP)
        if (purchase.billingResult.responseCode == OK) {
            if (purchase.purchasesList.isNullOrEmpty()) {
                Timber.i("Retrieved purchase list but it was empty or null: ${purchase.purchasesList}")
                return
            }
            val premiumSku = purchase.purchasesList!!.find { record -> record.sku == PREMIUM_IAP_SKU }
            if (premiumSku != null && premiumSku.purchaseState == PURCHASED) {
                Timber.i("Found premium SKU in user's history: $premiumSku")
                prefsRepo.premiumPurchaseToken = premiumSku.purchaseToken
            } else {
                prefsRepo.premiumPurchaseToken = NO_PREMIUM_TOKEN
            }
        } else {
            Timber.i("getPurchaseHistory() failed: %s", purchase.billingResult.debugMessage)
        }
    }

    private suspend fun querySkuDetails(billingClient: BillingClient) {
        Timber.i("Querying sku details")
        val params = SkuDetailsParams.newBuilder()
            .setSkusList(IAP_SKU_LIST)
            .setType(INAPP)
            .build()

        val skuDetailsResult = withContext(Dispatchers.IO) {
            billingClient.querySkuDetails(params)
        }

        if (skuDetailsResult.billingResult.responseCode == OK) {
            Timber.i("SKUs available: ${skuDetailsResult.skuDetailsList}")
            val skuDetailsList = skuDetailsResult.skuDetailsList ?: emptyList()
            for (skuDetails in skuDetailsList) {
                val sku = skuDetails.sku
                Timber.i("$PREMIUM_IAP_SKU vs. $sku")
                if (sku == PREMIUM_IAP_SKU) {
                    premiumUpgradeSku = skuDetails
                }
            }
        } else {
            Timber.i(
                "Failed to load SKU details: ${skuDetailsResult.billingResult.debugMessage}"
            )
        }
    }

    fun launchBillingFlow(activity: Activity): BillingResult {
        Timber.i("Premium upgrade sku initialized? ${::premiumUpgradeSku.isInitialized}")
        return if (::premiumUpgradeSku.isInitialized) {
            val flowParams = BillingFlowParams.newBuilder()
                .setSkuDetails(premiumUpgradeSku)
                .build()
            billingClient?.launchBillingFlow(activity, flowParams) ?: BillingResult()
        } else {
            BillingResult()
        }
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        nullablePurchases: MutableList<Purchase>?
    ) {
        Timber.i("Checking for purchases")
        val purchases = nullablePurchases?.toList() ?: emptyList()
        if (billingResult.responseCode == OK && purchases.isNotEmpty()) {
            Timber.i("IAP Purchased!")
            val purchase = purchases[0]
            prefsRepo.premiumPurchaseToken = purchase.purchaseToken
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                GlobalScope.launch(unhandledExceptionHandler) {
                    billingClient?.acknowledgePurchase(acknowledgePurchaseParams.build())
                }
            }
//            else {
//            TODO: retry?
//            }
        } else {
            Timber.e("Purchase failed (${billingResult.responseCode})")
        }
    }
}
