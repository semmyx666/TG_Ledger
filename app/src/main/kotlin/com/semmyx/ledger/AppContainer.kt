package com.semmyx.ledger

import android.content.Context
import androidx.biometric.BiometricManager
import com.semmyx.ledger.data.billing.PlayPremiumRepository
import com.semmyx.ledger.data.local.LedgerDatabase
import com.semmyx.ledger.data.remote.RateApi
import com.semmyx.ledger.data.repository.DataStorePreferences
import com.semmyx.ledger.data.repository.OfflineRateRepository
import com.semmyx.ledger.data.repository.RoomEntryRepository
import com.semmyx.ledger.data.security.BiometricKeys
import com.semmyx.ledger.data.security.KeystoreSecurityRepository
import com.semmyx.ledger.data.security.KeystoreVault
import com.semmyx.ledger.domain.*
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The single composition root. ViewModels know domain interfaces, not Room / Retrofit / Play. */
class AppContainer(context: Context, scope: CoroutineScope) {
    private val app = context.applicationContext
    val clock: Clock = Clock.systemDefaultZone()
    private val database = LedgerDatabase.create(app)
    private val vault = KeystoreVault(app)
    val bankImport = com.semmyx.ledger.data.bank.BankImportRepository(database, app, vault)
    val entries: EntryRepository = RoomEntryRepository(database.ledgerDao(), clock)
    val preferences: PreferencesRepository = DataStorePreferences(app, scope)
    val security: SecurityRepository = KeystoreSecurityRepository(app, vault, clock)
    val rates: RateRepository = OfflineRateRepository(database.rateDao(), RateApi.create(), clock)
    // Two independent entitlement paths for the same product: Google Play,
    // and a developer-issued activation code for direct / pre-launch sales.
    val billing = PlayPremiumRepository(
        app, vault, scope, clock,
        BuildConfig.PLAY_LICENSE_KEY,
        BuildConfig.DEVELOPER_SUBSCRIPTION_PUBLIC_KEY,
        BuildConfig.PREMIUM_PRODUCT_ID,
    )
    val premium: PremiumRepository = billing
    val biometricKeys = BiometricKeys(app)
    val observeDashboard = ObserveDashboard(entries)
    val saveEntry = SaveEntry(entries, clock)

    fun biometricAvailable(): Boolean = BiometricManager.from(app)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    suspend fun configureBiometrics(enabled: Boolean) = withContext(Dispatchers.IO) {
        if (enabled) {
            check(biometricAvailable()) { "No enrolled strong biometric" }
            biometricKeys.provision()
        } else biometricKeys.remove()
    }
}
