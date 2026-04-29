package com.forenserecovery.android.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recovery_items",
    indices = [
        Index(value = ["sha256"]),
        Index(value = ["status"]),
        Index(value = ["scanSource"])
    ]
)
data class RecoveryItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val type: String,
    val mimeType: String?,
    val originalPath: String?,
    val recoveredPath: String?,
    val sizeBytes: Long,
    val sha256: String?,
    val width: Int?,
    val height: Int?,
    val duration: Long?,
    val createdAt: Long?,
    val modifiedAt: Long?,
    val scanSource: String,
    val confidence: Float,
    val status: String,
    val notes: String?
)
