package com.example.caresync.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ReminderEntity::class, ReminderEventEntity::class],
    version = 3,  // ← Incremented from 2 to 3
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
    abstract fun reminderEventDao(): ReminderEventDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "caresync.db"
                )
                    .addMigrations(MIGRATION_2_3)  // ← Add migration instead of destructive fallback
                    .fallbackToDestructiveMigration()  // ← Keep as last resort for unknown migrations
                    .build().also { INSTANCE = it }
            }

        // ==========================================
        // MIGRATION FROM VERSION 2 TO 3
        // Adds analytics fields to reminder_events
        // ==========================================
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new columns to reminder_events table
                // All have DEFAULT values so existing rows get safe defaults

                // Time context fields
                database.execSQL("ALTER TABLE reminder_events ADD COLUMN hourOfDay INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE reminder_events ADD COLUMN dayOfWeek INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE reminder_events ADD COLUMN isWeekend INTEGER NOT NULL DEFAULT 0")

                // User behavior tracking
                database.execSQL("ALTER TABLE reminder_events ADD COLUMN responseTimeMillis INTEGER")
                database.execSQL("ALTER TABLE reminder_events ADD COLUMN snoozeDurationMinutes INTEGER")
                database.execSQL("ALTER TABLE reminder_events ADD COLUMN snoozeCount INTEGER NOT NULL DEFAULT 0")

                // Device context
                database.execSQL("ALTER TABLE reminder_events ADD COLUMN deviceState TEXT")
                database.execSQL("ALTER TABLE reminder_events ADD COLUMN activeAppPackage TEXT")
                database.execSQL("ALTER TABLE reminder_events ADD COLUMN activeAppCategory TEXT")
                database.execSQL("ALTER TABLE reminder_events ADD COLUMN screenTimeMinutes INTEGER")
                database.execSQL("ALTER TABLE reminder_events ADD COLUMN batteryLevel INTEGER")

                // Notification details
                database.execSQL("ALTER TABLE reminder_events ADD COLUMN notificationPriority TEXT")
                database.execSQL("ALTER TABLE reminder_events ADD COLUMN notificationMethod TEXT")
                database.execSQL("ALTER TABLE reminder_events ADD COLUMN toneUsed TEXT")
                database.execSQL("ALTER TABLE reminder_events ADD COLUMN vibrationUsed INTEGER NOT NULL DEFAULT 0")

                // ML model data
                database.execSQL("ALTER TABLE reminder_events ADD COLUMN modelConfidence REAL")
                database.execSQL("ALTER TABLE reminder_events ADD COLUMN triggerSource TEXT NOT NULL DEFAULT 'SCHEDULER'")

                // Create new indices for faster queries
                database.execSQL("CREATE INDEX IF NOT EXISTS index_reminder_events_timestamp ON reminder_events(timestamp)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_reminder_events_eventType ON reminder_events(eventType)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_reminder_events_reminderId_timestamp ON reminder_events(reminderId, timestamp)")
            }
        }
    }
}
