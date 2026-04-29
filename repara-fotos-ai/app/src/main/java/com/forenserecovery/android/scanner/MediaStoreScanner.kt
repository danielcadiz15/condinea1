package com.forenserecovery.android.scanner

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.forenserecovery.android.domain.model.RecoveryType
import com.forenserecovery.android.domain.model.ScanSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreScanner(
    private val context: Context
) {
    suspend fun scan(
        onProgress: suspend (String) -> Unit
    ): List<ScannerCandidate> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val candidates = mutableListOf<ScannerCandidate>()
        val targets = listOf(
            Triple(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, RecoveryType.IMAGE, ScanSource.MEDIA_STORE),
            Triple(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, RecoveryType.VIDEO, ScanSource.MEDIA_STORE),
            Triple(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, RecoveryType.AUDIO, ScanSource.MEDIA_STORE)
        )

        for ((uri, fallbackType, source) in targets) {
            queryMedia(
                resolver = resolver,
                collection = uri
            ) { cursor ->
                val row = cursorToCandidate(cursor, uri, source) ?: return@queryMedia
                onProgress(row.sourcePath ?: row.displayName ?: "content-item")
                val hit = MagicNumberDetector.inferFromPath(row.displayName)
                    ?: MagicNumberDetector.inferFromPath(row.sourcePath)
                val classified = row.copy(
                    mimeType = hit?.mimeType ?: row.mimeType,
                    suspectedOrigin = hit?.detectionType?.takeIf { it != RecoveryType.UNKNOWN }?.name
                        ?: fallbackType.name,
                    notes = "Registro detectado por MediaStore"
                )
                candidates += classified
            }
        }

        candidates
    }

    @Suppress("DEPRECATION")
    private suspend fun queryMedia(
        resolver: ContentResolver,
        collection: Uri,
        onRow: suspend (Cursor) -> Unit
    ) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DATA,
            MediaStore.Video.VideoColumns.DURATION
        )
        resolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                onRow(cursor)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun cursorToCandidate(
        cursor: Cursor,
        collection: Uri,
        source: ScanSource
    ): ScannerCandidate? {
        val id = cursor.getLongOrNull(MediaStore.MediaColumns._ID) ?: return null
        val displayName = cursor.getStringOrNull(MediaStore.MediaColumns.DISPLAY_NAME) ?: "unknown_$id"
        val uri = Uri.withAppendedPath(collection, id.toString()).toString()
        val path = cursor.getStringOrNull(MediaStore.MediaColumns.DATA)
        val size = cursor.getLongOrNull(MediaStore.MediaColumns.SIZE) ?: 0L
        val createdAt = cursor.getLongOrNull(MediaStore.MediaColumns.DATE_ADDED)?.times(1000)
        val modifiedAt = cursor.getLongOrNull(MediaStore.MediaColumns.DATE_MODIFIED)?.times(1000)
        val width = cursor.getIntOrNull(MediaStore.MediaColumns.WIDTH)
        val height = cursor.getIntOrNull(MediaStore.MediaColumns.HEIGHT)
        val duration = cursor.getLongOrNull(MediaStore.Video.VideoColumns.DURATION)
        val sourcePath = path ?: uri

        return ScannerCandidate(
            sourcePath = sourcePath,
            displayName = displayName,
            sizeBytes = size,
            mimeType = cursor.getStringOrNull(MediaStore.MediaColumns.MIME_TYPE),
            modifiedAt = modifiedAt,
            createdAt = createdAt,
            source = source,
            originalUriString = uri,
            width = width,
            height = height,
            duration = duration,
            isLikelyThumbnail = displayName.contains("thumb", ignoreCase = true)
        )
    }
}

private fun Cursor.getStringOrNull(columnName: String): String? {
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getString(index) else null
}

private fun Cursor.getLongOrNull(columnName: String): Long? {
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getLong(index) else null
}

private fun Cursor.getIntOrNull(columnName: String): Int? {
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getInt(index) else null
}
