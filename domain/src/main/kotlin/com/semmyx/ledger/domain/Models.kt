package com.semmyx.ledger.domain

import java.time.LocalDate

/** All supported currencies have exactly two fraction digits. Never store money as Double. */
enum class LedgerCurrency { UAH, USD, EUR, GBP, PLN }
enum class EntryType { INCOME, EXPENSE }
enum class Category { FOOD, TRANSPORT, HOME, HEALTH, LEISURE, SALARY, OTHER, TRANSFER, DINING, CASHBACK }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class AppLanguage(val tag: String) { ENGLISH("en"), UKRAINIAN("uk") }

data class UserPreferences(
    val displayName: String = "",
    val currency: LedgerCurrency = LedgerCurrency.UAH,
    val theme: ThemeMode = ThemeMode.DARK,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val onboardingComplete: Boolean = false,
    val biometricsEnabled: Boolean = false,
    // Gated separately from entitlement: a premium user can still hide the mascot.
    val premiumAssistantEnabled: Boolean = false,
    val mascotSkin: String = "gray",
    val mascotAnimations: Boolean = true,
    val refreshRateHz: Int = 0,
    val colorOverrides: Map<String, String> = emptyMap(),
)

data class LedgerEntry(
    val id: String,
    val title: String,
    val amountMinor: Long,
    val currency: LedgerCurrency,
    val type: EntryType,
    val category: Category,
    val date: LocalDate,
    val note: String = "",
    val sourceType: String = "UNKNOWN",
    val bankId: String? = null,
    val cardLast4: String? = null,
    val eventAtMillis: Long? = null,
    val customCategoryId: String? = null,
    val origin: String = "MANUAL",
)

data class EntryFilter(
    val currency: LedgerCurrency,
    val query: String = "",
    val type: EntryType? = null,
    val limit: Int = 50,
    val category: Category? = null,
    val from: LocalDate? = null,
    val through: LocalDate? = null,
)

data class Balance(val incomeMinor: Long = 0, val expenseMinor: Long = 0, val entryCount: Int = 0) {
    val netMinor: Long get() = Math.subtractExact(incomeMinor, expenseMinor)
}

data class DailyActivity(val date: LocalDate, val incomeMinor: Long, val expenseMinor: Long)
data class DeletionToken(val id: String, val deletedAtMillis: Long)
/** Informational NBU rate, not a transaction conversion. */
data class ExchangeRate(val currency: LedgerCurrency, val uahPerUnit: Double, val date: LocalDate, val fetchedAtMillis: Long)

enum class ValidationIssue { TITLE, AMOUNT, NOTE, DATE }
class EntryValidationException(val issue: ValidationIssue) : IllegalArgumentException(issue.name)
sealed interface PinResult {
    data object Success : PinResult
    data object Incorrect : PinResult
    data class Locked(val remainingSeconds: Long) : PinResult
}

enum class BillingIssue { UNAVAILABLE, CONFIGURATION, NETWORK, PENDING, VERIFICATION, ACKNOWLEDGEMENT, ACTIVATION_CODE }

/** Where the currently active entitlement (if any) came from. */
enum class PremiumSource { GOOGLE_PLAY, OFFICIAL_DEVELOPER, TRIAL }

data class PremiumState(
    // True once a Google Play billing public key is baked into this build.
    val configured: Boolean = false,
    val entitled: Boolean = false,
    val formattedPrice: String? = null,
    val busy: Boolean = false,
    val issue: BillingIssue? = null,
    val source: PremiumSource? = null,
    // Only set for a developer-issued activation code; Google Play grants do not expire here.
    val expiresAtMillis: Long? = null,
    // True once the developer's activation public key is baked into this build.
    val developerPurchaseEnabled: Boolean = false,
    // Stable per-installation id the developer binds an activation code to.
    val developerInstallationId: String = "",
    val trialAvailable: Boolean = false,
)
