package com.nudroidlabs.nuscan

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nudroidlabs.nuscan.pdf.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToImagePage(
    modifier: Modifier,
    onBack: () -> Unit,
    onExported: (PdfTools.ImageExportResult) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Uri?>(null) }
    var sourceName by remember { mutableStateOf("Select a PDF") }
    var pageCount by remember { mutableStateOf<Int?>(null) }
    var baseName by remember { mutableStateOf("NuScan_PDF") }
    var format by remember { mutableStateOf(PdfTools.ImageFormat.PNG) }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri == null) {
            busy = false
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        val sourceUri = source
        if (sourceUri == null) {
            busy = false
            errorText = "Choose a PDF first."
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    PdfTools.exportImages(context, sourceUri, treeUri, baseName, format)
                }
            }
            busy = false
            result.onSuccess(onExported)
                .onFailure { errorText = it.message ?: "Image export failed." }
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        source = uri
        sourceName = pdfDisplayName(context, uri)
        baseName = sourceName.removeSuffix(".pdf").removeSuffix(".PDF").ifBlank { "NuScan_PDF" }
        pageCount = null
        errorText = null
        busy = true
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { PdfTools.pageCount(context, uri) } }
            busy = false
            result.onSuccess { pageCount = it }
                .onFailure { errorText = it.message ?: "Unable to read PDF." }
        }
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("PDF to image") },
            navigationIcon = {
                IconButton(onClick = onBack, enabled = !busy) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Text("Export every PDF page as an image", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Choose PNG for lossless output or JPEG for smaller files. You choose the destination folder.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                FilledTonalButton(
                    onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(if (source == null) "Choose PDF" else "Choose another PDF")
                }
            }

            source?.let {
                item {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        ListItem(
                            headlineContent = { Text(sourceName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(pageCount?.let { "$it page${if (it == 1) "" else "s"} will be exported" } ?: "Reading pages")
                            },
                            leadingContent = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) }
                        )
                    }
                }
            }

            item {
                Text("Image format", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column {
                        ListItem(
                            headlineContent = { Text("PNG") },
                            supportingContent = { Text("Lossless, larger files") },
                            leadingContent = {
                                RadioButton(
                                    selected = format == PdfTools.ImageFormat.PNG,
                                    onClick = { format = PdfTools.ImageFormat.PNG }
                                )
                            }
                        )
                        ListItem(
                            headlineContent = { Text("JPEG") },
                            supportingContent = { Text("High quality, smaller files") },
                            leadingContent = {
                                RadioButton(
                                    selected = format == PdfTools.ImageFormat.JPEG,
                                    onClick = { format = PdfTools.ImageFormat.JPEG }
                                )
                            }
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = baseName,
                    onValueChange = { baseName = it },
                    enabled = !busy,
                    singleLine = true,
                    label = { Text("Image base name") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            errorText?.let { message ->
                item {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Text(message, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        errorText = null
                        busy = true
                        folderPicker.launch(null)
                    },
                    enabled = source != null && pageCount != null && baseName.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Text("Preparing export")
                    } else {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Choose folder and export")
                    }
                }
            }
        }
    }
}
