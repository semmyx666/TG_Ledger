package com.semmyx.ledger.cat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.AppLanguage

@Composable
fun CatAssistantCard(language: AppLanguage, listening: Boolean, lastReply: String?, onTalk: () -> Unit,
    modifier: Modifier = Modifier, skin: String = "gray", animations: Boolean = true,
    onText: (String) -> Unit = {}, balanceMinor: Long = 0) {
    var text by remember { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    val label = stringResource(R.string.voice_assistant_button)
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.mascot_card_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            LiveCatHost(balanceMinor, listening, skin, Modifier.fillMaxWidth().height(224.dp)
                .clickable(enabled = !listening, onClickLabel = label, role = Role.Button, onClick = onTalk)
                .semantics { contentDescription = label }.testTag("voiceAssistantButton"), motionEnabled = animations)
            Text(if (listening) stringResource(R.string.voice_assistant_listening) else lastReply ?: stringResource(R.string.cat_tap_hint),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { typing = !typing }) { Text(stringResource(R.string.assistant_text_hint)) }
            AnimatedVisibility(typing, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    OutlinedTextField(text, { text = it.take(500) }, modifier = Modifier.fillMaxWidth().testTag("assistantText"),
                        label = { Text(stringResource(R.string.assistant_text_hint)) }, singleLine = true)
                    TextButton(enabled = text.isNotBlank() && !listening, onClick = { val value = text; text = ""; onText(value) }) {
                        Text(stringResource(R.string.assistant_send))
                    }
                }
            }
        }
    }
}
