package com.forenserecovery.android.scanner

import android.webkit.MimeTypeMap
import com.forenserecovery.android.domain.model.RecoveryType

data class SignatureHit(
    val detectionType: RecoveryType,
    val mimeType: String,
    val extension: String
)

object MagicNumberDetector {
    private val signatures = listOf(
        SignatureRule(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()), RecoveryType.IMAGE, "image/jpeg", "jpg"),
        SignatureRule(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47), RecoveryType.IMAGE, "image/png", "png"),
        SignatureRule(byteArrayOf(0x52, 0x49, 0x46, 0x46), RecoveryType.IMAGE, "image/webp", "webp"),
        SignatureRule(byteArrayOf(0x4F, 0x67, 0x67, 0x53), RecoveryType.AUDIO, "audio/ogg", "ogg"),
        SignatureRule(byteArrayOf(0x49, 0x44, 0x33), RecoveryType.AUDIO, "audio/mpeg", "mp3"),
        SignatureRule(byteArrayOf(0xFF.toByte(), 0xFB.toByte()), RecoveryType.AUDIO, "audio/mpeg", "mp3"),
        SignatureRule("#!AMR".toByteArray(), RecoveryType.AUDIO, "audio/amr", "amr"),
        SignatureRule("%PDF".toByteArray(), RecoveryType.DOCUMENT, "application/pdf", "pdf")
    )

    fun detectStart(bytes: ByteArray): SignatureHit? {
        if (bytes.isEmpty()) return null
        signatures.forEach { rule ->
            if (startsWith(bytes, rule.pattern)) {
                return SignatureHit(rule.type, rule.mimeType, rule.extension)
            }
        }
        val ftypIndex = bytes.indexOfSubArray("ftyp".toByteArray())
        if (ftypIndex >= 0) {
            return SignatureHit(RecoveryType.VIDEO, "video/mp4", "mp4")
        }
        return null
    }

    fun inferFromPath(path: String?): SignatureHit? {
        if (path.isNullOrBlank()) return null
        val extension = path.substringAfterLast('.', "").lowercase()
        if (extension.isBlank()) return null
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: return null
        val type = when {
            mime.startsWith("image/") -> RecoveryType.IMAGE
            mime.startsWith("video/") -> RecoveryType.VIDEO
            mime.startsWith("audio/") -> RecoveryType.AUDIO
            mime == "application/pdf" -> RecoveryType.DOCUMENT
            else -> RecoveryType.UNKNOWN
        }
        return SignatureHit(type, mime, extension)
    }

    private fun startsWith(source: ByteArray, prefix: ByteArray): Boolean {
        if (source.size < prefix.size) return false
        for (i in prefix.indices) {
            if (source[i] != prefix[i]) return false
        }
        return true
    }

    private fun ByteArray.indexOfSubArray(target: ByteArray): Int {
        if (target.isEmpty() || target.size > size) return -1
        for (i in 0..(size - target.size)) {
            var match = true
            for (j in target.indices) {
                if (this[i + j] != target[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }
}

private data class SignatureRule(
    val pattern: ByteArray,
    val type: RecoveryType,
    val mimeType: String,
    val extension: String
)
