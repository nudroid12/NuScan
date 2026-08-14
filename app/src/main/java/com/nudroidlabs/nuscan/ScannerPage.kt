package com.nudroidlabs.nuscan

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.nudroidlabs.nuscan.data.DocumentRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerPage(
    modifier: Modifier,
    onBack: () -> Unit,
    onCreated: (File, Int) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    var outputName by remember {
        mutableStateOf("NuScan_Scan_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}")
    }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        busy = false
        if (activityResult.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult

        val result = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
        val pdf = result?.pdf
        val pageCount = result?.pages?.size ?: 0
        if (pdf == null) {
            errorText = "The scan finished without a PDF result."
            return@rememberLauncherForActivityResult
        }

        busy = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    DocumentRepository.importPdf(context, pdf.uri, outputName)
                }
            }.onSuccess { file ->
                busy = false
                onCreated(file, pageCount)
            }.onFailure { error ->
                busy = false
                errorText = error.message ?: "Unable to save the scanned PDF."
            }
        }
    }

    fun startScanner() {
        val hostActivity = activity
        if (hostActivity == null) {
            errorText = "Unable to start the document scanner on this device."
            return
        }

        busy = true
        errorText = null
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(50)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(hostActivity)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { error ->
                busy = false
                errorText = error.message ?: "Document scanner is unavailable."
            }
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Scan document") },
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.DocumentScanner,
                            contentDescription = null,
                            modifier = Modifier.size(38.dp)
                        )
                        Text(
                            "Paper to PDF in one flow",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "NuScan detects the document, lets you crop, rotate and apply scan filters, then saves the final PDF in Documents.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = outputName,
                    onValueChange = { outputName = it },
                    label = { Text("PDF name") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text("Scanner includes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            item { ScannerFeature("Automatic document detection and capture") }
            item { ScannerFeature("Manual crop and perspective correction") }
            item { ScannerFeature("Rotate and scan filters") }
            item { ScannerFeature("Multi-page scans, up to 50 pages") }
            item { ScannerFeature("Import pages from the photo gallery") }

            errorText?.let { message ->
                item {
                    Card {
                        Text(
                            message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = { startScanner() },
                    enabled = !busy && outputName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Text("Preparing scanner")
                    } else {
                        Icon(Icons.Default.DocumentScanner, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Start scanning")
                    }
                }
            }

            item {
                Text(
                    "The scanner runs through Google Play services. On first use, its scanner components may need to be downloaded. Scanning and document processing then run on the device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ScannerFeature(text: String) {
    Card {
        ListItem(
            headlineContent = { Text(text) },
            leadingContent = { Icon(Icons.Default.CheckCircle, contentDescription = null) }
        )
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
