package com.forenserecovery.android.scanner

import android.content.Context
import android.graphics.BitmapFactory
import androidx.documentfile.provider.DocumentFile
import com.forenserecovery.android.domain.model.ScanSource
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private const val SAF_MAX_FILE_SIZE = 700L * 1024 * 1024
private const val SAF_MAX_DEPTH = 8

class SafScanner(
    private val context: Context
) {
    suspend fun scanTreeUri(
        treeUri: String,
        onProgress: suspend (String) -> Unit
    ): List<ScannerCandidate> = withContext(Dispatchers.IO) {
        val root = runCatching {
            DocumentFile.fromTreeUri(context, android.net.Uri.parse(treeUri))
        }.getOrNull() ?: return@withContext emptyList()

        if (!root.canRead()) return@withContext emptyList()

        val out = mutableListOf<ScannerCandidate>()
        walk(root, depth = 0, out = out, onProgress = onProgress)
        out
    }

    private suspend fun walk(
        node: DocumentFile,
        depth: Int,
        out: MutableList<ScannerCandidate>,
        onProgress: suspend (String) -> Unit
    ) {
        currentCoroutineContext().ensureActive()
        if (depth > SAF_MAX_DEPTH || !node.canRead()) return
        if (node.isDirectory) {
            node.listFiles().forEach { child ->
                walk(child, depth + 1, out, onProgress)
            }
            return
        }
        if (!node.isFile) return
        val size = node.length()
        if (size <= 0 || size > SAF_MAX_FILE_SIZE) return

        val label = node.name ?: node.uri.toString()
        onProgress(label)
        buildCandidate(node)?.let(out::add)
    }

    private fun buildCandidate(file: DocumentFile): ScannerCandidate? {
        val header = ByteArray(256)
        val bytes = runCatching {
            context.contentResolver.openInputStream(file.uri)?.use { input ->
                val read = input.read(header)
                if (read <= 0) return@use null
                header.copyOf(read)
            }
        }.getOrNull() ?: return null

        val signature = MagicNumberDetector.detectStart(bytes)
            ?: MagicNumberDetector.inferFromPath(file.name)
        val displayName = file.name ?: "saf_file"
        val lower = displayName.lowercase()
        val size = file.length()
        val dims = extractImageBoundsIfPossible(file)
        val isThumb = "thumb" in lower || (dims != null && size in 1..90_000)

        return ScannerCandidate(
            sourcePath = file.uri.toString(),
            displayName = displayName,
            sizeBytes = size,
            mimeType = signature?.mimeType ?: file.type,
            modifiedAt = file.lastModified(),
            createdAt = null,
            source = ScanSource.SAF,
            originalUriString = file.uri.toString(),
            width = dims?.first,
            height = dims?.second,
            duration = null,
            isLikelyThumbnail = isThumb,
            isPartialHint = signature == null,
            suspectedOrigin = "SAF"
        )
    }

    private fun extractImageBoundsIfPossible(file: DocumentFile): Pair<Int, Int>? {
        val mime = file.type ?: return null
        if (!mime.startsWith("image/")) return null
        return runCatching {
            context.contentResolver.openInputStream(file.uri)?.use { input: InputStream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, options)
                if (options.outWidth > 0 && options.outHeight > 0) {
                    options.outWidth to options.outHeight
                } else {
                    null
                }
            }
        }.getOrNull()
    }
}
