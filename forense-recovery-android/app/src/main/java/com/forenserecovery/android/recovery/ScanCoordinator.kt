package com.forenserecovery.android.recovery

import android.content.Context
import androidx.lifecycle.asFlow
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.forenserecovery.android.domain.model.ScanMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private const val UNIQUE_SCAN_WORK = "forense_scan_work"

class ScanCoordinator(
    private val context: Context
) {
    private val workManager = WorkManager.getInstance(this.context)

    fun enqueueScan(mode: ScanMode, clearPrevious: Boolean = false): UUID {
        val request = OneTimeWorkRequestBuilder<ScanWorker>()
            .setInputData(
                Data.Builder()
                    .putString(ScanWorker.KEY_MODE, mode.name)
                    .putBoolean(ScanWorker.KEY_CLEAR_PREVIOUS, clearPrevious)
                    .build()
            )
            .build()
        workManager.enqueueUniqueWork(
            UNIQUE_SCAN_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
        return request.id
    }

    fun cancelScan() {
        workManager.cancelUniqueWork(UNIQUE_SCAN_WORK)
    }

    fun observeScanState(): Flow<ScanWorkerState> {
        return workManager
            .getWorkInfosForUniqueWorkLiveData(UNIQUE_SCAN_WORK)
            .asFlow()
            .map { infos ->
                val info = infos.firstOrNull()
                when {
                    info == null -> ScanWorkerState.Idle
                    info.state == WorkInfo.State.SUCCEEDED -> {
                        ScanWorkerState.Finished(
                            scanned = info.outputData.getInt(ScanWorker.KEY_OUT_SCANNED, 0),
                            discovered = info.outputData.getInt(ScanWorker.KEY_OUT_DISCOVERED, 0)
                        )
                    }

                    info.state == WorkInfo.State.FAILED -> {
                        ScanWorkerState.Error(info.outputData.getString(ScanWorker.KEY_OUT_ERROR))
                    }

                    info.state == WorkInfo.State.CANCELLED -> ScanWorkerState.Cancelled
                    info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED -> {
                        ScanWorkerState.Running(
                            scanned = info.progress.getInt(ScanWorker.KEY_PROGRESS_SCANNED, 0),
                            discovered = info.progress.getInt(ScanWorker.KEY_PROGRESS_DISCOVERED, 0),
                            stage = info.progress.getString(ScanWorker.KEY_PROGRESS_STAGE).orEmpty(),
                            currentPath = info.progress.getString(ScanWorker.KEY_PROGRESS_PATH).orEmpty(),
                            warning = info.progress.getString(ScanWorker.KEY_PROGRESS_WARNING)
                        )
                    }

                    else -> ScanWorkerState.Idle
                }
            }
    }
}

sealed class ScanWorkerState {
    data object Idle : ScanWorkerState()
    data object Cancelled : ScanWorkerState()
    data class Running(
        val scanned: Int,
        val discovered: Int,
        val stage: String,
        val currentPath: String,
        val warning: String?
    ) : ScanWorkerState()

    data class Finished(
        val scanned: Int,
        val discovered: Int
    ) : ScanWorkerState()

    data class Error(val message: String?) : ScanWorkerState()
}
