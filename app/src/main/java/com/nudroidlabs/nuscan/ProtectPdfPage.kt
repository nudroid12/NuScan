package com.nudroidlabs.nuscan

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nudroidlabs.nuscan.pdf.PdfSecurity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectPdfPage(
    modifier: Modifier,
    onBack: () -> Unit,
    onCreated: (java.io.File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Uri?>(null) }
    var sourceName by remember { mutableStateOf("No PDF selected") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var outputName by remember {
        mutableStateOf("NuScan_Protected_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}")
    }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            source = uri
            sourceName = protectDisplayName(context, uri)
        }
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Protect PDF") },
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
                Text("Add an open password", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("The protected copy will require this password when a compatible PDF reader opens it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            supportingContent = { Text("Selected PDF") },
                            leadingContent = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) }
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    enabled = !busy,
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    enabled = !busy,
                    label = { Text("Confirm password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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
                    Text("Keep the password somewhere safe. NuScan does not store it and cannot recover it later.", modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
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
                        if (password != confirmPassword) {
                            errorText = "Passwords do not match."
                            return@Button
                        }
                        busy = true
                        errorText = null
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) { PdfSecurity.protect(context, selected, outputName, password) }
                            }
                            busy = false
                            result.onSuccess(onCreated).onFailure { errorText = it.message ?: "Protection failed." }
                        }
                    },
                    enabled = source != null && password.length >= 4 && outputName.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Text("Protecting")
                    } else {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Protect PDF")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

private fun protectDisplayName(context: android.content.Context, uri: Uri): String {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    } ?: uri.lastPathSegment ?: "Document.pdf"
}
