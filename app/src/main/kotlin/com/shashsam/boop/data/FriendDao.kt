package com.shashsam.boop.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(friend: FriendEntity): Long

    @Query("SELECT * FROM friends ORDER BY lastSeenTimestamp DESC")
    fun getAll(): Flow<List<FriendEntity>>

    @Query("SELECT * FROM friends WHERE ssid = :ssid LIMIT 1")
    suspend fun getBySsid(ssid: String): FriendEntity?

    @Query("SELECT * FROM friends WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): FriendEntity?

    @Query("UPDATE friends SET lastSeenTimestamp = :timestamp, transferCount = transferCount + 1 WHERE ssid = :ssid")
    suspend fun updateLastSeen(ssid: String, timestamp: Long)

    @Query("UPDATE friends SET displayName = :name WHERE ssid = :ssid")
    suspend fun updateDisplayName(ssid: String, name: String)

    @Query("UPDATE friends SET profileJson = :profileJson, profilePicPath = :profilePicPath WHERE ssid = :ssid")
    suspend fun updateProfile(ssid: String, profileJson: String?, profilePicPath: String?)

    @Query("DELETE FROM friends WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Transaction
    suspend fun insertOrUpdate(friend: FriendEntity): Long {
        val id = insert(friend)
        if (id == -1L) {
            // Row already exists — update fields
            updateLastSeen(friend.ssid, friend.lastSeenTimestamp)
            updateDisplayName(friend.ssid, friend.displayName)
            if (friend.profileJson != null || friend.profilePicPath != null) {
                updateProfile(friend.ssid, friend.profileJson, friend.profilePicPath)
            }
            return getBySsid(friend.ssid)?.id ?: -1L
        }
        return id
    }
}
