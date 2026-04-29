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
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.abs

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
fun RepairHomeScreen() {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var repairedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var diagnosis by remember { mutableStateOf("Aún no hay diagnóstico") }
    var qualityScore by remember { mutableFloatStateOf(0f) }
    var repairing by remember { mutableStateOf(false) }
    var brightnessAdjust by remember { mutableFloatStateOf(0f) }
    var contrastAdjust by remember { mutableFloatStateOf(1f) }
    var sharpenAdjust by remember { mutableFloatStateOf(0f) }
    var selectedTool by remember { mutableStateOf(EditTool.AUTO_REPAIR) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedUri = uri
    }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri ?: return@LaunchedEffect
        val loaded = runCatching {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }
        }.getOrNull()
        if (loaded == null) {
            snackbarHostState.showSnackbar("No se pudo abrir la imagen seleccionada.")
            return@LaunchedEffect
        }
        originalBitmap = loaded
        repairedBitmap = null
        brightnessAdjust = 0f
        contrastAdjust = 1f
        sharpenAdjust = 0f
        selectedTool = EditTool.AUTO_REPAIR
        val report = analyzeBitmap(loaded)
        diagnosis = report.label
        qualityScore = report.score
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
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { picker.launch("image/*") }) {
                    Text("Seleccionar imagen")
                }
                OutlinedButton(
                    onClick = {
                        val source = originalBitmap ?: return@OutlinedButton
                        runCatching {
                            repairing = true
                            repairedBitmap = applyTool(
                                source = source,
                                tool = selectedTool,
                                brightnessAdjust = brightnessAdjust,
                                contrastAdjust = contrastAdjust,
                                sharpenAdjust = sharpenAdjust
                            )
                            val report = analyzeBitmap(repairedBitmap ?: source)
                            diagnosis = "Filtro ${selectedTool.label}: ${report.label}"
                            qualityScore = report.score
                        }.onFailure {
                            diagnosis = "No se pudo aplicar el filtro seleccionado."
                        }
                        repairing = false
                    },
                    enabled = originalBitmap != null && !repairing
                ) {
                    Text(if (repairing) "Reparando..." else "Reparar")
                }
                OutlinedButton(
                    onClick = {
                        repairedBitmap = originalBitmap
                        selectedTool = EditTool.AUTO_REPAIR
                        brightnessAdjust = 0f
                        contrastAdjust = 1f
                        sharpenAdjust = 0f
                        val source = originalBitmap
                        if (source != null) {
                            val report = analyzeBitmap(source)
                            diagnosis = "Ajustes reiniciados: ${report.label}"
                            qualityScore = report.score
                        }
                    },
                    enabled = originalBitmap != null && !repairing
                ) {
                    Text("Reset")
                }
            }

            if (originalBitmap != null) {
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
                    }
                }
                Text("Antes / Después", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ImagePanel(
                        title = "Original",
                        bitmap = originalBitmap,
                        modifier = Modifier.weight(1f)
                    )
                    ImagePanel(
                        title = "Reparada",
                        bitmap = repairedBitmap ?: originalBitmap,
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
    MANUAL_ADJUST("Manual")
}

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

private fun improveImage(
    source: Bitmap,
    brightnessAdjust: Float = 0f,
    contrastAdjust: Float = 1f,
    sharpenAdjust: Float = 0f
): Bitmap {
    val mutable = source.copy(Bitmap.Config.ARGB_8888, true)
    val width = mutable.width
    val height = mutable.height

    val contrast = contrastAdjust.coerceIn(0.6f, 1.8f)
    val brightness = (brightnessAdjust * 255f).toInt().coerceIn(-128, 128)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val c = mutable.getPixel(x, y)
            val r = ((Color.red(c) - 128) * contrast + 128 + brightness).toInt().coerceIn(0, 255)
            val g = ((Color.green(c) - 128) * contrast + 128 + brightness).toInt().coerceIn(0, 255)
            val b = ((Color.blue(c) - 128) * contrast + 128 + brightness).toInt().coerceIn(0, 255)
            mutable.setPixel(x, y, Color.argb(Color.alpha(c), r, g, b))
        }
    }

    val sharpened = if (sharpenAdjust > 0f) {
        sharpenBitmap(mutable, sharpenAdjust)
    } else {
        mutable
    }

    // Reencode to smooth artifacts and ensure valid output stream.
    val baos = ByteArrayOutputStream()
    sharpened.compress(Bitmap.CompressFormat.JPEG, 92, baos)
    val bytes = baos.toByteArray()
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: sharpened
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
    brightnessAdjust: Float,
    contrastAdjust: Float,
    sharpenAdjust: Float
): Bitmap {
    return when (tool) {
        EditTool.AUTO_REPAIR -> improveImage(
            source = source,
            brightnessAdjust = 0.06f + brightnessAdjust,
            contrastAdjust = (1.08f * contrastAdjust).coerceIn(0.6f, 1.8f),
            sharpenAdjust = (0.18f + sharpenAdjust).coerceIn(0f, 1f)
        )

        EditTool.INVERT -> invertColors(source)
        EditTool.EDGE_LINES -> detectEdges(source)
        EditTool.ENHANCE_FOCUS -> sharpenBitmap(source, (0.35f + sharpenAdjust).coerceIn(0f, 1f))
        EditTool.MANUAL_ADJUST -> improveImage(
            source = source,
            brightnessAdjust = brightnessAdjust,
            contrastAdjust = contrastAdjust,
            sharpenAdjust = sharpenAdjust
        )
    }
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
            val magnitude = kotlin.math.sqrt((sumX * sumX + sumY * sumY).toDouble())
                .toInt()
                .coerceIn(0, 255)
            result.setPixel(x, y, Color.argb(255, magnitude, magnitude, magnitude))
        }
    }
    return result
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
            dst.setPixel(x, y, Color.argb(Color.alpha(c), r, g, b))
        }
    }
    // Copy borders unchanged.
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
