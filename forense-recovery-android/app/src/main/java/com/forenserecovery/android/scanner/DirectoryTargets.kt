package com.forenserecovery.android.scanner

import android.os.Environment
import com.forenserecovery.android.domain.model.ScanMode
import java.io.File

object DirectoryTargets {
    fun buildCandidateDirectories(mode: ScanMode): List<File> {
        val external = Environment.getExternalStorageDirectory()
        val baseDirs = listOf(
            File(external, "DCIM"),
            File(external, "Pictures"),
            File(external, "Movies"),
            File(external, "Music"),
            File(external, "Download"),
            File(external, "WhatsApp/Media"),
            File(external, "Telegram"),
            File(external, "Android/media"),
            File(external, "DCIM/.thumbnails"),
            File(external, "Pictures/.thumbnails")
        )

        val advancedOnly = listOf(
            File(external, "Android/data"),
            File(external, "Android/obb")
        )

        val all = when (mode) {
            ScanMode.BASIC -> baseDirs
            ScanMode.ADVANCED, ScanMode.FORENSIC -> baseDirs + advancedOnly
        }
        return all.distinctBy { it.absolutePath }
    }
}
