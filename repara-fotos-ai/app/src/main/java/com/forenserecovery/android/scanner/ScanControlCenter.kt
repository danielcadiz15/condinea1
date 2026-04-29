package com.forenserecovery.android.scanner

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScanControlCenter {
    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()
    private val cancelled = AtomicBoolean(false)

    fun pause() {
        _paused.value = true
    }

    fun resume() {
        _paused.value = false
    }

    fun cancel() {
        cancelled.set(true)
        _paused.value = false
    }

    fun isCancelled(): Boolean = cancelled.get()

    fun isPaused(): Boolean = _paused.value

    suspend fun awaitIfPaused() {
        while (_paused.value && !cancelled.get()) {
            delay(150)
        }
    }

    fun reset() {
        cancelled.set(false)
        _paused.value = false
    }
}
