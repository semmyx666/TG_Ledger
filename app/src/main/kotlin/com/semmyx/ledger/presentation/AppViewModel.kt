package com.semmyx.ledger.presentation

import androidx.lifecycle.ViewModel
import com.semmyx.ledger.domain.PreferencesRepository
import com.semmyx.ledger.domain.SecurityRepository
import com.semmyx.ledger.domain.UserPreferences
import com.semmyx.ledger.presentation.common.guarded
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AppState(
    val ready: Boolean = false,
    val preferences: UserPreferences = UserPreferences(),
    val hasPin: Boolean = false,
    val unlocked: Boolean = false,
    val failed: Boolean = false,
    val lockEpoch: Long = 0,
)

class AppViewModel(private val preferences: PreferencesRepository, private val security: SecurityRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(AppState())
    val state = mutableState.asStateFlow()
    private var bootstrap: Job? = null
    init { retry() }

    fun retry() {
        bootstrap?.cancel()
        mutableState.value = AppState()
        bootstrap = guarded(onError = { mutableState.update { it.copy(ready = true, failed = true, unlocked = false) } }) {
            val configured = security.hasPin()
            mutableState.update { it.copy(hasPin = configured) }
            preferences.preferences.collect { prefs ->
                mutableState.update { it.copy(ready = true, preferences = prefs, failed = false) }
            }
        }
    }
    fun completeOnboarding() {
        guarded(onError = { mutableState.update { it.copy(failed = true) } }) { preferences.completeOnboarding() }
    }
    /** Called only by the PIN verifier event or a successful crypto-bound biometric callback. */
    fun unlock() { mutableState.update { it.copy(unlocked = true, hasPin = true) } }
    fun lock() { mutableState.update { it.copy(unlocked = false, lockEpoch = it.lockEpoch + 1) } }
}
