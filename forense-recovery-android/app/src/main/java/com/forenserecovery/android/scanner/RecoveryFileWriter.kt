package com.forenserecovery.android.scanner

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class RecoveryFileWriter(
    context: Context
) {
    private val outputRoot = File(context.filesDir, "recovered").apply { mkdirs() }

    fun writeCopy(sourceFile: File, extension: String, hint: String = sourceFile.nameWithoutExtension): File {
        val safeHint = hint.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val target = File(outputRoot, "${System.currentTimeMillis()}_${safeHint}.$extension")
        sourceFile.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    fun writeFragment(
        source: File,
        offset: Long,
        length: Long,
        extension: String,
        prefix: String = "fragment"
    ): File {
        val safeLength = length.coerceAtLeast(1L)
        val safeName = "${prefix}_${source.nameWithoutExtension}".replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val target = File(outputRoot, "${System.currentTimeMillis()}_${safeName}_${offset}.$extension")
        FileInputStream(source).use { input ->
            if (offset > 0) {
                input.skip(offset)
            }
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(8 * 1024)
                var remaining = safeLength
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    remaining -= read.toLong()
                }
            }
        }
        return target
    }
}
