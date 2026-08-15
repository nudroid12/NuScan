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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.nudroidlabs.nuscan.data.DocumentRepository
import com.nudroidlabs.nuscan.scan.ScanEnhancementEngine
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
    var busyText by remember { mutableStateOf("Preparing scanner") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        busy = false
        if (activityResult.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult

        val result = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
        val pdf = result?.pdf
        val pageUris = result?.pages?.map { it.imageUri }.orEmpty()
        val pageCount = pageUris.size
        if (pageUris.isEmpty() && pdf == null) {
            errorText = "The scan finished without usable page images or a PDF result."
            return@rememberLauncherForActivityResult
        }

        busy = true
        busyText = "Applying NuScan Clean"
        scope.launch {
            val enhanced = runCatching {
                require(pageUris.isNotEmpty()) { "No scanned JPEG pages were returned." }
                withContext(Dispatchers.IO) {
                    ScanEnhancementEngine.createEnhancedPdf(
                        context = context,
                        pageUris = pageUris,
                        requestedName = outputName,
                        onProgress = { current, total ->
                            scope.launch(Dispatchers.Main) {
                                busyText = "Enhancing page $current of $total"
                            }
                        }
                    ).file
                }
            }

            enhanced.onSuccess { file ->
                busy = false
                onCreated(file, pageCount)
            }.onFailure { enhancementError ->
                // Keep scanning reliable. If NuScan Clean cannot process a device-specific JPEG,
                // preserve the scan by importing the original ML Kit PDF instead.
                val fallback = pdf?.let { originalPdf ->
                    runCatching {
                        withContext(Dispatchers.IO) {
                            DocumentRepository.importPdf(context, originalPdf.uri, outputName)
                        }
                    }
                }

                if (fallback != null && fallback.isSuccess) {
                    busy = false
                    onCreated(fallback.getOrThrow(), result?.pages?.size ?: 0)
                } else {
                    busy = false
                    errorText = enhancementError.message ?: fallback?.exceptionOrNull()?.message
                        ?: "Unable to save the scanned PDF."
                }
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
        busyText = "Preparing scanner"
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
            title = { Text("Scan document", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                IconButton(onClick = onBack, enabled = !busy) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.DocumentScanner,
                            contentDescription = null,
                            modifier = Modifier.size(30.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Paper to PDF",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Capture, crop, rotate and filter pages, then save one PDF.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                Button(
                    onClick = { startScanner() },
                    enabled = !busy && outputName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Text(busyText)
                    } else {
                        Icon(Icons.Default.DocumentScanner, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Start scanning")
                    }
                }
            }

            errorText?.let { message ->
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            message,
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                Text(
                    "Scanner includes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        ScannerFeature("Automatic document detection and capture")
                        ScannerFeature("Crop, perspective, rotate and filters")
                        ScannerFeature("NuScan Clean lighting, shadow and text enhancement")
                        ScannerFeature("Multi-page scans and gallery import")
                    }
                }
            }

            item {
                Text(
                    "Scanning uses Google Play services and may download scanner components on first use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ScannerFeature(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
