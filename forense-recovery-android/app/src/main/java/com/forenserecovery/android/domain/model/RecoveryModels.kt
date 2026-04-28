package com.forenserecovery.android.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class RecoveryType {
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    UNKNOWN
}

@Serializable
enum class RecoveryStatus {
    COMPLETE,
    PARTIAL,
    THUMBNAIL,
    CORRUPT,
    DUPLICATE
}

@Serializable
enum class ScanMode {
    BASIC,
    ADVANCED,
    FORENSIC
}

enum class ScanProfile(
    val label: String
) {
    QUICK("Rápido"),
    BALANCED("Balanceado"),
    DEEP("Profundo")
}

@Serializable
enum class ScanSource {
    MEDIA_STORE,
    SHARED_STORAGE,
    THUMBNAIL_CACHE,
    BLOB_CARVING,
    SAF
}

@Serializable
enum class SignatureType(
    val mimeType: String,
    val extensions: List<String>
) {
    JPG("image/jpeg", listOf("jpg", "jpeg")),
    PNG("image/png", listOf("png")),
    WEBP("image/webp", listOf("webp")),
    MP4("video/mp4", listOf("mp4", "m4v")),
    HEIC("image/heic", listOf("heic", "heif")),
    OGG("audio/ogg", listOf("ogg", "opus")),
    MP3("audio/mpeg", listOf("mp3")),
    AMR("audio/amr", listOf("amr")),
    PDF("application/pdf", listOf("pdf")),
    UNKNOWN("application/octet-stream", listOf("bin"))
}

@Serializable
data class RecoveryItem(
    val id: Long = 0,
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

data class ScanProgress(
    val scanned: Int = 0,
    val discovered: Int = 0,
    val expectedTotal: Int? = null,
    val currentPath: String = "",
    val stage: String = "",
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val warning: String? = null
)

data class ScanSummary(
    val totalScanned: Int,
    val totalRecovered: Int,
    val duplicates: Int,
    val partials: Int,
    val corrupts: Int,
    val thumbnails: Int
)

enum class ItemViewMode {
    GRID,
    LIST
}

enum class ItemFilter {
    ALL,
    IMAGE,
    VIDEO,
    AUDIO,
    THUMBNAIL,
    PARTIAL,
    CORRUPT
}
