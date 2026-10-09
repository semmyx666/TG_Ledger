package com.semmyx.ledger.data.billing

import android.content.Context
import com.android.billingclient.api.*

internal fun createVersionedBillingClient(context: Context, listener: PurchasesUpdatedListener): BillingClient =
    BillingClient.newBuilder(context).setListener(listener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection().build()

internal fun BillingClient.queryVersionedProducts(params: QueryProductDetailsParams,
    callback: (BillingResult, List<ProductDetails>) -> Unit) {
    queryProductDetailsAsync(params) { result, response -> callback(result, response.productDetailsList) }
}
internal fun versionedPrice(details: ProductDetails): String? = details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
internal fun versionedProductParams(details: ProductDetails): BillingFlowParams.ProductDetailsParams {
    val offer = requireNotNull(details.oneTimePurchaseOfferDetailsList?.firstOrNull())
    return BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).setOfferToken(offer.offerToken).build()
}
