package com.forenserecovery.android.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.forenserecovery.android.domain.model.ItemFilter
import com.forenserecovery.android.domain.model.ItemViewMode
import com.forenserecovery.android.domain.model.RecoveryItem
import com.forenserecovery.android.domain.model.ScanMode
import com.forenserecovery.android.permissions.PermissionHelper
import com.forenserecovery.android.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.all { it }
        if (granted) {
            viewModel.startScan()
        } else {
            viewModel.showMessage("Faltan permisos para iniciar escaneo.")
        }
    }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        viewModel.clearMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Forense Recovery Android") }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbar) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LegalNoticeCard()
            ModeSelector(
                selected = state.selectedMode,
                onModeChanged = {
                    viewModel.selectMode(it)
                    viewModel.showMessage(PermissionHelper.modeDescription(it))
                }
            )
            ScanControls(
                state = state,
                onStartScan = {
                    val required = PermissionHelper.requiredPermissions(state.selectedMode)
                    if (PermissionHelper.hasPermissions(context, required)) {
                        if (PermissionHelper.canUseManageExternalStorage(state.selectedMode) &&
                            !PermissionHelper.hasManageExternalStorage()
                        ) {
                            val intent = PermissionHelper.buildManageAllFilesAccessIntent(context)
                            context.startActivity(intent)
                            viewModel.showMessage(
                                "Concede All Files Access y vuelve a iniciar el escaneo avanzado."
                            )
                        } else {
                            viewModel.startScan()
                        }
                    } else {
                        permissionLauncher.launch(required)
                    }
                },
                onPauseResume = {
                    if (state.progress.isPaused) viewModel.resumeScan() else viewModel.pauseScan()
                },
                onCancelScan = viewModel::cancelScan
            )
            FilterRow(
                selectedFilter = state.selectedFilter,
                onFilterChanged = viewModel::selectFilter
            )
            ExportRow(
                onExportAll = viewModel::exportAll,
                onClearDatabase = viewModel::clearDatabase
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Hallazgos: ${state.items.size}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                AssistChip(
                    onClick = viewModel::toggleViewMode,
                    label = {
                        Text(if (state.viewMode == ItemViewMode.GRID) "Vista: Grid" else "Vista: Lista")
                    }
                )
            }

            if (state.viewMode == ItemViewMode.GRID) {
                GridContent(state.items) { viewModel.selectItem(it) }
            } else {
                ListContent(state.items) { viewModel.selectItem(it) }
            }
        }
    }

    state.selectedItem?.let { selected ->
        ItemDetailDialog(item = selected, onDismiss = { viewModel.selectItem(null) })
    }
}

@Composable
private fun LegalNoticeCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Uso ético y legal", fontWeight = FontWeight.Bold)
            Text(
                "Esta app es para recuperación forense en dispositivos propios o autorizados. " +
                    "No garantiza recuperar todo lo eliminado. Procesa datos localmente y no sube archivos.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ModeSelector(
    selected: ScanMode,
    onModeChanged: (ScanMode) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Modo de acceso", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScanMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selected == mode,
                        onClick = { onModeChanged(mode) },
                        label = {
                            Text(
                                when (mode) {
                                    ScanMode.BASIC -> "Básico"
                                    ScanMode.ADVANCED -> "Avanzado"
                                    ScanMode.FORENSIC -> "Forense"
                                }
                            )
                        }
                    )
                }
            }
            Text(
                "Básico: MediaStore. Avanzado: añade All Files Access. Forense: integración opcional con Shizuku/ADB.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ScanControls(
    state: com.forenserecovery.android.ui.viewmodel.MainUiState,
    onStartScan: () -> Unit,
    onPauseResume: () -> Unit,
    onCancelScan: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStartScan, enabled = !state.progress.isRunning) {
                    Text("Iniciar escaneo")
                }
                OutlinedButton(onClick = onPauseResume, enabled = state.progress.isRunning) {
                    Text(if (state.progress.isPaused) "Reanudar" else "Pausar")
                }
                OutlinedButton(onClick = onCancelScan, enabled = state.progress.isRunning) {
                    Text("Cancelar")
                }
            }
            if (state.progress.isRunning) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.height(24.dp))
                    Text("${state.progress.stage} (${state.progress.scanned} analizados / ${state.progress.discovered} hallazgos)")
                }
                if (state.progress.currentPath.isNotBlank()) {
                    Text(
                        state.progress.currentPath,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                Text("Escaneo detenido", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun FilterRow(
    selectedFilter: ItemFilter,
    onFilterChanged: (ItemFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ItemFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterChanged(filter) },
                label = {
                    Text(
                        text = when (filter) {
                            ItemFilter.ALL -> "Todos"
                            ItemFilter.IMAGE -> "Imagen"
                            ItemFilter.VIDEO -> "Video"
                            ItemFilter.AUDIO -> "Audio"
                            ItemFilter.THUMBNAIL -> "Miniatura"
                            ItemFilter.PARTIAL -> "Parcial"
                            ItemFilter.CORRUPT -> "Corrupto"
                        },
                        modifier = Modifier.widthIn(min = 44.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun ExportRow(
    onExportAll: () -> Unit,
    onClearDatabase: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Exportación de informe", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onExportAll) { Text("CSV/JSON/HTML/ZIP") }
                OutlinedButton(onClick = onClearDatabase) { Text("Limpiar BD") }
            }
        }
    }
}

@Composable
private fun GridContent(
    items: List<RecoveryItem>,
    onSelectItem: (RecoveryItem) -> Unit
) {
    LazyVerticalGrid(
        modifier = Modifier.fillMaxWidth(),
        columns = GridCells.Adaptive(140.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items) { item ->
            Card(
                modifier = Modifier.clickable { onSelectItem(item) }
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    val preview = item.recoveredPath ?: item.originalPath
                    if (item.type.name == "IMAGE" && !preview.isNullOrBlank()) {
                        AsyncImage(
                            model = preview,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                    Text(item.type.name, fontWeight = FontWeight.SemiBold)
                    Text(item.status.name, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${item.sizeBytes} bytes",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ListContent(
    items: List<RecoveryItem>,
    onSelectItem: (RecoveryItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.id }) { item ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onSelectItem(item) }) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("${item.type} | ${item.status}", fontWeight = FontWeight.SemiBold)
                    Text(item.mimeType ?: "MIME desconocido", style = MaterialTheme.typography.bodySmall)
                    Text(item.originalPath ?: "-", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("SHA: ${item.sha256 ?: "N/A"}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ItemDetailDialog(
    item: RecoveryItem,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) { Text("Cerrar") }
        },
        title = { Text("Detalle técnico") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tipo: ${item.type}")
                Text("Estado: ${item.status}")
                Text("Origen: ${item.scanSource}")
                Text("MIME: ${item.mimeType ?: "-"}")
                Text("Ruta original: ${item.originalPath ?: "-"}")
                Text("Ruta recuperada: ${item.recoveredPath ?: "-"}")
                Text("Tamaño: ${item.sizeBytes}")
                Text("SHA-256: ${item.sha256 ?: "-"}")
                Text("Resolución: ${item.width ?: "-"}x${item.height ?: "-"}")
                Text("Duración: ${item.duration ?: "-"} ms")
                if (!item.notes.isNullOrBlank()) {
                    Text("Notas: ${item.notes}")
                }
            }
        }
    )
}
