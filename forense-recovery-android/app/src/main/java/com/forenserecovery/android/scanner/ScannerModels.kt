package com.forenserecovery.android.scanner

import com.forenserecovery.android.domain.model.RecoveryStatus
import com.forenserecovery.android.domain.model.RecoveryType
import com.forenserecovery.android.domain.model.ScanSource

data class ScannerCandidate(
    val sourcePath: String?,
    val displayName: String?,
    val sizeBytes: Long,
    val mimeType: String?,
    val modifiedAt: Long?,
    val createdAt: Long?,
    val source: ScanSource,
    val originalUriString: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val duration: Long? = null,
    val isLikelyThumbnail: Boolean = false,
    val isPartialHint: Boolean = false,
    val suspectedOrigin: String? = null,
    val notes: String? = null
)

data class ScanFinding(
    val type: RecoveryType,
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
    val scanSource: ScanSource,
    val confidence: Float,
    val status: RecoveryStatus,
    val notes: String?
)

sealed class ScanCommand {
    data object Pause : ScanCommand()
    data object Resume : ScanCommand()
    data object Cancel : ScanCommand()
}

data class ScanSummaryResult(
    val scanned: Int,
    val discovered: Int,
    val technicalLog: List<String>
)

