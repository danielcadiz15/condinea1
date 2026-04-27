package com.forenserecovery.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecoveryItemDao {

    @Query("SELECT * FROM recovery_items ORDER BY modifiedAt DESC")
    fun observeAll(): Flow<List<RecoveryItemEntity>>

    @Query("SELECT * FROM recovery_items ORDER BY modifiedAt DESC")
    suspend fun getAll(): List<RecoveryItemEntity>

    @Query("SELECT * FROM recovery_items WHERE status IN (:statuses) ORDER BY modifiedAt DESC")
    fun observeByStatuses(statuses: List<String>): Flow<List<RecoveryItemEntity>>

    @Query("SELECT * FROM recovery_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RecoveryItemEntity?

    @Query("SELECT * FROM recovery_items WHERE sha256 = :sha LIMIT 1")
    suspend fun getBySha(sha: String): RecoveryItemEntity?

    @Query("SELECT COUNT(*) FROM recovery_items WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RecoveryItemEntity): Long

    @Query("DELETE FROM recovery_items")
    suspend fun clear()
}
