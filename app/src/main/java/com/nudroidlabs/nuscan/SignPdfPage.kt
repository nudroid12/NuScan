package com.nudroidlabs.nuscan

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nudroidlabs.nuscan.pdf.PdfSigner
import com.nudroidlabs.nuscan.pdf.PdfTools
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignPdfPage(
    modifier: Modifier,
    onBack: () -> Unit,
    onCreated: (java.io.File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Uri?>(null) }
    var sourceName by remember { mutableStateOf("No PDF selected") }
    var pageCount by remember { mutableIntStateOf(0) }
    var pageText by remember { mutableStateOf("1") }
    var outputName by remember {
        mutableStateOf("NuScan_Signed_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}")
    }
    var position by remember { mutableStateOf(PdfSigner.Position.BOTTOM_RIGHT) }
    var widthPercent by remember { mutableIntStateOf(28) }
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            source = uri
            sourceName = signDisplayName(context, uri)
            scope.launch {
                val countResult = runCatching { withContext(Dispatchers.IO) { PdfTools.pageCount(context, uri) } }
                countResult.onSuccess {
                    pageCount = it
                    pageText = "1"
                }.onFailure { errorText = it.message ?: "Unable to read PDF." }
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Sign PDF") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Add a visible signature", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Draw once, choose the page and position, then NuScan places the signature into a new PDF copy.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Button(onClick = { picker.launch(arrayOf("application/pdf")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(if (source == null) "Choose PDF" else "Choose another PDF")
                }
            }
            if (source != null) {
                item {
                    Card {
                        ListItem(
                            headlineContent = { Text(sourceName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(if (pageCount > 0) "$pageCount page${if (pageCount == 1) "" else "s"}" else "Reading pages") },
                            leadingContent = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) }
                        )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Signature pad", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    FilledTonalButton(onClick = { strokes = emptyList() }, enabled = strokes.isNotEmpty() && !busy) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Clear")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .pointerInput(busy) {
                                    if (busy) return@pointerInput
                                    detectDragGestures(
                                        onDragStart = { point -> strokes = strokes + listOf(listOf(point)) },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            if (strokes.isNotEmpty()) {
                                                val last = strokes.last() + change.position
                                                strokes = strokes.dropLast(1) + listOf(last)
                                            }
                                        }
                                    )
                                }
                        ) {
                            drawSignatureStrokes(strokes)
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = pageText,
                    onValueChange = { pageText = it.filter(Char::isDigit).take(4) },
                    enabled = !busy && pageCount > 0,
                    singleLine = true,
                    label = { Text(if (pageCount > 0) "Page number, 1-$pageCount" else "Page number") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Text("Position", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            PdfSigner.Position.entries.forEach { option ->
                item {
                    Card(onClick = { if (!busy) position = option }) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                            RadioButton(selected = position == option, onClick = { if (!busy) position = option })
                            ListItem(headlineContent = { Text(option.label) }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                Text("Signature width: $widthPercent% of page", fontWeight = FontWeight.Medium)
                Slider(
                    value = widthPercent.toFloat(),
                    onValueChange = { widthPercent = it.toInt() },
                    valueRange = 15f..50f,
                    steps = 6,
                    enabled = !busy
                )
            }
            item {
                OutlinedTextField(
                    value = outputName,
                    onValueChange = { outputName = it },
                    enabled = !busy,
                    singleLine = true,
                    label = { Text("Output file name") },
                    suffix = { Text(".pdf") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text("This is a visible handwritten signature stamp, not a certificate-based cryptographic digital signature.", modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            errorText?.let { message ->
                item {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
                        Text(message, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        val selected = source ?: return@Button
                        val page = pageText.toIntOrNull()
                        if (page == null || page !in 1..pageCount) {
                            errorText = "Enter a page between 1 and $pageCount."
                            return@Button
                        }
                        busy = true
                        errorText = null
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    PdfSigner.sign(
                                        context = context,
                                        source = selected,
                                        requestedName = outputName,
                                        pageNumber = page,
                                        position = position,
                                        widthPercent = widthPercent,
                                        strokes = strokes.map { PdfSigner.Stroke(it) }
                                    )
                                }
                            }
                            busy = false
                            result.onSuccess(onCreated).onFailure { errorText = it.message ?: "Signing failed." }
                        }
                    },
                    enabled = source != null && pageCount > 0 && strokes.any { it.size >= 2 } && outputName.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Text("Signing")
                    } else {
                        Icon(Icons.Default.Draw, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Add signature")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

private fun DrawScope.drawSignatureStrokes(strokes: List<List<Offset>>) {
    strokes.forEach { stroke ->
        for (index in 1 until stroke.size) {
            drawLine(
                color = androidx.compose.ui.graphics.Color.Black,
                start = stroke[index - 1],
                end = stroke[index],
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun signDisplayName(context: android.content.Context, uri: Uri): String {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    } ?: uri.lastPathSegment ?: "Document.pdf"
}
