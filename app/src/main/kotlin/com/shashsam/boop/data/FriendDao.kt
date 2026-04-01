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

    @Query("SELECT * FROM friends ORDER BY lastInteractionTimestamp DESC")
    fun getAll(): Flow<List<FriendEntity>>

    @Query("SELECT * FROM friends WHERE ssid = :ssid LIMIT 1")
    suspend fun getBySsid(ssid: String): FriendEntity?

    @Query("SELECT * FROM friends WHERE ulid = :ulid LIMIT 1")
    suspend fun getByUlid(ulid: String): FriendEntity?

    @Query("SELECT * FROM friends WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): FriendEntity?

    @Query("SELECT * FROM friends WHERE id = :id LIMIT 1")
    fun getByIdFlow(id: Long): Flow<FriendEntity?>

    @Query("UPDATE friends SET lastSeenTimestamp = :timestamp, lastInteractionTimestamp = :timestamp, transferCount = transferCount + 1 WHERE id = :id")
    suspend fun updateLastSeen(id: Long, timestamp: Long)

    @Query("UPDATE friends SET displayName = :name WHERE id = :id")
    suspend fun updateDisplayName(id: Long, name: String)

    @Query("UPDATE friends SET profileJson = :profileJson, profilePicPath = :profilePicPath WHERE id = :id")
    suspend fun updateProfile(id: Long, profileJson: String?, profilePicPath: String?)

    @Query("UPDATE friends SET ssid = :newSsid WHERE id = :id")
    suspend fun updateSsid(id: Long, newSsid: String)

    @Query("UPDATE friends SET lastInteractionTimestamp = :timestamp WHERE id = :id")
    suspend fun updateLastInteraction(id: Long, timestamp: Long)

    @Query("DELETE FROM friends WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM friends WHERE ssid = :ssid")
    suspend fun deleteBySsid(ssid: String)

    @Query("SELECT * FROM friends WHERE displayName = :name LIMIT 1")
    suspend fun getByDisplayName(name: String): FriendEntity?

    /**
     * Upsert a friend by ULID — the stable, persistent identity.
     *
     * If a friend with the same ULID exists, update their SSID (it changes per session),
     * display name, profile data, and timestamps. If new, insert.
     *
     * This replaces the old SSID/displayName-based dedup which broke when either changed.
     */
    @Transaction
    suspend fun upsertByUlid(friend: FriendEntity): Long {
        val now = System.currentTimeMillis()

        // 1. Match by ULID — the canonical identity
        val byUlid = getByUlid(friend.ulid)
        if (byUlid != null) {
            // Same person, possibly new SSID/name/profile — update everything
            if (byUlid.ssid != friend.ssid) {
                // SSID changed (new Wi-Fi Direct session) — need to delete + reinsert
                // because SSID has a unique index
                val existingWithNewSsid = getBySsid(friend.ssid)
                if (existingWithNewSsid != null && existingWithNewSsid.id != byUlid.id) {
                    // Another friend entry has this SSID — remove the stale one
                    deleteById(existingWithNewSsid.id)
                }
                updateSsid(byUlid.id, friend.ssid)
            }
            updateDisplayName(byUlid.id, friend.displayName)
            updateLastSeen(byUlid.id, now)
            if (friend.profileJson != null || friend.profilePicPath != null) {
                updateProfile(byUlid.id, friend.profileJson, friend.profilePicPath)
            }
            return byUlid.id
        }

        // 2. No ULID match — clean up any stale SSID entry before inserting
        val bySsid = getBySsid(friend.ssid)
        if (bySsid != null) {
            // Old entry without ULID or with a different ULID that reused this SSID
            deleteById(bySsid.id)
        }

        // 3. Insert new friend
        return insert(friend)
    }
}
