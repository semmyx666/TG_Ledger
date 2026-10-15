package com.semmyx.ledger.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.ActivationProblem
import com.semmyx.ledger.domain.PremiumState

fun activationRequest(state: PremiumState): String =
    "TGREQ1|${state.developerInstallationId}|${state.developerKeyId}|${state.developerProductId}"

@Composable
fun DeveloperActivationPanel(state: PremiumState, code: String, onCodeChange: (String) -> Unit,
    busy: Boolean, onActivate: () -> Unit, onCopy: (String) -> Unit, onSupport: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.developer_purchase_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.developer_purchase_body), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.installation_id_label) + ": " + state.developerInstallationId, modifier = Modifier.testTag("installationId"))
        Text(stringResource(R.string.activation_key_id, state.developerKeyId.ifEmpty { "—" }),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("activationKeyId"))
        if (!state.developerPurchaseEnabled) {
            Text(stringResource(R.string.activation_not_configured), color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("activationNotConfigured"))
        } else {
            OutlinedButton(onClick = { onCopy(activationRequest(state)) }, modifier = Modifier.fillMaxWidth().testTag("copyActivationRequest")) {
                Text(stringResource(R.string.activation_copy_request))
            }
            OutlinedTextField(value = code, onValueChange = { onCodeChange(it.take(16384)) },
                modifier = Modifier.fillMaxWidth().testTag("activationCodeField"), minLines = 2, maxLines = 4,
                label = { Text(stringResource(R.string.activation_code_label)) },
                supportingText = { Text(stringResource(R.string.activation_code_hint)) })
            state.activationProblem?.let {
                Text(stringResource(when (it) {
                    ActivationProblem.NOT_CONFIGURED -> R.string.activation_not_configured
                    ActivationProblem.KEY_MISMATCH -> R.string.activation_key_mismatch
                    ActivationProblem.MALFORMED -> R.string.activation_malformed
                    ActivationProblem.SIGNATURE -> R.string.activation_signature
                    ActivationProblem.DEVICE -> R.string.activation_device
                    ActivationProblem.PRODUCT -> R.string.activation_product
                    ActivationProblem.CLOCK -> R.string.activation_clock
                    ActivationProblem.EXPIRED -> R.string.activation_expired
                    ActivationProblem.STORAGE -> R.string.activation_storage
                }), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("activationProblem"))
            }
            Button(onClick = onActivate, enabled = code.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth().testTag("activateDeveloperCode")) {
                Text(stringResource(if (busy) R.string.activation_checking else R.string.activation_code_button))
            }
        }
        OutlinedButton(onClick = onSupport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.developer_purchase_button)) }
        Text(stringResource(R.string.developer_purchase_note), style = MaterialTheme.typography.bodySmall)
    }
}
