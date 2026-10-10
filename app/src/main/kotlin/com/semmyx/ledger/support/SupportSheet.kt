package com.semmyx.ledger.support

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.semmyx.ledger.R
import com.semmyx.ledger.common.ContentCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportSheet(onDismiss: () -> Unit, onContact: () -> Unit) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.here_to_help), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.support_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
            ContentCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.lead_developer), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.support_handle), style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(modifier = Modifier.testTag("copySupport"), onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.support), SupportNavigator.HANDLE))
                        copied = true
                    }) { Icon(Icons.Default.ContentCopy, stringResource(R.string.copy_handle)) }
                }
                if (copied) Text(stringResource(R.string.handle_copied), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = onContact, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("contactTelegram")) {
                Icon(Icons.AutoMirrored.Filled.Send, null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.contact_telegram))
            }
            Text(stringResource(R.string.support_privacy), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
