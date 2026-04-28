package com.forenserecovery.android.scanner

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import com.forenserecovery.android.domain.model.RecoveryItem
import com.forenserecovery.android.domain.model.RecoveryStatus
import com.forenserecovery.android.domain.model.RecoveryType
import com.forenserecovery.android.domain.model.ScanMode
import com.forenserecovery.android.domain.model.ScanProgress
import com.forenserecovery.android.domain.model.ScanSource
import com.forenserecovery.android.domain.repository.RecoveryRepository
import com.forenserecovery.android.recovery.ScanRuntimeControl
import com.forenserecovery.android.utils.FileHashUtils
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private const val SIGNATURE_SCAN_LIMIT_BYTES = 4 * 1024 * 1024
private const val CARVING_MAX_RESULTS_PER_FILE = 20
private const val CARVING_MIN_FRAGMENT_BYTES = 1024

class ForensicEngine(
    context: Context,
    private val repository: RecoveryRepository
) {

    private val mediaStoreScanner = MediaStoreScanner(context)
    private val sharedStorageScanner = SharedStorageScanner(context)
    private val safScanner = SafScanner(context)
    private val controlCenter = ScanRuntimeControl.controlCenter()
    private val writer = RecoveryFileWriter(context)

    suspend fun runScan(
        mode: ScanMode,
        clearPrevious: Boolean,
        safTreeUri: String?,
        onProgress: suspend (ScanProgress) -> Unit
    ): ScanSummaryResult = withContext(Dispatchers.IO) {
        if (clearPrevious) repository.clear()
        controlCenter.resume()
        var scanned = 0
        var discovered = 0
        val technicalLog = mutableListOf<String>()

        suspend fun tick(
            stage: String,
            path: String = "",
            warning: String? = null,
            running: Boolean = true
        ) {
            onProgress(
                ScanProgress(
                    scanned = scanned,
                    discovered = discovered,
                    currentPath = path,
                    stage = stage,
                    isRunning = running,
                    isPaused = controlCenter.isPaused(),
                    warning = warning
                )
            )
        }

        tick(stage = "Preparando escaneo")

        try {
            val mediaHits = mediaStoreScanner.scan { current ->
                controlCenter.awaitIfPaused()
                currentCoroutineContext().ensureActive()
                if (controlCenter.isCancelled()) return@scan
                scanned += 1
                tick(stage = "Escaneando MediaStore", path = current)
            }

            for (hit in mediaHits) {
                currentCoroutineContext().ensureActive()
                if (controlCenter.isCancelled()) break
                controlCenter.awaitIfPaused()
                if (ingestCandidate(hit, technicalLog)) discovered += 1
            }

            if (!controlCenter.isCancelled()) {
                val storageHits = sharedStorageScanner.scan(mode) { current ->
                    controlCenter.awaitIfPaused()
                    currentCoroutineContext().ensureActive()
                    if (controlCenter.isCancelled()) return@scan
                    scanned += 1
                    tick(stage = "Escaneando almacenamiento compartido", path = current.absolutePath)
                }

                for (hit in storageHits) {
                    currentCoroutineContext().ensureActive()
                    if (controlCenter.isCancelled()) break
                    controlCenter.awaitIfPaused()
                    if (ingestCandidate(hit, technicalLog)) discovered += 1
                }
            }

            if (!controlCenter.isCancelled() && !safTreeUri.isNullOrBlank()) {
                val safHits = safScanner.scanTreeUri(safTreeUri) { currentPath ->
                    controlCenter.awaitIfPaused()
                    currentCoroutineContext().ensureActive()
                    if (!controlCenter.isCancelled()) {
                        scanned += 1
                        tick(stage = "Escaneando árbol SAF", path = currentPath)
                    }
                }
                for (hit in safHits) {
                    currentCoroutineContext().ensureActive()
                    if (controlCenter.isCancelled()) break
                    controlCenter.awaitIfPaused()
                    if (ingestCandidate(hit, technicalLog)) discovered += 1
                }
            }
        } finally {
            controlCenter.resume()
        }

        val stage = if (controlCenter.isCancelled()) "Escaneo cancelado" else "Escaneo finalizado"
        tick(stage = stage, running = false)
        ScanSummaryResult(scanned = scanned, discovered = discovered, technicalLog = technicalLog)
    }

    private suspend fun ingestCandidate(hit: ScannerCandidate, technicalLog: MutableList<String>): Boolean {
        return runCatching {
            val original = hit.sourcePath?.let(::File)
            val isFileReadable = original?.exists() == true && original.canRead() && original.isFile
            val signature = when {
                isFileReadable -> detectByHeader(original!!)
                else -> MagicNumberDetector.inferFromPath(hit.sourcePath ?: hit.displayName)
            }

            val type = signature?.detectionType ?: run {
                when {
                    (hit.mimeType ?: "").startsWith("image/") -> RecoveryType.IMAGE
                    (hit.mimeType ?: "").startsWith("video/") -> RecoveryType.VIDEO
                    (hit.mimeType ?: "").startsWith("audio/") -> RecoveryType.AUDIO
                    else -> RecoveryType.UNKNOWN
                }
            }
            val mimeType = signature?.mimeType ?: hit.mimeType

            val readableFile = if (isFileReadable) original else null

            val dimensions = if (readableFile != null && type == RecoveryType.IMAGE) readImageMetadata(readableFile) else null
            val duration = if (readableFile != null && (type == RecoveryType.VIDEO || type == RecoveryType.AUDIO)) {
                readDuration(readableFile)
            } else {
                null
            }

            val sha = when {
                readableFile != null -> FileHashUtils.sha256(readableFile)
                else -> (hit.sourcePath ?: hit.originalUriString)?.hashCode()?.toString()
            }

            if (!sha.isNullOrBlank() && repository.findBySha256(sha) != null) {
                val duplicate = RecoveryItem(
                    type = type,
                    mimeType = mimeType,
                    originalPath = hit.sourcePath ?: hit.originalUriString,
                    recoveredPath = null,
                    sizeBytes = hit.sizeBytes,
                    sha256 = sha,
                    width = dimensions?.first,
                    height = dimensions?.second,
                    duration = duration,
                    createdAt = hit.createdAt,
                    modifiedAt = hit.modifiedAt,
                    scanSource = hit.source,
                    confidence = 0.99f,
                    status = RecoveryStatus.DUPLICATE,
                    notes = "Mismo SHA-256 detectado; posible duplicado."
                )
                repository.upsert(duplicate)
                technicalLog += "DUPLICATE ${hit.sourcePath ?: "sin_path"}"
                return false
            }

            val thumbnail = hit.isLikelyThumbnail || isThumbnailFile(hit.sourcePath, hit.sizeBytes, dimensions)
            val carvingNotes = mutableListOf<String>()
            var recoveredPath: String? = null
            var status = when {
                thumbnail -> RecoveryStatus.THUMBNAIL
                hit.isPartialHint -> RecoveryStatus.PARTIAL
                else -> RecoveryStatus.COMPLETE
            }

            if (readableFile != null && hit.sizeBytes > SIGNATURE_SCAN_LIMIT_BYTES) {
                val carveCount = carveFromFile(readableFile, type, mimeType, hit.source, hit.sourcePath, technicalLog)
                if (carveCount > 0) {
                    carvingNotes += "Se extrajeron $carveCount fragmentos internos por firmas."
                    status = RecoveryStatus.PARTIAL
                }
            } else if (readableFile != null && hit.isPartialHint) {
                val extension = signature?.extension ?: fileExtensionForType(type)
                val out = writer.writeCopy(readableFile, extension, hit.sourcePath ?: "fragment")
                recoveredPath = out.absolutePath
            }

            val notes = buildString {
                append(hit.notes ?: "")
                if (hit.suspectedOrigin != null) append(" Posible origen: ${hit.suspectedOrigin}.")
                if (carvingNotes.isNotEmpty()) append(" ${carvingNotes.joinToString(" ")}")
            }.trim().ifBlank { null }

            val item = RecoveryItem(
                type = type,
                mimeType = mimeType,
                originalPath = hit.sourcePath ?: hit.originalUriString,
                recoveredPath = recoveredPath,
                sizeBytes = hit.sizeBytes,
                sha256 = sha,
                width = dimensions?.first,
                height = dimensions?.second,
                duration = duration,
                createdAt = hit.createdAt,
                modifiedAt = hit.modifiedAt,
                scanSource = hit.source,
                confidence = computeConfidence(hit, signature != null, thumbnail),
                status = status,
                notes = notes
            )
            repository.upsert(item)
            technicalLog += "FOUND ${hit.sourcePath ?: "sin_path"} status=${status.name}"
            true
        }.getOrElse { error ->
            technicalLog += "ERROR ${hit.sourcePath ?: "sin_path"} -> ${error.message}"
            false
        }
    }

    private fun isThumbnailFile(path: String?, sizeBytes: Long, dimensions: Pair<Int, Int>?): Boolean {
        if (path.isNullOrBlank()) return false
        val lower = path.lowercase()
        if ("thumb" in lower || ".thumbnails" in lower) return true
        if (sizeBytes in 1..90_000 && dimensions != null) {
            return dimensions.first <= 320 && dimensions.second <= 320
        }
        return false
    }

    private fun readImageMetadata(file: File): Pair<Int, Int>? {
        val fromBounds = runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                options.outWidth to options.outHeight
            } else {
                null
            }
        }.getOrNull()
        if (fromBounds != null) return fromBounds
        return runCatching {
            val exif = ExifInterface(file.absolutePath)
            val width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
            val height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
            if (width > 0 && height > 0) width to height else null
        }.getOrNull()
    }

    private fun readDuration(file: File): Long? {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            retriever.release()
            durationMs
        }.getOrNull()
    }

    private fun detectByHeader(file: File): SignatureHit? {
        val headerSize = minOf(file.length().toInt(), 1024)
        if (headerSize <= 0) return null
        return runCatching {
            FileInputStream(file).use { input ->
                val header = ByteArray(headerSize)
                val read = input.read(header)
                if (read <= 0) null else MagicNumberDetector.detectStart(header.copyOf(read))
            }
        }.getOrNull()
    }

    private suspend fun carveFromFile(
        source: File,
        defaultType: RecoveryType,
        defaultMime: String?,
        sourceType: ScanSource,
        originalPath: String?,
        technicalLog: MutableList<String>
    ): Int {
        val header = ByteArray(minOf(source.length().toInt(), SIGNATURE_SCAN_LIMIT_BYTES))
        val read = runCatching {
            FileInputStream(source).use { input -> input.read(header) }
        }.getOrDefault(-1)
        if (read <= 0) return 0

        var carved = 0
        val slice = header.copyOf(read)
        for (offset in slice.indices) {
            if (carved >= CARVING_MAX_RESULTS_PER_FILE) break
            if (offset + 8 >= slice.size) break
            val window = slice.copyOfRange(offset, minOf(offset + 64, slice.size))
            val hit = MagicNumberDetector.detectStart(window) ?: continue
            val remaining = source.length() - offset
            if (remaining < CARVING_MIN_FRAGMENT_BYTES) continue
            val maxLen = minOf(remaining, 3L * 1024 * 1024)
            val output = writer.writeFragment(
                source = source,
                offset = offset.toLong(),
                length = maxLen,
                extension = hit.extension,
                prefix = "carve"
            )
            val fragmentSha = FileHashUtils.partialSha256(output)
            if (repository.findBySha256(fragmentSha) != null) {
                output.delete()
                continue
            }
            val fragmentItem = RecoveryItem(
                type = hit.detectionType.takeIf { it != RecoveryType.UNKNOWN } ?: defaultType,
                mimeType = hit.mimeType.ifBlank { defaultMime },
                originalPath = originalPath,
                recoveredPath = output.absolutePath,
                sizeBytes = output.length(),
                sha256 = fragmentSha,
                width = null,
                height = null,
                duration = null,
                createdAt = source.lastModified(),
                modifiedAt = output.lastModified(),
                scanSource = ScanSource.BLOB_CARVING.takeIf { sourceType != ScanSource.MEDIA_STORE } ?: sourceType,
                confidence = 0.55f,
                status = RecoveryStatus.PARTIAL,
                notes = "Fragmento extraído por firma en offset=$offset."
            )
            repository.upsert(fragmentItem)
            technicalLog += "CARVED ${output.absolutePath} from ${source.absolutePath}"
            carved += 1
        }
        return carved
    }

    private fun fileExtensionForType(type: RecoveryType): String = when (type) {
        RecoveryType.IMAGE -> "jpg"
        RecoveryType.VIDEO -> "mp4"
        RecoveryType.AUDIO -> "mp3"
        RecoveryType.DOCUMENT -> "pdf"
        RecoveryType.UNKNOWN -> "bin"
    }

    private fun computeConfidence(hit: ScannerCandidate, signatureMatched: Boolean, thumbnail: Boolean): Float {
        var score = 0.50f
        if (signatureMatched) score += 0.30f
        if (hit.mimeType != null) score += 0.10f
        if (hit.isPartialHint) score -= 0.15f
        if (thumbnail) score -= 0.10f
        return score.coerceIn(0.05f, 0.99f)
    }
}

