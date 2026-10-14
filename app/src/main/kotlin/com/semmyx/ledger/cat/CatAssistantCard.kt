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
import androidx.compose.ui.platform.LocalDensity
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
    onText: (String) -> Unit = {}, balanceMinor: Long = 0, locked: Boolean = false) {
    var text by remember { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    val label = stringResource(R.string.voice_assistant_button)
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                @Composable fun Cat(modifier: Modifier) {
                    LiveCatHost(balanceMinor, listening, skin, modifier
                        .clickable(enabled = !listening, onClickLabel = label, role = Role.Button, onClick = onTalk)
                        .semantics { contentDescription = label }.testTag("voiceAssistantButton"), motionEnabled = animations)
                }
                @Composable fun Caption(modifier: Modifier) {
                    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.mascot_card_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(if (locked) stringResource(R.string.cat_locked_hint) else if (listening) stringResource(R.string.voice_assistant_listening) else lastReply ?: stringResource(R.string.cat_tap_hint),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!locked) TextButton(onClick = { typing = !typing }) { Text(stringResource(R.string.assistant_write_short)) }
                    }
                }
                if (LocalDensity.current.fontScale > 1.5f || maxWidth < 250.dp) Column {
                    Cat(Modifier.fillMaxWidth().height(104.dp))
                    Caption(Modifier.fillMaxWidth())
                } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Cat(Modifier.size(104.dp))
                    Caption(Modifier.weight(1f))
                }
            }
            AnimatedVisibility(typing && !locked, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
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
