package com.semmyx.ledger.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Cancellation is control flow, not a user-facing error. */
fun ViewModel.guarded(onError: (Exception) -> Unit, block: suspend CoroutineScope.() -> Unit): Job =
    viewModelScope.launch {
        try { block() } catch (error: Exception) {
            if (error is CancellationException) throw error
            onError(error)
        }
    }

class LedgerViewModelFactory<T : ViewModel>(private val modelClass: Class<T>, private val create: () -> T) : ViewModelProvider.Factory {
    override fun <V : ViewModel> create(modelClass: Class<V>): V {
        require(modelClass.isAssignableFrom(this.modelClass))
        return modelClass.cast(create()) ?: error("Invalid ViewModel type")
    }
}

enum class UiMessage {
    STORAGE,
    SAVED,
    DELETED,
    UNDO_EXPIRED,
    RATE_OFFLINE,
    BIOMETRIC_UNAVAILABLE,
    NAME_INVALID,
    NOT_FOUND,
    PREMIUM_ACTIVATED,
    ACTIVATION_INVALID,
}
