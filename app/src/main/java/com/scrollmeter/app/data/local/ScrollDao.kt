package com.scrollmeter.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.scrollmeter.app.data.model.AppSummary
import com.scrollmeter.app.data.model.DaySummary
import com.scrollmeter.app.data.model.ExportAppDay
import com.scrollmeter.app.data.model.ExportDay
import com.scrollmeter.app.data.model.PriorDays
import kotlinx.coroutines.flow.Flow

/**
 * Insert-or-add (D8): a flush inserts an empty row if missing and then adds its deltas. SQLite's
 * `INSERT … ON CONFLICT DO UPDATE` needs 3.24, and API 29 (minSdk) ships 3.22, so it is two statements in
 * one transaction. `MIN`/`MAX` with a NULL argument are NULL in SQLite, hence the `COALESCE`.
 */
@Dao
abstract class ScrollDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertIfAbsent(row: DailyAppAggregateEntity)

    @Query(
        """
        UPDATE daily_app_aggregate SET
            distanceMm = distanceMm + :distanceMm,
            horizontalDistanceMm = horizontalDistanceMm + :horizontalMm,
            verticalDistanceMm = verticalDistanceMm + :verticalMm,
            rawDeltaXPx = rawDeltaXPx + :rawDxPx,
            rawDeltaYPx = rawDeltaYPx + :rawDyPx,
            measuredEventCount = measuredEventCount + :measured,
            fallbackEventCount = fallbackEventCount + :fallback,
            unmeasurableEventCount = unmeasurableEventCount + :unmeasurable,
            rejectedOutlierCount = rejectedOutlierCount + :outliers,
            firstEventTimestamp = COALESCE(MIN(firstEventTimestamp, :first), firstEventTimestamp, :first),
            lastEventTimestamp = COALESCE(MAX(lastEventTimestamp, :last), lastEventTimestamp, :last),
            calibrationVersion = MAX(calibrationVersion, :calibrationVersion),
            activeScrollMs = activeScrollMs + :activeScrollMs
        WHERE date = :date AND packageName = :packageName
        """,
    )
    protected abstract suspend fun addTo(
        date: String,
        packageName: String,
        distanceMm: Double,
        horizontalMm: Double,
        verticalMm: Double,
        rawDxPx: Long,
        rawDyPx: Long,
        measured: Long,
        fallback: Long,
        unmeasurable: Long,
        outliers: Long,
        first: Long?,
        last: Long?,
        calibrationVersion: Int,
        activeScrollMs: Long,
    )

    @Insert
    protected abstract suspend fun insertSessions(sessions: List<ScrollSessionEntity>)

    /** One flush: every delta added and every session inserted, or nothing (spec §16). */
    @Transaction
    open suspend fun addFlush(rows: List<DailyAppAggregateEntity>, sessions: List<ScrollSessionEntity>) {
        for (r in rows) {
            insertIfAbsent(DailyAppAggregateEntity(r.date, r.packageName))
            addTo(
                r.date, r.packageName, r.distanceMm, r.horizontalDistanceMm, r.verticalDistanceMm, r.rawDeltaXPx,
                r.rawDeltaYPx, r.measuredEventCount, r.fallbackEventCount, r.unmeasurableEventCount,
                r.rejectedOutlierCount, r.firstEventTimestamp, r.lastEventTimestamp, r.calibrationVersion, r.activeScrollMs,
            )
        }
        if (sessions.isNotEmpty()) insertSessions(sessions)
    }

    @Query("DELETE FROM daily_app_usage WHERE date BETWEEN :fromDate AND :toDate")
    protected abstract suspend fun deleteUsage(fromDate: String, toDate: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertUsage(rows: List<DailyAppUsageEntity>)

    /** A usage sync owns whole days: the dates it recomputed are replaced, not added to (ADR-021). */
    @Transaction
    open suspend fun replaceUsage(fromDate: String, toDate: String, rows: List<DailyAppUsageEntity>) {
        deleteUsage(fromDate, toDate)
        if (rows.isNotEmpty()) insertUsage(rows)
    }

    // Every read below leaves out [excluded]: an excluded app disappears from totals, lists, history
    // and the export while it stays excluded, and comes back when it is included again (ADR-031).

    @Query("SELECT COALESCE(SUM(distanceMm), 0) FROM daily_app_aggregate WHERE date BETWEEN :fromDate AND :toDate AND packageName NOT IN (:excluded)")
    abstract fun distanceBetween(fromDate: String, toDate: String, excluded: List<String>): Flow<Double>

    @Query("SELECT COALESCE(SUM(distanceMm), 0) FROM daily_app_aggregate WHERE packageName NOT IN (:excluded)")
    abstract fun lifetimeDistance(excluded: List<String>): Flow<Double>

    /** Scroll and time in app side by side; SQLite has no FULL JOIN, so a UNION ALL of both tables. */
    @Query(
        """
        SELECT packageName,
            SUM(distanceMm) AS distanceMm,
            SUM(activeScrollMs) AS activeScrollMs,
            SUM(foregroundMs) AS foregroundMs,
            SUM(measuredEventCount) AS measuredEventCount,
            SUM(fallbackEventCount) AS fallbackEventCount,
            SUM(unmeasurableEventCount) AS unmeasurableEventCount,
            SUM(rejectedOutlierCount) AS rejectedOutlierCount
        FROM (
            SELECT packageName, distanceMm, activeScrollMs, NULL AS foregroundMs, measuredEventCount,
                fallbackEventCount, unmeasurableEventCount, rejectedOutlierCount
            FROM daily_app_aggregate WHERE date BETWEEN :fromDate AND :toDate AND packageName NOT IN (:excluded)
            UNION ALL
            SELECT packageName, NULL, NULL, foregroundMs, NULL, NULL, NULL, NULL
            FROM daily_app_usage WHERE date BETWEEN :fromDate AND :toDate AND packageName NOT IN (:excluded)
        )
        GROUP BY packageName
        ORDER BY distanceMm DESC, foregroundMs DESC
        """,
    )
    abstract fun appsBetween(fromDate: String, toDate: String, excluded: List<String>): Flow<List<AppSummary>>

    @Query(
        """
        SELECT date,
            SUM(distanceMm) AS distanceMm,
            SUM(activeScrollMs) AS activeScrollMs,
            SUM(foregroundMs) AS foregroundMs
        FROM (
            SELECT date, distanceMm, activeScrollMs, NULL AS foregroundMs
            FROM daily_app_aggregate WHERE date BETWEEN :fromDate AND :toDate AND packageName NOT IN (:excluded)
            UNION ALL
            SELECT date, NULL, NULL, foregroundMs
            FROM daily_app_usage WHERE date BETWEEN :fromDate AND :toDate AND packageName NOT IN (:excluded)
        )
        GROUP BY date
        ORDER BY date
        """,
    )
    abstract fun daysBetween(fromDate: String, toDate: String, excluded: List<String>): Flow<List<DaySummary>>

    /** One app per day — the app detail's chart (spec §24): distance, scroll time and time in app. */
    @Query(
        """
        SELECT date,
            SUM(distanceMm) AS distanceMm,
            SUM(activeScrollMs) AS activeScrollMs,
            SUM(foregroundMs) AS foregroundMs
        FROM (
            SELECT date, distanceMm, activeScrollMs, NULL AS foregroundMs
            FROM daily_app_aggregate WHERE packageName = :packageName AND date BETWEEN :fromDate AND :toDate
            UNION ALL
            SELECT date, NULL, NULL, foregroundMs
            FROM daily_app_usage WHERE packageName = :packageName AND date BETWEEN :fromDate AND :toDate
        )
        GROUP BY date
        ORDER BY date
        """,
    )
    abstract fun appDaysBetween(packageName: String, fromDate: String, toDate: String): Flow<List<DaySummary>>

    /** The first day anything was measured — history statistics start there (spec §23). */
    @Query("SELECT MIN(date) FROM daily_app_aggregate WHERE packageName NOT IN (:excluded)")
    abstract fun firstMeasuredDate(excluded: List<String>): Flow<String?>

    /** Every package with stored data, excluded ones included — the exclusion list (spec §44). */
    @Query("SELECT packageName FROM daily_app_aggregate UNION SELECT packageName FROM daily_app_usage ORDER BY packageName")
    abstract fun seenPackages(): Flow<List<String>>

    /** The best earlier day and how many earlier days had a distance — "Nový rekord" (ADR-031). */
    @Query(
        """
        SELECT COALESCE(MAX(total), 0) AS bestMm, COUNT(*) AS days FROM (
            SELECT SUM(distanceMm) AS total FROM daily_app_aggregate
            WHERE date < :date AND packageName NOT IN (:excluded)
            GROUP BY date HAVING total > 0
        )
        """,
    )
    abstract suspend fun daysBefore(date: String, excluded: List<String>): PriorDays

    /** `per_app.csv` (spec §28, ADR-021): one row per day and app, scroll and time side by side. */
    @Query(
        """
        SELECT date, packageName,
            SUM(distanceMm) AS distanceMm,
            SUM(measuredEventCount) AS measuredEventCount,
            SUM(fallbackEventCount) AS fallbackEventCount,
            SUM(unmeasurableEventCount) AS unmeasurableEventCount,
            SUM(rejectedOutlierCount) AS rejectedOutlierCount,
            SUM(foregroundMs) AS foregroundMs,
            SUM(activeScrollMs) AS activeScrollMs
        FROM (
            SELECT date, packageName, distanceMm, measuredEventCount, fallbackEventCount, unmeasurableEventCount,
                rejectedOutlierCount, NULL AS foregroundMs, activeScrollMs
            FROM daily_app_aggregate WHERE packageName NOT IN (:excluded)
            UNION ALL
            SELECT date, packageName, NULL, NULL, NULL, NULL, NULL, foregroundMs, NULL
            FROM daily_app_usage WHERE packageName NOT IN (:excluded)
        )
        GROUP BY date, packageName
        ORDER BY date, packageName
        """,
    )
    abstract suspend fun exportAppDays(excluded: List<String>): List<ExportAppDay>

    /** `daily_summary.csv` (spec §28): the measured days only — time without scroll is per app. */
    @Query(
        """
        SELECT date, SUM(distanceMm) AS distanceMm,
            SUM(measuredEventCount + fallbackEventCount + unmeasurableEventCount + rejectedOutlierCount) AS events
        FROM daily_app_aggregate WHERE packageName NOT IN (:excluded)
        GROUP BY date ORDER BY date
        """,
    )
    abstract suspend fun exportDays(excluded: List<String>): List<ExportDay>

    /** Sessions have no UI in the MVP (ADR-011); older ones are pruned (ADR-031). */
    @Query("DELETE FROM scroll_session WHERE startTimestamp < :beforeMs")
    abstract suspend fun deleteSessionsBefore(beforeMs: Long): Int

    @Query("SELECT * FROM daily_app_aggregate WHERE date = :date AND packageName = :packageName")
    abstract suspend fun aggregate(date: String, packageName: String): DailyAppAggregateEntity?

    @Query("SELECT * FROM daily_app_usage WHERE date = :date AND packageName = :packageName")
    abstract suspend fun usage(date: String, packageName: String): DailyAppUsageEntity?

    @Query("SELECT * FROM scroll_session ORDER BY startTimestamp")
    abstract suspend fun sessions(): List<ScrollSessionEntity>
}
