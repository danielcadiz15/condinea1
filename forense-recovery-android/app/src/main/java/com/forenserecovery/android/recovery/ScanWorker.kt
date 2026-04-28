package com.forenserecovery.android.recovery

import android.content.Context
import androidx.work.Data
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.forenserecovery.android.ForenseRecoveryApplication
import com.forenserecovery.android.domain.model.ScanMode
import com.forenserecovery.android.scanner.ForensicEngine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ScanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as ForenseRecoveryApplication
        val mode = runCatching {
            ScanMode.valueOf(inputData.getString(KEY_MODE) ?: ScanMode.BASIC.name)
        }.getOrDefault(ScanMode.BASIC)
        val profile = inputData.getString(KEY_PROFILE)
        val clear = inputData.getBoolean(KEY_CLEAR_PREVIOUS, false)
        val safTreeUri = ScanRuntimeControl.getSafTreeUri()
        if (!profile.isNullOrBlank()) {
            ScanRuntimeControl.setScanProfile(profile)
        }
        val engine = ForensicEngine(applicationContext, app.recoveryRepository)

        return runCatching {
            val summary = engine.runScan(
                mode = mode,
                clearPrevious = clear,
                safTreeUri = safTreeUri
            ) { progress ->
                latestProgress.emit(progress)
                setProgress(
                    Data.Builder()
                        .putInt(KEY_PROGRESS_SCANNED, progress.scanned)
                        .putInt(KEY_PROGRESS_DISCOVERED, progress.discovered)
                        .putInt(KEY_PROGRESS_TOTAL, progress.expectedTotal ?: 0)
                        .putString(KEY_PROGRESS_STAGE, progress.stage)
                        .putString(KEY_PROGRESS_PATH, progress.currentPath)
                        .putString(KEY_PROGRESS_WARNING, progress.warning)
                        .build()
                )
            }
            latestTechnicalLog.emit(summary.technicalLog)
            Result.success(
                Data.Builder()
                    .putInt(KEY_OUT_SCANNED, summary.scanned)
                    .putInt(KEY_OUT_DISCOVERED, summary.discovered)
                    .putString(KEY_OUT_ERROR, "")
                    .build()
            )
        }.getOrElse { error ->
            Result.failure(
                Data.Builder()
                    .putInt(KEY_OUT_SCANNED, 0)
                    .putInt(KEY_OUT_DISCOVERED, 0)
                    .putString(KEY_OUT_ERROR, error.message ?: "Error desconocido")
                    .build()
            )
        }
    }

    companion object {
        const val KEY_MODE = "scan_mode"
        const val KEY_PROFILE = "scan_profile"
        const val KEY_CLEAR_PREVIOUS = "clear_previous"
        const val KEY_PROGRESS_SCANNED = "progress_scanned"
        const val KEY_PROGRESS_DISCOVERED = "progress_discovered"
        const val KEY_PROGRESS_TOTAL = "progress_total"
        const val KEY_PROGRESS_STAGE = "progress_stage"
        const val KEY_PROGRESS_PATH = "progress_path"
        const val KEY_PROGRESS_WARNING = "progress_warning"

        const val KEY_OUT_SCANNED = "out_scanned"
        const val KEY_OUT_DISCOVERED = "out_discovered"
        const val KEY_OUT_ERROR = "out_error"

        private val latestProgress = MutableSharedFlow<com.forenserecovery.android.domain.model.ScanProgress>(
            replay = 1,
            extraBufferCapacity = 4
        )
        private val latestTechnicalLog = MutableSharedFlow<List<String>>(replay = 1, extraBufferCapacity = 2)

        fun progressFlow(): SharedFlow<com.forenserecovery.android.domain.model.ScanProgress> = latestProgress.asSharedFlow()
        fun technicalLogFlow(): SharedFlow<List<String>> = latestTechnicalLog.asSharedFlow()
    }
}
