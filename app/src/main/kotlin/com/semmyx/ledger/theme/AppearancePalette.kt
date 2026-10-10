package com.semmyx.ledger.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.semmyx.ledger.domain.*

class AppearancePalette(private val overrides: Map<String, String> = emptyMap(), private val defaults: Map<String, String> = emptyMap()) {
    fun hex(key: String): String {
        overrides[key]?.let { return it }
        if (key == "icons") overrides["accent"]?.let { return it }
        if (key.startsWith("category:INCOME:")) overrides["income"]?.let { return it }
        if (key.startsWith("category:EXPENSE:")) overrides["expense"]?.let { return it }
        if (key == "chartTop" || key == "chartBottom") overrides["expense"]?.let { return it }
        return defaults[key] ?: ColorPreferences.defaultHex(key)
    }
    fun color(key: String): Color = Color(0xFF000000L or hex(key).removePrefix("#").toLong(16))
    fun category(category: Category, income: Boolean): Color = color(ColorPreferences.categoryKey(category,
        if (income) EntryType.INCOME else EntryType.EXPENSE))
    fun type(type: EntryType): Color = color(if (type == EntryType.INCOME) "income" else "expense")
}
val LocalAppearance = staticCompositionLocalOf { AppearancePalette() }
fun readableOn(background: Color): Color = if (background.luminance() > .179f) Color.Black else Color.White
