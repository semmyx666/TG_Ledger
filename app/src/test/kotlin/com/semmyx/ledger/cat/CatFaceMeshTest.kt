package com.semmyx.ledger.cat

import org.junit.Assert.*
import org.junit.Test

class CatFaceMeshTest {
    @Test fun blinkMovesEyeVerticesWithoutChangingTextureOrTopology() {
        val mesh = CatFaceMesh()
        val open = mesh.update(CatPose(), 1024, 1024).copyOf()
        val half = mesh.update(CatPose(blink = .5f), 1024, 1024).copyOf()
        val shut = mesh.update(CatPose(blink = 1f), 1024, 1024).copyOf()
        val eyeColumn = 32
        val above = (30 * 81 + eyeColumn) * 2 + 1
        val below = (34 * 81 + eyeColumn) * 2 + 1
        assertTrue(half[above] > open[above]); assertTrue(shut[above] > half[above])
        assertTrue(half[below] < open[below]); assertTrue(shut[below] < half[below])
        assertTrue(shut[below] - shut[above] < open[below] - open[above])
        assertTrue(shut.all { it.isFinite() })
        assertEquals(open.size, shut.size)
    }
    @Test fun yawInterpolatesContinuousPositionsRatherThanDiscreteFrames() {
        val mesh = CatFaceMesh()
        val left = mesh.update(CatPose(yawDegrees = -10f), 1024, 1024).copyOf()
        val middle = mesh.update(CatPose(yawDegrees = 0f), 1024, 1024).copyOf()
        val right = mesh.update(CatPose(yawDegrees = 10f), 1024, 1024).copyOf()
        val nose = (40 * 81 + 40) * 2
        assertTrue(left[nose] < middle[nose]); assertTrue(right[nose] > middle[nose])
        val fine = mesh.update(CatPose(yawDegrees = .1f), 1024, 1024).copyOf()
        assertTrue(kotlin.math.abs(fine[nose] - middle[nose]) < 1f)
    }
}
