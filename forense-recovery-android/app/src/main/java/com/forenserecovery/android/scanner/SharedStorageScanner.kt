package com.forenserecovery.android.scanner

import android.content.Context
import android.os.Environment
import com.forenserecovery.android.domain.model.ScanMode
import com.forenserecovery.android.domain.model.ScanSource
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private const val MAX_WALK_DEPTH = 8
private const val MAX_FILE_SIZE_BYTES = 700L * 1024 * 1024

class SharedStorageScanner(
    context: Context
) {
    private val appExternal = context.getExternalFilesDir(null)
    private val root = Environment.getExternalStorageDirectory()

    suspend fun scan(
        mode: ScanMode,
        onProgress: suspend (File) -> Unit
    ): List<ScannerCandidate> = withContext(Dispatchers.IO) {
        val out = mutableListOf<ScannerCandidate>()
        val targetDirs = DirectoryTargets.buildCandidateDirectories(mode)
        for (dir in targetDirs) {
            if (!dir.exists() || !dir.canRead()) continue
            walk(dir, depth = 0, out = out, onProgress = onProgress)
        }
        out
    }

    private suspend fun walk(
        dir: File,
        depth: Int,
        out: MutableList<ScannerCandidate>,
        onProgress: suspend (File) -> Unit
    ) {
        currentCoroutineContext().ensureActive()
        if (depth > MAX_WALK_DEPTH) return
        val children = dir.listFiles() ?: return
        for (file in children) {
            currentCoroutineContext().ensureActive()
            if (!file.canRead()) continue
            if (shouldSkip(file)) continue
            if (file.isDirectory) {
                walk(file, depth + 1, out, onProgress)
            } else if (file.isFile && file.length() in 1..MAX_FILE_SIZE_BYTES) {
                onProgress(file)
                buildCandidate(file)?.let(out::add)
            }
        }
    }

    private fun buildCandidate(file: File): ScannerCandidate? {
        val header = ByteArray(256)
        val read = runCatching {
            FileInputStream(file).use { it.read(header) }
        }.getOrDefault(-1)
        if (read <= 0) return null
        val bytes = header.copyOf(read)
        val signature = MagicNumberDetector.detectStart(bytes) ?: MagicNumberDetector.inferFromPath(file.name)
        val path = file.absolutePath
        val lower = path.lowercase()
        val isLikelyThumbnail = (
            "thumb" in lower ||
                ".thumbnails" in lower ||
                (file.length() < 90_000 && (signature?.detectionType?.name == "IMAGE"))
            )
        val isPartial = signature == null || file.extension.isBlank() || file.extension.length > 5
        val source = when {
            ".thumbnails" in lower || "cache" in lower -> ScanSource.THUMBNAIL_CACHE
            else -> ScanSource.SHARED_STORAGE
        }
        return ScannerCandidate(
            sourcePath = path,
            displayName = file.name,
            sizeBytes = file.length(),
            mimeType = signature?.mimeType,
            modifiedAt = file.lastModified(),
            createdAt = null,
            source = source,
            originalUriString = null,
            width = null,
            height = null,
            duration = null,
            isLikelyThumbnail = isLikelyThumbnail,
            isPartialHint = isPartial,
            suspectedOrigin = guessOrigin(path)
        )
    }

    private fun guessOrigin(path: String): String? = when {
        path.contains("WhatsApp", ignoreCase = true) -> "WhatsApp"
        path.contains("Telegram", ignoreCase = true) -> "Telegram"
        path.contains("DCIM", ignoreCase = true) -> "Camera/DCIM"
        else -> null
    }

    private fun shouldSkip(file: File): Boolean {
        val path = file.absolutePath
        val appPath = appExternal?.absolutePath
        if (appPath != null && path.startsWith(appPath)) return true
        if (!path.startsWith(root.absolutePath)) return true
        return false
    }
}
