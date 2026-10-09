package com.semmyx.ledger.presentation.premium

import kotlin.math.*

/** One texture, continuously deformed vertices. No eye-open/closed resource switching. */
class CatFaceMesh(val columns: Int = 80, val rows: Int = 80) {
    val vertices = FloatArray((columns + 1) * (rows + 1) * 2)
    private val xs = FloatArray(vertices.size / 2)
    private val ys = FloatArray(xs.size)
    private val head = FloatArray(xs.size)
    private val depth = FloatArray(xs.size)
    private val lids = FloatArray(xs.size)
    private val body = FloatArray(xs.size)
    init {
        var i = 0
        for (row in 0..rows) for (column in 0..columns) {
            val x = column.toFloat() / columns; val y = row.toFloat() / rows
            xs[i] = x; ys[i] = y
            head[i] = exp(-(((x - .5f) / .35f).toDouble().pow(4) + ((y - .37f) / .30f).toDouble().pow(4))).toFloat()
            depth[i] = .10f * exp(-(((x - .5f) / .26f).toDouble().pow(2) + ((y - .42f) / .25f).toDouble().pow(2))).toFloat()
            // Actual eye landmarks measured on cat_amber_base.webp, in normalized texture coordinates.
            val eyeY = .396f
            val left = exp(-(((x - .404f) / .054f).toDouble().pow(4) + ((y - eyeY) / .042f).toDouble().pow(4))).toFloat()
            val right = exp(-(((x - .611f) / .054f).toDouble().pow(4) + ((y - eyeY) / .042f).toDouble().pow(4))).toFloat()
            lids[i] = -(y - eyeY) * maxOf(left, right) * .98f
            body[i] = exp(-(((x - .5f) / .38f).toDouble().pow(4) + ((y - .72f) / .25f).toDouble().pow(4))).toFloat()
            i++
        }
    }
    fun update(pose: CatPose, textureWidth: Int, textureHeight: Int): FloatArray {
        val yaw = pose.yawDegrees.coerceIn(-18f, 18f) * PI.toFloat() / 180f
        val pitch = pose.pitchDegrees.coerceIn(-10f, 10f) * PI.toFloat() / 180f
        val cosine = cos(yaw); val sine = sin(yaw)
        val blink = pose.blink.coerceIn(0f, 1f)
        val expression = (pose.pleased * .045f).coerceIn(0f, .045f)
        for (i in xs.indices) {
            val x = xs[i]; val y = ys[i]
            val h = head[i]
            val dx = h * ((x - .5f) * (cosine - 1) + depth[i] * sine)
            val dy = h * depth[i] * sin(pitch)
            // Upper/lower fur regions move together around the iris. The eye aperture closes continuously.
            val lid = lids[i] * (blink + expression * (1 - blink))
            val breathing = pose.breath * .010f * body[i]
            vertices[i * 2] = (x + dx + (x - .5f) * breathing) * textureWidth
            vertices[i * 2 + 1] = (y + dy + lid - breathing * .25f) * textureHeight
        }
        return vertices
    }
}
