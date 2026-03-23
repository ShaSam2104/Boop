package com.shashsam.boop.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TransferHistoryEntity::class, FriendEntity::class],
    version = 2,
    exportSchema = false
)
abstract class BoopDatabase : RoomDatabase() {
    abstract fun transferHistoryDao(): TransferHistoryDao
    abstract fun friendDao(): FriendDao

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

        fun getInstance(context: Context): BoopDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BoopDatabase::class.java,
                    "boop_database"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}
