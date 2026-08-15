package com.nudroidlabs.nuscan

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.automirrored.filled.CallMerge
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergePdfPage(
    modifier: Modifier,
    onBack: () -> Unit,
    onCreated: (File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selected = remember { mutableStateListOf<Uri>() }
    var outputName by remember {
        mutableStateOf("NuScan_Merged_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}")
    }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (uri !in selected) selected.add(uri)
        }
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Merge PDFs") },
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
                        Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null)
                        Text("Combine PDFs without flattening pages", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Choose two or more PDFs, arrange their order, then create one merged document.",
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
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(if (selected.isEmpty()) "Choose PDFs" else "Add more PDFs")
                }
            }

            if (selected.isEmpty()) {
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text("No PDFs selected.", modifier = Modifier.padding(16.dp))
                    }
                }
            } else {
                items(selected.size, key = { selected[it].toString() }) { index ->
                    val uri = selected[index]
                    Card(shape = RoundedCornerShape(16.dp)) {
                        ListItem(
                            headlineContent = {
                                Text("${index + 1}. ${pdfDisplayName(context, uri)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = { Text("Merge position ${index + 1}") },
                            leadingContent = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                            trailingContent = {
                                Row {
                                    IconButton(
                                        enabled = index > 0 && !busy,
                                        onClick = { selected.moveItem(index, index - 1) }
                                    ) { Icon(Icons.Default.ArrowUpward, contentDescription = "Move up") }
                                    IconButton(
                                        enabled = index < selected.lastIndex && !busy,
                                        onClick = { selected.moveItem(index, index + 1) }
                                    ) { Icon(Icons.Default.ArrowDownward, contentDescription = "Move down") }
                                    IconButton(
                                        enabled = !busy,
                                        onClick = { selected.removeAt(index) }
                                    ) { Icon(Icons.Default.DeleteOutline, contentDescription = "Remove") }
                                }
                            }
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = outputName,
                    onValueChange = { outputName = it },
                    enabled = !busy,
                    singleLine = true,
                    label = { Text("Merged PDF name") },
                    suffix = { Text(".pdf") },
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
                        busy = true
                        errorText = null
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    PdfTools.merge(context, selected.toList(), outputName)
                                }
                            }
                            busy = false
                            result.onSuccess(onCreated)
                                .onFailure { errorText = it.message ?: "PDF merge failed." }
                        }
                    },
                    enabled = selected.size >= 2 && outputName.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Text("Merging PDFs")
                    } else {
                        Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Merge PDFs")
                    }
                }
            }
        }
    }
}

private fun <T> MutableList<T>.moveItem(from: Int, to: Int) {
    if (from == to || from !in indices || to !in indices) return
    val item = removeAt(from)
    add(to, item)
}

internal fun pdfDisplayName(context: Context, uri: Uri): String {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    } ?: uri.lastPathSegment ?: "Document.pdf"
}
