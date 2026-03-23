package com.shashsam.boop.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendDao {
    @Insert
    suspend fun insert(friend: FriendEntity)

    @Query("SELECT * FROM friends ORDER BY lastSeenTimestamp DESC")
    fun getAll(): Flow<List<FriendEntity>>

    @Query("SELECT * FROM friends WHERE ssid = :ssid LIMIT 1")
    suspend fun getBySsid(ssid: String): FriendEntity?

    @Query("UPDATE friends SET lastSeenTimestamp = :timestamp, transferCount = transferCount + 1 WHERE ssid = :ssid")
    suspend fun updateLastSeen(ssid: String, timestamp: Long)

    @Query("DELETE FROM friends WHERE id = :id")
    suspend fun deleteById(id: Long)
}
