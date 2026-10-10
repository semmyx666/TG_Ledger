package com.semmyx.ledger.support

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.semmyx.ledger.BuildConfig
import com.semmyx.ledger.R
import com.semmyx.ledger.common.ContentCard
import com.semmyx.ledger.common.LedgerBottomBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onNavigate: (Int) -> Unit, onSupport: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.help)) }) }, bottomBar = { LedgerBottomBar(2, onNavigate) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { ContentCard {
                Text(stringResource(R.string.help_heading), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.help_intro))
                Button(onClick = onSupport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.contact_telegram)) }
            } }
            item { ContentCard {
                Text(stringResource(R.string.help_entries_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.help_entries_body))
            } }
            item { ContentCard {
                Text(stringResource(R.string.help_privacy_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.help_privacy_body))
            } }
            item { ContentCard {
                Text(stringResource(R.string.help_security_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.help_security_body))
            } }
            item { ContentCard {
                Text(stringResource(R.string.help_premium_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.help_premium_body))
            } }
            item { Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
