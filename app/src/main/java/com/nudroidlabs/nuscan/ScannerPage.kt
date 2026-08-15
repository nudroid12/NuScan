package com.nudroidlabs.nuscan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.nudroidlabs.nuscan.pdf.PdfCreator
import com.nudroidlabs.nuscan.scan.AutoCaptureTracker
import com.nudroidlabs.nuscan.scan.DocumentEdgeDetector
import com.nudroidlabs.nuscan.scan.PerspectiveCorrector
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ScanPageState(
    val rawFile: File,
    val correctedFile: File,
    val corners: List<PointF>,
    val autoCorrected: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerPage(
    modifier: Modifier,
    onBack: () -> Unit,
    onCreated: (File, Int) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val tracker = remember { AutoCaptureTracker() }
    val captureGuard = remember { AtomicBoolean(false) }
    val analysisEnabled = remember { AtomicBoolean(true) }
    val lastAnalysisAt = remember { AtomicLong(0L) }
    val acceptedPages = remember { mutableStateListOf<ScanPageState>() }

    var outputName by remember {
        mutableStateOf("NuScan_Scan_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}")
    }
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var currentPage by remember { mutableStateOf<ScanPageState?>(null) }
    var previewCorners by remember { mutableStateOf<List<Offset>?>(null) }
    var stableFraction by remember { mutableStateOf(0f) }
    var sharpEnough by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var creatingPdf by remember { mutableStateOf(false) }
    var autoCaptureSignal by remember { mutableIntStateOf(0) }
    var statusText by remember { mutableStateOf("Point the camera at a document") }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (!granted) cameraError = "Camera permission is required for NuScan's document scanner."
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && currentPage == null && captureGuard.compareAndSet(false, true)) {
            capturing = true
            analysisEnabled.set(false)
            val scanDir = File(context.cacheDir, "nuscan-camera").apply { mkdirs() }
            val stamp = System.currentTimeMillis()
            val rawFile = File(scanDir, "gallery-$stamp.img")
            val correctedFile = File(scanDir, "corrected-$stamp.jpg")
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            rawFile.outputStream().use { output -> input.copyTo(output) }
                        } ?: error("Unable to read the selected image.")
                    }
                    withContext(Dispatchers.Default) {
                        PerspectiveCorrector.correct(rawFile, correctedFile)
                    }
                }.onSuccess { corrected ->
                    currentPage = ScanPageState(
                        rawFile = rawFile,
                        correctedFile = corrected.outputFile,
                        corners = corrected.normalizedCorners,
                        autoCorrected = corrected.autoCorrected
                    )
                    capturing = false
                    captureGuard.set(false)
                    statusText = if (corrected.autoCorrected) "Perspective corrected" else "Edges need manual adjustment"
                }.onFailure { error ->
                    rawFile.delete()
                    correctedFile.delete()
                    capturing = false
                    captureGuard.set(false)
                    analysisEnabled.set(true)
                    cameraError = error.message ?: "Unable to process the selected image."
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
            acceptedPages.forEach {
                it.rawFile.delete()
                it.correctedFile.delete()
            }
            currentPage?.let {
                it.rawFile.delete()
                it.correctedFile.delete()
            }
        }
    }

    fun capturePhoto() {
        val capture = imageCapture ?: return
        if (currentPage != null || creatingPdf || !captureGuard.compareAndSet(false, true)) return

        capturing = true
        analysisEnabled.set(false)
        previewCorners = null
        stableFraction = 0f
        statusText = "Capturing high-quality image…"

        val scanDir = File(context.cacheDir, "nuscan-camera").apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val rawFile = File(scanDir, "raw-$stamp.jpg")
        val correctedFile = File(scanDir, "corrected-$stamp.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(rawFile).build()

        capture.takePicture(
            options,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.Default) {
                                PerspectiveCorrector.correct(rawFile, correctedFile)
                            }
                        }.onSuccess { corrected ->
                            currentPage = ScanPageState(
                                rawFile = rawFile,
                                correctedFile = corrected.outputFile,
                                corners = corrected.normalizedCorners,
                                autoCorrected = corrected.autoCorrected
                            )
                            capturing = false
                            captureGuard.set(false)
                            statusText = if (corrected.autoCorrected) {
                                "Perspective corrected"
                            } else {
                                "Edges need manual adjustment"
                            }
                        }.onFailure { error ->
                            rawFile.delete()
                            correctedFile.delete()
                            capturing = false
                            captureGuard.set(false)
                            analysisEnabled.set(true)
                            tracker.reset()
                            cameraError = error.message ?: "Unable to process the captured document."
                            statusText = "Try again"
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    mainHandler.post {
                        rawFile.delete()
                        correctedFile.delete()
                        capturing = false
                        captureGuard.set(false)
                        analysisEnabled.set(true)
                        tracker.reset()
                        cameraError = exception.message ?: "Unable to capture the document."
                        statusText = "Try again"
                    }
                }
            }
        )
    }

    LaunchedEffect(autoCaptureSignal) {
        if (autoCaptureSignal > 0 && currentPage == null && !capturing) capturePhoto()
    }

    DisposableEffect(permissionGranted, lifecycleOwner, previewView) {
        if (!permissionGranted) {
            onDispose { }
        } else {
            analysisEnabled.set(true)
            var disposed = false
            val providerFuture = ProcessCameraProvider.getInstance(context)
            val mainExecutor = ContextCompat.getMainExecutor(context)
            providerFuture.addListener({
                if (disposed) return@addListener
                runCatching {
                    val provider = providerFuture.get()
                    cameraProvider = provider
                    provider.unbindAll()

                    val targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
                    val preview = Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .setTargetRotation(targetRotation)
                        .build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                    val capture = ImageCapture.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .setTargetRotation(targetRotation)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setJpegQuality(100)
                        .build()

                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(android.util.Size(960, 720))
                        .setTargetRotation(targetRotation)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .setOutputImageRotationEnabled(true)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(cameraExecutor) { image ->
                        try {
                            if (!analysisEnabled.get() || captureGuard.get()) return@setAnalyzer
                            val now = SystemClock.elapsedRealtime()
                            val previousAnalysis = lastAnalysisAt.get()
                            if (now - previousAnalysis < 140L || !lastAnalysisAt.compareAndSet(previousAnalysis, now)) {
                                return@setAnalyzer
                            }
                            val detection = DocumentEdgeDetector.detect(image)
                            val normalized = detection?.corners?.map { point ->
                                Offset(
                                    (point.x / image.width.toFloat()).coerceIn(0f, 1f),
                                    (point.y / image.height.toFloat()).coerceIn(0f, 1f)
                                )
                            }
                            val autoState = tracker.update(detection, image.width, image.height, System.currentTimeMillis())

                            mainHandler.post {
                                if (currentPage != null || capturing) return@post
                                previewCorners = normalized
                                stableFraction = autoState.stableFraction
                                sharpEnough = autoState.sharpEnough
                                statusText = when {
                                    detection == null -> "Find all four document corners"
                                    !autoState.sharpEnough -> "Hold steady while NuScan focuses"
                                    autoState.stableFraction < 0.2f -> "Document detected"
                                    else -> "Hold steady ${(autoState.stableFraction * 100).toInt()}%"
                                }
                                if (autoState.shouldCapture) autoCaptureSignal++
                            }
                        } catch (_: Throwable) {
                            mainHandler.post {
                                previewCorners = null
                                stableFraction = 0f
                                statusText = "Move closer and keep all four corners visible"
                            }
                        } finally {
                            image.close()
                        }
                    }

                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture,
                        analysis
                    )
                    imageCapture = capture
                    cameraError = null
                    tracker.reset(1000L)
                }.onFailure { error ->
                    cameraError = error.message ?: "Unable to start the camera."
                }
            }, mainExecutor)

            onDispose {
                disposed = true
                analysisEnabled.set(false)
                cameraProvider?.unbindAll()
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Scan document") },
            navigationIcon = {
                IconButton(onClick = onBack, enabled = !creatingPdf) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        val page = currentPage
        if (page != null) {
            ScanReview(
                page = page,
                pageNumber = acceptedPages.size + 1,
                busy = creatingPdf,
                onRetake = {
                    page.rawFile.delete()
                    page.correctedFile.delete()
                    currentPage = null
                    analysisEnabled.set(true)
                    tracker.reset()
                    statusText = "Point the camera at a document"
                },
                onApplyCorners = { corners ->
                    creatingPdf = true
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.Default) {
                                PerspectiveCorrector.applyCorners(page.rawFile, corners, page.correctedFile)
                            }
                        }.onSuccess { corrected ->
                            currentPage = page.copy(corners = corrected.normalizedCorners, autoCorrected = true)
                            creatingPdf = false
                        }.onFailure { error ->
                            creatingPdf = false
                            cameraError = error.message ?: "Unable to apply the crop."
                        }
                    }
                },
                onAddPage = {
                    acceptedPages += page
                    currentPage = null
                    analysisEnabled.set(true)
                    tracker.reset()
                    statusText = "Page ${acceptedPages.size + 1}: point at the next document"
                },
                onFinish = {
                    val pages = acceptedPages.toList() + page
                    if (outputName.isBlank()) {
                        cameraError = "Enter a PDF name first."
                    } else {
                        creatingPdf = true
                        scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val uris = pages.map { scanPage ->
                                    FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.files",
                                        scanPage.correctedFile
                                    )
                                }
                                PdfCreator.create(context, uris, outputName)
                            }
                        }.onSuccess { file ->
                            pages.forEach {
                                it.rawFile.delete()
                                it.correctedFile.delete()
                            }
                            creatingPdf = false
                            onCreated(file, pages.size)
                        }.onFailure { error ->
                            creatingPdf = false
                            cameraError = error.message ?: "Unable to create the scanned PDF."
                        }
                        }
                    }
                }
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = outputName,
                    onValueChange = { outputName = it },
                    label = { Text("PDF name") },
                    singleLine = true,
                    enabled = !capturing,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!permissionGranted) {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Camera access is required", fontWeight = FontWeight.SemiBold)
                            Text("NuScan now detects and corrects document edges directly on-device.")
                            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                Text("Allow camera")
                            }
                            OutlinedButton(onClick = { galleryLauncher.launch("image/*") }) {
                                Text("Import photo instead")
                            }
                        }
                    }
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .background(Color.Black, RoundedCornerShape(18.dp))
                    ) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )
                        DocumentGuideOverlay(previewCorners, sharpEnough)

                        Card(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(12.dp)
                        ) {
                            Column(
                                Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(statusText, style = MaterialTheme.typography.labelLarge)
                                if (stableFraction > 0f && !capturing) {
                                    Spacer(Modifier.height(5.dp))
                                    LinearProgressIndicator(
                                        progress = { stableFraction },
                                        modifier = Modifier.width(180.dp)
                                    )
                                }
                            }
                        }

                        if (capturing) {
                            Card(
                                modifier = Modifier.align(Alignment.Center)
                            ) {
                                Row(
                                    Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text("Correcting perspective…")
                                }
                            }
                        }

                        IconButton(
                            onClick = { capturePhoto() },
                            enabled = !capturing && imageCapture != null,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(18.dp)
                                .size(68.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(34.dp))
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Capture now",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        enabled = !capturing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Import photo")
                    }

                    Text(
                        "Auto capture waits for four stable corners and sufficient sharpness. After capture, NuScan runs a second edge pass, straightens perspective and then sharpens the corrected page.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (acceptedPages.isNotEmpty()) {
                        Text(
                            "${acceptedPages.size} page${if (acceptedPages.size == 1) "" else "s"} ready",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                cameraError?.let { message ->
                    Card {
                        Text(
                            message,
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentGuideOverlay(corners: List<Offset>?, sharpEnough: Boolean) {
    val outline = if (sharpEnough) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val surface = MaterialTheme.colorScheme.surface
    Canvas(Modifier.fillMaxSize()) {
        val points = corners
        if (points == null || points.size != 4) {
            val insetX = size.width * 0.08f
            val insetY = size.height * 0.08f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.46f),
                topLeft = Offset(insetX, insetY),
                size = androidx.compose.ui.geometry.Size(size.width - insetX * 2, size.height - insetY * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(22f, 22f),
                style = Stroke(width = 3f)
            )
            return@Canvas
        }

        val mapped = points.map { Offset(it.x * size.width, it.y * size.height) }
        val path = Path().apply {
            moveTo(mapped[0].x, mapped[0].y)
            lineTo(mapped[1].x, mapped[1].y)
            lineTo(mapped[2].x, mapped[2].y)
            lineTo(mapped[3].x, mapped[3].y)
            close()
        }
        drawPath(path, color = outline.copy(alpha = 0.15f))
        drawPath(path, color = outline, style = Stroke(width = 5f))
        mapped.forEach { point ->
            drawCircle(outline, radius = 10f, center = point)
            drawCircle(surface, radius = 4f, center = point)
        }
    }
}

@Composable
private fun ScanReview(
    page: ScanPageState,
    pageNumber: Int,
    busy: Boolean,
    onRetake: () -> Unit,
    onApplyCorners: (List<PointF>) -> Unit,
    onAddPage: () -> Unit,
    onFinish: () -> Unit
) {
    var editing by remember(page.rawFile.absolutePath) { mutableStateOf(!page.autoCorrected) }
    var previewBitmap by remember(page.correctedFile.lastModified(), editing) {
        mutableStateOf<Bitmap?>(null)
    }
    var rawBitmap by remember(page.rawFile.absolutePath) { mutableStateOf<Bitmap?>(null) }
    var cornerOffsets by remember(page.rawFile.absolutePath, page.corners) {
        mutableStateOf(page.corners.map { Offset(it.x, it.y) })
    }

    LaunchedEffect(page.correctedFile.lastModified(), editing) {
        if (!editing) {
            previewBitmap?.recycle()
            previewBitmap = withContext(Dispatchers.Default) {
                PerspectiveCorrector.decodePreview(page.correctedFile)
            }
        }
    }
    LaunchedEffect(editing) {
        if (editing && rawBitmap == null) {
            rawBitmap = withContext(Dispatchers.Default) {
                PerspectiveCorrector.decodePreview(page.rawFile)
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            previewBitmap?.recycle()
            rawBitmap?.recycle()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Page $pageNumber", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (page.autoCorrected) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Perspective corrected", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (editing) {
            val bitmap = rawBitmap
            if (bitmap == null) {
                Box(Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Text(
                    "Drag the four corners onto the real paper edges, then apply crop.",
                    style = MaterialTheme.typography.bodyMedium
                )
                CornerEditor(
                    bitmap = bitmap,
                    corners = cornerOffsets,
                    onCornersChange = { cornerOffsets = it }
                )
                Button(
                    onClick = {
                        onApplyCorners(cornerOffsets.map { PointF(it.x, it.y) })
                        editing = false
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Crop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Apply perspective crop")
                }
                if (page.autoCorrected) {
                    OutlinedButton(
                        onClick = { editing = false },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel adjustment")
                    }
                }
            }
        } else {
            val bitmap = previewBitmap
            if (bitmap == null) {
                Box(Modifier.fillMaxWidth().height(380.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Corrected scan",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat()),
                    contentScale = ContentScale.Fit
                )
            }

            OutlinedButton(
                onClick = { editing = true },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Crop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Adjust corners")
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onRetake,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Retake")
                }
                Button(
                    onClick = onAddPage,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add page")
                }
            }
            Button(
                onClick = onFinish,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Creating PDF")
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Finish PDF")
                }
            }
        }
    }
}

@Composable
private fun CornerEditor(
    bitmap: Bitmap,
    corners: List<Offset>,
    onCornersChange: (List<Offset>) -> Unit
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val outline = MaterialTheme.colorScheme.primary
    val latestCorners = rememberUpdatedState(corners)
    val latestOnCornersChange = rememberUpdatedState(onCornersChange)

    Box(
        Modifier
            .fillMaxWidth()
            .height(500.dp)
            .background(Color.Black, RoundedCornerShape(16.dp))
            .onSizeChanged { boxSize = it }
            .pointerInput(bitmap, boxSize) {
                var active = -1
                detectDragGestures(
                    onDragStart = { position ->
                        val rect = fitRect(boxSize, bitmap.width, bitmap.height)
                        val mapped = latestCorners.value.map { normalizedToPx(it, rect) }
                        active = mapped.indices.minByOrNull { index ->
                            val p = mapped[index]
                            val dx = p.x - position.x
                            val dy = p.y - position.y
                            dx * dx + dy * dy
                        } ?: -1
                        if (active >= 0) {
                            val p = mapped[active]
                            val dx = p.x - position.x
                            val dy = p.y - position.y
                            if (dx * dx + dy * dy > 72.dp.toPx() * 72.dp.toPx()) active = -1
                        }
                    },
                    onDragEnd = { active = -1 },
                    onDragCancel = { active = -1 },
                    onDrag = { change, _ ->
                        val index = active
                        if (index < 0) return@detectDragGestures
                        change.consume()
                        val rect = fitRect(boxSize, bitmap.width, bitmap.height)
                        val n = pxToNormalized(change.position, rect)
                        val updated = latestCorners.value.toMutableList()
                        updated[index] = n
                        latestOnCornersChange.value(updated)
                    }
                )
            }
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Original capture",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
        Canvas(Modifier.fillMaxSize()) {
            val rect = fitRect(boxSize, bitmap.width, bitmap.height)
            val mapped = corners.map { normalizedToPx(it, rect) }
            if (mapped.size == 4) {
                val path = Path().apply {
                    moveTo(mapped[0].x, mapped[0].y)
                    lineTo(mapped[1].x, mapped[1].y)
                    lineTo(mapped[2].x, mapped[2].y)
                    lineTo(mapped[3].x, mapped[3].y)
                    close()
                }
                drawPath(path, outline.copy(alpha = 0.16f))
                drawPath(path, outline, style = Stroke(width = 5f))
                mapped.forEach { point ->
                    drawCircle(outline, radius = 18f, center = point)
                    drawCircle(Color.White, radius = 8f, center = point)
                }
            }
        }
    }
}

private fun fitRect(box: IntSize, imageWidth: Int, imageHeight: Int): Rect {
    if (box.width <= 0 || box.height <= 0 || imageWidth <= 0 || imageHeight <= 0) return Rect.Zero
    val boxRatio = box.width.toFloat() / box.height.toFloat()
    val imageRatio = imageWidth.toFloat() / imageHeight.toFloat()
    return if (imageRatio > boxRatio) {
        val h = box.width / imageRatio
        val top = (box.height - h) / 2f
        Rect(0f, top, box.width.toFloat(), top + h)
    } else {
        val w = box.height * imageRatio
        val left = (box.width - w) / 2f
        Rect(left, 0f, left + w, box.height.toFloat())
    }
}

private fun normalizedToPx(point: Offset, rect: Rect): Offset = Offset(
    rect.left + point.x.coerceIn(0f, 1f) * rect.width,
    rect.top + point.y.coerceIn(0f, 1f) * rect.height
)

private fun pxToNormalized(point: Offset, rect: Rect): Offset {
    if (rect.width <= 0f || rect.height <= 0f) return Offset(0.5f, 0.5f)
    return Offset(
        ((point.x - rect.left) / rect.width).coerceIn(0f, 1f),
        ((point.y - rect.top) / rect.height).coerceIn(0f, 1f)
    )
}
