package com.reparafotos.ai.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
fun RepairHomeScreen() {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var repairedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var diagnosis by remember { mutableStateOf("Aún no hay diagnóstico") }
    var qualityScore by remember { mutableFloatStateOf(0f) }
    var processingPreview by remember { mutableStateOf(false) }
    var loadingImage by remember { mutableStateOf(false) }

    var brightnessAdjust by remember { mutableFloatStateOf(0f) } // -0.5..0.5
    var contrastAdjust by remember { mutableFloatStateOf(1f) } // 0.6..1.8
    var sharpenAdjust by remember { mutableFloatStateOf(0f) } // 0..1
    var saturationAdjust by remember { mutableFloatStateOf(1f) } // 0..2
    var warmthAdjust by remember { mutableFloatStateOf(0f) } // -0.4..0.4

    var selectedTool by remember { mutableStateOf(EditTool.AUTO_REPAIR) }
    var selectedPreset by remember { mutableStateOf(PresetFilter.NONE) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedUri = uri
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
        selectedTool = EditTool.AUTO_REPAIR
        selectedPreset = PresetFilter.NONE
        val report = analyzeBitmap(loaded)
        diagnosis = report.label
        qualityScore = report.score
        loadingImage = false
    }

    // Real-time preview: every control change recomputes the processed image on background thread.
    LaunchedEffect(
        sourceBitmap,
        selectedTool,
        selectedPreset,
        brightnessAdjust,
        contrastAdjust,
        sharpenAdjust,
        saturationAdjust,
        warmthAdjust
    ) {
        val source = sourceBitmap ?: return@LaunchedEffect
        if (loadingImage) return@LaunchedEffect
        processingPreview = true
        try {
            delay(140)
            val baseSettings = ImageSettings(
                brightness = brightnessAdjust,
                contrast = contrastAdjust,
                sharpen = sharpenAdjust,
                saturation = saturationAdjust,
                warmth = warmthAdjust
            )
            val effective = resolveSettings(
                tool = selectedTool,
                preset = selectedPreset,
                base = baseSettings
            )
            val result = withContext(Dispatchers.Default) {
                applyTool(source = source, tool = selectedTool, settings = effective)
            }
            val report = withContext(Dispatchers.Default) { analyzeBitmap(result) }
            repairedBitmap = result
            diagnosis = "Filtro ${selectedTool.label}: ${report.label}"
            qualityScore = report.score
        } catch (_: Throwable) {
            diagnosis = "No se pudo generar la vista previa del ajuste."
        } finally {
            processingPreview = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Repara Fotos AI") })
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Diagnóstico de imagen", fontWeight = FontWeight.Bold)
                    Text(
                        "Evalúa fotos borrosas, oscuras o parciales y aplica una mejora local automática. " +
                            "No sube archivos a Internet."
                    )
                    Text("Estado: $diagnosis", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(
                        progress = { qualityScore.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Calidad estimada: ${(qualityScore * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        if (processingPreview) "Procesando vista previa en tiempo real..."
                        else "Vista previa en tiempo real activa",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { picker.launch("image/*") }) {
                    Text("Seleccionar imagen")
                }
                OutlinedButton(
                    onClick = {
                        // Fuerza refresco rápido de controles sin cambiar de filtro.
                        selectedPreset = selectedPreset
                    },
                    enabled = sourceBitmap != null && !loadingImage
                ) {
                    Text("Actualizar")
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
                        val source = sourceBitmap
                        if (source != null) {
                            val report = analyzeBitmap(source)
                            diagnosis = "Ajustes reiniciados: ${report.label}"
                            qualityScore = report.score
                        }
                    },
                    enabled = sourceBitmap != null && !loadingImage
                ) {
                    Text("Reset")
                }
            }

            if (sourceBitmap != null) {
                Text("Herramientas", fontWeight = FontWeight.Bold)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EditTool.entries.forEach { tool ->
                        FilterChip(
                            selected = selectedTool == tool,
                            onClick = { selectedTool = tool },
                            label = {
                                Text(
                                    text = tool.label,
                                    modifier = Modifier.widthIn(min = 72.dp)
                                )
                            }
                        )
                    }
                }

                Text("Filtros predeterminados", fontWeight = FontWeight.Bold)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetFilter.entries.forEach { preset ->
                        FilterChip(
                            selected = selectedPreset == preset,
                            onClick = { selectedPreset = preset },
                            label = {
                                Text(
                                    text = preset.label,
                                    modifier = Modifier.widthIn(min = 72.dp)
                                )
                            }
                        )
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Ajustes manuales", fontWeight = FontWeight.SemiBold)
                        Text("Brillo: ${(brightnessAdjust * 100).toInt()}%")
                        Slider(
                            value = brightnessAdjust,
                            onValueChange = { brightnessAdjust = it },
                            valueRange = -0.5f..0.5f
                        )
                        Text("Contraste: ${(contrastAdjust * 100).toInt()}%")
                        Slider(
                            value = contrastAdjust,
                            onValueChange = { contrastAdjust = it },
                            valueRange = 0.6f..1.6f
                        )
                        Text("Nitidez: ${(sharpenAdjust * 100).toInt()}%")
                        Slider(
                            value = sharpenAdjust,
                            onValueChange = { sharpenAdjust = it },
                            valueRange = 0f..1f
                        )
                        Text("Saturación: ${(saturationAdjust * 100).toInt()}%")
                        Slider(
                            value = saturationAdjust,
                            onValueChange = { saturationAdjust = it },
                            valueRange = 0f..2f
                        )
                        Text("Temperatura: ${(warmthAdjust * 100).toInt()}%")
                        Slider(
                            value = warmthAdjust,
                            onValueChange = { warmthAdjust = it },
                            valueRange = -0.4f..0.4f
                        )
                    }
                }
                Text("Antes / Después", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ImagePanel(
                        title = "Original",
                        bitmap = sourceBitmap,
                        modifier = Modifier.weight(1f)
                    )
                    ImagePanel(
                        title = "Reparada",
                        bitmap = repairedBitmap ?: sourceBitmap,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Selecciona una foto para empezar el diagnóstico.",
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

private data class ImageAnalysisReport(
    val label: String,
    val score: Float
)

private enum class EditTool(val label: String) {
    AUTO_REPAIR("Auto reparar"),
    INVERT("Invertir"),
    EDGE_LINES("Ver líneas"),
    ENHANCE_FOCUS("Enfoque"),
    GRAYSCALE("B/N"),
    SEPIA("Sepia"),
    MANUAL_ADJUST("Manual")
}

private enum class PresetFilter(val label: String) {
    NONE("Sin preset"),
    VIVID("Vívido"),
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
    val warmth: Float
)

private fun analyzeBitmap(bitmap: Bitmap): ImageAnalysisReport {
    val width = bitmap.width.coerceAtLeast(1)
    val height = bitmap.height.coerceAtLeast(1)
    val sampleStep = (width / 80).coerceAtLeast(1)

    var edgeAccum = 0L
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
            edgeAccum += abs(l1 - l2).toLong()
            brightnessAccum += l1.toLong()
            count++
            x += sampleStep
        }
        y += sampleStep
    }

    val edge = if (count == 0L) 0f else (edgeAccum.toFloat() / count.toFloat()) / 255f
    val brightness = if (count == 0L) 0f else (brightnessAccum.toFloat() / count.toFloat()) / 255f

    val score = (edge * 0.65f + (1f - abs(0.55f - brightness)) * 0.35f).coerceIn(0f, 1f)
    val label = when {
        score >= 0.75f -> "Buena calidad"
        edge < 0.12f -> "Posible fuera de foco"
        brightness < 0.28f -> "Subexpuesta (oscura)"
        brightness > 0.85f -> "Sobreexpuesta"
        else -> "Calidad media, mejorable"
    }
    return ImageAnalysisReport(label = label, score = score)
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

    return if (settings.sharpen > 0f) {
        sharpenBitmap(adjusted, settings.sharpen)
    } else {
        adjusted
    }
}

private fun luminance(pixel: Int): Int {
    val r = Color.red(pixel)
    val g = Color.green(pixel)
    val b = Color.blue(pixel)
    return (0.299 * r + 0.587 * g + 0.114 * b).toInt().coerceIn(0, 255)
}

private fun applyTool(
    source: Bitmap,
    tool: EditTool,
    settings: ImageSettings
): Bitmap {
    return when (tool) {
        EditTool.AUTO_REPAIR -> improveImage(source = source, settings = settings)
        EditTool.INVERT -> invertColors(source)
        EditTool.EDGE_LINES -> detectEdges(source)
        EditTool.ENHANCE_FOCUS -> sharpenBitmap(source, settings.sharpen.coerceAtLeast(0.35f))
        EditTool.GRAYSCALE -> toGrayscale(source)
        EditTool.SEPIA -> sepia(source)
        EditTool.MANUAL_ADJUST -> improveImage(source = source, settings = settings)
    }
}

private fun resolveSettings(
    tool: EditTool,
    preset: PresetFilter,
    base: ImageSettings
): ImageSettings {
    var settings = base
    if (tool == EditTool.AUTO_REPAIR) {
        settings = settings.copy(
            brightness = (settings.brightness + 0.06f).coerceIn(-0.5f, 0.5f),
            contrast = (settings.contrast * 1.08f).coerceIn(0.6f, 1.8f),
            sharpen = (settings.sharpen + 0.18f).coerceIn(0f, 1f),
            saturation = (settings.saturation * 1.05f).coerceIn(0f, 2f)
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
            brightness = (settings.brightness + 0.05f).coerceIn(-0.5f, 0.5f),
            warmth = (settings.warmth + 0.08f).coerceIn(-0.5f, 0.5f),
            contrast = (settings.contrast * 0.95f).coerceIn(0.6f, 1.8f)
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

private fun invertColors(source: Bitmap): Bitmap {
    val width = source.width
    val height = source.height
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)
    for (i in pixels.indices) {
        val c = pixels[i]
        pixels[i] = Color.argb(
            Color.alpha(c),
            255 - Color.red(c),
            255 - Color.green(c),
            255 - Color.blue(c)
        )
    }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, width, 0, 0, width, height)
    }
}

private fun detectEdges(source: Bitmap): Bitmap {
    val width = source.width
    val height = source.height
    val sourcePixels = IntArray(width * height)
    source.getPixels(sourcePixels, 0, width, 0, 0, width, height)
    val gray = IntArray(width * height) { index -> luminance(sourcePixels[index]) }
    val out = IntArray(width * height)
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
            val baseIndex = y * width + x
            for (ky in -1..1) {
                for (kx in -1..1) {
                    val lum = gray[(y + ky) * width + (x + kx)]
                    sumX += lum * gx[ky + 1][kx + 1]
                    sumY += lum * gy[ky + 1][kx + 1]
                }
            }
            val magnitude = sqrt((sumX * sumX + sumY * sumY).toDouble())
                .toInt()
                .coerceIn(0, 255)
            out[baseIndex] = Color.argb(255, magnitude, magnitude, magnitude)
        }
    }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(out, 0, width, 0, 0, width, height)
    }
}

private fun toGrayscale(source: Bitmap): Bitmap {
    val width = source.width
    val height = source.height
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)
    for (i in pixels.indices) {
        val c = pixels[i]
        val lum = luminance(c)
        pixels[i] = Color.argb(Color.alpha(c), lum, lum, lum)
    }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, width, 0, 0, width, height)
    }
}

private fun sepia(source: Bitmap): Bitmap {
    val width = source.width
    val height = source.height
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)
    for (i in pixels.indices) {
        val c = pixels[i]
        val r = Color.red(c)
        val g = Color.green(c)
        val b = Color.blue(c)
        val tr = (0.393 * r + 0.769 * g + 0.189 * b).toInt().coerceIn(0, 255)
        val tg = (0.349 * r + 0.686 * g + 0.168 * b).toInt().coerceIn(0, 255)
        val tb = (0.272 * r + 0.534 * g + 0.131 * b).toInt().coerceIn(0, 255)
        pixels[i] = Color.argb(Color.alpha(c), tr, tg, tb)
    }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, width, 0, 0, width, height)
    }
}

private fun sharpenBitmap(source: Bitmap, amount: Float): Bitmap {
    val width = source.width
    val height = source.height
    if (width < 3 || height < 3) return source.copy(Bitmap.Config.ARGB_8888, true)
    val srcPixels = IntArray(width * height)
    source.getPixels(srcPixels, 0, width, 0, 0, width, height)
    val outPixels = IntArray(width * height)
    val k = amount.coerceIn(0f, 1f)
    val center = 1f + 4f * k
    val neighbor = -k
    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val idx = y * width + x
            val c = srcPixels[idx]
            val left = srcPixels[idx - 1]
            val right = srcPixels[idx + 1]
            val up = srcPixels[idx - width]
            val down = srcPixels[idx + width]
            val r = (
                Color.red(c) * center +
                    Color.red(left) * neighbor +
                    Color.red(right) * neighbor +
                    Color.red(up) * neighbor +
                    Color.red(down) * neighbor
                ).toInt().coerceIn(0, 255)
            val g = (
                Color.green(c) * center +
                    Color.green(left) * neighbor +
                    Color.green(right) * neighbor +
                    Color.green(up) * neighbor +
                    Color.green(down) * neighbor
                ).toInt().coerceIn(0, 255)
            val b = (
                Color.blue(c) * center +
                    Color.blue(left) * neighbor +
                    Color.blue(right) * neighbor +
                    Color.blue(up) * neighbor +
                    Color.blue(down) * neighbor
                ).toInt().coerceIn(0, 255)
            outPixels[idx] = Color.argb(Color.alpha(c), r, g, b)
        }
    }
    // Copy borders unchanged.
    for (x in 0 until width) {
        outPixels[x] = srcPixels[x]
        outPixels[(height - 1) * width + x] = srcPixels[(height - 1) * width + x]
    }
    for (y in 0 until height) {
        outPixels[y * width] = srcPixels[y * width]
        outPixels[y * width + (width - 1)] = srcPixels[y * width + (width - 1)]
    }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(outPixels, 0, width, 0, 0, width, height)
    }
}

private suspend fun decodeBitmapForPreview(
    context: android.content.Context,
    uri: Uri,
    maxDimension: Int = 960
): Bitmap? = withContext(Dispatchers.IO) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    } ?: return@withContext null

    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
    var sample = 1
    while (max(bounds.outWidth / sample, bounds.outHeight / sample) > maxDimension) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample.coerceAtLeast(1)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, opts)
    } ?: return@withContext null

    val maxSide = max(decoded.width, decoded.height)
    if (maxSide <= maxDimension) return@withContext decoded
    val scale = maxDimension.toFloat() / maxSide.toFloat()
    val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
    val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
    Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
}

@Composable
private fun ImagePanel(
    title: String,
    bitmap: Bitmap?,
    modifier: Modifier = Modifier
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
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                )
            }
        }
    }
}
