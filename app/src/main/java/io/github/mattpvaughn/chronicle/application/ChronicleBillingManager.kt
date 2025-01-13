package io.github.mattpvaughn.chronicle.application

import android.app.Activity
import android.content.Context
import com.limurse.iap.IapConnector
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton wrapper around the Google Playing Billing library. Handles the initialization of
 * Billing, restores previous purchases, and exposes a method to launch billing flow.
 *
 * TODO: use a more sophisticated method to prevent cheats
 */
@Singleton
class ChronicleBillingManager
    @Inject
    constructor(
        applicationContext: Context,
        private val prefsRepo: PrefsRepo,
    ) {
        fun launchBillingFlow(activity: Activity) {
            iapConnector.purchase(activity, PREMIUM_IAP_SKU)
        }

        private val iapConnector =
            IapConnector(
                context = applicationContext,
                nonConsumableKeys = listOf(PREMIUM_IAP_SKU),
                enableLogging = true,
            )
            /*.apply {
                addPurchaseListener(
                    object : PurchaseServiceListener {
                        override fun onPricesUpdated(iapKeyPrices: Map<String, DataWrappers.SkuDetails>) {
                            // no-op
                        }

                        override fun onProductPurchased(purchaseInfo: DataWrappers.PurchaseInfo) {
                            prefsRepo.premiumPurchaseToken = purchaseInfo.purchaseToken
                        }

                        override fun onProductRestored(purchaseInfo: DataWrappers.PurchaseInfo) {
                            prefsRepo.premiumPurchaseToken = purchaseInfo.purchaseToken
                        }
                    },
                )
            }*/
    }
