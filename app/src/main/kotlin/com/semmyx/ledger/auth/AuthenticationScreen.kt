package com.semmyx.ledger.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.semmyx.ledger.R

@Composable
fun AuthenticationScreen(
    state: AuthState,
    biometrics: Boolean,
    onDigit: (Int) -> Unit,
    onErase: () -> Unit,
    onStartOver: () -> Unit,
    onBiometric: () -> Unit,
    onSupport: () -> Unit,
) {
    val heading = when { !state.setup -> R.string.unlock_title; state.confirming -> R.string.confirm_pin; else -> R.string.create_pin }
    val description = stringResource(R.string.pin_digit_count, state.digits)
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(8.dp))
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(Icons.Default.Lock, null, Modifier.padding(20.dp).size(32.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(stringResource(heading), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(if (state.setup) R.string.pin_setup_subtitle else R.string.pin_unlock_subtitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.padding(vertical = 8.dp).testTag("pinDots").clearAndSetSemantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(6) { index ->
                Box(Modifier.size(16.dp).clip(CircleShape).background(if (index < state.digits) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
            }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.lockoutSeconds > 0) Text(stringResource(R.string.pin_locked, state.lockoutSeconds), color = MaterialTheme.colorScheme.error)
        state.error?.let { error ->
            val res = when (error) {
                AuthError.INCORRECT -> R.string.pin_incorrect; AuthError.MISMATCH -> R.string.pin_mismatch
                AuthError.UNAVAILABLE -> R.string.security_error; AuthError.BIOMETRIC -> R.string.biometric_failed
            }
            Text(stringResource(res), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        // Native buttons provide keyboard / switch access and never expose PIN text to autofill.
        Column(Modifier.widthIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in 0..2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (column in 1..3) {
                        val digit = row * 3 + column
                        PinKey(digit, !state.busy && state.lockoutSeconds == 0L, { onDigit(digit) }, Modifier.weight(1f))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (biometrics && !state.setup) IconButton(onClick = onBiometric, enabled = !state.busy, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.Fingerprint, stringResource(R.string.unlock_biometric), Modifier.size(28.dp))
                    }
                }
                PinKey(0, !state.busy && state.lockoutSeconds == 0L, { onDigit(0) }, Modifier.weight(1f))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    IconButton(onClick = onErase, enabled = !state.busy && state.digits > 0, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Backspace, stringResource(R.string.erase_digit))
                    }
                }
            }
        }
        if (state.confirming) TextButton(onClick = onStartOver, enabled = !state.busy) { Text(stringResource(R.string.start_over)) }
        TextButton(onClick = onSupport) { Text(stringResource(R.string.need_help)) }
        Text(stringResource(R.string.pin_recovery_notice), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PinKey(value: Int, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 64.dp).testTag("pinKey$value"),
        shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(8.dp)) {
        Text(value.toString(), style = MaterialTheme.typography.headlineMedium)
    }
}
