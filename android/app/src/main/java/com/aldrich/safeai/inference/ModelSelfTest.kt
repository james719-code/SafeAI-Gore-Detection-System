package com.aldrich.safeai.inference

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color

object ModelSelfTest {

    data class Result(
        val isReady: Boolean,
        val modelBytes: Int,
        val errorMessage: String? = null,
    )

    const val MODEL_ASSET_NAME: String = "gore_classifier.tflite"
    const val LABELS_ASSET_NAME: String = "labels.txt"

    fun run(context: Context): Result {
        return runCatching {
            val modelBytes = context.assets.open(MODEL_ASSET_NAME).use { input ->
                input.readBytes()
            }
            require(modelBytes.isNotEmpty()) {
                "Model file is empty. Export a trained TFLite model first."
            }

            val labels = context.assets.open(LABELS_ASSET_NAME).bufferedReader().useLines { lines ->
                lines.map { it.trim() }.filter { it.isNotBlank() }.toList()
            }
            require(labels.isNotEmpty()) {
                "Labels file is empty."
            }

            GoreClassifier(context, MODEL_ASSET_NAME, LABELS_ASSET_NAME).use { classifier ->
                val probeBitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
                probeBitmap.eraseColor(Color.rgb(130, 200, 255))
                classifier.classify(probeBitmap, 0.5f)
                probeBitmap.recycle()
            }

            Result(
                isReady = true,
                modelBytes = modelBytes.size,
            )
        }.getOrElse { error ->
            Result(
                isReady = false,
                modelBytes = 0,
                errorMessage = error.message ?: "Unknown model error",
            )
        }
    }
}
