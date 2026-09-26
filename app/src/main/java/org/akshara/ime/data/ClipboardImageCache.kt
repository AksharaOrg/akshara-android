package org.akshara.ime.data

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.util.UUID

/** Copies clipboard images behind Akshara's FileProvider so grants can be delegated safely. */
class ClipboardImageCache(private val context: Context) {
    fun stage(source: Uri, mimeType: String): Uri? = runCatching {
        val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        prune(directory)
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?.takeIf { it.matches(Regex("[a-zA-Z0-9]{1,8}")) }
            ?: "img"
        val target = File(directory, "${UUID.randomUUID()}.$extension")
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAXIMUM_BYTES) throw IOException("Clipboard image is too large")
                        output.write(buffer, 0, count)
                    }
                }
            } ?: throw IOException("Clipboard image cannot be opened")
            FileProvider.getUriForFile(context, "${context.packageName}.clipboard", target)
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }.getOrNull()

    private fun prune(directory: File) {
        val files = directory.listFiles()?.sortedByDescending(File::lastModified).orEmpty()
        val cutoff = System.currentTimeMillis() - MAXIMUM_AGE_MS
        files.forEachIndexed { index, file ->
            if (index >= MAXIMUM_FILES - 1 || file.lastModified() < cutoff) file.delete()
        }
    }

    companion object {
        private const val DIRECTORY = "clipboard"
        private const val MAXIMUM_BYTES = 25L * 1024 * 1024
        private const val MAXIMUM_FILES = 8
        private const val MAXIMUM_AGE_MS = 24L * 60 * 60 * 1000
    }
}
