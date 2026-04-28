package com.forenserecovery.android.recovery

import android.content.Context
import android.content.pm.PackageManager
import java.io.File

data class ForensicCapabilities(
    val shizukuInstalled: Boolean,
    val shizukuPermissionGranted: Boolean,
    val shizukuServiceRunning: Boolean,
    val adbExecutableVisible: Boolean
) {
    val hasAnyForensicBridge: Boolean
        get() = shizukuInstalled || adbExecutableVisible
}

object ForensicCapabilityDetector {
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    fun detect(context: Context): ForensicCapabilities {
        val shizukuInstalled = isShizukuInstalled(context)
        val shizukuBridge = ShizukuBridgeManager(context)
        val shizukuPermissionGranted = shizukuBridge.isPermissionGranted()
        val shizukuServiceRunning = shizukuBridge.isServiceRunning()
        val adbExecutableVisible = isAdbBinaryVisible()
        return ForensicCapabilities(
            shizukuInstalled = shizukuInstalled,
            shizukuPermissionGranted = shizukuPermissionGranted,
            shizukuServiceRunning = shizukuServiceRunning,
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
            detected.shizukuInstalled && detected.shizukuServiceRunning && detected.shizukuPermissionGranted ->
                "Shizuku detectado, servicio activo y permiso concedido. Integración forense lista."

            detected.shizukuInstalled && detected.shizukuServiceRunning ->
                "Shizuku detectado y activo, falta conceder permiso a esta app."

            detected.shizukuInstalled ->
                "Shizuku detectado, pero el servicio aún no está activo."

            detected.adbExecutableVisible ->
                "ADB visible en el sistema. Integración forense limitada disponible."

            else ->
                "Sin puente forense detectado (Shizuku/ADB). El modo forense opera con capacidades locales estándar."
        }
    }
}
