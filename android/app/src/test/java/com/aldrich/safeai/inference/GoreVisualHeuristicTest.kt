package com.aldrich.safeai.inference

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoreVisualHeuristicTest {

    @Test
    fun detectsLargeBloodColoredRegion() {
        val width = 100
        val height = 100
        val pixels = IntArray(width * height) { rgb(245, 245, 245) }
        for (y in 35 until 75) {
            for (x in 35 until 75) {
                pixels[y * width + x] = rgb(125, 18, 15)
            }
        }

        assertTrue(GoreVisualHeuristic.isLikelyGore(width, height, pixels))
    }

    @Test
    fun ignoresSmallRedUiAccent() {
        val width = 100
        val height = 100
        val pixels = IntArray(width * height) { rgb(245, 245, 245) }
        for (y in 4 until 10) {
            for (x in 4 until 24) {
                pixels[y * width + x] = rgb(210, 28, 34)
            }
        }

        assertFalse(GoreVisualHeuristic.isLikelyGore(width, height, pixels))
    }

    private fun rgb(red: Int, green: Int, blue: Int): Int {
        return (red shl 16) or (green shl 8) or blue
    }
}
