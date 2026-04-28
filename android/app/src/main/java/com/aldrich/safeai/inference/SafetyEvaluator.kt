package com.aldrich.safeai.inference

enum class SafetyLevel {
    SAFE,
    BLOCKED,
}

data class SafetyAssessment(
    val level: SafetyLevel,
    val goreProbability: Float,
    val threshold: Float,
)

object SafetyEvaluator {
    fun assess(goreProbability: Float, threshold: Float): SafetyAssessment {
        val clampedProbability = goreProbability.coerceIn(0f, 1f)
        val clampedThreshold = threshold.coerceIn(0f, 1f)

        val level = if (clampedProbability >= clampedThreshold) {
            SafetyLevel.BLOCKED
        } else {
            SafetyLevel.SAFE
        }

        return SafetyAssessment(
            level = level,
            goreProbability = clampedProbability,
            threshold = clampedThreshold,
        )
    }
}
