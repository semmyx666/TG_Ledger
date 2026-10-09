package com.semmyx.ledger.domain

object ColorPreferences {
    val roles = linkedMapOf(
        "accent" to "#6FAEFF", "background" to "#000000", "surface" to "#1C201E",
        "text" to "#F3F5F7", "muted" to "#A3ABB5", "income" to "#48D597", "expense" to "#FF647C",
        "chartTop" to "#FF9AAC", "chartBottom" to "#E74765", "grid" to "#343B45", "icons" to "#6FAEFF", "error" to "#FF647C"
    )
    fun categoryKey(category: Category, type: EntryType): String = "category:${type.name}:${category.name}"
    fun validKey(key: String): Boolean = key in roles || Category.entries.any { category ->
        EntryType.entries.any { key == categoryKey(category, it) }
    }
    fun normalizeHex(value: String): String? {
        val cleaned = value.trim().removePrefix("#")
        return if (Regex("[0-9a-fA-F]{6}").matches(cleaned)) "#" + cleaned.uppercase() else null
    }
    fun defaultHex(key: String): String {
        roles[key]?.let { return it }
        val income = key.startsWith("category:INCOME:")
        val category = Category.entries.firstOrNull { key.endsWith(":" + it.name) } ?: return "#6FAEFF"
        val colors = if (income) listOf("#48D597", "#55BF8C", "#6BC9A5", "#37C58E", "#7DD9AB", "#19B950", "#5CAC85", "#35CE75", "#72D4A6", "#7ACC73")
            else listOf("#EF5277", "#FC7969", "#D45368", "#F78D9F", "#E54775", "#C96268", "#AB6976", "#D96570", "#F37591", "#B75668")
        return colors[category.ordinal]
    }
}
