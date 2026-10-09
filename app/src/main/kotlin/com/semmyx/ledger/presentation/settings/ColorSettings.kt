package com.semmyx.ledger.presentation.settings

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.presentation.common.*
import com.semmyx.ledger.presentation.theme.LocalAppearance
import com.semmyx.ledger.presentation.theme.readableOn
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorSettings(preferences: UserPreferences, onColor: (String, String?) -> Unit, onReset: () -> Unit) {
    var editing by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(true) }
    var type by remember { mutableStateOf(EntryType.EXPENSE) }
    val palette = LocalAppearance.current
    ContentCard {
        Text(stringResource(R.string.colors_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.colors_explanation), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(if (expanded) R.string.colors_collapse else R.string.colors_edit))
        }
        if (expanded) {
            ColorPreferences.roles.keys.forEach { role ->
                ColorSettingRow(stringResource(roleLabel(role)), palette.color(role), palette.hex(role)) { editing = role }
            }
            HorizontalDivider()
            Text(stringResource(R.string.colors_categories), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EntryType.entries.forEach { choice ->
                    val tint = palette.type(choice)
                    FilterChip(selected = choice == type, onClick = { type = choice },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = tint.copy(alpha = .17f), selectedLabelColor = tint),
                        label = { Text(stringResource(if (choice == EntryType.INCOME) R.string.income else R.string.expenses)) })
                }
            }
            Category.entries.forEach { category ->
                val key = ColorPreferences.categoryKey(category, type)
                ColorSettingRow(stringResource(category.label()), palette.color(key), palette.hex(key)) { editing = key }
            }
            TextButton(onClick = onReset) { Text(stringResource(R.string.colors_reset_all)) }
        }
    }
    editing?.let { key ->
        val label = if (key in ColorPreferences.roles) stringResource(roleLabel(key)) else
            stringResource(Category.entries.first { key.endsWith(":" + it.name) }.label())
        ColorPicker(label, palette.hex(key),
            onDismiss = { editing = null }, onApply = { value -> onColor(key, value); editing = null },
            onReset = { onColor(key, null); editing = null })
    }
}

@Composable
private fun ColorSettingRow(label: String, color: Color, hex: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(32.dp).background(color, CircleShape))
        Text(label, Modifier.weight(1f))
        Text(hex, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(title: String, initialHex: String, onDismiss: () -> Unit, onApply: (String) -> Unit, onReset: () -> Unit) {
    val hsv = remember(initialHex) { FloatArray(3).also { AndroidColor.colorToHSV(AndroidColor.parseColor(initialHex), it) } }
    var hue by remember(initialHex) { mutableFloatStateOf(hsv[0]) }
    var saturation by remember(initialHex) { mutableFloatStateOf(hsv[1]) }
    var brightness by remember(initialHex) { mutableFloatStateOf(hsv[2]) }
    var hex by remember(initialHex) { mutableStateOf(initialHex) }
    val valid = ColorPreferences.normalizeHex(hex)
    val preview = Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)))
    fun updateHex() { hex = String.format(Locale.ROOT, "#%06X", AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)) and 0xFFFFFF) }
    fun loadHex(value: String) {
        hex = value
        ColorPreferences.normalizeHex(value)?.let { normalized ->
            val values = FloatArray(3)
            AndroidColor.colorToHSV(AndroidColor.parseColor(normalized), values)
            hue = values[0]; saturation = values[1]; brightness = values[2]
        }
    }
    val background = MaterialTheme.colorScheme.background
    val contrast = (maxOf(preview.luminance(), background.luminance()) + .05f) /
        (minOf(preview.luminance(), background.luminance()) + .05f)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = preview, contentColor = readableOn(preview), shape = MaterialTheme.shapes.medium) {
                    Box(Modifier.fillMaxWidth().height(58.dp), contentAlignment = Alignment.Center) { Text(valid ?: hex) }
                }
                OutlinedTextField(value = hex, onValueChange = { if (it.length <= 7) loadHex(it) },
                    label = { Text("HEX · #RRGGBB") }, singleLine = true, isError = valid == null, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.color_hue)); Slider(value = hue, onValueChange = { hue = it; updateHex() }, valueRange = 0f..360f)
                Text(stringResource(R.string.color_saturation)); Slider(value = saturation, onValueChange = { saturation = it; updateHex() })
                Text(stringResource(R.string.color_brightness)); Slider(value = brightness, onValueChange = { brightness = it; updateHex() })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("#6FAEFF", "#48D597", "#FF647C", "#B18CFF", "#FFB95A", "#00D0DA", "#FFFFFF", "#000000").forEach { value ->
                        Box(Modifier.size(32.dp).background(Color(AndroidColor.parseColor(value)), CircleShape).clickable { loadHex(value) })
                    }
                }
                if (valid == null) Text(stringResource(R.string.color_invalid), color = MaterialTheme.colorScheme.error)
                else if (contrast < 3f) Text(stringResource(R.string.color_contrast_warning), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onReset) { Text(stringResource(R.string.color_reset)) }
            }
        }, confirmButton = { TextButton(enabled = valid != null, onClick = { valid?.let(onApply) }) { Text(stringResource(R.string.color_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

private fun roleLabel(role: String): Int = when (role) {
    "accent" -> R.string.color_accent
    "background" -> R.string.color_background
    "surface" -> R.string.color_surface
    "text" -> R.string.color_text
    "muted" -> R.string.color_muted
    "income" -> R.string.income
    "expense" -> R.string.expenses
    "chartTop" -> R.string.color_chart_top
    "chartBottom" -> R.string.color_chart_bottom
    "error" -> R.string.color_error
    "grid" -> R.string.color_grid
    else -> R.string.color_icons
}
