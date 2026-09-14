package io.github.immaghzbad.aetherst.core

import io.github.immaghzbad.aetherst.data.LogRepository
import io.github.immaghzbad.aetherst.desktop.AppPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

object BinaryManager {

    const val BINARY_VERSION = "1.7.0"
    private const val BINARY_URL = "https://github.com/CluvexStudio/Aether/releases/download/v$BINARY_VERSION/aether-windows-x86_64.zip"
    private const val BINARY_FILE_NAME = "aether.exe"
    private const val VERSION_FILE_NAME = "aether-version.txt"

    const val HEV_VERSION = "2.17.1"
    private const val HEV_URL = "https://github.com/heiher/hev-socks5-tunnel/releases/download/$HEV_VERSION/hev-socks5-tunnel-win64.zip"
    private const val HEV_EXE_NAME = "hev-socks5-tunnel.exe"
    private const val HEV_WINTUN_NAME = "wintun.dll"
    private const val HEV_MSYS_NAME = "msys-2.0.dll"
    private const val HEV_VERSION_FILE_NAME = "hev-version.txt"
    private val HEV_FILES = listOf(HEV_EXE_NAME, HEV_WINTUN_NAME, HEV_MSYS_NAME)

    @Synchronized
    fun prepareHevBinary(): File {
        val binDir = AppPaths.binDir
        if (!binDir.exists() && !binDir.mkdirs()) {
            throw IOException("Failed to create binary directory: ${binDir.absolutePath}")
        }

        val targetFile = File(binDir, HEV_EXE_NAME)
        if (targetFile.exists() && targetFile.isFile && targetFile.length() > 0 &&
            File(binDir, HEV_WINTUN_NAME).exists() && File(binDir, HEV_MSYS_NAME).exists()
        ) {
            return targetFile
        }

        downloadHev()
        return targetFile
    }

    fun isHevReady(): Boolean {
        val binDir = AppPaths.binDir
        return HEV_FILES.all { File(binDir, it).exists() && File(binDir, it).length() > 0 }
    }

    private fun downloadHev() {
        val binDir = AppPaths.binDir
        val zipFile = File(binDir, "hev-socks5-tunnel-win64.zip")
        try {
            LogRepository.i("Downloading HEV tunnel v$HEV_VERSION...")
            val request = Request.Builder().url(HEV_URL).build()
            NetworkClient.instance.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Download failed: HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("Empty response body")
                body.byteStream().use { input ->
                    zipFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            LogRepository.i("HEV download complete, extracting...")
            extractHev(zipFile, binDir)
            zipFile.delete()

            if (!File(binDir, HEV_EXE_NAME).exists() || File(binDir, HEV_EXE_NAME).length() == 0L) {
                throw IOException("Extraction failed: hev-socks5-tunnel.exe not found")
            }
            File(binDir, HEV_VERSION_FILE_NAME).writeText(HEV_VERSION)
            LogRepository.i("HEV tunnel v$HEV_VERSION ready.")
        } catch (e: Exception) {
            zipFile.delete()
            LogRepository.e("HEV download error: ${e.localizedMessage}")
            throw IOException("Failed to download HEV tunnel: ${e.localizedMessage}", e)
        }
    }

    private fun extractHev(zipFile: File, destDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.replace('\\', '/')
                val fileName = name.substringAfterLast('/')
                if (!entry.isDirectory && fileName in HEV_FILES) {
                    val outFile = File(destDir, fileName)
                    outFile.outputStream().use { output -> zis.copyTo(output) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        if (!HEV_FILES.all { File(destDir, it).exists() }) {
            throw IOException("Required HEV files missing after extraction")
        }
    }

    data class DownloadState(
        val isDownloading: Boolean = false,
        val progress: Float = 0f,
        val error: String? = null
    )

    private val _downloadState = MutableStateFlow(DownloadState())
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    @Synchronized
    fun prepareBinary(): File {
        val binDir = AppPaths.binDir
        if (!binDir.exists() && !binDir.mkdirs()) {
            throw IOException("Failed to create binary directory: ${binDir.absolutePath}")
        }

        val targetFile = File(binDir, BINARY_FILE_NAME)
        if (targetFile.exists() && targetFile.isFile && targetFile.length() > 0) {
            return targetFile
        }

        downloadBinary(targetFile)
        return targetFile
    }

    fun isBinaryReady(): Boolean {
        val targetFile = File(AppPaths.binDir, BINARY_FILE_NAME)
        return targetFile.exists() && targetFile.isFile && targetFile.length() > 0
    }

    private fun downloadBinary(targetFile: File) {
        val binDir = AppPaths.binDir
        val zipFile = File(binDir, "aether-windows-x86_64.zip")

        try {
            LogRepository.i("Downloading Aether core v$BINARY_VERSION...")
            _downloadState.value = DownloadState(isDownloading = true, progress = 0f)

            val request = Request.Builder().url(BINARY_URL).build()
            NetworkClient.instance.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Download failed: HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("Empty response body")
                val totalBytes = body.contentLength().coerceAtLeast(1L)

                body.byteStream().use { input ->
                    zipFile.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n <= 0) break
                            output.write(buffer, 0, n)
                            downloaded += n
                            _downloadState.value = DownloadState(
                                isDownloading = true,
                                progress = (downloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
                            )
                        }
                    }
                }
            }

            LogRepository.i("Download complete, extracting...")
            extractAether(zipFile, binDir)
            zipFile.delete()

            if (!targetFile.exists() || targetFile.length() == 0L) {
                throw IOException("Extraction failed: aether.exe not found")
            }

            File(binDir, VERSION_FILE_NAME).writeText(BINARY_VERSION)
            LogRepository.i("Aether core v$BINARY_VERSION ready.")
            _downloadState.value = DownloadState(isDownloading = false, progress = 1f)
        } catch (e: Exception) {
            zipFile.delete()
            _downloadState.value = DownloadState(isDownloading = false, error = e.localizedMessage)
            LogRepository.e("Binary download error: ${e.localizedMessage}")
            throw IOException("Failed to download Aether core: ${e.localizedMessage}", e)
        }
    }

    private fun extractAether(zipFile: File, destDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.replace('\\', '/')
                val fileName = name.substringAfterLast('/')
                if (!entry.isDirectory && fileName.equals(BINARY_FILE_NAME, ignoreCase = true)) {
                    val outFile = File(destDir, BINARY_FILE_NAME)
                    outFile.outputStream().use { output ->
                        zis.copyTo(output)
                    }
                    return
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        throw IOException("aether.exe not found inside archive")
    }

    suspend fun prepareBinaryAsync(): File = withContext(Dispatchers.IO) {
        prepareBinary()
    }
}