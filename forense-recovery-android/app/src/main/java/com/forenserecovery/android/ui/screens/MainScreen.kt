package com.forenserecovery.android.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.forenserecovery.android.domain.model.ItemFilter
import com.forenserecovery.android.domain.model.ItemViewMode
import com.forenserecovery.android.domain.model.RecoveryItem
import com.forenserecovery.android.domain.model.RecoveryType
import com.forenserecovery.android.domain.model.ScanMode
import com.forenserecovery.android.domain.model.ScanProfile
import com.forenserecovery.android.permissions.PermissionHelper
import com.forenserecovery.android.recovery.ShizukuAuthorizationState
import com.forenserecovery.android.ui.viewmodel.MainViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showShizukuHelp by remember { mutableStateOf(false) }
    var showImagePreview by remember { mutableStateOf(false) }
    var previewImagePath by remember { mutableStateOf<String?>(null) }
    val gridRows = remember(state.items) { state.items.chunked(2) }

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

    val safTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) {
            viewModel.showMessage("No se seleccionó carpeta SAF.")
            return@rememberLauncherForActivityResult
        }
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }
        viewModel.setSafTreeUri(uri.toString())
        viewModel.showMessage("Árbol SAF configurado para escaneo: $uri")
    }

    val restoreDestinationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) {
            viewModel.showMessage("No se seleccionó carpeta destino de restauración.")
            return@rememberLauncherForActivityResult
        }
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }
        viewModel.selectRestoreDestination(uri.toString())
        viewModel.showMessage("Destino de restauración establecido: $uri")
    }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        viewModel.clearMessage()
    }
    LaunchedEffect(Unit) {
        viewModel.refreshShizukuState()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Forense Recovery Android") }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbar) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                LegalNoticeCard()
            }
            item {
                ModeSelector(
                    selected = state.selectedMode,
                    onModeChanged = {
                        viewModel.selectMode(it)
                        viewModel.showMessage(PermissionHelper.modeDescription(it))
                    }
                )
            }
            item {
                ScanProfileSelector(
                    selected = state.selectedScanProfile,
                    onSelected = viewModel::selectScanProfile
                )
            }
            item {
                ForensicStatusCard(
                    mode = state.selectedMode,
                    capability = state.forensicCapability,
                    shizukuState = state.shizukuState,
                    safTree = state.selectedSafTreeUri,
                    onSelectSafTree = { safTreeLauncher.launch(null) },
                    onShowShizukuHelp = { showShizukuHelp = true },
                    onRequestShizukuPermission = viewModel::requestShizukuAuthorization
                )
            }
            item {
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
            }
            item {
                FilterRow(
                    selectedFilter = state.selectedFilter,
                    onFilterChanged = viewModel::selectFilter,
                    selectedFolder = state.selectedSourceFolder,
                    availableFolders = state.availableSourceFolders,
                    onFolderChanged = viewModel::selectSourceFolder
                )
            }
            item {
                RestoreActionsCard(
                    state = state,
                    onSelectDestination = { restoreDestinationLauncher.launch(null) },
                    onRestoreSelected = {
                        if (state.selectedRestoreDestinationUri.isNullOrBlank()) {
                            restoreDestinationLauncher.launch(null)
                        } else {
                            viewModel.restoreSelected()
                        }
                    },
                    onRestoreAllVisible = {
                        if (state.selectedRestoreDestinationUri.isNullOrBlank()) {
                            restoreDestinationLauncher.launch(null)
                        } else {
                            viewModel.restoreAllVisible()
                        }
                    },
                    onClearSelection = viewModel::clearSelection
                )
            }
            item {
                ExportRow(
                    onExportAll = viewModel::exportAll,
                    onClearDatabase = viewModel::clearDatabase
                )
            }
            item {
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
            }

            if (state.viewMode == ItemViewMode.GRID) {
                items(gridRows, key = { row -> row.firstOrNull()?.id ?: -1L }) { rowItems ->
                    GridContent(
                        modifier = Modifier.fillMaxWidth(),
                        items = rowItems,
                        selectedIds = state.selectedRestoreIds,
                        onSelectItem = { viewModel.selectItem(it) },
                        onToggleSelection = { viewModel.toggleItemSelection(it) },
                        onOpenPreview = { path ->
                            previewImagePath = path
                            showImagePreview = true
                        }
                    )
                }
            } else {
                items(state.items, key = { it.id }) { rowItem ->
                    ListItemCard(
                        item = rowItem,
                        selectedIds = state.selectedRestoreIds,
                        onSelectItem = { viewModel.selectItem(it) },
                        onToggleSelection = { viewModel.toggleItemSelection(it) },
                        onOpenPreview = { path ->
                            previewImagePath = path
                            showImagePreview = true
                        }
                    )
                }
            }
        }
    }

    state.selectedItem?.let { selected ->
        ItemDetailDialog(
            item = selected,
            onDismiss = { viewModel.selectItem(null) },
            onOpenExternal = { item ->
                val path = item.recoveredPath ?: item.originalPath
                if (path.isNullOrBlank()) {
                    viewModel.showMessage("No hay ruta de archivo para abrir.")
                    return@ItemDetailDialog
                }
                val file = File(path)
                if (!file.exists()) {
                    viewModel.showMessage("Archivo no encontrado en disco.")
                    return@ItemDetailDialog
                }
                val uri = runCatching {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                }.getOrElse {
                    Uri.fromFile(file)
                }
                val mime = item.mimeType ?: when (item.type) {
                    RecoveryType.IMAGE -> "image/*"
                    RecoveryType.VIDEO -> "video/*"
                    RecoveryType.AUDIO -> "audio/*"
                    else -> "*/*"
                }
                val intent = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val ok = runCatching { context.startActivity(intent) }.isSuccess
                if (!ok) {
                    viewModel.showMessage("No se pudo abrir el archivo con una app externa.")
                }
            }
        )
    }

    if (showShizukuHelp) {
        ShizukuHelpDialog(onDismiss = { showShizukuHelp = false })
    }
    if (showImagePreview && !previewImagePath.isNullOrBlank()) {
        ImagePreviewDialog(
            imagePath = previewImagePath!!,
            onDismiss = { showImagePreview = false }
        )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeSelector(
    selected: ScanMode,
    onModeChanged: (ScanMode) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Modo de acceso", fontWeight = FontWeight.Bold)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScanProfileSelector(
    selected: ScanProfile,
    onSelected: (ScanProfile) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Tipo de escaneo", fontWeight = FontWeight.Bold)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScanProfile.entries.forEach { profile ->
                    FilterChip(
                        selected = selected == profile,
                        onClick = { onSelected(profile) },
                        label = { Text(profile.label) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ForensicStatusCard(
    mode: ScanMode,
    capability: String,
    shizukuState: ShizukuAuthorizationState,
    safTree: String?,
    onSelectSafTree: () -> Unit,
    onShowShizukuHelp: () -> Unit,
    onRequestShizukuPermission: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Estado forense", fontWeight = FontWeight.Bold)
            Text(capability, style = MaterialTheme.typography.bodySmall)
            Text(
                "SAF seleccionado: ${safTree ?: "ninguno"}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (mode == ScanMode.FORENSIC || mode == ScanMode.ADVANCED) {
                    OutlinedButton(onClick = onSelectSafTree) {
                        Text("Seleccionar carpeta SAF")
                    }
                }
                if (mode == ScanMode.FORENSIC) {
                    Button(
                        onClick = onRequestShizukuPermission,
                        enabled = shizukuState != ShizukuAuthorizationState.Granted
                    ) {
                        Text(
                            when (shizukuState) {
                                ShizukuAuthorizationState.Granted -> "Shizuku autorizado"
                                ShizukuAuthorizationState.NotInstalled -> "Instalar Shizuku"
                                ShizukuAuthorizationState.ServiceUnavailable -> "Activar servicio Shizuku"
                                ShizukuAuthorizationState.Denied -> "Reintentar permiso Shizuku"
                                ShizukuAuthorizationState.Unavailable -> "Shizuku no disponible"
                            }
                        )
                    }
                }
                TextButton(onClick = onShowShizukuHelp) {
                    Text("Ayuda Shizuku")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScanControls(
    state: com.forenserecovery.android.ui.viewmodel.MainUiState,
    onStartScan: () -> Unit,
    onPauseResume: () -> Unit,
    onCancelScan: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                val progressValue = runCatching {
                    val expected = state.progress.expectedTotal
                    when {
                        expected != null && expected > 0 ->
                            (state.progress.scanned.toFloat() / expected.toFloat()).coerceIn(0f, 1f)

                        state.progress.scanned > 0 ->
                            (state.progress.discovered.toFloat() / state.progress.scanned.toFloat()).coerceIn(0f, 1f)

                        else -> 0f
                    }
                }.getOrDefault(0f)
                val zoneLabel = buildScanZoneLabel(
                    stage = state.progress.stage,
                    currentPath = state.progress.currentPath
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.height(24.dp))
                    Text("${state.progress.stage} (${state.progress.scanned} analizados / ${state.progress.discovered} hallazgos)")
                }
                LinearProgressIndicator(
                    progress = { progressValue },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Zona actual: $zoneLabel",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (state.progress.currentPath.isNotBlank()) {
                    Text(
                        "Ruta: ${state.progress.currentPath}",
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterRow(
    selectedFilter: ItemFilter,
    onFilterChanged: (ItemFilter) -> Unit,
    selectedFolder: String,
    availableFolders: List<String>,
    onFolderChanged: (String) -> Unit
) {
    var showFolders by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
        Box {
            OutlinedButton(onClick = { showFolders = true }) {
                val label = if (selectedFolder == "Todas") {
                    "Todas"
                } else {
                    selectedFolder.substringAfterLast('/')
                }
                Text("Carpeta: $label", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(
                expanded = showFolders,
                onDismissRequest = { showFolders = false }
            ) {
                availableFolders.forEach { folder ->
                    DropdownMenuItem(
                        text = { Text(folder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = {
                            onFolderChanged(folder)
                            showFolders = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestoreActionsCard(
    state: com.forenserecovery.android.ui.viewmodel.MainUiState,
    onSelectDestination: () -> Unit,
    onRestoreSelected: () -> Unit,
    onRestoreAllVisible: () -> Unit,
    onClearSelection: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Restauración", fontWeight = FontWeight.Bold)
            Text(
                "Destino: ${state.selectedRestoreDestinationUri ?: "No seleccionado"}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Seleccionadas: ${state.selectedRestoreIds.size} | Visibles: ${state.items.size}",
                style = MaterialTheme.typography.bodySmall
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onSelectDestination) { Text("Elegir destino") }
                OutlinedButton(onClick = onRestoreSelected, enabled = state.selectedRestoreIds.isNotEmpty() && !state.isRestoring) {
                    Text("Restaurar seleccionadas")
                }
                OutlinedButton(onClick = onRestoreAllVisible, enabled = state.items.isNotEmpty() && !state.isRestoring) {
                    Text("Restaurar todas")
                }
                OutlinedButton(onClick = onClearSelection, enabled = state.selectedRestoreIds.isNotEmpty()) {
                    Text("Limpiar selección")
                }
            }
            if (state.isRestoring) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Restaurando archivos...", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExportRow(
    onExportAll: () -> Unit,
    onClearDatabase: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Exportación de informe", fontWeight = FontWeight.Bold)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onExportAll) { Text("CSV/JSON/HTML/ZIP") }
                OutlinedButton(onClick = onClearDatabase) { Text("Limpiar BD") }
            }
        }
    }
}

private fun buildScanZoneLabel(
    stage: String,
    currentPath: String
): String {
    if (currentPath.startsWith("content://")) {
        return "Contenido SAF/MediaStore"
    }
    if (currentPath.isBlank()) {
        return when {
            stage.contains("MediaStore", ignoreCase = true) -> "MediaStore"
            stage.contains("SAF", ignoreCase = true) -> "Árbol SAF"
            stage.contains("almacenamiento", ignoreCase = true) -> "Almacenamiento compartido"
            stage.contains("preparando", ignoreCase = true) -> "Inicializando escaneo"
            else -> "Sin ruta activa todavía"
        }
    }

    val normalized = currentPath.replace('\\', '/')
    val parts = normalized.split('/').filter { it.isNotBlank() }
    val highlights = listOf("DCIM", "Pictures", "Movies", "Music", "Download", "WhatsApp", "Telegram", "Android")
    val firstMatch = parts.firstOrNull { it in highlights }
    if (firstMatch != null) {
        return firstMatch
    }
    return parts.takeLast(2).joinToString("/").ifBlank { "Ruta activa" }
}

@Composable
private fun ShizukuHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) { Text("Entendido") }
        },
        title = { Text("Cómo habilitar Shizuku") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Para usar el modo forense ampliado con Shizuku:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("1) Instala Shizuku desde Play Store o GitHub.")
                Text("2) Activa Opciones de desarrollador en Android.")
                Text("3) Habilita Depuración inalámbrica o conecta por USB (ADB).")
                Text("4) Abre Shizuku y pulsa Start para iniciar el servicio.")
                Text("5) Regresa a esta app y permite autorización cuando Shizuku la solicite.")
                Text("6) Si no hay puente activo, la app seguirá en modo local estándar.")
                Text(
                    "Nota: Shizuku no da root; solo permite ejecutar operaciones autorizadas por el usuario.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GridContent(
    modifier: Modifier = Modifier,
    items: List<RecoveryItem>,
    selectedIds: Set<Long>,
    onSelectItem: (RecoveryItem) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onOpenPreview: (String) -> Unit
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            Card(
                modifier = Modifier
                    .widthIn(min = 110.dp, max = 160.dp)
                    .clickable { onSelectItem(item) }
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Checkbox(
                            checked = item.id in selectedIds,
                            onCheckedChange = { onToggleSelection(item.id) }
                        )
                    }
                    val preview = item.recoveredPath ?: item.originalPath
                    if (item.type.name == "IMAGE" && !preview.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(preview)
                                .size(180, 180)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onOpenPreview(preview) }
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
private fun ListItemCard(
    item: RecoveryItem,
    selectedIds: Set<Long>,
    onSelectItem: (RecoveryItem) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onOpenPreview: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onSelectItem(item) }) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Checkbox(
                    checked = item.id in selectedIds,
                    onCheckedChange = { onToggleSelection(item.id) }
                )
            }
            Text("${item.type} | ${item.status}", fontWeight = FontWeight.SemiBold)
            val preview = item.recoveredPath ?: item.originalPath
            if (item.type == RecoveryType.IMAGE && !preview.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(preview)
                        .size(260, 260)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onOpenPreview(preview) }
                )
            }
            Text(item.mimeType ?: "MIME desconocido", style = MaterialTheme.typography.bodySmall)
            Text(item.originalPath ?: "-", maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("SHA: ${item.sha256 ?: "N/A"}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ImagePreviewDialog(
    imagePath: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss) { Text("Cerrar") } },
        title = { Text("Vista previa") },
        text = {
            Surface(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imagePath)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
    )
}

@Composable
private fun ItemDetailDialog(
    item: RecoveryItem,
    onDismiss: () -> Unit,
    onOpenExternal: (RecoveryItem) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.type == RecoveryType.AUDIO ||
                    item.type == RecoveryType.VIDEO ||
                    item.type == RecoveryType.IMAGE
                ) {
                    OutlinedButton(onClick = { onOpenExternal(item) }) { Text("Abrir") }
                }
                Button(onClick = onDismiss) { Text("Cerrar") }
            }
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
