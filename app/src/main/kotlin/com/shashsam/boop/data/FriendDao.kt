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

    @Query("SELECT * FROM friends WHERE id = :id LIMIT 1")
    fun getByIdFlow(id: Long): Flow<FriendEntity?>

    @Query("UPDATE friends SET lastSeenTimestamp = :timestamp, transferCount = transferCount + 1 WHERE ssid = :ssid")
    suspend fun updateLastSeen(ssid: String, timestamp: Long)

    @Query("UPDATE friends SET displayName = :name WHERE ssid = :ssid")
    suspend fun updateDisplayName(ssid: String, name: String)

    @Query("UPDATE friends SET profileJson = :profileJson, profilePicPath = :profilePicPath WHERE ssid = :ssid")
    suspend fun updateProfile(ssid: String, profileJson: String?, profilePicPath: String?)

    @Query("DELETE FROM friends WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM friends WHERE ssid = :ssid")
    suspend fun deleteBySsid(ssid: String)

    @Query("UPDATE friends SET ssid = :newSsid WHERE id = :id")
    suspend fun updateSsid(id: Long, newSsid: String)

    @Transaction
    suspend fun insertOrUpdate(friend: FriendEntity): Long {
        // Check if a friend with this SSID already exists
        val existingBySsid = getBySsid(friend.ssid)
        if (existingBySsid != null) {
            // Same SSID — update in place
            updateLastSeen(friend.ssid, friend.lastSeenTimestamp)
            updateDisplayName(friend.ssid, friend.displayName)
            if (friend.profileJson != null || friend.profilePicPath != null) {
                updateProfile(friend.ssid, friend.profileJson, friend.profilePicPath)
            }
            return existingBySsid.id
        }
        // Try inserting — may succeed if SSID is new
        val id = insert(friend)
        if (id != -1L) return id
        // IGNORE fired — shouldn't happen after getBySsid check, but handle gracefully
        return getBySsid(friend.ssid)?.id ?: -1L
    }

    /**
     * Upsert by display name: if a friend with the same [displayName] exists
     * but has a different SSID (Wi-Fi Direct SSIDs change per session), migrate
     * the existing entry to the new SSID and update profile data.
     * Returns the friend's row ID.
     */
    @Transaction
    suspend fun upsertByIdentity(friend: FriendEntity): Long {
        // 1. Exact SSID match — fast path
        val bySsid = getBySsid(friend.ssid)
        if (bySsid != null) {
            updateLastSeen(friend.ssid, friend.lastSeenTimestamp)
            updateDisplayName(friend.ssid, friend.displayName)
            if (friend.profileJson != null || friend.profilePicPath != null) {
                updateProfile(friend.ssid, friend.profileJson, friend.profilePicPath)
            }
            return bySsid.id
        }
        // 2. Same display name with different SSID — migrate
        val byName = getByDisplayName(friend.displayName)
        if (byName != null) {
            // Delete old entry and insert fresh with new SSID (unique index prevents update-in-place)
            deleteById(byName.id)
            return insert(
                friend.copy(
                    id = 0,
                    firstSeenTimestamp = byName.firstSeenTimestamp,
                    transferCount = byName.transferCount + 1
                )
            )
        }
        // 3. Completely new friend
        return insert(friend)
    }

    @Query("SELECT * FROM friends WHERE displayName = :name LIMIT 1")
    suspend fun getByDisplayName(name: String): FriendEntity?
}
