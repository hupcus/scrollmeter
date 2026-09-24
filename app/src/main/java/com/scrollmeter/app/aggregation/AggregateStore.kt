package com.scrollmeter.app.aggregation

/** Where a flush goes — Room in the app ([com.scrollmeter.app.data.repository.ScrollRepository]), a list in tests. */
fun interface AggregateStore {
    /** Adds [deltas] to their rows and inserts [sessions], all or nothing. */
    suspend fun write(deltas: List<AggregateDelta>, sessions: List<ClosedSession>)
}
