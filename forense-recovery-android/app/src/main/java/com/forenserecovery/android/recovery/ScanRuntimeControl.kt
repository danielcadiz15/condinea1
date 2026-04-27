package com.forenserecovery.android.recovery

import com.forenserecovery.android.scanner.ScanControlCenter

/**
 * Estado de control compartido entre UI y Worker.
 * Permite pausar/reanudar/cancelar durante una corrida activa.
 */
object ScanRuntimeControl {
    private val control = ScanControlCenter()
    @Volatile
    private var safTreeUri: String? = null

    fun controlCenter(): ScanControlCenter = control

    fun reset() = control.reset()

    fun pause() = control.pause()

    fun resume() = control.resume()

    fun cancel() = control.cancel()

    fun isCancelled(): Boolean = control.isCancelled()

    fun setSafTreeUri(uri: String?) {
        safTreeUri = uri
    }

    fun getSafTreeUri(): String? = safTreeUri
}
