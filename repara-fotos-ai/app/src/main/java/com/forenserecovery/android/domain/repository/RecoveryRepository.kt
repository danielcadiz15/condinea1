package com.forenserecovery.android.domain.repository

import com.forenserecovery.android.domain.model.RecoveryItem
import kotlinx.coroutines.flow.Flow

interface RecoveryRepository {
    fun observeItems(): Flow<List<RecoveryItem>>
    suspend fun getAll(): List<RecoveryItem>
    suspend fun getById(id: Long): RecoveryItem?
    suspend fun findBySha256(sha256: String): RecoveryItem?
    suspend fun upsert(item: RecoveryItem): Long
    suspend fun clear()
}
