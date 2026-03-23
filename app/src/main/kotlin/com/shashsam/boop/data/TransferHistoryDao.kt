package com.shashsam.boop.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferHistoryDao {
    @Insert
    suspend fun insert(entry: TransferHistoryEntity)

    @Query("SELECT * FROM transfer_history ORDER BY timestamp DESC")
    fun getAll(): Flow<List<TransferHistoryEntity>>

    @Query("DELETE FROM transfer_history WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
