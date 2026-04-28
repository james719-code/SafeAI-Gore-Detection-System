package com.aldrich.safeai.capture

import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenScanRegionsTest {

    @Test
    fun portraitScreenIncludesOverlappingImageSizedRegions() {
        val regions = ScreenScanRegions.create(width = 432, height = 768)

        assertTrue(regions.any { it.left == 0 && it.top == 0 && it.width == 432 && it.height == 768 })
        assertTrue(regions.any { it.left == 0 && it.top == 0 && it.width == 432 && it.height == 432 })
        assertTrue(regions.any { it.left == 0 && it.top > 0 && it.width == 432 && it.height == 432 })
    }
}
