package com.nudroidlabs.nuscan.pdf

import android.content.Context
import android.net.Uri
import com.nudroidlabs.nuscan.data.DocumentRepository
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File
import java.io.FileOutputStream
import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider

object PdfSecurity {
    fun protect(
        context: Context,
        source: Uri,
        requestedName: String,
        password: String
    ): File {
        require(password.length >= 4) { "Password must be at least 4 characters." }

        val temp = copyUriToTempPdf(context, source)
        val output = DocumentRepository.uniquePdfFile(
            DocumentRepository.outputDirectory(context),
            requestedName
        )

        try {
            PDDocument.load(temp, MemoryUsageSetting.setupTempFileOnly()).use { document ->
                require(!document.isEncrypted) { "This PDF is already password protected." }

                val permissions = AccessPermission()
                val policy = StandardProtectionPolicy(password, password, permissions).apply {
                    setEncryptionKeyLength(128)
                    setPermissions(permissions)
                }
                if (Security.getProvider("BC") == null) {
                    Security.addProvider(BouncyCastleProvider())
                }
                document.protect(policy)
                document.save(output)
            }
            require(output.exists() && output.length() > 0L) { "Protected PDF was not created." }
            return output
        } catch (error: Throwable) {
            output.delete()
            throw error
        } finally {
            temp.delete()
        }
    }

    private fun copyUriToTempPdf(context: Context, source: Uri): File {
        val file = File.createTempFile("nuscan_protect_", ".pdf", context.cacheDir)
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
