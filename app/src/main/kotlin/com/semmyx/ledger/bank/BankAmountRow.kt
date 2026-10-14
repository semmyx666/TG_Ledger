package com.semmyx.ledger.bank

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/** Never let a right-hand amount squeeze the bank/category name into a few letters. */
@Composable
fun BankAmountRow(label: String, amount: String, color: Color? = null,
    onClick: (() -> Unit)? = null, labelTag: String = "") {
    val labelStyle = MaterialTheme.typography.bodyMedium
    val amountStyle = MaterialTheme.typography.titleSmall
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth().then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)).padding(vertical = 4.dp)) {
        val available = with(LocalDensity.current) { maxWidth.toPx() }
        val desired = measurer.measure(label, style = labelStyle, softWrap = false).size.width +
            measurer.measure(amount, style = amountStyle, softWrap = false).size.width +
            with(LocalDensity.current) { (if (color == null) 12.dp else 44.dp).toPx() }
        @Composable fun Label(modifier: Modifier) {
            Text(label, modifier.testTag(labelTag), style = labelStyle)
        }
        @Composable fun Dot() { color?.let { Box(Modifier.size(18.dp).background(it,CircleShape)) } }
        if (desired > available) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dot(); Label(Modifier.weight(1f))
            }
            Text(amount, modifier = Modifier.fillMaxWidth(), style = amountStyle)
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dot(); Label(Modifier.weight(1f)); Text(amount, style = amountStyle)
        }
    }
}
