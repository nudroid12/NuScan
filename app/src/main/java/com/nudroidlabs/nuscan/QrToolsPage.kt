package com.nudroidlabs.nuscan

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.nudroidlabs.nuscan.qr.QrEngine
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrToolsPage(
    modifier: Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var busyScan by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val scanner = remember(context) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options)
    }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val current = bitmap
        if (uri != null && current != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                    check(current.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Unable to write QR image." }
                } ?: error("Unable to write QR image.")
            }.onSuccess {
                message = "QR image saved."
                errorText = null
            }.onFailure {
                errorText = it.message ?: "Save failed."
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("QR Tools") },
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
                Text("Scan or create a QR code", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Scanning is delegated to Google Play services and returns only the decoded QR value to NuScan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Button(
                    onClick = {
                        busyScan = true
                        message = null
                        errorText = null
                        scope.launch {
                            runCatching { scanner.startScan().await() }
                                .onSuccess { barcode ->
                                    text = barcode.rawValue.orEmpty()
                                    bitmap = null
                                    message = if (text.isBlank()) "QR scanned, but it contained no text." else "QR scanned."
                                }
                                .onFailure { error ->
                                    errorText = error.message ?: "QR scan cancelled or failed."
                                }
                            busyScan = false
                        }
                    },
                    enabled = !busyScan,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (busyScan) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Text("Opening scanner")
                    } else {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Scan QR code")
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        bitmap = null
                    },
                    label = { Text("Text or link") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = {
                            copyQrText(context, text)
                            message = "Copied to clipboard."
                            errorText = null
                        },
                        enabled = text.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Copy")
                    }
                    Button(
                        onClick = {
                            runCatching { QrEngine.generate(text) }
                                .onSuccess {
                                    bitmap = it
                                    message = "QR generated."
                                    errorText = null
                                }
                                .onFailure { errorText = it.message ?: "Unable to generate QR." }
                        },
                        enabled = text.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Generate")
                    }
                }
            }
            bitmap?.let { qr ->
                item {
                    Surface(shape = RoundedCornerShape(22.dp), color = androidx.compose.ui.graphics.Color.White) {
                        Image(
                            bitmap = qr.asImageBitmap(),
                            contentDescription = "Generated QR code",
                            modifier = Modifier.fillMaxWidth().padding(20.dp)
                        )
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(
                            onClick = { saver.launch("NuScan_QR.png") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Save PNG")
                        }
                        Button(
                            onClick = {
                                runCatching { shareQrBitmap(context, qr) }
                                    .onFailure { errorText = it.message ?: "Share failed." }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Share")
                        }
                    }
                }
            }
            message?.let { info ->
                item {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(info, modifier = Modifier.padding(14.dp))
                    }
                }
            }
            errorText?.let { error ->
                item {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
                        Text(error, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

private fun copyQrText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("NuScan QR", text))
}

private fun shareQrBitmap(context: Context, bitmap: Bitmap) {
    val dir = File(context.cacheDir, "qr").apply { mkdirs() }
    val file = File(dir, "NuScan_QR.png")
    FileOutputStream(file).use { output ->
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Unable to prepare QR image." }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share QR code"))
}
