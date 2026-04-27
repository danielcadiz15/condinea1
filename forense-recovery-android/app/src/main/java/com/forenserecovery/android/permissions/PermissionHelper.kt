package com.forenserecovery.android.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.forenserecovery.android.domain.model.ScanMode
import android.content.pm.PackageManager

object PermissionHelper {
    fun requiredPermissions(mode: ScanMode): Array<String> {
        val mediaPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return when (mode) {
            ScanMode.BASIC -> mediaPermissions
            ScanMode.ADVANCED, ScanMode.FORENSIC -> mediaPermissions
        }
    }

    fun canUseManageExternalStorage(mode: ScanMode): Boolean {
        return mode != ScanMode.BASIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    fun hasManageExternalStorage(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    }

    fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun buildManageAllFilesAccessIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }
    }

    fun modeDescription(mode: ScanMode): String {
        return when (mode) {
            ScanMode.BASIC -> "Modo básico: usa permisos estándar de medios (MediaStore)."
            ScanMode.ADVANCED -> "Modo avanzado: requiere acceso ampliado para explorar más rutas compartidas."
            ScanMode.FORENSIC -> "Modo forense: añade compatibilidad opcional con Shizuku/ADB, según disponibilidad."
        }
    }
}
