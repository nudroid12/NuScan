package com.nudroidlabs.nuscan.data

import android.content.Context
import android.os.Environment
import java.io.File

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
}
