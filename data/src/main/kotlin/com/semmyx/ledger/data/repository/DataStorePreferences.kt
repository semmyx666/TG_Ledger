package com.semmyx.ledger.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.semmyx.ledger.domain.AppLanguage
import com.semmyx.ledger.domain.LedgerCurrency
import com.semmyx.ledger.domain.PreferencesRepository
import com.semmyx.ledger.domain.ThemeMode
import com.semmyx.ledger.domain.UserPreferences
import java.util.Locale
import org.json.JSONObject
import com.semmyx.ledger.domain.ColorPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map

class DataStorePreferences(context: Context, scope: CoroutineScope) : PreferencesRepository {
    private val defaultLanguage = if (Locale.getDefault().language == "uk") AppLanguage.UKRAINIAN else AppLanguage.ENGLISH
    private val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = CoroutineScope(scope.coroutineContext + Dispatchers.IO),
        produceFile = { context.applicationContext.preferencesDataStoreFile("preferences") },
    )
    private object Keys {
        val name = stringPreferencesKey("display_name")
        val currency = stringPreferencesKey("currency")
        val theme = stringPreferencesKey("theme")
        val language = stringPreferencesKey("language")
        val onboarded = booleanPreferencesKey("onboarded")
        val biometrics = booleanPreferencesKey("biometrics")
        val colors = stringPreferencesKey("color_overrides")
        val refreshRate = intPreferencesKey("refresh_rate_hz")
        val visualRevision = intPreferencesKey("visual_revision")
        val skin = stringPreferencesKey("mascot_skin")
        val animations = booleanPreferencesKey("mascot_animations")
        val premiumAssistant = booleanPreferencesKey("premium_assistant")
    }
    init {
        scope.launch {
            store.edit { values ->
                if ((values[Keys.visualRevision] ?: 0) < 4) {
                    values[Keys.theme] = ThemeMode.DARK.name
                    values[Keys.animations] = true
                    values[Keys.visualRevision] = 4
                }
            }
        }
    }
    override val preferences = store.data.map { p ->
        UserPreferences(
            displayName = p[Keys.name].orEmpty(),
            currency = enumOrDefault(p[Keys.currency], LedgerCurrency.UAH),
            theme = enumOrDefault(p[Keys.theme], ThemeMode.DARK),
            language = enumOrDefault(p[Keys.language], defaultLanguage),
            onboardingComplete = p[Keys.onboarded] ?: false,
            biometricsEnabled = p[Keys.biometrics] ?: false,
            premiumAssistantEnabled = p[Keys.premiumAssistant] ?: false,
            mascotSkin = p[Keys.skin] ?: "gray",
            mascotAnimations = p[Keys.animations] ?: true,
            refreshRateHz = p[Keys.refreshRate] ?: 0,
            colorOverrides = decodeColors(p[Keys.colors]),
        )
    }
    override suspend fun setName(name: String) { require(name.trim().length <= 40); store.edit { it[Keys.name] = name.trim() } }
    override suspend fun setCurrency(currency: LedgerCurrency) { store.edit { it[Keys.currency] = currency.name } }
    override suspend fun setTheme(theme: ThemeMode) { store.edit { it[Keys.theme] = theme.name } }
    override suspend fun setLanguage(language: AppLanguage) { store.edit { it[Keys.language] = language.name } }
    override suspend fun completeOnboarding() { store.edit { it[Keys.onboarded] = true } }
    override suspend fun setBiometrics(enabled: Boolean) { store.edit { it[Keys.biometrics] = enabled } }
    override suspend fun setColor(key: String, hex: String?) {
        require(ColorPreferences.validKey(key))
        val normalized = hex?.let { ColorPreferences.normalizeHex(it) ?: error("Invalid RGB color") }
        store.edit { prefs ->
            val values = decodeColors(prefs[Keys.colors]).toMutableMap()
            if (normalized == null) values.remove(key) else values[key] = normalized
            prefs[Keys.colors] = JSONObject(values as Map<*, *>).toString()
        }
    }
    override suspend fun resetColors() { store.edit { it.remove(Keys.colors) } }
    override suspend fun setRefreshRate(hz: Int) { require(hz in listOf(0, 60, 90, 120)); store.edit { it[Keys.refreshRate] = hz } }
    override suspend fun setMascotSkin(value: String) { require(value in listOf("gray", "sky")); store.edit { it[Keys.skin] = value } }
    override suspend fun setMascotAnimations(enabled: Boolean) { store.edit { it[Keys.animations] = enabled } }
    override suspend fun setPremiumAssistant(enabled: Boolean) { store.edit { it[Keys.premiumAssistant] = enabled } }
}

private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: fallback

private fun decodeColors(text: String?): Map<String, String> {
    if (text.isNullOrBlank()) return emptyMap()
    return try {
        val json = JSONObject(text)
        buildMap { json.keys().forEach { key ->
            if (ColorPreferences.validKey(key)) ColorPreferences.normalizeHex(json.optString(key))?.let { put(key, it) }
        } }
    } catch (_: Exception) { emptyMap() }
}
