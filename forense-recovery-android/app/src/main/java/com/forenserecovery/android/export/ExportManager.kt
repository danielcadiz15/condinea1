package com.forenserecovery.android.export

import android.content.Context
import com.forenserecovery.android.domain.model.RecoveryItem
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class ExportArtifacts(
    val csv: File,
    val json: File,
    val html: File,
    val zip: File,
    val log: File
)

class ExportManager(
    private val context: Context
) {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun exportAll(items: List<RecoveryItem>, technicalLog: List<String>): ExportArtifacts {
        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val csv = File(exportDir, "recovery_$stamp.csv")
        val jsonFile = File(exportDir, "recovery_$stamp.json")
        val html = File(exportDir, "recovery_$stamp.html")
        val log = File(exportDir, "scan_$stamp.log")
        val zip = File(exportDir, "recovered_$stamp.zip")

        csv.writeText(buildCsv(items))
        jsonFile.writeText(json.encodeToString(items))
        html.writeText(buildHtml(items))
        log.writeText(technicalLog.joinToString("\n"))
        buildRecoveredZip(items, zip)

        return ExportArtifacts(csv = csv, json = jsonFile, html = html, zip = zip, log = log)
    }

    private fun buildCsv(items: List<RecoveryItem>): String {
        val header = "id,type,mimeType,originalPath,recoveredPath,sizeBytes,sha256,width,height,duration,createdAt,modifiedAt,scanSource,confidence,status,notes"
        val rows = items.map { item ->
            listOf(
                item.id.toString(),
                item.type.name,
                item.mimeType.orEmpty(),
                item.originalPath.orEmpty(),
                item.recoveredPath.orEmpty(),
                item.sizeBytes.toString(),
                item.sha256.orEmpty(),
                item.width?.toString().orEmpty(),
                item.height?.toString().orEmpty(),
                item.duration?.toString().orEmpty(),
                item.createdAt?.toString().orEmpty(),
                item.modifiedAt?.toString().orEmpty(),
                item.scanSource.name,
                item.confidence.toString(),
                item.status.name,
                item.notes.orEmpty()
            ).joinToString(",") { escapeCsv(it) }
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun buildHtml(items: List<RecoveryItem>): String {
        val rows = items.joinToString("\n") { item ->
            val thumb = item.recoveredPath?.let { path ->
                if ((item.mimeType ?: "").startsWith("image/")) {
                    """<img src="$path" alt="thumb" style="width:96px;height:96px;object-fit:cover;" />"""
                } else {
                    "-"
                }
            } ?: "-"
            """
            <tr>
                <td>${item.id}</td>
                <td>${item.type}</td>
                <td>${item.mimeType.orEmpty()}</td>
                <td>${item.status}</td>
                <td>${item.scanSource}</td>
                <td>${item.sizeBytes}</td>
                <td>${item.confidence}</td>
                <td>$thumb</td>
            </tr>
            """.trimIndent()
        }

        return """
            <!doctype html>
            <html lang="es">
            <head>
              <meta charset="utf-8" />
              <title>Forense Recovery Android - Reporte</title>
              <style>
                body { font-family: sans-serif; margin: 20px; }
                table { border-collapse: collapse; width: 100%; }
                th, td { border: 1px solid #ddd; padding: 8px; vertical-align: top; }
                th { background: #f0f0f0; }
              </style>
            </head>
            <body>
              <h1>Forense Recovery Android - Reporte</h1>
              <p>Este reporte resume hallazgos locales. No garantiza recuperación completa de datos eliminados.</p>
              <table>
                <thead>
                  <tr>
                    <th>ID</th><th>Tipo</th><th>MIME</th><th>Estado</th><th>Origen</th><th>Tamaño</th><th>Confianza</th><th>Miniatura</th>
                  </tr>
                </thead>
                <tbody>
                  $rows
                </tbody>
              </table>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildRecoveredZip(items: List<RecoveryItem>, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            items.forEach { item ->
                val path = item.recoveredPath ?: return@forEach
                val file = File(path)
                if (!file.exists() || !file.isFile || !file.canRead()) return@forEach
                val entry = ZipEntry(file.name)
                zos.putNextEntry(entry)
                file.inputStream().use { input -> input.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }
}
