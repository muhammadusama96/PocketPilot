package com.pocketpilot.app.model

import com.pocketpilot.core.config.OnDeviceModelSpec
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * Downloads an open-weight model from Hugging Face into the app's private
 * files dir. Models are NEVER bundled in the APK (they are hundreds of MB);
 * the user picks one in Settings and it downloads once, on Wi-Fi by default.
 * The file stays on-device; nothing about it is uploaded anywhere.
 */
class ModelDownloader(private val modelsDir: File) {

    data class Progress(val downloadedBytes: Long, val totalBytes: Long)

    fun localPath(spec: OnDeviceModelSpec): File =
        File(modelsDir, "${spec.repoId.replace('/', '_')}__${spec.fileName}")

    fun isDownloaded(spec: OnDeviceModelSpec): Boolean =
        localPath(spec).let { it.exists() && it.length() > 0 }

    /**
     * Downloads the model file. Returns the local file. Throws on failure;
     * a partial file is deleted so a retry starts clean.
     */
    fun download(spec: OnDeviceModelSpec, onProgress: (Progress) -> Unit = {}): File {
        val target = localPath(spec)
        if (isDownloaded(spec)) return target
        modelsDir.mkdirs()
        val url = URL("https://huggingface.co/${spec.repoId}/resolve/main/${spec.fileName}")
        val tmp = File(modelsDir, target.name + ".part")
        try {
            url.openConnection().apply { connectTimeout = 15_000; readTimeout = 60_000 }.getInputStream().use { input ->
                FileOutputStream(tmp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var total = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        total += read
                        onProgress(Progress(total, spec.sizeMb * 1024 * 1024))
                    }
                }
            }
            tmp.renameTo(target)
            return target
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }

    fun delete(spec: OnDeviceModelSpec) {
        localPath(spec).delete()
    }
}
