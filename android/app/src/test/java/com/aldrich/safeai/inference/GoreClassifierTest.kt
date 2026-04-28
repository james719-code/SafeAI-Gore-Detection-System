package com.aldrich.safeai.inference

import org.junit.Assert.assertEquals
import org.junit.Test

class GoreClassifierTest {

    @Test
    fun modelInputChannelValueKeepsPixelScaleExpectedByModel() {
        assertEquals(0f, GoreClassifier.modelInputChannelValue(0), 0f)
        assertEquals(128f, GoreClassifier.modelInputChannelValue(128), 0f)
        assertEquals(255f, GoreClassifier.modelInputChannelValue(255), 0f)
    }
}
