package com.forenserecovery.android.recovery

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.forenserecovery.android.domain.model.RecoveryItem
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RestoreResult(
    val restoredCount: Int,
    val failedCount: Int,
    val requestedCount: Int,
    val destinationUri: String
)

class RestoreManager(
    private val context: Context
) {
    suspend fun restoreItems(
        items: List<RecoveryItem>,
        destinationTreeUri: String
    ): RestoreResult = withContext(Dispatchers.IO) {
        val destination = Uri.parse(destinationTreeUri)
        val root = DocumentFile.fromTreeUri(context, destination)
            ?: return@withContext RestoreResult(
                restoredCount = 0,
                failedCount = items.size,
                requestedCount = items.size,
                destinationUri = destinationTreeUri
            )

        var restored = 0
        var failed = 0
        val destinationLabel = root.uri.toString()

        items.forEachIndexed { index, item ->
            val sourcePath = item.recoveredPath ?: item.originalPath
            if (sourcePath.isNullOrBlank()) {
                failed += 1
                return@forEachIndexed
            }
            val input = openSourceInput(sourcePath)
            if (input == null) {
                failed += 1
                return@forEachIndexed
            }

            input.use { source ->
                val targetName = buildTargetName(item = item, sourcePath = sourcePath, index = index)
                val outDoc = root.createFile(item.mimeType ?: "application/octet-stream", targetName)
                if (outDoc == null) {
                    failed += 1
                    return@use
                }
                val output = context.contentResolver.openOutputStream(outDoc.uri, "w")
                if (output == null) {
                    failed += 1
                    return@use
                }
                output.use { target ->
                    source.copyTo(target)
                    restored += 1
                }
            }
        }

        RestoreResult(
            restoredCount = restored,
            failedCount = failed,
            requestedCount = items.size,
            destinationUri = destinationLabel
        )
    }

    private fun openSourceInput(sourcePath: String): InputStream? {
        return if (sourcePath.startsWith("content://")) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(sourcePath))
            }.getOrNull()
        } else {
            val file = File(sourcePath)
            if (!file.exists() || !file.isFile || !file.canRead()) {
                null
            } else {
                runCatching { file.inputStream() }.getOrNull()
            }
        }
    }

    private fun buildTargetName(item: RecoveryItem, sourcePath: String, index: Int): String {
        val rawName = sourcePath
            .substringAfterLast('/')
            .substringBefore('?')
            .ifBlank { "restored_${item.id}" }
        val safeName = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return "restored_${System.currentTimeMillis()}_${index}_$safeName"
    }
}
