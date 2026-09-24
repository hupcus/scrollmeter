package com.scrollmeter.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.scrollmeter.app.BuildConfig

/**
 * Room v1 (D8, spec §60): the schema is exported to `app/schemas/` and versioned in git. Debug builds
 * may recreate the database on a version change; release builds need a real migration — a release
 * without one fails loudly instead of silently deleting someone's history.
 */
@Database(
    entities = [DailyAppAggregateEntity::class, ScrollSessionEntity::class, DailyAppUsageEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ScrollDatabase : RoomDatabase() {
    abstract fun scrollDao(): ScrollDao

    companion object {
        const val NAME = "scrollmeter.db"

        fun create(context: Context): ScrollDatabase =
            Room.databaseBuilder(context.applicationContext, ScrollDatabase::class.java, NAME)
                .apply { if (BuildConfig.DEBUG) fallbackToDestructiveMigration(dropAllTables = true) }
                .build()
    }
}
