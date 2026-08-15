package com.nudroidlabs.nuscan

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCut
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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SplitMode { EveryPage, CustomGroups }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitPdfPage(
    modifier: Modifier,
    onBack: () -> Unit,
    onCreated: (List<File>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Uri?>(null) }
    var sourceName by remember { mutableStateOf("Select a PDF") }
    var pageCount by remember { mutableStateOf<Int?>(null) }
    var baseName by remember { mutableStateOf("NuScan_Split") }
    var mode by remember { mutableStateOf(SplitMode.EveryPage) }
    var groups by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        source = uri
        sourceName = pdfDisplayName(context, uri)
        baseName = sourceName.removeSuffix(".pdf").removeSuffix(".PDF").ifBlank { "NuScan_Split" }
        pageCount = null
        errorText = null
        busy = true
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { PdfTools.pageCount(context, uri) } }
            busy = false
            result.onSuccess { count ->
                pageCount = count
                groups = if (count <= 1) "1" else "1-${minOf(3, count)}"
            }.onFailure { errorText = it.message ?: "Unable to read PDF." }
        }
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Split PDF") },
            navigationIcon = {
                IconButton(onClick = onBack, enabled = !busy) {
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
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.ContentCut, contentDescription = null)
                        Text("Split a PDF into smaller documents", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Create one file per page, or define custom page groups such as 1-3, 4, 5-8.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                FilledTonalButton(
                    onClick = { picker.launch(arrayOf("application/pdf")) },
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
                                Text(pageCount?.let { "$it page${if (it == 1) "" else "s"}" } ?: "Reading pages")
                            },
                            leadingContent = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) }
                        )
                    }
                }
            }

            item {
                Text("Split mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column {
                        ListItem(
                            headlineContent = { Text("Every page") },
                            supportingContent = { Text("Create one PDF for each page") },
                            leadingContent = {
                                RadioButton(selected = mode == SplitMode.EveryPage, onClick = { mode = SplitMode.EveryPage })
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Custom groups") },
                            supportingContent = { Text("Example: 1-3, 4, 5-8 creates three PDFs") },
                            leadingContent = {
                                RadioButton(selected = mode == SplitMode.CustomGroups, onClick = { mode = SplitMode.CustomGroups })
                            }
                        )
                    }
                }
            }

            if (mode == SplitMode.CustomGroups) {
                item {
                    OutlinedTextField(
                        value = groups,
                        onValueChange = { groups = it },
                        enabled = !busy,
                        label = { Text("Page groups") },
                        supportingText = { Text(pageCount?.let { "Available pages: 1-$it" } ?: "Choose a PDF first") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = baseName,
                    onValueChange = { baseName = it },
                    enabled = !busy,
                    singleLine = true,
                    label = { Text("Output base name") },
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
                        val uri = source ?: return@Button
                        busy = true
                        errorText = null
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    when (mode) {
                                        SplitMode.EveryPage -> PdfTools.splitEveryPage(context, uri, baseName)
                                        SplitMode.CustomGroups -> PdfTools.splitGroups(context, uri, baseName, groups)
                                    }
                                }
                            }
                            busy = false
                            result.onSuccess { onCreated(it.files) }
                                .onFailure { errorText = it.message ?: "PDF split failed." }
                        }
                    },
                    enabled = source != null && pageCount != null && baseName.isNotBlank() &&
                        (mode == SplitMode.EveryPage || groups.isNotBlank()) && !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Text("Splitting PDF")
                    } else {
                        Icon(Icons.Default.ContentCut, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Split PDF")
                    }
                }
            }
        }
    }
}
