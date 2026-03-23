package com.shashsam.boop.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TransferHistoryEntity::class], version = 1, exportSchema = false)
abstract class BoopDatabase : RoomDatabase() {
    abstract fun transferHistoryDao(): TransferHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: BoopDatabase? = null

        fun getInstance(context: Context): BoopDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BoopDatabase::class.java,
                    "boop_database"
                ).build().also { INSTANCE = it }
            }
    }
}
