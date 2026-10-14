package com.semmyx.ledger.settings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.semmyx.ledger.R

@Composable
fun RestorePurchasesAction(enabled: Boolean, onClick: () -> Unit) {
    // A dedicated full-width line, never the squeezed last child of a button row.
    TextButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag("restorePurchases")) {
        Text(stringResource(R.string.restore_purchases), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}
