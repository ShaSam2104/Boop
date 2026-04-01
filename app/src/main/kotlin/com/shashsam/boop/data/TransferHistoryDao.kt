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

    @Query("SELECT * FROM transfer_history WHERE peerUlid = :ulid ORDER BY timestamp DESC")
    fun getByPeerUlid(ulid: String): Flow<List<TransferHistoryEntity>>

    @Query("UPDATE transfer_history SET peerUlid = :ulid WHERE peerUlid IS NULL AND timestamp >= :since")
    suspend fun updatePeerUlidForRecent(ulid: String, since: Long)

    @Query("SELECT * FROM transfer_history ORDER BY timestamp DESC")
    suspend fun getAllOnce(): List<TransferHistoryEntity>

    @Query("DELETE FROM transfer_history WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
