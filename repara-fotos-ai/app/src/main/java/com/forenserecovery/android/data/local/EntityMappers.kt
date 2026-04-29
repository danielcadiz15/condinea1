package com.forenserecovery.android.data.local

import com.forenserecovery.android.domain.model.RecoveryItem
import com.forenserecovery.android.domain.model.RecoveryStatus
import com.forenserecovery.android.domain.model.RecoveryType
import com.forenserecovery.android.domain.model.ScanSource

fun RecoveryItemEntity.toDomain(): RecoveryItem = RecoveryItem(
    id = id,
    type = runCatching { RecoveryType.valueOf(type) }.getOrDefault(RecoveryType.UNKNOWN),
    mimeType = mimeType,
    originalPath = originalPath,
    recoveredPath = recoveredPath,
    sizeBytes = sizeBytes,
    sha256 = sha256,
    width = width,
    height = height,
    duration = duration,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
    scanSource = runCatching { ScanSource.valueOf(scanSource) }.getOrDefault(ScanSource.SHARED_STORAGE),
    confidence = confidence,
    status = runCatching { RecoveryStatus.valueOf(status) }.getOrDefault(RecoveryStatus.CORRUPT),
    notes = notes
)

fun RecoveryItem.toEntity(): RecoveryItemEntity = RecoveryItemEntity(
    id = id,
    type = type.name,
    mimeType = mimeType,
    originalPath = originalPath,
    recoveredPath = recoveredPath,
    sizeBytes = sizeBytes,
    sha256 = sha256,
    width = width,
    height = height,
    duration = duration,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
    scanSource = scanSource.name,
    confidence = confidence,
    status = status.name,
    notes = notes
)
