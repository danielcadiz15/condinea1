package com.forenserecovery.android.recovery

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

enum class ShizukuAuthorizationState {
    Granted,
    Denied,
    NotInstalled,
    ServiceUnavailable,
    Unavailable
}

class ShizukuBridgeManager(
    private val context: Context
) {
    private val detector = ForensicCapabilityDetector

    fun currentState(): ShizukuAuthorizationState {
        val caps = detector.detect(context)
        if (!caps.shizukuInstalled) return ShizukuAuthorizationState.NotInstalled
        if (!caps.shizukuServiceRunning) return ShizukuAuthorizationState.ServiceUnavailable
        if (caps.shizukuPermissionGranted) return ShizukuAuthorizationState.Granted
        return ShizukuAuthorizationState.Denied
    }

    suspend fun requestAuthorization(): ShizukuAuthorizationState {
        val installed = detector.detect(context).shizukuInstalled
        if (!installed) return ShizukuAuthorizationState.NotInstalled

        if (!isServiceRunning()) {
            return ShizukuAuthorizationState.ServiceUnavailable
        }

        if (isPermissionGranted()) {
            return ShizukuAuthorizationState.Granted
        }

        return suspendCancellableCoroutine { continuation ->
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    if (requestCode != REQUEST_CODE) return
                    Shizuku.removeRequestPermissionResultListener(this)
                    if (continuation.isActive) {
                        continuation.resume(
                            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                                ShizukuAuthorizationState.Granted
                            } else {
                                ShizukuAuthorizationState.Denied
                            }
                        )
                    }
                }
            }

            continuation.invokeOnCancellation {
                Shizuku.removeRequestPermissionResultListener(listener)
            }
            Shizuku.addRequestPermissionResultListener(listener)
            runCatching {
                Shizuku.requestPermission(REQUEST_CODE)
            }.onFailure {
                Shizuku.removeRequestPermissionResultListener(listener)
                if (continuation.isActive) {
                    continuation.resume(ShizukuAuthorizationState.Unavailable)
                }
            }
        }
    }

    fun isServiceRunning(): Boolean {
        return runCatching {
            Shizuku.pingBinder() && Shizuku.getVersion() >= 10
        }.getOrDefault(false)
    }

    fun isPermissionGranted(): Boolean {
        return runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    companion object {
        private const val REQUEST_CODE = 11991
    }
}
