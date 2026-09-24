package com.scrollmeter.app.insights

import com.scrollmeter.app.data.model.AppSummary

/** The dashboard's "Top aplikace dnes" (spec §21): a few apps by distance, the rest as one "Ostatní" row. */
data class TopApps(val apps: List<AppSummary>, val other: AppSummary?) {
    val isEmpty: Boolean get() = apps.isEmpty()

    companion object {
        const val OTHER = "other"
        const val LIMIT = 4

        /**
         * Apps with scrolled distance, longest first. More than [limit] + 1 → the tail becomes one
         * "Ostatní" row (distance and times summed; a time stays unknown only if every app's is);
         * exactly [limit] + 1 → the last app is shown by name instead of a one-app "Ostatní".
         */
        fun of(apps: List<AppSummary>, limit: Int = LIMIT): TopApps {
            val scrolled = apps.filter { (it.distanceMm ?: 0.0) > 0 }.sortedByDescending { it.distanceMm }
            if (scrolled.size <= limit + 1) return TopApps(scrolled, null)
            val tail = scrolled.drop(limit)
            val other = AppSummary(
                packageName = OTHER,
                distanceMm = tail.sumOf { it.distanceMm ?: 0.0 },
                activeScrollMs = tail.mapNotNull { it.activeScrollMs }.takeIf { it.isNotEmpty() }?.sum(),
                foregroundMs = tail.mapNotNull { it.foregroundMs }.takeIf { it.isNotEmpty() }?.sum(),
                measuredEventCount = null,
                fallbackEventCount = null,
                unmeasurableEventCount = null,
                rejectedOutlierCount = null,
            )
            return TopApps(scrolled.take(limit), other)
        }
    }
}
