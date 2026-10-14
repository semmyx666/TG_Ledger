package com.semmyx.ledger.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semmyx.ledger.domain.PinPolicy
import com.semmyx.ledger.domain.PinResult
import com.semmyx.ledger.domain.SecurityRepository
import com.semmyx.ledger.common.guarded
import java.security.MessageDigest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AuthError { INCORRECT, MISMATCH, UNAVAILABLE, BIOMETRIC }
data class AuthState(
    val setup: Boolean,
    val confirming: Boolean = false,
    val digits: Int = 0,
    val busy: Boolean = false,
    val lockoutSeconds: Long = 0,
    val error: AuthError? = null,
)

class AuthViewModel(setup: Boolean, private val security: SecurityRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(AuthState(setup, busy = true))
    val state = mutableState.asStateFlow()
    private val unlockedChannel = Channel<Unit>(Channel.BUFFERED)
    val unlocked = unlockedChannel.receiveAsFlow()
    // PIN material is not placed in SavedStateHandle, saved instance state, or logs.
    private val input = CharArray(PinPolicy.LENGTH)
    private var length = 0
    private var firstPin: CharArray? = null

    init {
        guarded(onError = { mutableState.update { it.copy(error = AuthError.UNAVAILABLE, busy = true) } }) {
            val configured = security.hasPin()
            mutableState.update { it.copy(setup = !configured, busy = false) }
            while (isActive) {
                val seconds = security.lockoutSeconds()
                mutableState.update { it.copy(lockoutSeconds = seconds) }
                delay(1000)
            }
        }
    }
    fun digit(value: Int) {
        if (value !in 0..9 || state.value.busy || state.value.lockoutSeconds > 0 || length == input.size) return
        input[length++] = ('0'.code + value).toChar()
        mutableState.update { it.copy(digits = length, error = null) }
        if (length == input.size) submit()
    }
    fun erase() {
        if (state.value.busy || length == 0) return
        input[--length] = 0.toChar()
        mutableState.update { it.copy(digits = length, error = null) }
    }
    fun startOver() {
        if (state.value.busy) return
        firstPin?.fill(0.toChar()); firstPin = null
        clearInput()
        mutableState.update { it.copy(confirming = false, error = null) }
    }
    fun biometricFailed() { mutableState.update { it.copy(error = AuthError.BIOMETRIC) } }

    private fun submit() {
        val attempt = input.copyOf()
        if (state.value.setup && firstPin == null) {
            firstPin = attempt
            clearInput()
            mutableState.update { it.copy(confirming = true) }
            return
        }
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                if (state.value.setup) {
                    val first = requireNotNull(firstPin)
                    // Avoid a string comparison that could short-circuit on the first digit.
                    val firstBytes = ByteArray(first.size) { first[it].code.toByte() }
                    val attemptBytes = ByteArray(attempt.size) { attempt[it].code.toByte() }
                    val same = try { MessageDigest.isEqual(firstBytes, attemptBytes) }
                        finally { firstBytes.fill(0); attemptBytes.fill(0) }
                    if (same) {
                        security.createPin(attempt)
                        first.fill(0.toChar()); firstPin = null
                        unlockedChannel.send(Unit)
                    } else {
                        first.fill(0.toChar()); firstPin = null
                        mutableState.update { it.copy(confirming = false, error = AuthError.MISMATCH) }
                    }
                } else {
                    when (val result = security.verifyPin(attempt)) {
                        PinResult.Success -> unlockedChannel.send(Unit)
                        PinResult.Incorrect -> mutableState.update { it.copy(error = AuthError.INCORRECT) }
                        is PinResult.Locked -> mutableState.update { it.copy(lockoutSeconds = result.remainingSeconds, error = null) }
                    }
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                mutableState.update { it.copy(error = AuthError.UNAVAILABLE) }
            } finally {
                attempt.fill(0.toChar())
                clearInput()
                mutableState.update { it.copy(busy = false) }
            }
        }
    }
    private fun clearInput() { input.fill(0.toChar()); length = 0; mutableState.update { it.copy(digits = 0) } }
    override fun onCleared() { input.fill(0.toChar()); firstPin?.fill(0.toChar()); unlockedChannel.close() }
}
