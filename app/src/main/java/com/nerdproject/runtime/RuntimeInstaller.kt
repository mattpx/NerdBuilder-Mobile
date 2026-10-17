package com.nerdproject.runtime

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream

data class InstalledRuntime(val proot: File, val rootfs: File)

data class InstallProgress(val message: String, val fraction: Float)

class RuntimeInstaller(private val context: Context) {
    private val runtimeDir = File(context.filesDir, "runtime")
    private val rootfs = File(runtimeDir, "ubuntu")
    private val downloads = File(context.cacheDir, "runtime-downloads")
    private val readyMarker = File(rootfs, ".nerd-runtime-ready")
    private val versionMarker = File(rootfs, ".nerd-rootfs-version")

    companion object {
        const val ROOTFS_VERSION = "1"
        // TODO: host Ubuntu 20.04 ARM64 rootfs tar.zst, publish SHA256
        const val CORE_URL = ""
        const val CORE_SHA256 = ""
    }

    fun isInstalled(): Boolean {
        val proot = File(context.applicationInfo.nativeLibraryDir, "libnerdspawn.so")
        return rootfs.isDirectory &&
            File(rootfs, "usr/bin/bash").exists() &&
            versionMarker.readTextOrNull() == ROOTFS_VERSION &&
            readyMarker.exists()
    }

    fun installedRuntime(): InstalledRuntime {
        check(isInstalled()) { "Runtime not installed" }
        return InstalledRuntime(
            proot = File(context.applicationInfo.nativeLibraryDir, "libproot.so"),
            rootfs = rootfs,
        )
    }

    suspend fun ensureInstalled(onProgress: suspend (InstallProgress) -> Unit): InstalledRuntime =
        withContext(Dispatchers.IO) {
            require(isArm64()) { "ARM64 required" }
            if (!isInstalled()) {
                onProgress(InstallProgress("Downloading core runtime", 0.05f))
                val archive = downloadVerified(CORE_URL, CORE_SHA256, "core.tar.zst", onProgress)
                onProgress(InstallProgress("Unpacking", 0.5f))
                val staging = File(runtimeDir, "ubuntu.installing").apply { deleteRecursively(); mkdirs() }
                extractZstdTar(archive, staging)
                // atomic swap
                val backup = File(runtimeDir, "ubuntu.backup").apply { deleteRecursively() }
                if (rootfs.exists()) rootfs.renameTo(backup)
                staging.renameTo(rootfs)
                backup.deleteRecursively()
                archive.delete()
                writeResolver()
                versionMarker.writeText(ROOTFS_VERSION)
                readyMarker.writeText("1")
            }
            onProgress(InstallProgress("Ready", 1f))
            installedRuntime()
        }

    /** Build proot launch argv: proot -r rootfs -b workspace:/workspace -w /workspace cmd... */
    fun prootArgv(proot: File, rootfs: File, workspace: File, guestCwd: String, command: List<String>): List<String> =
        buildList {
            add(root.absolutePath)
            add("-r"); add(rootfs.absolutePath)
            add("-b"); add("${workspace.absolutePath}:/workspace")
            add("-b"); add("/proc:/proc")
            add("-w"); add(guestCwd)
            addAll(command)
        }

    private fun isArm64(): Boolean {
        val abis = android.os.Build.SUPPORTED_ABIS.orEmpty()
        return abis.any { it.contains("arm64", ignoreCase = true) }
    }

    private suspend fun downloadVerified(url: String, sha256: String, name: String, onProgress: suspend (InstallProgress) -> Unit): File {
        require(url.isNotBlank()) { "Runtime bundle URL not configured" }
        downloads.mkdirs()
        val out = File(downloads, name)
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connect()
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                FileOutputStream(out).use { output ->
                    val buf = ByteArray(256 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(InstallProgress("Downloading", 0.05f + 0.4f * done / total))
                    }
                }
            }
        }
        verifySha256(out, sha256)
        return out
    }

    private fun verifySha256(file: File, expected: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        check(actual.equals(expected, ignoreCase = true)) { "SHA256 mismatch: $actual" }
    }

    private fun extractZstdTar(archive: File, dest: File) {
        archive.inputStream().buffered().use { raw ->
            // try zstd first, fall back to gzip
            val tar = try {
                TarArchiveInputStream(ZstdCompressorInputStream(BufferedInputStream(raw)))
            } catch (e: Exception) {
                TarArchiveInputStream(GzipCompressorInputStream(BufferedInputStream(archive.inputStream())))
            }
            tar.use {
                var entry = it.nextEntry
                while (entry != null) {
                    val f = File(dest, entry.name)
                    if (!f.canonicalPath.startsWith(dest.canonicalPath)) { entry = it.nextEntry; continue }
                    if (entry.isDirectory) f.mkdirs()
                    else { f.parentFile?.mkdirs(); FileOutputStream(f).use { out -> it.copyTo(out) } }
                    entry = it.nextEntry
                }
            }
        }
    }

    private fun writeResolver() {
        File(rootfs, "etc/resolv.conf").apply {
            parentFile?.mkdirs()
            writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
        }
    }

    private fun File.readTextOrNull(): String? = runCatching { readText().trim() }.getOrNull()
}
