package com.reparafotos.ai.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forenserecovery.android.domain.model.ScanMode
import com.forenserecovery.android.monetization.MonetizationConfig
import com.forenserecovery.android.ui.viewmodel.MonetizationUiState
import com.forenserecovery.android.ui.viewmodel.MonetizationViewModel
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
fun RepairHomeScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val snackbarHostState = remember { SnackbarHostState() }
    val monetizationViewModel: MonetizationViewModel = viewModel()
    val monetizationState by monetizationViewModel.ui.collectAsStateWithLifecycle()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var repairedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var diagnosis by remember { mutableStateOf("Aun no hay diagnostico") }
    var qualityScore by remember { mutableFloatStateOf(0f) }
    var processingPreview by remember { mutableStateOf(false) }
    var loadingImage by remember { mutableStateOf(false) }

    var brightnessAdjust by remember { mutableFloatStateOf(0f) }
    var contrastAdjust by remember { mutableFloatStateOf(1f) }
    var sharpenAdjust by remember { mutableFloatStateOf(0f) }
    var saturationAdjust by remember { mutableFloatStateOf(1f) }
    var warmthAdjust by remember { mutableFloatStateOf(0f) }
    var motionDeblurAdjust by remember { mutableFloatStateOf(0.35f) }
    var autoMotionAssistEnabled by remember { mutableStateOf(true) }

    var selectedTool by remember { mutableStateOf(EditTool.AUTO_REPAIR) }
    var selectedPreset by remember { mutableStateOf(PresetFilter.NONE) }

    var history by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var historyIndex by remember { mutableIntStateOf(-1) }
    var batchRunning by remember { mutableStateOf(false) }
    var batchTotal by remember { mutableIntStateOf(0) }
    var batchDone by remember { mutableIntStateOf(0) }
    var batchQueue by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showPaywall by remember { mutableStateOf(false) }
    var showFullScreenPreview by remember { mutableStateOf(false) }
    var basicOpenedImagesCount by remember { mutableIntStateOf(0) }
    var pendingSingleOpenUri by remember { mutableStateOf<Uri?>(null) }
    var showAdOrPremiumDialog by remember { mutableStateOf(false) }

    fun requestPremiumAccessOrOpenPaywall(): Boolean {
        if (monetizationState.isPremiumUnlocked) return true
        val allowed = monetizationViewModel.ensureAccessOrShowPaywall(ScanMode.FORENSIC)
        if (!allowed) showPaywall = true
        return allowed
    }

    val singlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        persistReadPermission(context, uri)
        if (monetizationState.isPremiumUnlocked) {
            selectedUri = uri
            return@rememberLauncherForActivityResult
        }
        if (basicOpenedImagesCount < 3) {
            basicOpenedImagesCount += 1
            selectedUri = uri
        } else {
            pendingSingleOpenUri = uri
            showAdOrPremiumDialog = true
        }
    }
    val batchPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        if (!requestPremiumAccessOrOpenPaywall()) return@rememberLauncherForActivityResult
        uris.forEach { persistReadPermission(context, it) }
        batchQueue = uris
        batchTotal = uris.size
        batchDone = 0
        batchRunning = true
        diagnosis = "Iniciando procesamiento por lote..."
    }

    LaunchedEffect(Unit) {
        monetizationViewModel.startBilling()
        monetizationViewModel.preloadAds()
    }
    LaunchedEffect(monetizationState.showPaywall) {
        showPaywall = monetizationState.showPaywall
    }
    LaunchedEffect(monetizationState.message) {
        val msg = monetizationState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        monetizationViewModel.clearMessage()
    }
    LaunchedEffect(monetizationState.isPremiumUnlocked) {
        if (monetizationState.isPremiumUnlocked) {
            monetizationViewModel.consumePendingMode()
            showPaywall = false
            snackbarHostState.showSnackbar("Premium activo. Funciones avanzadas desbloqueadas.")
        }
    }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri ?: return@LaunchedEffect
        loadingImage = true
        val loaded = decodeBitmapForPreview(context = context, uri = uri)
        if (loaded == null) {
            snackbarHostState.showSnackbar("No se pudo abrir la imagen seleccionada.")
            loadingImage = false
            return@LaunchedEffect
        }
        sourceBitmap = loaded
        repairedBitmap = loaded
        brightnessAdjust = 0f
        contrastAdjust = 1f
        sharpenAdjust = 0f
        saturationAdjust = 1f
        warmthAdjust = 0f
        motionDeblurAdjust = 0.35f
        selectedTool = EditTool.AUTO_REPAIR
        selectedPreset = PresetFilter.NONE
        history = listOf(loaded)
        historyIndex = 0
        val report = analyzeBitmap(loaded)
        diagnosis = report.label
        qualityScore = report.score
        loadingImage = false
    }

    // Preview en tiempo real sin colgar UI.
    LaunchedEffect(
        sourceBitmap,
        selectedTool,
        selectedPreset,
        brightnessAdjust,
        contrastAdjust,
        sharpenAdjust,
        saturationAdjust,
        warmthAdjust,
        motionDeblurAdjust,
        autoMotionAssistEnabled,
        historyIndex
    ) {
        val source = sourceBitmap ?: return@LaunchedEffect
        if (loadingImage) return@LaunchedEffect
        if (batchRunning) return@LaunchedEffect
        if (historyIndex >= 0 && historyIndex < history.lastIndex) return@LaunchedEffect

        processingPreview = true
        delay(120)
        val base = ImageSettings(
            brightness = brightnessAdjust,
            contrast = contrastAdjust,
            sharpen = sharpenAdjust,
            saturation = saturationAdjust,
            warmth = warmthAdjust,
            motionDeblur = motionDeblurAdjust
        )
        val effective = resolveSettings(tool = selectedTool, preset = selectedPreset, base = base)
        val processed = withContext(Dispatchers.Default) {
            processImagePipeline(
                source = source,
                tool = selectedTool,
                preset = selectedPreset,
                settings = effective,
                autoMotionAssistEnabled = autoMotionAssistEnabled
            )
        }
        repairedBitmap = processed.bitmap
        diagnosis = buildDiagnosisText(
            tool = selectedTool,
            report = processed.report,
            autoMotionApplied = processed.autoMotionApplied,
            portraitBoostApplied = processed.portraitBoostApplied
        )
        qualityScore = processed.report.score
        processingPreview = false
    }

    LaunchedEffect(batchRunning, batchQueue) {
        if (!batchRunning || batchQueue.isEmpty()) return@LaunchedEffect
        processingPreview = true
        val queue = batchQueue
        val toolSnapshot = selectedTool
        val base = ImageSettings(
            brightness = brightnessAdjust,
            contrast = contrastAdjust,
            sharpen = sharpenAdjust,
            saturation = saturationAdjust,
            warmth = warmthAdjust,
            motionDeblur = motionDeblurAdjust
        )
        val presetSnapshot = selectedPreset
        val settingsSnapshot = resolveSettings(tool = toolSnapshot, preset = presetSnapshot, base = base)

        var savedCount = 0
        var lastSource: Bitmap? = null
        var lastResult: Bitmap? = null
        var lastReport: ImageAnalysisReport? = null

        queue.forEachIndexed { index, uri ->
            val loaded = decodeBitmapForPreview(context = context, uri = uri)
            if (loaded != null) {
                val processed = withContext(Dispatchers.Default) {
                    processImagePipeline(
                        source = loaded,
                        tool = toolSnapshot,
                        preset = presetSnapshot,
                        settings = settingsSnapshot,
                        autoMotionAssistEnabled = autoMotionAssistEnabled
                    )
                }
                val savedPath = saveBitmapToAppFolder(
                    context = context,
                    bitmap = processed.bitmap,
                    prefix = "batch_repair_${index + 1}"
                )
                if (savedPath != null) savedCount++
                lastSource = loaded
                lastResult = processed.bitmap
                lastReport = processed.report
            }
            batchDone = index + 1
            diagnosis = "Procesando lote: ${batchDone}/${batchTotal}"
        }

        val finalSource = lastSource
        if (finalSource != null) {
            val finalBitmap = lastResult ?: finalSource
            sourceBitmap = finalSource
            repairedBitmap = finalBitmap
            history = listOf(finalBitmap)
            historyIndex = 0
            val finalReport = lastReport ?: analyzeBitmap(finalBitmap)
            diagnosis = "Lote completo: $savedCount/$batchTotal guardadas. ${finalReport.label}"
            qualityScore = finalReport.score
        } else {
            diagnosis = "Lote completado, pero no se pudieron procesar imagenes."
        }
        snackbarHostState.showSnackbar("Lote finalizado: $savedCount de $batchTotal guardadas")
        batchRunning = false
        processingPreview = false
        batchQueue = emptyList()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Repara Fotos AI", fontWeight = FontWeight.Bold)
                        Text(
                            "Estilo pro para restaurar imagenes en segundos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    val qualityPercent = (qualityScore * 100).toInt().coerceIn(0, 100)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Diagnostico inteligente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Procesamiento local, sin subir archivos. Visual premium con resultados en tiempo real.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text("Estado actual: $diagnosis", style = MaterialTheme.typography.bodyMedium)
                            LinearProgressIndicator(
                                progress = { qualityScore.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Calidad estimada: $qualityPercent%")
                                StatusBadge(text = if (processingPreview) "Procesando" else "Preview activa")
                            }
                            ProcessingClockIndicator(isProcessing = processingPreview)
                            if (batchRunning) {
                                val progress = if (batchTotal <= 0) 0f else batchDone.toFloat() / batchTotal.toFloat()
                                LinearProgressIndicator(
                                    progress = { progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text("Procesamiento por lote: $batchDone / $batchTotal", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                item {
                    MonetizationStatusCard(
                        isPremiumUnlocked = monetizationState.isPremiumUnlocked,
                        hasAds = !monetizationState.isPremiumUnlocked,
                        basicRemainingFreeOpens = (3 - basicOpenedImagesCount).coerceAtLeast(0),
                        onOpenPaywall = { showPaywall = true },
                        onRestore = monetizationViewModel::restorePurchases
                    )
                }
                if (!monetizationState.isPremiumUnlocked) {
                    item {
                        BasicModeBannerAd(adUnitId = MonetizationConfig.bannerAdUnitId)
                    }
                }

                item {
                    SectionTitle("Acciones")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = { singlePicker.launch(arrayOf("image/*")) }) {
                            Text("Abrir imagen")
                        }
                        OutlinedButton(onClick = {
                            if (!requestPremiumAccessOrOpenPaywall()) return@OutlinedButton
                            batchPicker.launch(arrayOf("image/*"))
                        }) {
                            Text("Procesar lote")
                        }
                        OutlinedButton(
                            onClick = {
                                val current = repairedBitmap ?: return@OutlinedButton
                                val doSave = {
                                    val path = saveBitmapToAppFolder(context, current)
                                    if (path == null) {
                                        diagnosis = "No se pudo guardar la imagen."
                                    } else {
                                        diagnosis = "Guardada en: $path"
                                    }
                                }
                                if (!monetizationState.isPremiumUnlocked) {
                                    monetizationViewModel.showInterstitial(activity) { doSave() }
                                } else {
                                    doSave()
                                }
                            },
                            enabled = repairedBitmap != null
                        ) {
                            Text("Guardar")
                        }
                        OutlinedButton(
                            onClick = {
                                repairedBitmap = sourceBitmap
                                selectedTool = EditTool.AUTO_REPAIR
                                selectedPreset = PresetFilter.NONE
                                brightnessAdjust = 0f
                                contrastAdjust = 1f
                                sharpenAdjust = 0f
                                saturationAdjust = 1f
                                warmthAdjust = 0f
                                motionDeblurAdjust = 0.35f
                                history = sourceBitmap?.let { listOf(it) } ?: emptyList()
                                historyIndex = if (history.isEmpty()) -1 else 0
                            },
                            enabled = sourceBitmap != null
                        ) {
                            Text("Reset")
                        }
                    }
                }

                item {
                    SectionTitle("Historial")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (historyIndex > 0) {
                                    historyIndex--
                                    repairedBitmap = history[historyIndex]
                                }
                            },
                            enabled = historyIndex > 0
                        ) {
                            Text("Deshacer")
                        }
                        OutlinedButton(
                            onClick = {
                                if (historyIndex >= 0 && historyIndex < history.lastIndex) {
                                    historyIndex++
                                    repairedBitmap = history[historyIndex]
                                }
                            },
                            enabled = historyIndex >= 0 && historyIndex < history.lastIndex
                        ) {
                            Text("Rehacer")
                        }
                        Button(
                            onClick = {
                                val current = repairedBitmap ?: return@Button
                                val trimmed = if (historyIndex >= 0 && historyIndex < history.size - 1) {
                                    history.take(historyIndex + 1)
                                } else {
                                    history
                                }
                                history = trimmed + current
                                historyIndex = history.lastIndex
                            },
                            enabled = repairedBitmap != null
                        ) {
                            Text("Aplicar cambios")
                        }
                    }
                }

                if (sourceBitmap != null) {
                    item {
                        SectionTitle("Herramientas")
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EditTool.entries.forEach { tool ->
                                FilterChip(
                                    selected = selectedTool == tool,
                                    onClick = {
                                        val needsPremium = tool == EditTool.MOTION_FIX
                                        if (needsPremium && !requestPremiumAccessOrOpenPaywall()) {
                                            return@FilterChip
                                        }
                                        selectedTool = tool
                                    },
                                    label = { Text(tool.label, modifier = Modifier.widthIn(min = 72.dp)) }
                                )
                            }
                        }
                    }

                    item {
                        SectionTitle("Presets visuales")
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PresetFilter.entries.forEach { preset ->
                                FilterChip(
                                    selected = selectedPreset == preset,
                                    onClick = {
                                        val needsPremium = preset == PresetFilter.PORTRAIT
                                        if (needsPremium && !requestPremiumAccessOrOpenPaywall()) {
                                            return@FilterChip
                                        }
                                        selectedPreset = preset
                                    },
                                    label = { Text(preset.label, modifier = Modifier.widthIn(min = 72.dp)) }
                                )
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Ajustes manuales", fontWeight = FontWeight.SemiBold)
                                Text("Brillo: ${(brightnessAdjust * 100).toInt()}%")
                                Slider(value = brightnessAdjust, onValueChange = { brightnessAdjust = it }, valueRange = -0.5f..0.5f)
                                Text("Contraste: ${(contrastAdjust * 100).toInt()}%")
                                Slider(value = contrastAdjust, onValueChange = { contrastAdjust = it }, valueRange = 0.6f..1.8f)
                                Text("Nitidez: ${(sharpenAdjust * 100).toInt()}%")
                                Slider(value = sharpenAdjust, onValueChange = { sharpenAdjust = it }, valueRange = 0f..1f)
                                Text("Saturacion: ${(saturationAdjust * 100).toInt()}%")
                                Slider(value = saturationAdjust, onValueChange = { saturationAdjust = it }, valueRange = 0f..2f)
                                Text("Temperatura: ${(warmthAdjust * 100).toInt()}%")
                                Slider(value = warmthAdjust, onValueChange = { warmthAdjust = it }, valueRange = -0.4f..0.4f)
                                Text("Correcion movimiento: ${(motionDeblurAdjust * 100).toInt()}%")
                                Slider(value = motionDeblurAdjust, onValueChange = { motionDeblurAdjust = it }, valueRange = 0f..1f)
                                FilterChip(
                                    selected = autoMotionAssistEnabled,
                                    onClick = { autoMotionAssistEnabled = !autoMotionAssistEnabled },
                                    label = {
                                        Text(
                                            if (autoMotionAssistEnabled) {
                                                "Auto corregir foto movida: activo"
                                            } else {
                                                "Auto corregir foto movida: inactivo"
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }

                    item { SectionTitle("Antes y despues") }
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ImagePanel(title = "Original", bitmap = sourceBitmap, modifier = Modifier.weight(1f))
                            ImagePanel(
                                title = "Reparada",
                                bitmap = repairedBitmap ?: sourceBitmap,
                                modifier = Modifier.weight(1f),
                                onClick = { showFullScreenPreview = true }
                            )
                        }
                    }
                } else {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Listo para reparar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Selecciona una foto para comenzar el diagnostico y aplicar mejoras con un look profesional.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFullScreenPreview && repairedBitmap != null) {
        FullScreenImageDialog(
            title = "Vista completa - imagen reparada",
            bitmap = repairedBitmap!!,
            onDismiss = { showFullScreenPreview = false }
        )
    }
    if (showPaywall) {
        PremiumPaywallDialog(
            state = monetizationState,
            onDismiss = {
                showPaywall = false
                monetizationViewModel.dismissPaywall()
            },
            onBuy = { productId ->
                if (activity != null) {
                    monetizationViewModel.launchPurchase(activity, productId)
                }
            },
            onRestore = monetizationViewModel::restorePurchases
        )
    }
    if (showAdOrPremiumDialog) {
        BasicLimitDialog(
            onDismiss = {
                showAdOrPremiumDialog = false
                pendingSingleOpenUri = null
            },
            onWatchAd = {
                val pendingUri = pendingSingleOpenUri
                showAdOrPremiumDialog = false
                if (pendingUri != null) {
                    monetizationViewModel.showInterstitial(activity) {
                        selectedUri = pendingUri
                        pendingSingleOpenUri = null
                    }
                }
            },
            onGoPremium = {
                showAdOrPremiumDialog = false
                showPaywall = true
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun StatusBadge(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

private data class ImageAnalysisReport(
    val label: String,
    val score: Float,
    val motionBlurScore: Float
)

private enum class EditTool(val label: String) {
    AUTO_REPAIR("Auto"),
    MOTION_FIX("Movimiento"),
    INVERT("Invertir"),
    EDGE_LINES("Lineas"),
    ENHANCE_FOCUS("Enfoque"),
    GRAYSCALE("B/N"),
    SEPIA("Sepia"),
    MANUAL_ADJUST("Manual")
}

private enum class PresetFilter(val label: String) {
    NONE("Sin preset"),
    VIVID("Vivido"),
    PORTRAIT("Retrato"),
    NIGHT("Noche"),
    DOCUMENT("Documento"),
    VINTAGE("Vintage")
}

private data class ImageSettings(
    val brightness: Float,
    val contrast: Float,
    val sharpen: Float,
    val saturation: Float,
    val warmth: Float,
    val motionDeblur: Float
)

private fun analyzeBitmap(bitmap: Bitmap): ImageAnalysisReport {
    val width = bitmap.width.coerceAtLeast(1)
    val height = bitmap.height.coerceAtLeast(1)
    val sampleStep = (width / 80).coerceAtLeast(1)
    var edgeAccum = 0L
    var horizontalAccum = 0L
    var verticalAccum = 0L
    var brightnessAccum = 0L
    var count = 0L
    var y = 0
    while (y < height - sampleStep) {
        var x = 0
        while (x < width - sampleStep) {
            val c1 = bitmap.getPixel(x, y)
            val c2 = bitmap.getPixel(x + sampleStep, y + sampleStep)
            val l1 = luminance(c1)
            val l2 = luminance(c2)
            val hLum = luminance(bitmap.getPixel(x + sampleStep, y))
            val vLum = luminance(bitmap.getPixel(x, y + sampleStep))
            edgeAccum += abs(l1 - l2).toLong()
            horizontalAccum += abs(l1 - hLum).toLong()
            verticalAccum += abs(l1 - vLum).toLong()
            brightnessAccum += l1.toLong()
            count++
            x += sampleStep
        }
        y += sampleStep
    }
    val edge = if (count == 0L) 0f else (edgeAccum.toFloat() / count.toFloat()) / 255f
    val horizontal = if (count == 0L) 0f else (horizontalAccum.toFloat() / count.toFloat()) / 255f
    val vertical = if (count == 0L) 0f else (verticalAccum.toFloat() / count.toFloat()) / 255f
    val brightness = if (count == 0L) 0f else (brightnessAccum.toFloat() / count.toFloat()) / 255f
    val directionalAsymmetry = if (horizontal + vertical < 0.001f) {
        0f
    } else {
        abs(horizontal - vertical) / (horizontal + vertical)
    }
    val lowSharpness = ((0.22f - edge).coerceAtLeast(0f) / 0.22f).coerceIn(0f, 1f)
    val motionBlurScore = (lowSharpness * 0.72f + directionalAsymmetry * 0.28f).coerceIn(0f, 1f)
    val score = (edge * 0.65f + (1f - abs(0.55f - brightness)) * 0.35f).coerceIn(0f, 1f)
    val label = when {
        score >= 0.75f -> "Buena calidad"
        motionBlurScore > 0.55f -> "Foto movida detectada"
        edge < 0.12f -> "Posible fuera de foco"
        brightness < 0.28f -> "Subexpuesta"
        brightness > 0.85f -> "Sobreexpuesta"
        else -> "Calidad media"
    }
    return ImageAnalysisReport(
        label = label,
        score = score,
        motionBlurScore = motionBlurScore
    )
}

private fun resolveSettings(tool: EditTool, preset: PresetFilter, base: ImageSettings): ImageSettings {
    var settings = base
    if (tool == EditTool.AUTO_REPAIR) {
        settings = settings.copy(
            brightness = (settings.brightness + 0.06f).coerceIn(-0.5f, 0.5f),
            contrast = (settings.contrast * 1.08f).coerceIn(0.6f, 1.8f),
            sharpen = (settings.sharpen + 0.18f).coerceIn(0f, 1f),
            saturation = (settings.saturation * 1.05f).coerceIn(0f, 2f),
            motionDeblur = (settings.motionDeblur + 0.15f).coerceIn(0f, 1f)
        )
    }
    return applyPreset(settings, preset)
}

private fun applyPreset(settings: ImageSettings, preset: PresetFilter): ImageSettings {
    return when (preset) {
        PresetFilter.NONE -> settings
        PresetFilter.VIVID -> settings.copy(
            contrast = (settings.contrast * 1.18f).coerceIn(0.6f, 1.8f),
            saturation = (settings.saturation * 1.25f).coerceIn(0f, 2f),
            sharpen = (settings.sharpen + 0.08f).coerceIn(0f, 1f)
        )
        PresetFilter.PORTRAIT -> settings.copy(
            brightness = (settings.brightness + 0.07f).coerceIn(-0.5f, 0.5f),
            warmth = (settings.warmth + 0.10f).coerceIn(-0.5f, 0.5f),
            contrast = (settings.contrast * 0.94f).coerceIn(0.6f, 1.8f),
            saturation = (settings.saturation * 0.92f).coerceIn(0f, 2f),
            sharpen = (settings.sharpen + 0.05f).coerceIn(0f, 1f)
        )
        PresetFilter.NIGHT -> settings.copy(
            brightness = (settings.brightness + 0.16f).coerceIn(-0.5f, 0.5f),
            contrast = (settings.contrast * 1.12f).coerceIn(0.6f, 1.8f),
            sharpen = (settings.sharpen + 0.10f).coerceIn(0f, 1f)
        )
        PresetFilter.DOCUMENT -> settings.copy(
            saturation = 0f,
            contrast = (settings.contrast * 1.25f).coerceIn(0.6f, 1.8f),
            brightness = (settings.brightness + 0.08f).coerceIn(-0.5f, 0.5f),
            sharpen = (settings.sharpen + 0.14f).coerceIn(0f, 1f)
        )
        PresetFilter.VINTAGE -> settings.copy(
            warmth = (settings.warmth + 0.15f).coerceIn(-0.5f, 0.5f),
            saturation = (settings.saturation * 0.82f).coerceIn(0f, 2f),
            contrast = (settings.contrast * 0.92f).coerceIn(0.6f, 1.8f)
        )
    }
}

private fun applyTool(source: Bitmap, tool: EditTool, settings: ImageSettings): Bitmap {
    return when (tool) {
        EditTool.AUTO_REPAIR -> improveImage(source, settings)
        EditTool.MOTION_FIX -> fixMotionBlur(source, settings.motionDeblur.coerceAtLeast(0.2f))
        EditTool.INVERT -> invertColors(source)
        EditTool.EDGE_LINES -> detectEdges(source)
        EditTool.ENHANCE_FOCUS -> sharpenBitmap(source, settings.sharpen.coerceAtLeast(0.35f))
        EditTool.GRAYSCALE -> toGrayscale(source)
        EditTool.SEPIA -> sepia(source)
        EditTool.MANUAL_ADJUST -> improveImage(source, settings)
    }
}

private data class PipelineResult(
    val bitmap: Bitmap,
    val report: ImageAnalysisReport,
    val autoMotionApplied: Boolean,
    val portraitBoostApplied: Boolean
)

private fun processImagePipeline(
    source: Bitmap,
    tool: EditTool,
    preset: PresetFilter,
    settings: ImageSettings,
    autoMotionAssistEnabled: Boolean
): PipelineResult {
    var result = applyTool(source = source, tool = tool, settings = settings)
    var report = analyzeBitmap(result)
    var autoMotionApplied = false
    val canAutoMotion = tool != EditTool.MOTION_FIX &&
        tool != EditTool.EDGE_LINES &&
        tool != EditTool.GRAYSCALE &&
        tool != EditTool.SEPIA
    if (autoMotionAssistEnabled && canAutoMotion && report.motionBlurScore > 0.46f) {
        val dynamicAmount = max(settings.motionDeblur, report.motionBlurScore.coerceIn(0.25f, 0.9f))
        result = fixMotionBlur(result, dynamicAmount)
        report = analyzeBitmap(result)
        autoMotionApplied = true
    }
    var portraitBoostApplied = false
    if (preset == PresetFilter.PORTRAIT) {
        result = enhancePortrait(result, settings)
        report = analyzeBitmap(result)
        portraitBoostApplied = true
    }
    return PipelineResult(
        bitmap = result,
        report = report,
        autoMotionApplied = autoMotionApplied,
        portraitBoostApplied = portraitBoostApplied
    )
}

private fun buildDiagnosisText(
    tool: EditTool,
    report: ImageAnalysisReport,
    autoMotionApplied: Boolean,
    portraitBoostApplied: Boolean
): String {
    val suffix = buildList {
        if (autoMotionApplied) add("auto corrigiendo movimiento")
        if (portraitBoostApplied) add("retrato inteligente")
    }.joinToString(separator = " + ")
    return if (suffix.isBlank()) {
        "Filtro ${tool.label}: ${report.label}"
    } else {
        "Filtro ${tool.label}: ${report.label} ($suffix)"
    }
}

private fun improveImage(source: Bitmap, settings: ImageSettings): Bitmap {
    val width = source.width
    val height = source.height
    val inPixels = IntArray(width * height)
    source.getPixels(inPixels, 0, width, 0, 0, width, height)
    val outPixels = IntArray(inPixels.size)
    val contrast = settings.contrast.coerceIn(0.6f, 1.8f)
    val brightness = (settings.brightness * 255f).toInt().coerceIn(-128, 128)
    val saturation = settings.saturation.coerceIn(0f, 2f)
    val warmth = settings.warmth.coerceIn(-0.5f, 0.5f)
    for (i in inPixels.indices) {
        val c = inPixels[i]
        var r = ((Color.red(c) - 128) * contrast + 128 + brightness).toInt()
        var g = ((Color.green(c) - 128) * contrast + 128 + brightness).toInt()
        var b = ((Color.blue(c) - 128) * contrast + 128 + brightness).toInt()
        val gray = (0.299f * r + 0.587f * g + 0.114f * b)
        r = (gray + (r - gray) * saturation).toInt()
        g = (gray + (g - gray) * saturation).toInt()
        b = (gray + (b - gray) * saturation).toInt()
        val warmShift = (warmth * 80f).toInt()
        r += warmShift
        b -= warmShift
        outPixels[i] = Color.argb(
            Color.alpha(c),
            r.coerceIn(0, 255),
            g.coerceIn(0, 255),
            b.coerceIn(0, 255)
        )
    }
    val adjusted = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(outPixels, 0, width, 0, 0, width, height)
    }
    return if (settings.sharpen > 0f) sharpenBitmap(adjusted, settings.sharpen) else adjusted
}

private fun invertColors(source: Bitmap): Bitmap {
    val bitmap = source.copy(Bitmap.Config.ARGB_8888, true)
    for (y in 0 until bitmap.height) {
        for (x in 0 until bitmap.width) {
            val c = bitmap.getPixel(x, y)
            bitmap.setPixel(
                x,
                y,
                Color.argb(
                    Color.alpha(c),
                    255 - Color.red(c),
                    255 - Color.green(c),
                    255 - Color.blue(c)
                )
            )
        }
    }
    return bitmap
}

private fun detectEdges(source: Bitmap): Bitmap {
    val width = source.width
    val height = source.height
    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val gx = arrayOf(
        intArrayOf(-1, 0, 1),
        intArrayOf(-2, 0, 2),
        intArrayOf(-1, 0, 1)
    )
    val gy = arrayOf(
        intArrayOf(1, 2, 1),
        intArrayOf(0, 0, 0),
        intArrayOf(-1, -2, -1)
    )
    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            var sumX = 0
            var sumY = 0
            for (ky in -1..1) {
                for (kx in -1..1) {
                    val lum = luminance(source.getPixel(x + kx, y + ky))
                    sumX += lum * gx[ky + 1][kx + 1]
                    sumY += lum * gy[ky + 1][kx + 1]
                }
            }
            val magnitude = sqrt((sumX * sumX + sumY * sumY).toDouble()).toInt().coerceIn(0, 255)
            result.setPixel(x, y, Color.argb(255, magnitude, magnitude, magnitude))
        }
    }
    return result
}

private fun toGrayscale(source: Bitmap): Bitmap {
    val out = source.copy(Bitmap.Config.ARGB_8888, true)
    for (y in 0 until out.height) {
        for (x in 0 until out.width) {
            val c = out.getPixel(x, y)
            val lum = luminance(c)
            out.setPixel(x, y, Color.argb(Color.alpha(c), lum, lum, lum))
        }
    }
    return out
}

private fun sepia(source: Bitmap): Bitmap {
    val out = source.copy(Bitmap.Config.ARGB_8888, true)
    for (y in 0 until out.height) {
        for (x in 0 until out.width) {
            val c = out.getPixel(x, y)
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            val tr = (0.393 * r + 0.769 * g + 0.189 * b).toInt().coerceIn(0, 255)
            val tg = (0.349 * r + 0.686 * g + 0.168 * b).toInt().coerceIn(0, 255)
            val tb = (0.272 * r + 0.534 * g + 0.131 * b).toInt().coerceIn(0, 255)
            out.setPixel(x, y, Color.argb(Color.alpha(c), tr, tg, tb))
        }
    }
    return out
}

private fun sharpenBitmap(source: Bitmap, amount: Float): Bitmap {
    val width = source.width
    val height = source.height
    if (width < 3 || height < 3) return source.copy(Bitmap.Config.ARGB_8888, true)
    val src = source.copy(Bitmap.Config.ARGB_8888, false)
    val dst = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val k = amount.coerceIn(0f, 1f)
    val center = 1f + 4f * k
    val neighbor = -k
    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val c = src.getPixel(x, y)
            val left = src.getPixel(x - 1, y)
            val right = src.getPixel(x + 1, y)
            val up = src.getPixel(x, y - 1)
            val down = src.getPixel(x, y + 1)
            val r = (Color.red(c) * center + Color.red(left) * neighbor + Color.red(right) * neighbor +
                Color.red(up) * neighbor + Color.red(down) * neighbor).toInt().coerceIn(0, 255)
            val g = (Color.green(c) * center + Color.green(left) * neighbor + Color.green(right) * neighbor +
                Color.green(up) * neighbor + Color.green(down) * neighbor).toInt().coerceIn(0, 255)
            val b = (Color.blue(c) * center + Color.blue(left) * neighbor + Color.blue(right) * neighbor +
                Color.blue(up) * neighbor + Color.blue(down) * neighbor).toInt().coerceIn(0, 255)
            dst.setPixel(x, y, Color.argb(Color.alpha(c), r, g, b))
        }
    }
    for (x in 0 until width) {
        dst.setPixel(x, 0, src.getPixel(x, 0))
        dst.setPixel(x, height - 1, src.getPixel(x, height - 1))
    }
    for (y in 0 until height) {
        dst.setPixel(0, y, src.getPixel(0, y))
        dst.setPixel(width - 1, y, src.getPixel(width - 1, y))
    }
    return dst
}

private fun fixMotionBlur(source: Bitmap, amount: Float): Bitmap {
    val strength = amount.coerceIn(0f, 1f)
    if (strength <= 0f) return source.copy(Bitmap.Config.ARGB_8888, true)
    val width = source.width
    val height = source.height
    if (width < 3 || height < 3) return source.copy(Bitmap.Config.ARGB_8888, true)

    val src = source.copy(Bitmap.Config.ARGB_8888, false)
    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val blend = (0.22f + 0.58f * strength).coerceIn(0.22f, 0.8f)

    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val center = src.getPixel(x, y)
            val left = src.getPixel(x - 1, y)
            val right = src.getPixel(x + 1, y)
            val up = src.getPixel(x, y - 1)
            val down = src.getPixel(x, y + 1)

            val avgR = (Color.red(left) + Color.red(right) + Color.red(up) + Color.red(down)) / 4
            val avgG = (Color.green(left) + Color.green(right) + Color.green(up) + Color.green(down)) / 4
            val avgB = (Color.blue(left) + Color.blue(right) + Color.blue(up) + Color.blue(down)) / 4

            val deblurR = (Color.red(center) * (1f + 1.7f * strength) - avgR * (0.7f * strength)).toInt()
            val deblurG = (Color.green(center) * (1f + 1.7f * strength) - avgG * (0.7f * strength)).toInt()
            val deblurB = (Color.blue(center) * (1f + 1.7f * strength) - avgB * (0.7f * strength)).toInt()

            val outR = (Color.red(center) * (1f - blend) + deblurR * blend).toInt().coerceIn(0, 255)
            val outG = (Color.green(center) * (1f - blend) + deblurG * blend).toInt().coerceIn(0, 255)
            val outB = (Color.blue(center) * (1f - blend) + deblurB * blend).toInt().coerceIn(0, 255)

            result.setPixel(
                x,
                y,
                Color.argb(Color.alpha(center), outR, outG, outB)
            )
        }
    }
    for (x in 0 until width) {
        result.setPixel(x, 0, src.getPixel(x, 0))
        result.setPixel(x, height - 1, src.getPixel(x, height - 1))
    }
    for (y in 0 until height) {
        result.setPixel(0, y, src.getPixel(0, y))
        result.setPixel(width - 1, y, src.getPixel(width - 1, y))
    }
    return result
}

private fun enhancePortrait(source: Bitmap, settings: ImageSettings): Bitmap {
    val width = source.width
    val height = source.height
    if (width < 3 || height < 3) return source.copy(Bitmap.Config.ARGB_8888, true)
    val src = source.copy(Bitmap.Config.ARGB_8888, false)
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val smoothStrength = (0.18f + settings.motionDeblur * 0.22f).coerceIn(0.18f, 0.45f)
    val glow = (8f + settings.brightness * 40f).toInt()
    val warmBoost = (6f + settings.warmth * 30f).toInt()
    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val center = src.getPixel(x, y)
            if (!isLikelySkinTone(center)) {
                out.setPixel(x, y, center)
                continue
            }
            val left = src.getPixel(x - 1, y)
            val right = src.getPixel(x + 1, y)
            val up = src.getPixel(x, y - 1)
            val down = src.getPixel(x, y + 1)
            val avgR = (Color.red(center) + Color.red(left) + Color.red(right) + Color.red(up) + Color.red(down)) / 5
            val avgG = (Color.green(center) + Color.green(left) + Color.green(right) + Color.green(up) + Color.green(down)) / 5
            val avgB = (Color.blue(center) + Color.blue(left) + Color.blue(right) + Color.blue(up) + Color.blue(down)) / 5

            val r = (
                Color.red(center) * (1f - smoothStrength) + avgR * smoothStrength + glow + warmBoost
                ).toInt().coerceIn(0, 255)
            val g = (
                Color.green(center) * (1f - smoothStrength) + avgG * smoothStrength + (glow * 0.65f)
                ).toInt().coerceIn(0, 255)
            val b = (
                Color.blue(center) * (1f - smoothStrength) + avgB * smoothStrength - (warmBoost * 0.45f)
                ).toInt().coerceIn(0, 255)
            out.setPixel(x, y, Color.argb(Color.alpha(center), r, g, b))
        }
    }
    for (x in 0 until width) {
        out.setPixel(x, 0, src.getPixel(x, 0))
        out.setPixel(x, height - 1, src.getPixel(x, height - 1))
    }
    for (y in 0 until height) {
        out.setPixel(0, y, src.getPixel(0, y))
        out.setPixel(width - 1, y, src.getPixel(width - 1, y))
    }
    return out
}

private fun isLikelySkinTone(pixel: Int): Boolean {
    val r = Color.red(pixel)
    val g = Color.green(pixel)
    val b = Color.blue(pixel)
    val maxChannel = max(r, max(g, b))
    val minChannel = minOf(r, minOf(g, b))
    return r > 95 &&
        g > 40 &&
        b > 20 &&
        (maxChannel - minChannel) > 15 &&
        abs(r - g) > 10 &&
        r > g &&
        r > b
}

private fun luminance(pixel: Int): Int {
    val r = Color.red(pixel)
    val g = Color.green(pixel)
    val b = Color.blue(pixel)
    return (0.299 * r + 0.587 * g + 0.114 * b).toInt().coerceIn(0, 255)
}

private suspend fun decodeBitmapForPreview(
    context: Context,
    uri: Uri,
    maxDimension: Int = 1280
): Bitmap? = withContext(Dispatchers.IO) {
    val fromImageDecoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        runCatching {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val srcW = info.size.width.coerceAtLeast(1)
                val srcH = info.size.height.coerceAtLeast(1)
                val maxSide = max(srcW, srcH)
                if (maxSide > maxDimension) {
                    val scale = maxDimension.toFloat() / maxSide.toFloat()
                    decoder.setTargetSize(
                        (srcW * scale).toInt().coerceAtLeast(1),
                        (srcH * scale).toInt().coerceAtLeast(1)
                    )
                }
            }
        }.getOrNull()
    } else {
        null
    }
    if (fromImageDecoder != null) return@withContext fromImageDecoder
    decodeWithBitmapFactory(context, uri, maxDimension)
}

private fun decodeWithBitmapFactory(
    context: Context,
    uri: Uri,
    maxDimension: Int
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    } ?: return null
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth / sample, bounds.outHeight / sample) > maxDimension) sample *= 2
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample.coerceAtLeast(1)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, opts)
    } ?: return null
    val maxSide = max(decoded.width, decoded.height)
    if (maxSide <= maxDimension) return decoded
    val scale = maxDimension.toFloat() / maxSide.toFloat()
    val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
    val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
}

private fun persistReadPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
}

private fun saveBitmapToAppFolder(
    context: Context,
    bitmap: Bitmap,
    prefix: String = "repair"
): String? {
    val dir = File(
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
        "ReparaFotosAI"
    )
    if (!dir.exists() && !dir.mkdirs()) return null
    val safePrefix = prefix.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val file = File(dir, "${safePrefix}_${System.currentTimeMillis()}.jpg")
    return runCatching {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)
        }
        file.absolutePath
    }.getOrNull()
}

@Composable
private fun ImagePanel(
    title: String,
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (bitmap == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Sin imagen", modifier = Modifier.padding(12.dp))
                }
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .then(
                            if (onClick != null) {
                                Modifier.clickable { onClick() }
                            } else {
                                Modifier
                            }
                        )
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                )
            }
        }
    }
}

@Composable
private fun ProcessingClockIndicator(isProcessing: Boolean) {
    if (!isProcessing) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            "Procesando cambios...",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun FullScreenImageDialog(
    title: String,
    bitmap: Bitmap,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onDismiss) { Text("Cerrar") }
                }
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonetizationStatusCard(
    isPremiumUnlocked: Boolean,
    hasAds: Boolean,
    basicRemainingFreeOpens: Int,
    onOpenPaywall: () -> Unit,
    onRestore: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Plan actual", fontWeight = FontWeight.Bold)
            Text(
                if (isPremiumUnlocked) {
                    "Premium activo: sin anuncios + funciones avanzadas desbloqueadas."
                } else {
                    "Básico gratis: con anuncios y funciones premium bloqueadas."
                },
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                if (hasAds) "Anuncios: activos" else "Anuncios: desactivados",
                style = MaterialTheme.typography.bodySmall
            )
            if (!isPremiumUnlocked) {
                Text(
                    "Aperturas gratis restantes: $basicRemainingFreeOpens de 3",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isPremiumUnlocked) {
                    Button(onClick = onOpenPaywall) { Text("Pasar a Premium") }
                }
                OutlinedButton(onClick = onRestore) { Text("Restaurar compras") }
            }
        }
    }
}

@Composable
private fun BasicModeBannerAd(
    adUnitId: String
) {
    val isPreview = LocalInspectionMode.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Publicidad (modo básico)", style = MaterialTheme.typography.bodySmall)
            if (isPreview) {
                Text("Banner preview", style = MaterialTheme.typography.bodySmall)
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { ctx ->
                        AdView(ctx).apply {
                            setAdSize(AdSize.BANNER)
                            this.adUnitId = adUnitId
                            loadAd(AdRequest.Builder().build())
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PremiumPaywallDialog(
    state: MonetizationUiState,
    onDismiss: () -> Unit,
    onBuy: (String) -> Unit,
    onRestore: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) { Text("Cerrar") }
        },
        title = { Text("Premium Repara Fotos AI") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Desbloquea funciones pro: procesamiento por lote, retrato inteligente, corrección avanzada y sin anuncios.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (!state.isReady) {
                    Text("Conectando a Google Play Billing...", style = MaterialTheme.typography.bodySmall)
                }
                if (state.products.isEmpty()) {
                    Text(
                        "Aún no hay productos cargados. Verifica los IDs en Play Console.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    state.products.forEachIndexed { index, product ->
                        if (index > 0) HorizontalDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(product.title, fontWeight = FontWeight.SemiBold)
                            Text(product.description, style = MaterialTheme.typography.bodySmall)
                            Text(product.price, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { onBuy(product.productId) }) {
                                Text("Comprar")
                            }
                        }
                    }
                }
                OutlinedButton(onClick = onRestore) {
                    Text("Restaurar compras")
                }
            }
        }
    )
}

@Composable
private fun BasicLimitDialog(
    onDismiss: () -> Unit,
    onWatchAd: () -> Unit,
    onGoPremium: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onWatchAd) { Text("Ver anuncio y abrir") }
        },
        dismissButton = {
            OutlinedButton(onClick = onGoPremium) { Text("Pasar a Premium") }
        },
        title = { Text("Límite del plan básico") },
        text = {
            Text(
                "Ya usaste tus 3 aperturas gratis. Puedes ver un anuncio para abrir una foto extra o pasarte a Premium.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    )
}
