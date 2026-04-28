package com.aldrich.safeai.inference

object GoreVisualHeuristic {
    fun isLikelyGore(width: Int, height: Int, pixels: IntArray): Boolean {
        if (width <= 0 || height <= 0 || pixels.isEmpty()) {
            return false
        }

        val usablePixelCount = minOf(width * height, pixels.size)
        if (hasEnoughBloodColoredPixels(pixels, 0, usablePixelCount)) {
            return true
        }

        val columns = 4
        val rows = 4
        for (row in 0 until rows) {
            val top = row * height / rows
            val bottom = (row + 1) * height / rows
            for (column in 0 until columns) {
                val left = column * width / columns
                val right = (column + 1) * width / columns
                if (hasEnoughBloodColoredPixelsInTile(width, pixels, left, top, right, bottom)) {
                    return true
                }
            }
        }

        return false
    }

    private fun hasEnoughBloodColoredPixels(
        pixels: IntArray,
        start: Int,
        endExclusive: Int,
    ): Boolean {
        val pixelCount = (endExclusive - start).coerceAtLeast(0)
        if (pixelCount < MIN_TILE_PIXELS) {
            return false
        }

        var bloodPixels = 0
        for (index in start until endExclusive) {
            if (isBloodColoredPixel(pixels[index])) {
                bloodPixels++
            }
        }

        return bloodPixels >= MIN_FULL_BLOOD_PIXELS &&
            bloodPixels.toFloat() / pixelCount.toFloat() >= MIN_FULL_BLOOD_RATIO
    }

    private fun hasEnoughBloodColoredPixelsInTile(
        width: Int,
        pixels: IntArray,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): Boolean {
        val tileWidth = (right - left).coerceAtLeast(0)
        val tileHeight = (bottom - top).coerceAtLeast(0)
        val tilePixelCount = tileWidth * tileHeight
        if (tilePixelCount < MIN_TILE_PIXELS) {
            return false
        }

        var bloodPixels = 0
        for (y in top until bottom) {
            val rowOffset = y * width
            for (x in left until right) {
                val index = rowOffset + x
                if (index in pixels.indices && isBloodColoredPixel(pixels[index])) {
                    bloodPixels++
                }
            }
        }

        return bloodPixels >= MIN_TILE_BLOOD_PIXELS &&
            bloodPixels.toFloat() / tilePixelCount.toFloat() >= MIN_TILE_BLOOD_RATIO
    }

    private fun isBloodColoredPixel(pixel: Int): Boolean {
        val red = (pixel shr 16) and 0xFF
        val green = (pixel shr 8) and 0xFF
        val blue = pixel and 0xFF
        val maxChannel = maxOf(red, green, blue)
        val minChannel = minOf(red, green, blue)
        val saturation = maxChannel - minChannel

        return red >= 80 &&
            saturation >= 45 &&
            green <= 110 &&
            blue <= 115 &&
            red > green * 1.25f &&
            red > blue * 1.15f
    }

    private const val MIN_TILE_PIXELS = 100
    private const val MIN_FULL_BLOOD_PIXELS = 350
    private const val MIN_FULL_BLOOD_RATIO = 0.045f
    private const val MIN_TILE_BLOOD_PIXELS = 180
    private const val MIN_TILE_BLOOD_RATIO = 0.09f
}
