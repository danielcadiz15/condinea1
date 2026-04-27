package com.forenserecovery.android.recovery

import android.content.Context
import android.content.pm.PackageManager
import java.io.File

data class ForensicCapabilities(
    val shizukuInstalled: Boolean,
    val adbExecutableVisible: Boolean
) {
    val hasAnyForensicBridge: Boolean
        get() = shizukuInstalled || adbExecutableVisible
}

object ForensicCapabilityDetector {
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    fun detect(context: Context): ForensicCapabilities {
        val shizukuInstalled = isShizukuInstalled(context)
        val adbExecutableVisible = isAdbBinaryVisible()
        return ForensicCapabilities(
            shizukuInstalled = shizukuInstalled,
            adbExecutableVisible = adbExecutableVisible
        )
    }

    private fun isShizukuInstalled(context: Context): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            true
        }.recoverCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        }.getOrDefault(false)
    }

    private fun isAdbBinaryVisible(): Boolean {
        val candidates = listOf(
            "/system/bin/adb",
            "/system/xbin/adb",
            "/vendor/bin/adb"
        )
        return candidates.any { path ->
            val file = File(path)
            file.exists() && file.canExecute()
        }
    }

    fun describeForensicCapability(context: Context): String {
        val detected = detect(context)
        return when {
            detected.shizukuInstalled && detected.adbExecutableVisible ->
                "Shizuku detectado y binario ADB visible. Modo forense ampliado disponible."

            detected.shizukuInstalled ->
                "Shizuku detectado. Puedes habilitar puente forense opcional."

            detected.adbExecutableVisible ->
                "ADB visible en el sistema. Integración forense limitada disponible."

            else ->
                "Sin puente forense detectado (Shizuku/ADB). El modo forense opera con capacidades locales estándar."
        }
    }
}
