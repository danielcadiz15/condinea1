package com.forenserecovery.android.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.forenserecovery.android.ForenseRecoveryApplication
import com.forenserecovery.android.domain.model.ItemFilter
import com.forenserecovery.android.domain.model.ItemViewMode
import com.forenserecovery.android.domain.model.RecoveryItem
import com.forenserecovery.android.domain.model.ScanMode
import com.forenserecovery.android.domain.model.ScanProgress
import com.forenserecovery.android.domain.usecase.matchesFilter
import com.forenserecovery.android.export.ExportManager
import com.forenserecovery.android.recovery.ForensicCapabilityDetector
import com.forenserecovery.android.recovery.ScanCoordinator
import com.forenserecovery.android.recovery.ScanRuntimeControl
import com.forenserecovery.android.recovery.ScanWorker
import com.forenserecovery.android.recovery.ScanWorkerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val selectedMode: ScanMode = ScanMode.BASIC,
    val selectedFilter: ItemFilter = ItemFilter.ALL,
    val selectedSafTreeUri: String? = null,
    val viewMode: ItemViewMode = ItemViewMode.GRID,
    val progress: ScanProgress = ScanProgress(),
    val workerState: ScanWorkerState = ScanWorkerState.Idle,
    val allItems: List<RecoveryItem> = emptyList(),
    val items: List<RecoveryItem> = emptyList(),
    val technicalLog: List<String> = emptyList(),
    val selectedItem: RecoveryItem? = null,
    val lastExportPath: String? = null,
    val forensicCapability: String = "No evaluado",
    val message: String? = null
)

class MainViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val app = application as ForenseRecoveryApplication
    private val repository = app.recoveryRepository
    private val scanCoordinator = ScanCoordinator(application)
    private val exportManager = ExportManager(application)

    private val _ui = MutableStateFlow(MainUiState())
    val ui: StateFlow<MainUiState> = _ui

    init {
        _ui.update {
            it.copy(forensicCapability = ForensicCapabilityDetector.describeForensicCapability(getApplication()))
        }
        observeItems()
        observeWorkerState()
        observeWorkerProgress()
        observeTechnicalLog()
    }

    private fun observeItems() {
        viewModelScope.launch {
            repository.observeItems().collect { allItems ->
                val filter = _ui.value.selectedFilter
                val filtered = allItems.filter { it.matchesFilter(filter) }
                val selected = _ui.value.selectedItem
                val updatedSelected = selected?.let { sel ->
                    filtered.firstOrNull { it.id == sel.id }
                }
                _ui.update {
                    it.copy(
                        allItems = allItems,
                        items = filtered,
                        selectedItem = updatedSelected
                    )
                }
            }
        }
    }

    private fun observeWorkerState() {
        viewModelScope.launch {
            scanCoordinator.observeScanState().collect { state ->
                _ui.update { it.copy(workerState = state) }
                when (state) {
                    is ScanWorkerState.Running -> _ui.update {
                        it.copy(
                            progress = it.progress.copy(
                                scanned = state.scanned,
                                discovered = state.discovered,
                                stage = state.stage,
                                currentPath = state.currentPath,
                                warning = state.warning,
                                isRunning = true,
                                isPaused = ScanRuntimeControl.controlCenter().isPaused()
                            )
                        )
                    }

                    is ScanWorkerState.Finished -> _ui.update {
                        it.copy(
                            progress = it.progress.copy(
                                scanned = state.scanned,
                                discovered = state.discovered,
                                isRunning = false,
                                isPaused = false,
                                stage = "Escaneo finalizado"
                            ),
                            message = "Escaneo completado."
                        )
                    }

                    is ScanWorkerState.Error -> _ui.update {
                        it.copy(
                            progress = it.progress.copy(isRunning = false, isPaused = false),
                            message = state.message ?: "Error en escaneo"
                        )
                    }

                    ScanWorkerState.Cancelled -> _ui.update {
                        it.copy(
                            progress = it.progress.copy(isRunning = false, isPaused = false, stage = "Escaneo cancelado"),
                            message = "Escaneo cancelado"
                        )
                    }

                    ScanWorkerState.Idle -> Unit
                }
            }
        }
    }

    private fun observeWorkerProgress() {
        viewModelScope.launch {
            ScanWorker.progressFlow().collect { progress ->
                _ui.update {
                    it.copy(progress = progress)
                }
            }
        }
    }

    private fun observeTechnicalLog() {
        viewModelScope.launch {
            ScanWorker.technicalLogFlow().collect { logs ->
                _ui.update { it.copy(technicalLog = logs) }
            }
        }
    }

    fun selectMode(mode: ScanMode) {
        val capability = if (mode == ScanMode.FORENSIC) {
            ForensicCapabilityDetector.describeForensicCapability(getApplication())
        } else {
            _ui.value.forensicCapability
        }
        _ui.update { it.copy(selectedMode = mode, forensicCapability = capability) }
    }

    fun setSafTreeUri(uri: String?) {
        _ui.update { it.copy(selectedSafTreeUri = uri) }
    }

    fun selectFilter(filter: ItemFilter) {
        val allItems = _ui.value.allItems
        val filtered = if (filter == ItemFilter.ALL) allItems else allItems.filter { it.matchesFilter(filter) }
        _ui.update { it.copy(selectedFilter = filter, items = filtered) }
    }

    fun toggleViewMode() {
        _ui.update {
            it.copy(
                viewMode = if (it.viewMode == ItemViewMode.GRID) ItemViewMode.LIST else ItemViewMode.GRID
            )
        }
    }

    fun selectItem(item: RecoveryItem?) {
        _ui.update { it.copy(selectedItem = item) }
    }

    fun startScan(clearPrevious: Boolean = false) {
        val mode = _ui.value.selectedMode
        val safTreeUri = _ui.value.selectedSafTreeUri
        ScanRuntimeControl.reset()
        ScanRuntimeControl.setSafTreeUri(safTreeUri)
        scanCoordinator.enqueueScan(mode = mode, clearPrevious = clearPrevious)
        _ui.update {
            it.copy(
                progress = it.progress.copy(
                    stage = "Escaneo en cola",
                    isRunning = true,
                    isPaused = false
                ),
                message = null
            )
        }
    }

    fun pauseScan() {
        ScanRuntimeControl.pause()
        _ui.update {
            it.copy(
                progress = it.progress.copy(isPaused = true, stage = "Escaneo pausado")
            )
        }
    }

    fun resumeScan() {
        ScanRuntimeControl.resume()
        _ui.update {
            it.copy(
                progress = it.progress.copy(isPaused = false, stage = "Escaneo reanudado")
            )
        }
    }

    fun cancelScan() {
        ScanRuntimeControl.cancel()
        scanCoordinator.cancelScan()
        ScanRuntimeControl.setSafTreeUri(null)
        _ui.update {
            it.copy(selectedSafTreeUri = null)
        }
    }

    fun exportAll() {
        viewModelScope.launch {
            val all = repository.getAll()
            val artifacts = exportManager.exportAll(all, _ui.value.technicalLog)
            _ui.update {
                it.copy(
                    lastExportPath = artifacts.zip.parentFile?.absolutePath,
                    message = "Informe exportado en ${artifacts.zip.parentFile?.absolutePath}"
                )
            }
        }
    }

    fun clearDatabase() {
        viewModelScope.launch {
            repository.clear()
            ScanRuntimeControl.setSafTreeUri(null)
            _ui.update {
                it.copy(
                    selectedItem = null,
                    allItems = emptyList(),
                    items = emptyList(),
                    selectedSafTreeUri = null,
                    technicalLog = emptyList(),
                    message = "Base local limpiada"
                )
            }
        }
    }

    fun clearMessage() {
        _ui.update { it.copy(message = null) }
    }

    fun showMessage(message: String) {
        _ui.update { it.copy(message = message) }
    }
}
