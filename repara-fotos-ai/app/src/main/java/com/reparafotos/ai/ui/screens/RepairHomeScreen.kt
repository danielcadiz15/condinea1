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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
@OptIn(ExperimentalMaterial3Api::class)
fun RepairHomeScreen() {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var repairedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var diagnosis by remember { mutableStateOf("Aún no hay diagnóstico") }
    var qualityScore by remember { mutableFloatStateOf(0f) }
    var repairing by remember { mutableStateOf(false) }

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
                        repairing = true
                        repairedBitmap = improveImage(source)
                        val report = analyzeBitmap(repairedBitmap ?: source)
                        diagnosis = "Reparación aplicada: ${report.label}"
                        qualityScore = report.score
                        repairing = false
                    },
                    enabled = originalBitmap != null && !repairing
                ) {
                    Text(if (repairing) "Reparando..." else "Reparar")
                }
            }

            if (originalBitmap != null) {
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

private fun improveImage(source: Bitmap): Bitmap {
    val mutable = source.copy(Bitmap.Config.ARGB_8888, true)
    val width = mutable.width
    val height = mutable.height

    val contrast = 1.08f
    val brightness = 6
    for (y in 0 until height) {
        for (x in 0 until width) {
            val c = mutable.getPixel(x, y)
            val r = ((Color.red(c) - 128) * contrast + 128 + brightness).toInt().coerceIn(0, 255)
            val g = ((Color.green(c) - 128) * contrast + 128 + brightness).toInt().coerceIn(0, 255)
            val b = ((Color.blue(c) - 128) * contrast + 128 + brightness).toInt().coerceIn(0, 255)
            mutable.setPixel(x, y, Color.argb(Color.alpha(c), r, g, b))
        }
    }

    // Reencode to smooth artifacts and ensure valid output stream.
    val baos = ByteArrayOutputStream()
    mutable.compress(Bitmap.CompressFormat.JPEG, 92, baos)
    val bytes = baos.toByteArray()
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: mutable
}

private fun luminance(pixel: Int): Int {
    val r = Color.red(pixel)
    val g = Color.green(pixel)
    val b = Color.blue(pixel)
    return (0.299 * r + 0.587 * g + 0.114 * b).toInt().coerceIn(0, 255)
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
