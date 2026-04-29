package com.forenserecovery.android.utils

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

private const val DEFAULT_BUFFER_SIZE = 8 * 1024

object FileHashUtils {
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read = input.read(buffer)
            while (read >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read)
                }
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read = input.read(buffer)
            while (read >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read)
                }
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun partialSha256(file: File, maxBytes: Long = 256 * 1024): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var remaining = maxBytes
            while (remaining > 0) {
                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                val read = input.read(buffer, 0, toRead)
                if (read <= 0) break
                digest.update(buffer, 0, read)
                remaining -= read.toLong()
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
