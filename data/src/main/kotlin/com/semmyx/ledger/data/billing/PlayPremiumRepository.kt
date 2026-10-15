package com.semmyx.ledger.data.billing

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.android.billingclient.api.*
import com.semmyx.ledger.data.security.KeystoreVault
import com.semmyx.ledger.domain.*
import java.security.MessageDigest
import java.time.Clock
import kotlin.coroutines.resume
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * One non-consumable premium product ("Ledger Plus"), grantable through two independent,
 * additive paths:
 *  - Google Play (a signed PURCHASED receipt, verified as before).
 *  - A manually issued developer activation code (see DeveloperSubscriptionVerifier), used
 *    for direct/Telegram sales while a Play listing is unavailable or unpublished. This never
 *    talks to a server; codes are asymmetric-signature tokens checked entirely on-device.
 * Either path alone is sufficient; losing one never revokes a still-valid grant from the other.
 */
class PlayPremiumRepository(
    context: Context,
    private val vault: KeystoreVault,
    private val scope: CoroutineScope,
    private val clock: Clock,
    publicKey: String,
    developerPublicKey: String,
    private val productId: String,
) : PremiumRepository, PurchasesUpdatedListener {
    private val app = context.applicationContext
    private val verifier = PurchaseSignatureVerifier(publicKey)
    private val developerVerifier = DeveloperSubscriptionVerifier(developerPublicKey)
    private val installationId = deriveInstallationId(app)
    private val playState = MutableStateFlow(PremiumState(configured = verifier.configured))
    private val developerActivation = MutableStateFlow<DeveloperSubscriptionVerifier.Activation?>(null)
    private data class Trial(val available: Boolean = false, val expires: Long? = null)
    private val trial = MutableStateFlow(Trial())
    private val playExpiry = MutableStateFlow<Long?>(null)
    private var lastNetworkRefresh = 0L
    override val state = combine(playState, developerActivation, trial) { play, dev, _ -> merge(play, dev) }
        .stateIn(scope, SharingStarted.Eagerly, merge(playState.value, developerActivation.value))
    private val client = createVersionedBillingClient(app, this)
    private val refreshLock = Mutex()
    private val developerLock = Mutex()
    private var details: ProductDetails? = null
    private val cacheTtlMillis = 7L * 24 * 60 * 60 * 1000

    init {
        scope.launch {
            refreshLocalEntitlements()
            // The single 14-day free trial starts by itself on first launch: no button, no card, no renewal.
            if (trial.value.available) startTrial(TrialPolicy.DAYS)
        }
        scope.launch {
            // One timer at the actual next entitlement deadline, not one AES/RSA read per minute.
            combine(developerActivation, trial, playExpiry) { dev, test, play ->
                listOfNotNull(dev?.expiresAtMillis, test.expires, play).minOrNull()
            }.collectLatest { deadline ->
                if (deadline != null) {
                    delay((deadline - clock.millis()).coerceAtLeast(1))
                    refreshLocalEntitlements()
                }
            }
        }
    }

    /** Foreground checks are local; Play discovery is throttled. Restore/purchase explicitly force refresh. */
    suspend fun refreshIfNeeded() {
        refreshLocalEntitlements()
        if (lastNetworkRefresh == 0L || SystemClock.elapsedRealtime() - lastNetworkRefresh > 6 * 60 * 60 * 1000L) refresh()
    }

    private suspend fun refreshLocalEntitlements() {
        try {
            developerLock.withLock { developerActivation.value = cachedDeveloperEntitlement() }
            val now = clock.millis()
            val trialStarted = vault.read { it.has("trialStart") }
            trial.value = if (!trialStarted) Trial(available = true) else vault.update { json ->
                val start = json.optLong("trialStart", 0)
                val end = json.optLong("trialEnd", 0)
                val last = json.optLong("trialLastSeen", start)
                if (start == 0L) Trial(available = true) else {
                    val elapsed = SystemClock.elapsedRealtime()
                    val sameBoot = json.optInt("trialBoot", -2) == bootCount()
                    val monotonicEnd = json.optLong("trialElapsedEnd", 0)
                    val valid = !json.optBoolean("trialRevoked", false) && TrialPolicy.active(start, end, now, last) &&
                        (!sameBoot || elapsed < monotonicEnd)
                    if (!valid) json.put("trialRevoked", true)
                    json.put("trialLastSeen", maxOf(last, now))
                    val remaining = if (sameBoot) minOf(end - now, monotonicEnd - elapsed) else end - now
                    Trial(expires = if (valid) now + remaining else null)
                }
            }
            val active = cachedEntitlement()
            playState.update { it.copy(entitled = active) }
            playExpiry.value = if (active) vault.read { it.optLong("premiumCheckedAt") + cacheTtlMillis } else null
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            trial.value = Trial()
            developerActivation.value = null
            playExpiry.value = null
            playState.update { it.copy(entitled = false, issue = BillingIssue.VERIFICATION) }
        }
    }

    /** Exactly one local trial. No card, Play purchase, automatic renewal, or charge involved. */
    override suspend fun startTrial(days: Int): Boolean {
        if (!TrialPolicy.validDays(days)) return false
        return refreshLock.withLock {
            if (state.value.entitled) return@withLock false
            val granted = vault.update { json ->
                if (json.has("trialStart")) false else {
                    val now = clock.millis()
                    json.put("trialStart", now).put("trialEnd", now + days * 86400000L)
                        .put("trialLastSeen", now).put("trialBoot", bootCount())
                        .put("trialElapsedEnd", SystemClock.elapsedRealtime() + days * 86400000L)
                    true
                }
            }
            refreshLocalEntitlements()
            granted
        }
    }

    override suspend fun refresh() = withContext(Dispatchers.Main.immediate) {
        refreshLock.withLock {
            refreshLocalEntitlements()
            lastNetworkRefresh = SystemClock.elapsedRealtime()
            if (!verifier.configured) {
                playState.update { it.copy(configured = false, entitled = false, issue = BillingIssue.CONFIGURATION) }
                return@withLock
            }
            playState.update { it.copy(busy = true, issue = null) }
            try {
                withTimeout(20_000) {
                    ensureConnected()
                    val (result, purchases) = queryOwned()
                    checkBilling(result)
                    val valid = withContext(Dispatchers.Default) { purchases.firstOrNull(::validPurchase) }
                    if (valid == null) {
                        playExpiry.value = null
                        vault.update { it.remove("premiumReceipt"); it.remove("premiumSignature"); it.remove("premiumCheckedAt") }
                        val pending = purchases.any { productId in it.products && it.purchaseState == Purchase.PurchaseState.PENDING }
                        val rejected = purchases.any { productId in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
                        playState.update { it.copy(entitled = false, issue = when {
                            rejected -> BillingIssue.VERIFICATION
                            pending -> BillingIssue.PENDING
                            else -> null
                        }) }
                    } else {
                        // Durable grant first, then acknowledgement; both are idempotent.
                        vault.update {
                            it.put("premiumReceipt", valid.originalJson).put("premiumSignature", valid.signature)
                                .put("premiumCheckedAt", clock.millis()).put("premiumCheckedElapsed", SystemClock.elapsedRealtime())
                                .put("premiumCheckedBoot", bootCount())
                        }
                        playExpiry.value = clock.millis() + cacheTtlMillis
                        playState.update { it.copy(entitled = true) }
                        if (!valid.isAcknowledged) {
                            val ack = acknowledge(valid.purchaseToken)
                            if (ack.responseCode != BillingClient.BillingResponseCode.OK) {
                                playState.update { it.copy(issue = BillingIssue.ACKNOWLEDGEMENT) }
                            }
                        }
                    }
                    // Product discovery failing never revokes an already verified purchase.
                    val (productResult, products) = queryDetails()
                    details = if (productResult.responseCode == BillingClient.BillingResponseCode.OK)
                        products.firstOrNull { it.productId == productId } else null
                    playState.update { it.copy(formattedPrice = details?.let(::versionedPrice)) }
                }
            } catch (error: TimeoutCancellationException) {
                playState.update { it.copy(issue = BillingIssue.NETWORK) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // Keep only a previously cryptographically verified, time-bounded offline grant.
                val cached = try { cachedEntitlement() } catch (inner: Exception) {
                    if (inner is CancellationException) throw inner
                    false
                }
                playState.update { it.copy(entitled = cached, issue = BillingIssue.UNAVAILABLE) }
            } finally {
                playState.update { it.copy(busy = false) }
            }
        }
    }

    /**
     * Verifies a developer-issued activation code (from a manual/Telegram sale) against this
     * exact installation, and — only if valid and not expired — stores it so the grant survives
     * restarts without any network access. Returns false (and sets BillingIssue.ACTIVATION_CODE)
     * for a wrong, tampered, expired, or installation-mismatched code.
     */
    override suspend fun activateDeveloperToken(token: String): Boolean = withContext(Dispatchers.Default) {
        developerLock.withLock {
            val result = developerVerifier.check(token, installationId, productId, clock.millis())
            val activation = result.activation
            if (activation == null) {
                playState.update { it.copy(issue = BillingIssue.ACTIVATION_CODE, activationProblem = result.problem) }
                return@withLock false
            }
            try {
                // Keep a valid permanent/longer grant when an older code is pasted again.
                val existing = cachedDeveloperEntitlement()
                val keep = existing != null && (existing.expiresAtMillis == null ||
                    (activation.expiresAtMillis != null && existing.expiresAtMillis >= activation.expiresAtMillis))
                if (!keep) vault.update { it.put("developerActivationCode", developerVerifier.normalize(token)) }
                developerActivation.value = if (keep) existing else activation
                playState.update { it.copy(issue = null, activationProblem = null) }
                true
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                playState.update { it.copy(issue = BillingIssue.ACTIVATION_CODE, activationProblem = ActivationProblem.STORAGE) }
                false
            }
        }
    }

    override suspend fun developerInstallationId(): String = installationId

    fun launchPurchase(activity: Activity) {
        scope.launch(Dispatchers.Main.immediate) {
            if (state.value.busy || (state.value.entitled && state.value.source != PremiumSource.TRIAL && state.value.expiresAtMillis == null)) return@launch
            refresh() // Do not reuse stale ProductDetails / offer tokens.
            if (state.value.entitled && state.value.source != PremiumSource.TRIAL && state.value.expiresAtMillis == null) return@launch
            val product = details
            if (activity.isFinishing || activity.isDestroyed) return@launch
            if (product == null || !verifier.configured || versionedPrice(product) == null) {
                playState.update { it.copy(issue = if (verifier.configured) BillingIssue.UNAVAILABLE else BillingIssue.CONFIGURATION) }
                return@launch
            }
            val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(versionedProductParams(product))).build()
            val result = client.launchBillingFlow(activity, params)
            if (result.responseCode != BillingClient.BillingResponseCode.OK && result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
                playState.update { it.copy(issue = BillingIssue.UNAVAILABLE) }
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK, BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { refresh() }
            BillingClient.BillingResponseCode.USER_CANCELED -> clearIssue()
            else -> playState.update { it.copy(busy = false, issue = BillingIssue.UNAVAILABLE) }
        }
    }
    override fun clearIssue() { playState.update { it.copy(issue = null) } }

    private fun merge(play: PremiumState, dev: DeveloperSubscriptionVerifier.Activation?): PremiumState {
        val developerActive = dev != null && (dev.expiresAtMillis == null || dev.expiresAtMillis > clock.millis())
        val trialEnd = trial.value.expires?.takeIf { it > clock.millis() }
        return play.copy(
            entitled = play.entitled || developerActive || trialEnd != null,
            trialAvailable = trial.value.available,
            source = when { play.entitled -> PremiumSource.GOOGLE_PLAY; developerActive -> PremiumSource.OFFICIAL_DEVELOPER; trialEnd != null -> PremiumSource.TRIAL; else -> null },
            expiresAtMillis = when { play.entitled -> null; developerActive -> dev?.expiresAtMillis; else -> trialEnd },
            developerPurchaseEnabled = developerVerifier.configured,
            developerInstallationId = installationId,
            developerKeyId = developerVerifier.keyId,
            developerProductId = productId,
        )
    }

    private suspend fun cachedDeveloperEntitlement(): DeveloperSubscriptionVerifier.Activation? = vault.read { json ->
        val code = json.optString("developerActivationCode", "")
        if (code.isBlank()) return@read null
        developerVerifier.verify(code, installationId, productId, clock.millis())
    }

    private suspend fun ensureConnected() {
        if (client.isReady) return
        val result = suspendCancellableCoroutine<BillingResult> { continuation ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (continuation.isActive) continuation.resume(result)
                }
                override fun onBillingServiceDisconnected() {
                    playState.update { it.copy(busy = false, issue = BillingIssue.UNAVAILABLE) }
                }
            })
        }
        checkBilling(result)
    }
    private suspend fun queryOwned() = suspendCancellableCoroutine<Pair<BillingResult, List<Purchase>>> { continuation ->
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            if (continuation.isActive) continuation.resume(result to purchases)
        }
    }
    private suspend fun queryDetails() = suspendCancellableCoroutine<Pair<BillingResult, List<ProductDetails>>> { continuation ->
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productId).setProductType(BillingClient.ProductType.INAPP).build())).build()
        client.queryVersionedProducts(params) { result, products ->
            if (continuation.isActive) continuation.resume(result to products)
        }
    }
    private suspend fun acknowledge(token: String) = suspendCancellableCoroutine<BillingResult> { continuation ->
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()) { result ->
            if (continuation.isActive) continuation.resume(result)
        }
    }
    private fun validPurchase(purchase: Purchase): Boolean = try {
        purchase.purchaseState == Purchase.PurchaseState.PURCHASED && productId in purchase.products &&
            JSONObject(purchase.originalJson).optString("packageName") == app.packageName &&
            verifier.verify(purchase.originalJson, purchase.signature)
    } catch (_: Exception) { false }

    private suspend fun cachedEntitlement(): Boolean = vault.read { json ->
        val checked = json.optLong("premiumCheckedAt", 0)
        val wallAge = clock.millis() - checked
        val age = if (json.optInt("premiumCheckedBoot", -2) == bootCount()) {
            SystemClock.elapsedRealtime() - json.optLong("premiumCheckedElapsed", 0)
        } else wallAge
        if (!verifier.configured || checked == 0L || wallAge < 0 || age !in 0..cacheTtlMillis) return@read false
        val receipt = json.optString("premiumReceipt", "")
        val signature = json.optString("premiumSignature", "")
        try { validPurchase(Purchase(receipt, signature)) } catch (_: Exception) { false }
    }
    private fun bootCount() = Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private fun checkBilling(result: BillingResult) {
        check(result.responseCode == BillingClient.BillingResponseCode.OK) { "Play response ${result.responseCode}" }
    }
}

private fun deriveInstallationId(context: Context): String {
    val androidId = try { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "" } catch (_: Exception) { "" }
    val digest = MessageDigest.getInstance("SHA-256").digest("$androidId:${context.packageName}".toByteArray(Charsets.UTF_8))
    val hex = digest.joinToString("") { "%02x".format(it) }.uppercase().take(12)
    return hex.chunked(4).joinToString("-")
}
