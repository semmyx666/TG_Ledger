package com.semmyx.ledger.cat

import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.semmyx.ledger.R
import kotlinx.coroutines.isActive
import kotlin.math.*

/** Engine-neutral, stable rig channels. Realistic artwork is supplied separately, never fabricated. */
data class CatPose(val breath: Float = 0f, val blink: Float = 0f, val yawDegrees: Float = 0f,
    val pitchDegrees: Float = 0f, val pleased: Float = 0f, val serious: Float = 0f, val listening: Boolean = false)

interface CatRig {
    fun morph(name: String, weight: Float)
    fun head(yawDegrees: Float, pitchDegrees: Float)
}

/** Complete mapping from controller output to a rig; a Filament adapter implements CatRig. */
class CatRigDriver(private val rig: CatRig) {
    fun apply(pose: CatPose) {
        rig.morph("breath", pose.breath)
        rig.morph("blinkLeft", pose.blink)
        rig.morph("blinkRight", pose.blink)
        rig.morph("pleased", pose.pleased)
        rig.morph("serious", pose.serious)
        rig.morph("earsForward", if (pose.listening) 1f else .2f)
        rig.head(pose.yawDegrees, pose.pitchDegrees)
    }
}

class CatMotionController {
    private var yaw = 0f
    private var pitch = 0f
    private var happy = .5f
    private var lastTime = 0.0
    fun step(seconds: Double, balanceMinor: Long, listening: Boolean, target: Offset): CatPose {
        val dt = if (lastTime == 0.0) 1.0 / 120 else (seconds - lastTime).coerceIn(0.0, .1)
        lastTime = seconds
        val smooth = (1.0 - exp(-9.0 * dt)).toFloat()
        yaw += (target.x.coerceIn(-1f, 1f) * 18f - yaw) * smooth
        pitch += (target.y.coerceIn(-1f, 1f) * -10f - pitch) * smooth
        val destination = if (balanceMinor < 0) 0f else if (balanceMinor > 0) 1f else .5f
        happy += (destination - happy) * smooth
        val cycle = seconds % 4.7
        val blink = if (cycle < .22) sin(cycle / .22 * PI).toFloat().coerceIn(0f, 1f) else 0f
        return CatPose(((sin(seconds * 2 * PI / 3.6) + 1) / 2).toFloat(), blink, yaw, pitch,
            happy, 1f - happy, listening)
    }
}

/** Install a licensed asset-backed implementation at the composition root to enable real 3D. */
fun interface CatAssetRenderer {
    @Composable fun Render(pose: CatPose, modifier: Modifier)
}
object CatAssetRegistry {
    var renderer: CatAssetRenderer? by mutableStateOf(null)
}
object CatTouchBus {
    var target by mutableStateOf(Offset.Zero)
}

/** Observe, don't consume: ordinary taps, scrolling, editing and speech buttons still work. */
fun Modifier.trackCatTouch(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.firstOrNull { it.pressed }?.let { change ->
                CatTouchBus.target = Offset((change.position.x / size.width.coerceAtLeast(1) - .5f) * 2,
                    (change.position.y / size.height.coerceAtLeast(1) - .5f) * 2)
            }
        }
    }
}

@Composable
fun LiveCatHost(balanceMinor: Long, listening: Boolean, skin: String, modifier: Modifier, motionEnabled: Boolean = true) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val controller = remember { CatMotionController() }
    val balance by rememberUpdatedState(balanceMinor)
    val hears by rememberUpdatedState(listening)
    var pose by remember { mutableStateOf(CatPose()) }
    LaunchedEffect(lifecycle, controller, motionEnabled) {
        if (!motionEnabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) withFrameNanos { time ->
                pose = controller.step(time / 1e9, balance, hears, CatTouchBus.target)
            }
        }
    }
    val renderer = CatAssetRegistry.renderer
    if (renderer != null) renderer.Render(pose, modifier)
    else CatMeshRenderer(if (motionEnabled) pose else CatPose(pleased = if (balanceMinor > 0) 1f else 0f), modifier, skin = skin)
}
