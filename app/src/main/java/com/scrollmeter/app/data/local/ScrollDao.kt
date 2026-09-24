package com.scrollmeter.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.scrollmeter.app.data.model.AppSummary
import com.scrollmeter.app.data.model.DaySummary
import kotlinx.coroutines.flow.Flow

/**
 * Insert-or-add (D8): a flush inserts an empty row if missing and then adds its deltas. SQLite's
 * `INSERT … ON CONFLICT DO UPDATE` needs 3.24, and API 28 ships 3.22, so it is two statements in
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

    @Query("SELECT COALESCE(SUM(distanceMm), 0) FROM daily_app_aggregate WHERE date BETWEEN :fromDate AND :toDate")
    abstract fun distanceBetween(fromDate: String, toDate: String): Flow<Double>

    @Query("SELECT COALESCE(SUM(distanceMm), 0) FROM daily_app_aggregate")
    abstract fun lifetimeDistance(): Flow<Double>

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
            FROM daily_app_aggregate WHERE date BETWEEN :fromDate AND :toDate
            UNION ALL
            SELECT packageName, NULL, NULL, foregroundMs, NULL, NULL, NULL, NULL
            FROM daily_app_usage WHERE date BETWEEN :fromDate AND :toDate
        )
        GROUP BY packageName
        ORDER BY distanceMm DESC, foregroundMs DESC
        """,
    )
    abstract fun appsBetween(fromDate: String, toDate: String): Flow<List<AppSummary>>

    @Query(
        """
        SELECT date,
            SUM(distanceMm) AS distanceMm,
            SUM(activeScrollMs) AS activeScrollMs,
            SUM(foregroundMs) AS foregroundMs
        FROM (
            SELECT date, distanceMm, activeScrollMs, NULL AS foregroundMs
            FROM daily_app_aggregate WHERE date BETWEEN :fromDate AND :toDate
            UNION ALL
            SELECT date, NULL, NULL, foregroundMs
            FROM daily_app_usage WHERE date BETWEEN :fromDate AND :toDate
        )
        GROUP BY date
        ORDER BY date
        """,
    )
    abstract fun daysBetween(fromDate: String, toDate: String): Flow<List<DaySummary>>

    @Query("SELECT * FROM daily_app_aggregate WHERE date = :date AND packageName = :packageName")
    abstract suspend fun aggregate(date: String, packageName: String): DailyAppAggregateEntity?

    @Query("SELECT * FROM daily_app_usage WHERE date = :date AND packageName = :packageName")
    abstract suspend fun usage(date: String, packageName: String): DailyAppUsageEntity?

    @Query("SELECT * FROM scroll_session ORDER BY startTimestamp")
    abstract suspend fun sessions(): List<ScrollSessionEntity>
}
