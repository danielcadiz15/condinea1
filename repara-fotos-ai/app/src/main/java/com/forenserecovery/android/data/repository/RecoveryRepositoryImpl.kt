package com.forenserecovery.android.data.repository

import com.forenserecovery.android.data.local.RecoveryItemDao
import com.forenserecovery.android.data.local.toDomain
import com.forenserecovery.android.data.local.toEntity
import com.forenserecovery.android.domain.model.RecoveryItem
import com.forenserecovery.android.domain.repository.RecoveryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RecoveryRepositoryImpl(
    private val dao: RecoveryItemDao
) : RecoveryRepository {

    override fun observeItems(): Flow<List<RecoveryItem>> = dao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    override suspend fun getAll(): List<RecoveryItem> = dao.getAll().map { it.toDomain() }

    override suspend fun upsert(item: RecoveryItem): Long = dao.upsert(item.toEntity())

    override suspend fun getById(id: Long): RecoveryItem? = dao.getById(id)?.toDomain()

    override suspend fun findBySha256(sha256: String): RecoveryItem? = dao.getBySha(sha256)?.toDomain()

    override suspend fun clear() = dao.clear()
}
