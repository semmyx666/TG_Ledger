package com.semmyx.ledger.cat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import com.semmyx.ledger.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A single decoded bitmap + a real-time mesh. Blink/yaw never change the resource. */
@Composable
fun CatMeshRenderer(pose: CatPose, modifier: Modifier, skin: String = "gray") {
    val app = LocalContext.current.applicationContext
    // Decode each appearance once. Frame updates and blinking only deform the shared mesh.
    val textures by produceState<Pair<Bitmap?, Bitmap?>?>(initialValue = null, key1 = app) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeResource(app.resources, R.drawable.cat_amber_base) to
                BitmapFactory.decodeResource(app.resources, R.drawable.cat_amber_sky)
        }
    }
    val bitmap = if (skin == "sky") textures?.second ?: textures?.first else textures?.first
    val mesh = remember { CatFaceMesh() }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG) }
    Canvas(modifier) {
        val image = bitmap ?: return@Canvas
        val scale = minOf(size.width / image.width, size.height / image.height)
        val left = (size.width - image.width * scale) / 2f
        val top = (size.height - image.height * scale) / 2f
        val verts = mesh.update(pose, image.width, image.height)
        drawIntoCanvas { wrapper ->
            val native = wrapper.nativeCanvas
            val saved = native.save()
            native.translate(left, top)
            native.scale(scale, scale)
            native.drawBitmapMesh(image, mesh.columns, mesh.rows, verts, 0, null, 0, paint)
            native.restoreToCount(saved)
        }
    }
}
