package com.nudroidlabs.nuscan.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.FileOutputStream

object DocumentRepository {
    fun outputDirectory(context: Context): File {
        val root = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: File(context.filesDir, "Documents")
        return File(root, "NuScan").apply { mkdirs() }
    }

    fun listPdfFiles(context: Context): List<File> =
        outputDirectory(context)
            .listFiles { file -> file.isFile && file.extension.equals("pdf", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    fun importPdf(context: Context, source: Uri, requestedName: String): File {
        val safeName = sanitizeBaseName(requestedName)
        val output = uniquePdfFile(outputDirectory(context), safeName)

        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                FileOutputStream(output).use { outputStream ->
                    input.copyTo(outputStream)
                }
            } ?: error("Unable to read scanned PDF.")
            return output
        } catch (error: Throwable) {
            output.delete()
            throw error
        }
    }

    fun uniquePdfFile(directory: File, requestedName: String): File {
        val safeName = sanitizeBaseName(requestedName)
        var candidate = File(directory, "$safeName.pdf")
        var suffix = 2
        while (candidate.exists()) {
            candidate = File(directory, "$safeName ($suffix).pdf")
            suffix++
        }
        return candidate
    }

    fun sanitizeBaseName(requestedName: String): String =
        requestedName
            .trim()
            .removeSuffix(".pdf")
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .take(80)
            .ifBlank { "NuScan_Document" }
}
