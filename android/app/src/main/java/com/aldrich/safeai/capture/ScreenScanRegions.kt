package com.aldrich.safeai.capture

import kotlin.math.roundToInt

data class ScreenScanRegion(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
) {
    fun covers(width: Int, height: Int): Boolean {
        return left == 0 && top == 0 && this.width == width && this.height == height
    }
}

object ScreenScanRegions {
    fun create(width: Int, height: Int): List<ScreenScanRegion> {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val regions = linkedSetOf(ScreenScanRegion(0, 0, safeWidth, safeHeight))
        val tileSize = minOf(safeWidth, safeHeight)

        if (safeHeight > safeWidth) {
            addVerticalTiles(regions, safeWidth, safeHeight, tileSize)
        } else if (safeWidth > safeHeight) {
            addHorizontalTiles(regions, safeWidth, safeHeight, tileSize)
        }

        return regions.toList()
    }

    private fun addVerticalTiles(
        regions: MutableSet<ScreenScanRegion>,
        width: Int,
        height: Int,
        tileSize: Int,
    ) {
        val maxTop = height - tileSize
        val step = (tileSize * TILE_STEP_RATIO).roundToInt().coerceAtLeast(1)
        var top = 0
        while (top < maxTop) {
            regions.add(ScreenScanRegion(0, top, width, tileSize))
            top += step
        }
        regions.add(ScreenScanRegion(0, maxTop, width, tileSize))
    }

    private fun addHorizontalTiles(
        regions: MutableSet<ScreenScanRegion>,
        width: Int,
        height: Int,
        tileSize: Int,
    ) {
        val maxLeft = width - tileSize
        val step = (tileSize * TILE_STEP_RATIO).roundToInt().coerceAtLeast(1)
        var left = 0
        while (left < maxLeft) {
            regions.add(ScreenScanRegion(left, 0, tileSize, height))
            left += step
        }
        regions.add(ScreenScanRegion(maxLeft, 0, tileSize, height))
    }

    private const val TILE_STEP_RATIO = 0.55f
}
