package com.forenserecovery.android.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reparafotos.ai.ReparaFotosApplication
import com.forenserecovery.android.domain.model.ItemFilter
import com.forenserecovery.android.domain.model.ItemViewMode
import com.forenserecovery.android.domain.model.RecoveryItem
import com.forenserecovery.android.domain.model.ScanMode
import com.forenserecovery.android.domain.model.ScanProgress
import com.forenserecovery.android.domain.model.ScanProfile
import com.forenserecovery.android.domain.usecase.matchesFilter
import com.forenserecovery.android.export.ExportManager
import com.forenserecovery.android.monetization.MonetizationManager
import com.forenserecovery.android.recovery.ForensicCapabilityDetector
import com.forenserecovery.android.recovery.RestoreManager
import com.forenserecovery.android.recovery.ScanCoordinator
import com.forenserecovery.android.recovery.ScanRuntimeControl
import com.forenserecovery.android.recovery.ScanWorker
import com.forenserecovery.android.recovery.ScanWorkerState
import com.forenserecovery.android.recovery.ShizukuAuthorizationState
import com.forenserecovery.android.recovery.ShizukuBridgeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val selectedMode: ScanMode = ScanMode.BASIC,
    val selectedScanProfile: ScanProfile = ScanProfile.BALANCED,
    val monetization: com.forenserecovery.android.monetization.MonetizationState =
        com.forenserecovery.android.monetization.MonetizationState(),
    val selectedFilter: ItemFilter = ItemFilter.ALL,
    val selectedSafTreeUri: String? = null,
    val selectedSourceFolder: String = "Todas",
    val availableSourceFolders: List<String> = listOf("Todas"),
    val viewMode: ItemViewMode = ItemViewMode.GRID,
    val progress: ScanProgress = ScanProgress(),
    val workerState: ScanWorkerState = ScanWorkerState.Idle,
    val allItems: List<RecoveryItem> = emptyList(),
    val items: List<RecoveryItem> = emptyList(),
    val pagedItems: List<RecoveryItem> = emptyList(),
    val visibleItemsTarget: Int = DEFAULT_VISIBLE_PAGE_SIZE,
    val selectedRestoreIds: Set<Long> = emptySet(),
    val selectedRestoreDestinationUri: String? = null,
    val selectedRestoreDestinationLabel: String? = null,
    val isRestoring: Boolean = false,
    val technicalLog: List<String> = emptyList(),
    val selectedItem: RecoveryItem? = null,
    val lastExportPath: String? = null,
    val forensicCapability: String = "No evaluado",
    val shizukuState: ShizukuAuthorizationState = ShizukuAuthorizationState.Unavailable,
    val message: String? = null
)

private const val DEFAULT_VISIBLE_PAGE_SIZE = 80

class MainViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val app = application as ReparaFotosApplication
    private val repository = app.recoveryRepository
    private val scanCoordinator = ScanCoordinator(application)
    private val exportManager = ExportManager(application)
    private val restoreManager = RestoreManager(application)
    private val shizukuBridgeManager = ShizukuBridgeManager(application)
    private val monetizationManager = MonetizationManager(application)

    private val _ui = MutableStateFlow(MainUiState())
    val ui: StateFlow<MainUiState> = _ui

    init {
        _ui.update {
            it.copy(
                forensicCapability = ForensicCapabilityDetector.describeForensicCapability(getApplication()),
                shizukuState = shizukuBridgeManager.currentState()
            )
        }
        observeItems()
        observeWorkerState()
        observeWorkerProgress()
        observeTechnicalLog()
        observeMonetization()
        monetizationManager.start()
    }

    override fun onCleared() {
        super.onCleared()
        monetizationManager.stop()
    }

    private fun observeItems() {
        viewModelScope.launch {
            repository.observeItems().collect { allItems ->
                val currentState = _ui.value
                val folders = buildFolderOptions(allItems)
                val effectiveFolder = if (currentState.selectedSourceFolder in folders) {
                    currentState.selectedSourceFolder
                } else {
                    "Todas"
                }
                val filtered = applyFilters(
                    source = allItems,
                    itemFilter = currentState.selectedFilter,
                    sourceFolder = effectiveFolder
                )
                val targetVisible = currentState.visibleItemsTarget
                    .coerceAtLeast(DEFAULT_VISIBLE_PAGE_SIZE)
                    .coerceAtMost(filtered.size.coerceAtLeast(DEFAULT_VISIBLE_PAGE_SIZE))
                val selected = currentState.selectedItem
                val updatedSelected = selected?.let { sel ->
                    filtered.firstOrNull { it.id == sel.id }
                }
                _ui.update {
                    it.copy(
                        allItems = allItems,
                        items = filtered,
                        pagedItems = filtered.take(targetVisible),
                        visibleItemsTarget = targetVisible,
                        selectedSourceFolder = effectiveFolder,
                        availableSourceFolders = folders,
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
                                expectedTotal = state.total,
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
                            selectedRestoreIds = emptySet(),
                            message = "Escaneo completado."
                        )
                    }

                    is ScanWorkerState.Error -> _ui.update {
                        it.copy(
                            progress = it.progress.copy(isRunning = false, isPaused = false),
                            selectedRestoreIds = emptySet(),
                            message = state.message ?: "Error en escaneo"
                        )
                    }

                    ScanWorkerState.Cancelled -> _ui.update {
                        it.copy(
                            progress = it.progress.copy(isRunning = false, isPaused = false, stage = "Escaneo cancelado"),
                            selectedRestoreIds = emptySet(),
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
        if (!hasModeAccess(mode)) {
            _ui.update {
                it.copy(
                    message = "Modo premium bloqueado. Desbloquea Premium para usar Forense."
                )
            }
            return
        }
        val capability = ForensicCapabilityDetector.describeForensicCapability(getApplication())
        _ui.update {
            it.copy(
                selectedMode = mode,
                forensicCapability = capability,
                shizukuState = shizukuBridgeManager.currentState()
            )
        }
    }

    fun selectScanProfile(profile: ScanProfile) {
        _ui.update { it.copy(selectedScanProfile = profile) }
    }

    fun setSafTreeUri(uri: String?) {
        _ui.update { it.copy(selectedSafTreeUri = uri) }
    }

    fun refreshShizukuState() {
        refreshForensicStatus()
    }

    fun restorePurchases() {
        monetizationManager.restorePurchases()
        _ui.update { it.copy(message = "Restaurando compras...") }
    }

    fun launchPremiumPurchase(activity: android.app.Activity, productId: String) {
        val launched = monetizationManager.launchPurchase(activity, productId)
        if (!launched) {
            _ui.update { it.copy(message = "No se pudo abrir Google Play Billing para esta compra.") }
        }
    }

    fun selectFilter(filter: ItemFilter) {
        val state = _ui.value
        val filtered = applyFilters(
            source = state.allItems,
            itemFilter = filter,
            sourceFolder = state.selectedSourceFolder
        )
        _ui.update {
            it.copy(
                selectedFilter = filter,
                items = filtered,
                visibleItemsTarget = DEFAULT_VISIBLE_PAGE_SIZE,
                pagedItems = filtered.take(DEFAULT_VISIBLE_PAGE_SIZE)
            )
        }
    }

    fun selectSourceFolder(folder: String) {
        val state = _ui.value
        val filtered = applyFilters(
            source = state.allItems,
            itemFilter = state.selectedFilter,
            sourceFolder = folder
        )
        _ui.update {
            it.copy(
                selectedSourceFolder = folder,
                items = filtered,
                visibleItemsTarget = DEFAULT_VISIBLE_PAGE_SIZE,
                pagedItems = filtered.take(DEFAULT_VISIBLE_PAGE_SIZE)
            )
        }
    }

    fun loadMoreVisibleItems() {
        _ui.update { state ->
            if (state.pagedItems.size >= state.items.size) {
                state
            } else {
                val nextTarget = (state.visibleItemsTarget + DEFAULT_VISIBLE_PAGE_SIZE)
                    .coerceAtMost(state.items.size)
                state.copy(
                    visibleItemsTarget = nextTarget,
                    pagedItems = state.items.take(nextTarget)
                )
            }
        }
    }

    fun toggleItemSelection(itemId: Long) {
        _ui.update { state ->
            val selected = state.selectedRestoreIds.toMutableSet()
            if (!selected.add(itemId)) {
                selected.remove(itemId)
            }
            state.copy(selectedRestoreIds = selected)
        }
    }

    fun clearSelection() {
        _ui.update { it.copy(selectedRestoreIds = emptySet()) }
    }

    fun toggleSelectAllVisible() {
        _ui.update { state ->
            if (state.items.isEmpty()) {
                state
            } else {
                val visibleIds = state.items.map { it.id }.toSet()
                val allVisibleSelected = visibleIds.all { it in state.selectedRestoreIds }
                val updated = state.selectedRestoreIds.toMutableSet()
                if (allVisibleSelected) {
                    updated.removeAll(visibleIds)
                } else {
                    updated.addAll(visibleIds)
                }
                state.copy(selectedRestoreIds = updated)
            }
        }
    }

    fun selectRestoreDestination(uri: String?, label: String?) {
        _ui.update {
            it.copy(
                selectedRestoreDestinationUri = uri,
                selectedRestoreDestinationLabel = label
            )
        }
    }

    fun restoreSelected() {
        val state = _ui.value
        if (state.selectedRestoreDestinationUri.isNullOrBlank()) {
            showMessage("Selecciona primero la carpeta destino para restaurar.")
            return
        }
        val targetItems = state.allItems.filter { it.id in state.selectedRestoreIds }
        if (targetItems.isEmpty()) {
            showMessage("No hay elementos seleccionados para restaurar.")
            return
        }
        restoreItems(targetItems)
    }

    fun restoreAllVisible() {
        val state = _ui.value
        if (state.selectedRestoreDestinationUri.isNullOrBlank()) {
            showMessage("Selecciona primero la carpeta destino para restaurar.")
            return
        }
        if (state.items.isEmpty()) {
            showMessage("No hay elementos visibles para restaurar.")
            return
        }
        restoreItems(state.items)
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
        val profile = _ui.value.selectedScanProfile
        ScanRuntimeControl.reset()
        ScanRuntimeControl.setSafTreeUri(safTreeUri)
        ScanRuntimeControl.setScanProfile(profile.name)
        scanCoordinator.enqueueScan(
            mode = mode,
            profileName = profile.name,
            clearPrevious = clearPrevious
        )
        _ui.update {
            it.copy(
                progress = it.progress.copy(
                    stage = "Escaneo en cola (${profile.label})",
                    isRunning = true,
                    isPaused = false
                ),
                selectedRestoreIds = emptySet(),
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
            it.copy(selectedSafTreeUri = null, selectedRestoreIds = emptySet())
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
                    pagedItems = emptyList(),
                    visibleItemsTarget = DEFAULT_VISIBLE_PAGE_SIZE,
                    selectedSafTreeUri = null,
                    selectedSourceFolder = "Todas",
                    availableSourceFolders = listOf("Todas"),
                    selectedRestoreDestinationUri = null,
                    selectedRestoreDestinationLabel = null,
                    selectedRestoreIds = emptySet(),
                    technicalLog = emptyList(),
                    message = "Base local limpiada"
                )
            }
        }
    }

    fun clearMessage() {
        _ui.update { it.copy(message = null) }
    }

    fun requestShizukuAuthorization() {
        viewModelScope.launch {
            val result = shizukuBridgeManager.requestAuthorization()
            refreshForensicStatus()
            val feedback = when (result) {
                ShizukuAuthorizationState.Granted -> "Shizuku autorizado correctamente."
                ShizukuAuthorizationState.Denied -> "Permiso de Shizuku denegado. Puedes reintentar desde la ayuda."
                ShizukuAuthorizationState.NotInstalled -> "Shizuku no está instalado en este dispositivo."
                ShizukuAuthorizationState.ServiceUnavailable -> "Servicio de Shizuku no activo. Inícialo y vuelve a intentar."
                ShizukuAuthorizationState.Unavailable -> "Shizuku no disponible en este entorno."
            }
            _ui.update { it.copy(message = feedback) }
        }
    }

    fun refreshForensicStatus() {
        _ui.update {
            it.copy(
                shizukuState = shizukuBridgeManager.currentState(),
                forensicCapability = ForensicCapabilityDetector.describeForensicCapability(getApplication())
            )
        }
    }

    fun showMessage(message: String) {
        _ui.update { it.copy(message = message) }
    }

    private fun applyFilters(
        source: List<RecoveryItem>,
        itemFilter: ItemFilter,
        sourceFolder: String
    ): List<RecoveryItem> {
        return source.filter { item ->
            val typeMatch = item.matchesFilter(itemFilter)
            val folder = extractSourceFolder(item)
            val folderMatch = sourceFolder == "Todas" || folder == sourceFolder
            typeMatch && folderMatch
        }
    }

    private fun buildFolderOptions(items: List<RecoveryItem>): List<String> {
        val folders = items.map { extractSourceFolder(it) }
            .distinct()
            .sorted()
        return listOf("Todas") + folders
    }

    private fun extractSourceFolder(item: RecoveryItem): String {
        val path = item.originalPath ?: return "Desconocida"
        return runCatching {
            val normalized = path.substringBeforeLast('/')
            if (normalized.isBlank()) "Desconocida" else normalized
        }.getOrDefault("Desconocida")
    }

    private fun restoreItems(items: List<RecoveryItem>) {
        val destination = _ui.value.selectedRestoreDestinationUri ?: return
        viewModelScope.launch {
            _ui.update { it.copy(isRestoring = true) }
            val result = restoreManager.restoreItems(
                destinationTreeUri = destination,
                items = items
            )
            _ui.update {
                it.copy(
                    isRestoring = false,
                    selectedRestoreIds = emptySet(),
                    message = "Restaurados ${result.restoredCount}/${result.requestedCount}. Fallidos: ${result.failedCount}. Destino: ${result.destinationUri}"
                )
            }
        }
    }

    private fun observeMonetization() {
        viewModelScope.launch {
            monetizationManager.state.collect { monetization ->
                _ui.update { state ->
                    state.copy(
                        monetization = monetization,
                        selectedMode = if (!monetization.isPremiumUnlocked &&
                            state.selectedMode != ScanMode.BASIC
                        ) {
                            ScanMode.BASIC
                        } else {
                            state.selectedMode
                        }
                    )
                }
            }
        }
    }

    private fun hasModeAccess(mode: ScanMode): Boolean {
        return mode == ScanMode.BASIC || _ui.value.monetization.isPremiumUnlocked
    }
}
