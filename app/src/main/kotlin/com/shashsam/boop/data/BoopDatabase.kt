package com.shashsam.boop.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TransferHistoryEntity::class, FriendEntity::class, ProfileItemEntity::class],
    version = 4,
    exportSchema = false
)
abstract class BoopDatabase : RoomDatabase() {
    abstract fun transferHistoryDao(): TransferHistoryDao
    abstract fun friendDao(): FriendDao
    abstract fun profileItemDao(): ProfileItemDao

    companion object {
        @Volatile
        private var INSTANCE: BoopDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS friends (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        ssid TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        firstSeenTimestamp INTEGER NOT NULL,
                        lastSeenTimestamp INTEGER NOT NULL,
                        transferCount INTEGER NOT NULL DEFAULT 1
                    )"""
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create profile_items table
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS profile_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        type TEXT NOT NULL,
                        label TEXT NOT NULL,
                        value TEXT NOT NULL,
                        size TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL
                    )"""
                )

                // 2. Deduplicate existing friends by SSID (keep highest id per SSID)
                db.execSQL(
                    """DELETE FROM friends WHERE id NOT IN (
                        SELECT MAX(id) FROM friends GROUP BY ssid
                    )"""
                )

                // 3. Add new columns to friends table
                db.execSQL("ALTER TABLE friends ADD COLUMN profileJson TEXT")
                db.execSQL("ALTER TABLE friends ADD COLUMN profilePicPath TEXT")

                // 4. Create unique index on ssid
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_friends_ssid ON friends (ssid)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add ulid and lastInteractionTimestamp columns
                db.execSQL("ALTER TABLE friends ADD COLUMN ulid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE friends ADD COLUMN lastInteractionTimestamp INTEGER NOT NULL DEFAULT 0")

                // 2. Backfill: set lastInteractionTimestamp = lastSeenTimestamp
                db.execSQL("UPDATE friends SET lastInteractionTimestamp = lastSeenTimestamp")

                // 3. Generate unique placeholder ULIDs for existing friends (id-based to ensure uniqueness)
                // Real ULIDs will be assigned on next friend exchange
                db.execSQL("UPDATE friends SET ulid = 'LEGACY_' || id")

                // 4. Create unique index on ulid
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_friends_ulid ON friends (ulid)")
            }
        }

        fun getInstance(context: Context): BoopDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BoopDatabase::class.java,
                    "boop_database"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { INSTANCE = it }
            }
    }
}
