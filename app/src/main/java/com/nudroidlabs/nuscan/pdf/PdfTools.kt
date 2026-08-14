package com.nudroidlabs.nuscan.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.DocumentsContract
import com.nudroidlabs.nuscan.data.DocumentRepository
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

object PdfTools {
    enum class ImageFormat(val extension: String, val mimeType: String) {
        PNG("png", "image/png"),
        JPEG("jpg", "image/jpeg")
    }

    data class SplitResult(val files: List<File>, val pageCount: Int)
    data class ImageExportResult(val imageCount: Int, val folderName: String)

    private const val MAX_RENDER_DIMENSION = 2400

    fun pageCount(context: Context, source: Uri): Int {
        val descriptor = context.contentResolver.openFileDescriptor(source, "r")
            ?: error("Unable to open PDF.")
        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                return renderer.pageCount
            }
        }
    }

    fun merge(context: Context, sources: List<Uri>, requestedName: String): File {
        require(sources.size >= 2) { "Choose at least two PDF files." }

        val output = DocumentRepository.uniquePdfFile(
            DocumentRepository.outputDirectory(context),
            requestedName
        )
        val tempFiles = mutableListOf<File>()

        try {
            val merger = PDFMergerUtility().apply {
                setDestinationFileName(output.absolutePath)
            }

            sources.forEachIndexed { index, uri ->
                val temp = copyUriToTempPdf(context, uri, "merge_${index + 1}")
                tempFiles += temp
                merger.addSource(temp)
            }

            merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
            require(output.exists() && output.length() > 0L) { "Merged PDF was not created." }
            return output
        } catch (error: Throwable) {
            output.delete()
            throw error
        } finally {
            tempFiles.forEach(File::delete)
        }
    }

    fun splitEveryPage(context: Context, source: Uri, requestedBaseName: String): SplitResult {
        val temp = copyUriToTempPdf(context, source, "split_source")
        val created = mutableListOf<File>()

        try {
            PDDocument.load(temp, MemoryUsageSetting.setupTempFileOnly()).use { document ->
                val pageCount = document.numberOfPages
                require(pageCount > 0) { "This PDF has no pages." }

                val splitter = Splitter().apply {
                    setSplitAtPage(1)
                    setMemoryUsageSetting(MemoryUsageSetting.setupTempFileOnly())
                }
                val parts = splitter.split(document)

                try {
                    parts.forEachIndexed { index, part ->
                        val output = DocumentRepository.uniquePdfFile(
                            DocumentRepository.outputDirectory(context),
                            "${DocumentRepository.sanitizeBaseName(requestedBaseName)}_page_${(index + 1).toString().padStart(3, '0')}"
                        )
                        part.save(output)
                        created += output
                    }
                } catch (error: Throwable) {
                    created.forEach(File::delete)
                    throw error
                } finally {
                    parts.forEach { runCatching { it.close() } }
                }

                return SplitResult(created.toList(), pageCount)
            }
        } finally {
            temp.delete()
        }
    }

    fun splitGroups(
        context: Context,
        source: Uri,
        requestedBaseName: String,
        groupsSpec: String
    ): SplitResult {
        val temp = copyUriToTempPdf(context, source, "split_source")
        val created = mutableListOf<File>()

        try {
            PDDocument.load(temp, MemoryUsageSetting.setupTempFileOnly()).use { document ->
                val pageCount = document.numberOfPages
                require(pageCount > 0) { "This PDF has no pages." }
                val groups = parsePageGroups(groupsSpec, pageCount)
                val safeBase = DocumentRepository.sanitizeBaseName(requestedBaseName)

                try {
                    groups.forEachIndexed { index, range ->
                        val splitter = Splitter().apply {
                            setStartPage(range.first)
                            setEndPage(range.last)
                            setSplitAtPage(range.last - range.first + 1)
                            setMemoryUsageSetting(MemoryUsageSetting.setupTempFileOnly())
                        }
                        val outputs = splitter.split(document)
                        require(outputs.size == 1) { "Unable to split pages ${range.first}-${range.last}." }
                        val part = outputs.first()
                        try {
                            val suffix = if (range.first == range.last) {
                                "page_${range.first.toString().padStart(3, '0')}"
                            } else {
                                "pages_${range.first}-${range.last}"
                            }
                            val output = DocumentRepository.uniquePdfFile(
                                DocumentRepository.outputDirectory(context),
                                "${safeBase}_$suffix"
                            )
                            part.save(output)
                            created += output
                        } finally {
                            outputs.forEach { runCatching { it.close() } }
                        }
                    }
                } catch (error: Throwable) {
                    created.forEach(File::delete)
                    throw error
                }

                return SplitResult(created.toList(), pageCount)
            }
        } finally {
            temp.delete()
        }
    }

    fun exportImages(
        context: Context,
        source: Uri,
        targetTree: Uri,
        requestedBaseName: String,
        format: ImageFormat,
        jpegQuality: Int = 92
    ): ImageExportResult {
        val resolver = context.contentResolver
        val sourceName = DocumentRepository.sanitizeBaseName(requestedBaseName)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val folderName = "${sourceName}_images_$stamp"
        val treeDocumentId = DocumentsContract.getTreeDocumentId(targetTree)
        val treeDocumentUri = DocumentsContract.buildDocumentUriUsingTree(targetTree, treeDocumentId)
        val folderUri = DocumentsContract.createDocument(
            resolver,
            treeDocumentUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            folderName
        ) ?: error("Unable to create export folder.")

        val descriptor = resolver.openFileDescriptor(source, "r")
            ?: error("Unable to open PDF.")

        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                require(renderer.pageCount > 0) { "This PDF has no pages." }

                for (index in 0 until renderer.pageCount) {
                    renderer.openPage(index).use { page ->
                        val longest = max(page.width, page.height).coerceAtLeast(1)
                        val scale = (MAX_RENDER_DIMENSION.toFloat() / longest.toFloat()).coerceAtMost(2f)
                        val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                        val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val displayName = "${sourceName}_page_${(index + 1).toString().padStart(3, '0')}.${format.extension}"
                            val imageUri = DocumentsContract.createDocument(
                                resolver,
                                folderUri,
                                format.mimeType,
                                displayName
                            ) ?: error("Unable to create image ${index + 1}.")

                            resolver.openOutputStream(imageUri, "w")?.use { output ->
                                val compressFormat = when (format) {
                                    ImageFormat.PNG -> Bitmap.CompressFormat.PNG
                                    ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
                                }
                                val quality = if (format == ImageFormat.PNG) 100 else jpegQuality.coerceIn(60, 100)
                                check(bitmap.compress(compressFormat, quality, output)) {
                                    "Unable to write image ${index + 1}."
                                }
                            } ?: error("Unable to write image ${index + 1}.")
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }

                return ImageExportResult(renderer.pageCount, folderName)
            }
        }
    }

    fun parsePageGroups(spec: String, pageCount: Int): List<IntRange> {
        require(pageCount > 0) { "Page count must be greater than zero." }
        val rawGroups = spec.split(',').map(String::trim).filter(String::isNotBlank)
        require(rawGroups.isNotEmpty()) { "Enter page groups such as 1-3, 4, 5-8." }

        return rawGroups.map { token ->
            val rangeMatch = Regex("^(\\d+)\\s*-\\s*(\\d+)$").matchEntire(token)
            val singleMatch = Regex("^\\d+$").matches(token)

            val range = when {
                rangeMatch != null -> {
                    val start = rangeMatch.groupValues[1].toInt()
                    val end = rangeMatch.groupValues[2].toInt()
                    require(start <= end) { "Invalid page group: $token" }
                    start..end
                }
                singleMatch -> {
                    val page = token.toInt()
                    page..page
                }
                else -> error("Invalid page group: $token")
            }

            require(range.first >= 1 && range.last <= pageCount) {
                "Page group $token is outside 1-$pageCount."
            }
            range
        }
    }

    private fun copyUriToTempPdf(context: Context, source: Uri, prefix: String): File {
        val file = File.createTempFile("nuscan_${prefix}_", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            } ?: error("Unable to read PDF.")
            return file
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }
}
