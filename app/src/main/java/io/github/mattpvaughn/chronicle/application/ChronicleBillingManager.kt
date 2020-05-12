package io.github.mattpvaughn.chronicle.application

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.BillingResponseCode.OK
import com.android.billingclient.api.BillingClient.SkuType.INAPP
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.NO_PREMIUM_TOKEN
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton wrapper around the Google Playing Billing library. Handles the initialization of
 * Billing, restores previous purchases, and exposes a method to launch billing flow.
 *
 * TODO: use a more sophisticated method to prevent cheats
 */
@Singleton
class ChronicleBillingManager @Inject constructor(private val prefsRepo: PrefsRepo) :
    PurchasesUpdatedListener {

    private lateinit var premiumUpgradeSku: SkuDetails

    // TODO: I think we exposed this to avoid a circular dependency but look into it
    var billingClient: BillingClient? = null
        set(value) {
            field = value
            Log.i(APP_NAME, "Billing client set: $billingClient")
            GlobalScope.launch {
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
            if (purchase.purchasesList.isEmpty()) {
                Log.i(APP_NAME, "Retrieved purchase list but it was empty")
                return
            }
            val premiumSku =
                purchase.purchasesList.first { record -> record.sku == PREMIUM_IAP_SKU }
            if (premiumSku != null) {
                prefsRepo.premiumPurchaseToken = premiumSku.purchaseToken
            } else {
                prefsRepo.premiumPurchaseToken = NO_PREMIUM_TOKEN
            }
        } else {
            Log.i(
                APP_NAME,
                "getPurchaseHistory() failed: " + purchase.billingResult.debugMessage
            )
        }
    }

    private suspend fun querySkuDetails(billingClient: BillingClient) {
        Log.i(APP_NAME, "Querying sku deets")
        val params = SkuDetailsParams.newBuilder()
            .setSkusList(IAP_SKU_LIST)
            .setType(INAPP)
            .build()

        val skuDetailsResult = withContext(Dispatchers.IO) {
            billingClient.querySkuDetails(params)
        }

        if (skuDetailsResult.billingResult.responseCode == OK) {
            Log.i(APP_NAME, "SKUs available: ${skuDetailsResult.skuDetailsList}")
            val skuDetailsList = skuDetailsResult.skuDetailsList ?: emptyList()
            for (skuDetails in skuDetailsList) {
                val sku = skuDetails.sku
                Log.i(APP_NAME, "$PREMIUM_IAP_SKU vs. $sku")
                if (sku == PREMIUM_IAP_SKU) {
                    premiumUpgradeSku = skuDetails
                }
            }
        } else {
            Log.i(
                APP_NAME,
                "Failed to load SKU details: ${skuDetailsResult.billingResult.debugMessage}"
            )
        }
    }

    fun launchBillingFlow(activity: Activity): BillingResult {
        Log.i(APP_NAME, "Premium upgrade sku initialized? ${::premiumUpgradeSku.isInitialized}")
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
        Log.i(APP_NAME, "Checking for purchases")
        val purchases = nullablePurchases?.toList() ?: emptyList()
        if (billingResult.responseCode == OK && purchases.isNotEmpty()) {
            Log.i(APP_NAME, "IAP Purchased!")
            val purchase = purchases[0]
            prefsRepo.premiumPurchaseToken = purchase.purchaseToken
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                GlobalScope.launch {
                    billingClient?.acknowledgePurchase(acknowledgePurchaseParams.build())
                }
            } else {
                // TODO- retry?
            }
        } else {
            Log.e(APP_NAME, "Purchase failed (${billingResult.responseCode})")
        }
    }
}
