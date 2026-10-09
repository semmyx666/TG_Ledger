package com.semmyx.ledger.platform

import android.app.Activity
import android.view.Display
import kotlin.math.abs

object HighRefreshRate {
    fun supported(display: Display?): Set<Int> {
        if (display == null) return setOf(60)
        val current = display.mode
        val modes = display.supportedModes.filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        return listOf(60, 90, 120).filter { rate -> modes.any { abs(it.refreshRate - rate) <= 1.1f } }.toSet()
    }
    fun request(activity: Activity, requestedHz: Int = 0) {
        val display = activity.window.decorView.display ?: return
        val current = display.mode
        val modes = display.supportedModes.filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        val exact = modes.filter { requestedHz > 0 && abs(it.refreshRate - requestedHz) <= 1.1f }.firstOrNull()
        val selected = if (requestedHz == 0) null else (exact
            ?: modes.filter { it.refreshRate <= requestedHz + 1.1f }.maxByOrNull { it.refreshRate }
            ?: modes.minByOrNull { it.refreshRate })
        activity.window.attributes = activity.window.attributes.apply {
            // Auto releases the override back to Android, rather than forcing the maximum forever.
            preferredDisplayModeId = selected?.modeId ?: 0
            preferredRefreshRate = selected?.refreshRate ?: 0f
        }
    }
}
