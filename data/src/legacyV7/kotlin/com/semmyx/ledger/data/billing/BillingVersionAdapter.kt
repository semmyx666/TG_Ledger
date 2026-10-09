package com.semmyx.ledger.data.billing

import android.content.Context
import com.android.billingclient.api.*

internal fun createVersionedBillingClient(context: Context, listener: PurchasesUpdatedListener): BillingClient =
    BillingClient.newBuilder(context).setListener(listener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()).build()

internal fun BillingClient.queryVersionedProducts(params: QueryProductDetailsParams,
    callback: (BillingResult, List<ProductDetails>) -> Unit) {
    queryProductDetailsAsync(params) { result, products -> callback(result, products) }
}
internal fun versionedPrice(details: ProductDetails): String? = details.oneTimePurchaseOfferDetails?.formattedPrice
internal fun versionedProductParams(details: ProductDetails): BillingFlowParams.ProductDetailsParams =
    BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()
