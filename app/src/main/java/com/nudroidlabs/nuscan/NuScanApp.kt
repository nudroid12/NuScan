package com.nudroidlabs.nuscan

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.nudroidlabs.nuscan.data.DocumentRepository
import com.nudroidlabs.nuscan.monetization.NuScanBannerAd
import com.nudroidlabs.nuscan.monetization.PrivacyConsentController
import com.nudroidlabs.nuscan.pdf.PdfCreator
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class MainSection(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Documents("Documents", Icons.Default.Description),
    Tools("Tools", Icons.Default.Build),
    Settings("Settings", Icons.Default.Settings)
}

private sealed interface AppPage {
    data class Main(val section: MainSection) : AppPage
    data object Scanner : AppPage
    data object ImageToPdf : AppPage
    data object MergePdf : AppPage
    data object SplitPdf : AppPage
    data object PdfToImage : AppPage
    data object CompressPdf : AppPage
    data object Ocr : AppPage
    data object SignPdf : AppPage
    data object ProtectPdf : AppPage
    data object QrTools : AppPage
}

@Composable
fun NuScanApp() {
    val context = LocalContext.current
    val activity = context.findActivity()
    val privacy = remember { PrivacyConsentController(context.applicationContext) }
    var showOnboarding by remember { mutableStateOf(!AppPreferences.isOnboardingComplete(context)) }
    var page: AppPage by remember { mutableStateOf(AppPage.Main(MainSection.Home)) }
    var documentRefresh by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val appScope = rememberCoroutineScope()


    LaunchedEffect(activity) {
        activity?.let { privacy.requestConsent(it) }
    }

    if (showOnboarding) {
        OnboardingPage(
            onFinish = {
                AppPreferences.setOnboardingComplete(context, true)
                showOnboarding = false
            }
        )
        return
    }


    BackHandler(enabled = page !is AppPage.Main) {
        page = AppPage.Main(MainSection.Home)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (page is AppPage.Main) {
                val current = (page as AppPage.Main).section
                NavigationBar {
                    MainSection.entries.forEach { section ->
                        NavigationBarItem(
                            selected = current == section,
                            onClick = { page = AppPage.Main(section) },
                            icon = { Icon(section.icon, contentDescription = section.label) },
                            label = { Text(section.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        when (val current = page) {
            is AppPage.Main -> MainPage(
                section = current.section,
                refreshKey = documentRefresh,
                modifier = Modifier.padding(padding),
                onScan = { page = AppPage.Scanner },
                onImageToPdf = { page = AppPage.ImageToPdf },
                onMergePdf = { page = AppPage.MergePdf },
                onSplitPdf = { page = AppPage.SplitPdf },
                onPdfToImage = { page = AppPage.PdfToImage },
                onCompressPdf = { page = AppPage.CompressPdf },
                onOcr = { page = AppPage.Ocr },
                onSignPdf = { page = AppPage.SignPdf },
                onProtectPdf = { page = AppPage.ProtectPdf },
                onQrTools = { page = AppPage.QrTools },
                privacy = privacy,
                onPrivacyOptions = { activity?.let(privacy::showPrivacyOptions) },
                onReplayOnboarding = {
                    AppPreferences.setOnboardingComplete(context, false)
                    showOnboarding = true
                }
            )
            AppPage.Scanner -> ScannerPage(
                modifier = Modifier.padding(padding),
                onBack = { page = AppPage.Main(MainSection.Home) },
                onCreated = { file, pageCount ->
                    documentRefresh++
                    page = AppPage.Main(MainSection.Documents)
                    appScope.launch {
                        snackbar.showSnackbar("Scanned $pageCount page${if (pageCount == 1) "" else "s"} to ${file.name}")
                    }
                }
            )
            AppPage.ImageToPdf -> ImageToPdfPage(
                modifier = Modifier.padding(padding),
                onBack = { page = AppPage.Main(MainSection.Home) },
                onCreated = { file ->
                    documentRefresh++
                    page = AppPage.Main(MainSection.Documents)
                    appScope.launch {
                        snackbar.showSnackbar("Created ${file.name}")
                    }
                }
            )
            AppPage.MergePdf -> MergePdfPage(
                modifier = Modifier.padding(padding),
                onBack = { page = AppPage.Main(MainSection.Tools) },
                onCreated = { file ->
                    documentRefresh++
                    page = AppPage.Main(MainSection.Documents)
                    appScope.launch { snackbar.showSnackbar("Merged into ${file.name}") }
                }
            )
            AppPage.SplitPdf -> SplitPdfPage(
                modifier = Modifier.padding(padding),
                onBack = { page = AppPage.Main(MainSection.Tools) },
                onCreated = { files ->
                    documentRefresh++
                    page = AppPage.Main(MainSection.Documents)
                    appScope.launch { snackbar.showSnackbar("Created ${files.size} split PDF${if (files.size == 1) "" else "s"}") }
                }
            )
            AppPage.PdfToImage -> PdfToImagePage(
                modifier = Modifier.padding(padding),
                onBack = { page = AppPage.Main(MainSection.Tools) },
                onExported = { result ->
                    page = AppPage.Main(MainSection.Tools)
                    appScope.launch { snackbar.showSnackbar("Exported ${result.imageCount} image${if (result.imageCount == 1) "" else "s"} to ${result.folderName}") }
                }
            )
            AppPage.CompressPdf -> CompressPdfPage(
                modifier = Modifier.padding(padding),
                onBack = { page = AppPage.Main(MainSection.Tools) },
                onCreated = { result ->
                    documentRefresh++
                    page = AppPage.Main(MainSection.Documents)
                    appScope.launch {
                        val before = if (result.originalBytes > 0) formatSize(result.originalBytes) else "unknown size"
                        val after = formatSize(result.outputBytes)
                        snackbar.showSnackbar("Compressed ${result.pageCount} page${if (result.pageCount == 1) "" else "s"}: $before to $after")
                    }
                }
            )
            AppPage.Ocr -> OcrPage(
                modifier = Modifier.padding(padding),
                onBack = { page = AppPage.Main(MainSection.Tools) }
            )
            AppPage.SignPdf -> SignPdfPage(
                modifier = Modifier.padding(padding),
                onBack = { page = AppPage.Main(MainSection.Tools) },
                onCreated = { file ->
                    documentRefresh++
                    page = AppPage.Main(MainSection.Documents)
                    appScope.launch { snackbar.showSnackbar("Signed copy created: ${file.name}") }
                }
            )
            AppPage.ProtectPdf -> ProtectPdfPage(
                modifier = Modifier.padding(padding),
                onBack = { page = AppPage.Main(MainSection.Tools) },
                onCreated = { file ->
                    documentRefresh++
                    page = AppPage.Main(MainSection.Documents)
                    appScope.launch { snackbar.showSnackbar("Protected copy created: ${file.name}") }
                }
            )
            AppPage.QrTools -> QrToolsPage(
                modifier = Modifier.padding(padding),
                onBack = { page = AppPage.Main(MainSection.Tools) }
            )
        }
    }
}

@Composable
private fun MainPage(
    section: MainSection,
    refreshKey: Int,
    modifier: Modifier,
    onScan: () -> Unit,
    onImageToPdf: () -> Unit,
    onMergePdf: () -> Unit,
    onSplitPdf: () -> Unit,
    onPdfToImage: () -> Unit,
    onCompressPdf: () -> Unit,
    onOcr: () -> Unit,
    onSignPdf: () -> Unit,
    onProtectPdf: () -> Unit,
    onQrTools: () -> Unit,
    privacy: PrivacyConsentController,
    onPrivacyOptions: () -> Unit,
    onReplayOnboarding: () -> Unit
) {
    when (section) {
        MainSection.Home -> HomePage(modifier, refreshKey, onScan, onImageToPdf, onMergePdf, onSplitPdf, onPdfToImage, onCompressPdf, onOcr, onSignPdf, onProtectPdf, onQrTools)
        MainSection.Documents -> DocumentsPage(modifier, refreshKey)
        MainSection.Tools -> ToolsPage(modifier, onScan, onImageToPdf, onMergePdf, onSplitPdf, onPdfToImage, onCompressPdf, onOcr, onSignPdf, onProtectPdf, onQrTools)
        MainSection.Settings -> SettingsPage(
            modifier = modifier,
            privacy = privacy,
            onReplayOnboarding = onReplayOnboarding,
            onPrivacyOptions = onPrivacyOptions
        )
    }
}

@Composable
private fun HomePage(
    modifier: Modifier,
    refreshKey: Int,
    onScan: () -> Unit,
    onImageToPdf: () -> Unit,
    onMergePdf: () -> Unit,
    onSplitPdf: () -> Unit,
    onPdfToImage: () -> Unit,
    onCompressPdf: () -> Unit,
    onOcr: () -> Unit,
    onSignPdf: () -> Unit,
    onProtectPdf: () -> Unit,
    onQrTools: () -> Unit
) {
    val context = LocalContext.current
    val recent = remember(refreshKey) { DocumentRepository.listPdfFiles(context).take(3) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("NuScan", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Scan, create and manage documents.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Card(
                onClick = onScan,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            Icons.Default.DocumentScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(12.dp).size(28.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Scan document", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Capture pages and save a PDF",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
        item {
            Text("Quick tools", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTool("Image to PDF", Icons.Default.Image, Modifier.weight(1f), onClick = onImageToPdf)
                QuickTool("Compress", Icons.Default.SwapVert, Modifier.weight(1f), onClick = onCompressPdf)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTool("Merge PDFs", Icons.AutoMirrored.Filled.CallMerge, Modifier.weight(1f), onClick = onMergePdf)
                QuickTool("OCR", Icons.Default.TextFields, Modifier.weight(1f), onClick = onOcr)
            }
        }
        item {
            Text(
                "More tools are available in the Tools tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Text("Recent documents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (recent.isEmpty()) {
            item { EmptyDocumentsCard() }
        } else {
            items(recent, key = { it.absolutePath }) { file ->
                DocumentRow(file = file)
            }
        }
    }
}

@Composable
private fun QuickTool(
    title: String,
    icon: ImageVector,
    modifier: Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier,
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun EmptyDocumentsCard() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("No documents yet", fontWeight = FontWeight.Medium)
            Text(
                "Your NuScan PDFs will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DocumentsPage(modifier: Modifier, refreshKey: Int) {
    val context = LocalContext.current
    var refresh by remember(refreshKey) { mutableIntStateOf(refreshKey) }
    val files = remember(refresh) { DocumentRepository.listPdfFiles(context) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Documents", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "PDFs created by NuScan stay on this device.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
        if (files.isEmpty()) {
            item { EmptyDocumentsCard() }
        } else {
            items(files, key = { it.absolutePath }) { file ->
                DocumentRow(file = file, onDeleted = { refresh++ })
            }
        }
    }
}

@Composable
private fun DocumentRow(file: File, onDeleted: (() -> Unit)? = null) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    Card(shape = RoundedCornerShape(18.dp)) {
        ListItem(
            headlineContent = {
                Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            },
            supportingContent = {
                Text("${formatSize(file.length())}  •  ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(file.lastModified()))}")
            },
            leadingContent = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
            trailingContent = {
                Row {
                    IconButton(onClick = { sharePdf(context, file) }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    if (onDeleted != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete")
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(
            onClick = { openPdf(context, file) },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp).fillMaxWidth()
        ) {
            Text("Open PDF")
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete document?") },
            text = { Text(file.name) },
            confirmButton = {
                Button(onClick = {
                    file.delete()
                    confirmDelete = false
                    onDeleted?.invoke()
                }) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ToolsPage(
    modifier: Modifier,
    onScan: () -> Unit,
    onImageToPdf: () -> Unit,
    onMergePdf: () -> Unit,
    onSplitPdf: () -> Unit,
    onPdfToImage: () -> Unit,
    onCompressPdf: () -> Unit,
    onOcr: () -> Unit,
    onSignPdf: () -> Unit,
    onProtectPdf: () -> Unit,
    onQrTools: () -> Unit
) {
    val tools = listOf(
        ToolItem("Image to PDF", "Images into a multi-page PDF", Icons.Default.Image, ToolAction.ImageToPdf),
        ToolItem("Document scanner", "Auto crop, rotate and filters", Icons.Default.DocumentScanner, ToolAction.Scanner),
        ToolItem("Merge PDFs", "Combine multiple PDFs in your chosen order", Icons.AutoMirrored.Filled.CallMerge, ToolAction.MergePdf),
        ToolItem("Split PDF", "Every page or custom page groups", Icons.Default.ContentCut, ToolAction.SplitPdf),
        ToolItem("PDF to image", "Export pages as PNG or JPEG", Icons.Default.PictureAsPdf, ToolAction.PdfToImage),
        ToolItem("Compress PDF", "Reduce scanned PDF size with quality presets", Icons.Default.SwapVert, ToolAction.CompressPdf),
        ToolItem("OCR", "Extract editable text from images or PDFs", Icons.Default.TextFields, ToolAction.Ocr),
        ToolItem("Sign PDF", "Draw and place a visible signature", Icons.Default.Draw, ToolAction.SignPdf),
        ToolItem("Protect PDF", "Require a password to open a PDF copy", Icons.Default.Lock, ToolAction.ProtectPdf),
        ToolItem("QR tools", "Scan, generate, save and share QR codes", Icons.Default.QrCode2, ToolAction.QrTools)
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Tools", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("All NuScan tools are free and ready to use.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
        items(tools) { tool ->
            Card(
                onClick = {
                    when (tool.action) {
                        ToolAction.ImageToPdf -> onImageToPdf()
                        ToolAction.Scanner -> onScan()
                        ToolAction.MergePdf -> onMergePdf()
                        ToolAction.SplitPdf -> onSplitPdf()
                        ToolAction.PdfToImage -> onPdfToImage()
                        ToolAction.CompressPdf -> onCompressPdf()
                        ToolAction.Ocr -> onOcr()
                        ToolAction.SignPdf -> onSignPdf()
                        ToolAction.ProtectPdf -> onProtectPdf()
                        ToolAction.QrTools -> onQrTools()
                        null -> Unit
                    }
                },
                enabled = tool.action != null,
                shape = RoundedCornerShape(18.dp)
            ) {
                ListItem(
                    headlineContent = { Text(tool.title, fontWeight = FontWeight.Medium) },
                    supportingContent = { Text(tool.subtitle) },
                    leadingContent = { Icon(tool.icon, contentDescription = null) },
                    trailingContent = {
                        if (tool.action != null) Icon(Icons.Default.CheckCircle, contentDescription = "Available")
                    }
                )
            }
        }
    }
}

private enum class ToolAction { ImageToPdf, Scanner, MergePdf, SplitPdf, PdfToImage, CompressPdf, Ocr, SignPdf, ProtectPdf, QrTools }

private data class ToolItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val action: ToolAction?
)

@Composable
private fun SettingsPage(
    modifier: Modifier,
    privacy: PrivacyConsentController,
    onReplayOnboarding: () -> Unit,
    onPrivacyOptions: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Simple controls for NuScan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
        }
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                ListItem(
                    headlineContent = { Text("Free document tools") },
                    supportingContent = { Text("Every NuScan tool is available without a subscription or Pro plan.") },
                    leadingContent = { Icon(Icons.Default.CheckCircle, contentDescription = null) }
                )
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                ListItem(
                    headlineContent = { Text("On-device processing") },
                    supportingContent = { Text("PDF tools, compression, OCR, QR generation and visible signing run on-device. Scanner and QR scanning use Google Play services UI.") },
                    leadingContent = { Icon(Icons.Default.Lock, contentDescription = null) }
                )
            }
        }
        item {
            OutlinedButton(onClick = onReplayOnboarding, modifier = Modifier.fillMaxWidth()) {
                Text("Replay onboarding")
            }
        }
        if (privacy.privacyOptionsRequired) {
            item {
                Card(onClick = onPrivacyOptions, shape = RoundedCornerShape(18.dp)) {
                    ListItem(
                        headlineContent = { Text("Privacy choices") },
                        supportingContent = { Text(privacy.statusText) },
                        leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) }
                    )
                }
            }
        }
        item {
            Text(
                "NuScan uses a light ad-supported model. Development builds use Google's test banner IDs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (privacy.canRequestAds) {
            item { NuScanBannerAd() }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                ListItem(
                    headlineContent = { Text("NuScan ${BuildConfig.VERSION_NAME}") },
                    supportingContent = { Text("Developer: NudroidLabs\nPackage: ${BuildConfig.APPLICATION_ID}") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageToPdfPage(
    modifier: Modifier,
    onBack: () -> Unit,
    onCreated: (File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selected = remember { mutableStateListOf<Uri>() }
    var outputName by remember {
        mutableStateOf("NuScan_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}")
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
            title = { Text("Image to PDF") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("1. Add images", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("The list order becomes the PDF page order.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = { picker.launch(arrayOf("image/*")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(if (selected.isEmpty()) "Choose images" else "Add more images")
                }
            }

            if (selected.isEmpty()) {
                item {
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text("No images selected.", modifier = Modifier.padding(18.dp))
                    }
                }
            } else {
                items(selected.size, key = { index -> selected[index].toString() }) { index ->
                    val uri = selected[index]
                    Card(shape = RoundedCornerShape(16.dp)) {
                        ListItem(
                            headlineContent = {
                                Text("${index + 1}. ${displayName(context, uri)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = { Text("Page ${index + 1}") },
                            leadingContent = { Icon(Icons.Default.Image, contentDescription = null) },
                            trailingContent = {
                                Row {
                                    IconButton(
                                        enabled = index > 0 && !busy,
                                        onClick = { selected.move(index, index - 1) }
                                    ) { Icon(Icons.Default.ArrowUpward, contentDescription = "Move up") }
                                    IconButton(
                                        enabled = index < selected.lastIndex && !busy,
                                        onClick = { selected.move(index, index + 1) }
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
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("2. Name your PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = outputName,
                    onValueChange = { outputName = it },
                    enabled = !busy,
                    singleLine = true,
                    label = { Text("File name") },
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
                                    PdfCreator.create(context, selected.toList(), outputName)
                                }
                            }
                            busy = false
                            result.onSuccess(onCreated)
                                .onFailure { errorText = it.message ?: "PDF creation failed." }
                        }
                    },
                    enabled = selected.isNotEmpty() && outputName.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Text("Creating PDF")
                    } else {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Create PDF")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

private fun <T> MutableList<T>.move(from: Int, to: Int) {
    if (from == to || from !in indices || to !in indices) return
    val item = removeAt(from)
    add(to, item)
}

private fun displayName(context: Context, uri: Uri): String {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    } ?: uri.lastPathSegment ?: "Image"
}

private fun pdfUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.files", file)

private fun openPdf(context: Context, file: File) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(pdfUri(context, file), "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        sharePdf(context, file)
    }
}

private fun sharePdf(context: Context, file: File) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, pdfUri(context, file))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share PDF"))
}


private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
