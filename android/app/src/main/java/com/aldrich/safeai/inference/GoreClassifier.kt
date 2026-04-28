package com.aldrich.safeai.inference

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GoreClassifier(
    context: Context,
    private val modelAssetName: String = "gore_classifier.tflite",
    private val labelsAssetName: String = "labels.txt",
) : AutoCloseable {

    private enum class OutputMode {
        SINGLE_PROBABILITY,
        TWO_CLASS_PROBABILITIES,
    }

    data class Prediction(
        val label: String,
        val goreProbability: Float,
        val nonGoreProbability: Float,
        val threshold: Float,
    )

    private val interpreter: Interpreter
    private val labels: List<String>
    private val inputSize: Int
    private val inputBuffer: ByteBuffer
    private val outputMode: OutputMode
    private val singleOutputBuffer = Array(1) { FloatArray(1) }
    private val dualOutputBuffer = Array(1) { FloatArray(2) }
    private val goreClassIndex: Int
    private val classOneRepresentsGore: Boolean
    private val classZeroRepresentsGore: Boolean

    init {
        val modelBuffer = loadModelBuffer(context, modelAssetName)
        interpreter = Interpreter(modelBuffer, Interpreter.Options().apply { setNumThreads(4) })

        labels = loadLabels(context, labelsAssetName)

        val inputShape = interpreter.getInputTensor(0).shape()
        require(inputShape.size == 4) {
            "Unexpected model input shape: ${inputShape.contentToString()}"
        }
        require(inputShape[3] == 3) {
            "Model must accept 3-channel RGB input."
        }

        val outputShape = interpreter.getOutputTensor(0).shape()
        val outputLastDimension = outputShape.lastOrNull() ?: 1
        outputMode = when (outputLastDimension) {
            1 -> OutputMode.SINGLE_PROBABILITY
            2 -> OutputMode.TWO_CLASS_PROBABILITIES
            else -> {
                throw IllegalArgumentException(
                    "Unsupported model output shape: ${outputShape.contentToString()}"
                )
            }
        }

        goreClassIndex = resolveGoreClassIndex(labels)
        classOneRepresentsGore = goreClassIndex == 1
        classZeroRepresentsGore = goreClassIndex == 0

        inputSize = inputShape[1]
        inputBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
            .order(ByteOrder.nativeOrder())
    }

    fun classify(bitmap: Bitmap, threshold: Float = 0.5f): Prediction {
        require(threshold in 0f..1f) { "Threshold must be between 0 and 1." }

        preprocess(bitmap)
        val goreProbability = when (outputMode) {
            OutputMode.SINGLE_PROBABILITY -> {
                singleOutputBuffer[0][0] = 0f
                interpreter.run(inputBuffer, singleOutputBuffer)

                val classOneProbability = singleOutputBuffer[0][0].coerceIn(0f, 1f)
                when {
                    classOneRepresentsGore -> classOneProbability
                    classZeroRepresentsGore -> 1f - classOneProbability
                    else -> classOneProbability
                }
            }

            OutputMode.TWO_CLASS_PROBABILITIES -> {
                dualOutputBuffer[0][0] = 0f
                dualOutputBuffer[0][1] = 0f
                interpreter.run(inputBuffer, dualOutputBuffer)

                val probability = dualOutputBuffer[0].getOrNull(goreClassIndex)
                    ?: dualOutputBuffer[0].getOrNull(1)
                    ?: dualOutputBuffer[0][0]
                probability.coerceIn(0f, 1f)
            }
        }

        val nonGoreProbability = 1f - goreProbability
        val predictedLabel = if (goreProbability >= threshold) "gore" else "non_gore"

        return Prediction(
            label = predictedLabel,
            goreProbability = goreProbability,
            nonGoreProbability = nonGoreProbability,
            threshold = threshold,
        )
    }

    override fun close() {
        interpreter.close()
    }

    private fun preprocess(bitmap: Bitmap) {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val pixels = IntArray(inputSize * inputSize)
        scaledBitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        inputBuffer.rewind()
        for (pixel in pixels) {
            val red = modelInputChannelValue((pixel shr 16) and 0xFF)
            val green = modelInputChannelValue((pixel shr 8) and 0xFF)
            val blue = modelInputChannelValue(pixel and 0xFF)

            inputBuffer.putFloat(red)
            inputBuffer.putFloat(green)
            inputBuffer.putFloat(blue)
        }
    }

    private fun loadModelBuffer(context: Context, assetName: String): ByteBuffer {
        val modelBytes = context.assets.open(assetName).use { it.readBytes() }
        require(modelBytes.isNotEmpty()) {
            "Model asset $assetName is empty."
        }
        return ByteBuffer.allocateDirect(modelBytes.size)
            .order(ByteOrder.nativeOrder())
            .apply {
                put(modelBytes)
                rewind()
            }
    }

    private fun loadLabels(context: Context, assetName: String): List<String> {
        return runCatching {
            context.assets.open(assetName).bufferedReader().useLines { lines ->
                lines.map { it.trim() }.filter { it.isNotBlank() }.toList()
            }
        }.getOrElse {
            listOf("gore", "non_gore")
        }.ifEmpty {
            listOf("gore", "non_gore")
        }
    }

    private fun resolveGoreClassIndex(labels: List<String>): Int {
        val explicitGoreIndex = labels.indexOfFirst { isGoreLabel(it) }
        if (explicitGoreIndex >= 0) {
            return explicitGoreIndex
        }

        return if (labels.size > 1) 1 else 0
    }

    private fun isGoreLabel(label: String): Boolean {
        val normalized = label.trim().lowercase().replace('-', '_').replace(' ', '_')
        return normalized == "gore"
    }

    internal companion object {
        fun modelInputChannelValue(channel: Int): Float = channel.coerceIn(0, 255).toFloat()
    }
}
