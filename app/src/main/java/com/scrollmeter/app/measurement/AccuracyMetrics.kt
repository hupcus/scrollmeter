package com.scrollmeter.app.measurement

import kotlin.math.abs

/** One comparison of what the engine measured against a known ground truth (spec §36). */
data class AccuracyRun(val groundTruthMm: Double, val measuredMm: Double) {
    val absoluteErrorMm: Double get() = abs(measuredMm - groundTruthMm)

    /** Undefined without ground truth — such a run is left out of MAPE. */
    val percentageError: Double? get() = if (groundTruthMm > 0.0) absoluteErrorMm / groundTruthMm * 100.0 else null
}

/** Spec §38: MAE = mean(|measured − truth|), MAPE = mean(|measured − truth| / truth × 100). */
data class AccuracySummary(val runs: Int, val maeMm: Double?, val mapePercent: Double?) {
    companion object {
        fun of(runs: List<AccuracyRun>): AccuracySummary {
            val percentages = runs.mapNotNull { it.percentageError }
            return AccuracySummary(
                runs = runs.size,
                maeMm = runs.takeIf { it.isNotEmpty() }?.map { it.absoluteErrorMm }?.average(),
                mapePercent = percentages.takeIf { it.isNotEmpty() }?.average(),
            )
        }
    }
}
