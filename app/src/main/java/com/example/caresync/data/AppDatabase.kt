package com.example.caresync.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.caresync.analytics.data.AnalyticsDao
import com.example.caresync.analytics.data.AchievementEntity
import com.example.caresync.analytics.data.UserProgressEntity

@Database(
    entities = [
        ReminderEntity::class,
        ReminderEventEntity::class,
        BlacklistHour::class,
        AchievementEntity::class,
        UserProgressEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
    abstract fun reminderEventDao(): ReminderEventDao
    abstract fun blacklistHourDao(): BlacklistHourDao
    abstract fun analyticsDao(): AnalyticsDao  // ← ADD THIS

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "caresync.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                    .fallbackToDestructiveMigration()
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

        // ==========================================
        // MIGRATION FROM VERSION 3 TO 4
        // Adds blacklist_hours table
        // ==========================================
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create blacklist_hours table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS blacklist_hours (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        reminderId INTEGER NOT NULL,
                        hourOfDay INTEGER NOT NULL,
                        dismissalCount INTEGER NOT NULL,
                        lastDismissalTimestamp INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(reminderId) REFERENCES reminders(id) ON DELETE CASCADE
                    )
                """)

                // Create unique index
                database.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS index_blacklist_hours_reminderId_hourOfDay 
                    ON blacklist_hours(reminderId, hourOfDay)
                """)
            }
        }

        // ==========================================
        // ✅ NEW: MIGRATION FROM VERSION 4 TO 5
        // Adds snoozeDurationMinutes to reminders table
        // ==========================================
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add snoozeDurationMinutes column to reminders table
                // Default is 10 minutes for existing reminders
                database.execSQL(
                    "ALTER TABLE reminders ADD COLUMN snoozeDurationMinutes INTEGER NOT NULL DEFAULT 10"
                )
            }
        }

        // ✅ NEW: Migration 5 → 6 (Add CASCADE to blacklist_hours)
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Recreate blacklist_hours table with CASCADE

                // Step 1: Create temporary table with CASCADE
                database.execSQL("""
            CREATE TABLE IF NOT EXISTS blacklist_hours_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                reminderId INTEGER NOT NULL,
                hourOfDay INTEGER NOT NULL,
                dismissalCount INTEGER NOT NULL,
                lastDismissalTimestamp INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(reminderId) REFERENCES reminders(id) ON DELETE CASCADE
            )
        """)

                // Step 2: Copy existing data (if any)
                database.execSQL("""
            INSERT INTO blacklist_hours_new 
            SELECT * FROM blacklist_hours
        """)

                // Step 3: Drop old table
                database.execSQL("DROP TABLE IF EXISTS blacklist_hours")

                // Step 4: Rename new table
                database.execSQL("ALTER TABLE blacklist_hours_new RENAME TO blacklist_hours")

                // Step 5: Recreate index
                database.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS index_blacklist_hours_reminderId_hourOfDay 
            ON blacklist_hours(reminderId, hourOfDay)
        """)

                Log.d("MIGRATION", "✅ Migration 5→6: Added CASCADE to blacklist_hours (preserving existing data)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE reminders ADD COLUMN boostModeActive INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE reminders ADD COLUMN boostModeEndTime INTEGER")
                database.execSQL("ALTER TABLE reminders ADD COLUMN boostModeFrequency INTEGER NOT NULL DEFAULT 5")

                Log.d("MIGRATION", "✅ Migration 6→7: Added Boost Mode fields")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE reminders ADD COLUMN allowedTimePeriodsJson TEXT NOT NULL DEFAULT '[\"MORNING\",\"AFTERNOON\",\"EVENING\"]'"
                )
                Log.d("MIGRATION", "✅ Migration 7→8: Added time period restrictions")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE reminders ADD COLUMN dueDate INTEGER")
                Log.d("MIGRATION", "✅ Migration 8→9: Added due date field")
            }
        }

        private val MIGRATION_9_10 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create achievements table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS achievements (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        icon TEXT NOT NULL,
                        pointsRequired INTEGER NOT NULL,
                        isUnlocked INTEGER NOT NULL DEFAULT 0,
                        unlockedAt INTEGER
                    )
                """)

                // Create user_progress table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS user_progress (
                        id INTEGER PRIMARY KEY NOT NULL DEFAULT 1,
                        totalPoints INTEGER NOT NULL DEFAULT 0,
                        currentLevel INTEGER NOT NULL DEFAULT 1,
                        currentStreak INTEGER NOT NULL DEFAULT 0,
                        longestStreak INTEGER NOT NULL DEFAULT 0,
                        lastCompletionDate INTEGER,
                        totalTasksCompleted INTEGER NOT NULL DEFAULT 0
                    )
                """)

                // Insert initial user progress row
                database.execSQL("""
                    INSERT INTO user_progress (id) VALUES (1)
                """)
            }
        }

        // ✅ DUMMY MIGRATION (Data not important)
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Empty - uninstalling anyway
            }
        }
    }
}
