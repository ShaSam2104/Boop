package com.shashsam.boop.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileItemDao {
    @Insert
    suspend fun insert(item: ProfileItemEntity): Long

    @Update
    suspend fun update(item: ProfileItemEntity)

    @Query("DELETE FROM profile_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM profile_items ORDER BY sortOrder ASC")
    fun getAll(): Flow<List<ProfileItemEntity>>

    @Query("SELECT * FROM profile_items ORDER BY sortOrder ASC")
    suspend fun getAllOnce(): List<ProfileItemEntity>

    @Query("DELETE FROM profile_items")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM profile_items")
    suspend fun getCount(): Int

    @Query("UPDATE profile_items SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)
}
