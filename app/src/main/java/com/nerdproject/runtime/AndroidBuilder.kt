package com.nerdproject.runtime

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext

/**
 * Builds Android projects inside PRoot, installs APK via system installer.
 * No ADB, no USB. Gradle runs in guest, APK passed to PackageInstaller.
 */
class AndroidBuilder(private val context: Context) {
    private val installer = RuntimeInstaller(context)
    private val bus = MutableSharedFlow<AndroidBuildRecord>(extraBufferCapacity = 16)
    val events: Flow<AndroidBuildRecord> = bus

    @Volatile private var building = false
    @Volatile private var cancel = false

    suspend fun buildAndInstall(projectId: String): AndroidBuildRecord = withContext(Dispatchers.IO) {
        building = true
        cancel = false
        var record = AndroidBuildRecord(phase = AndroidBuildPhase.PREPARING, stage = AndroidBuildStage.PREPARING)
        bus.emit(record)
        try {
            val installed = installer.installedRuntime()
            val workspace = File(context.filesDir, "workspaces/$projectId")
            val projectRoot = findAndroidProjectRoot(workspace)
                ?: error("No Android project found (missing settings.gradle)")
            val guestRoot = "/workspace/${projectRoot.relativeTo(workspace).invariantSeparatorsPath}"
            val cmd = androidGradleCommand(projectRoot, "assembleDebug")
            val log = StringBuilder()
            var stage = AndroidBuildStage.RESOLVING

            val outputFile = File(context.cacheDir, "gradle-$projectId.log")
            val argv = installer.prootArgv(installed.proot, installed.rootfs, workspace, guestRoot, listOf("bash", "-c", cmd))
            val env = mapOf("HOME" to "/root", "LANG" to "C.UTF-8", "TERM" to "xterm",
                "ANDROID_HOME" to "/root/android-sdk", "ANDROID_SDK_ROOT" to "/root/android-sdk",
                "GRADLE_USER_HOME" to "/root/.gradle")
            val spawned = NativeSpawn.spawn(
                argv.toTypedArray(), env.map { "${it.key}=${it.value}" }.toTypedArray(),
                workspace.absolutePath, outputFile.absolutePath, false, 40, 120)
            check(spawned[0] > 0) { "Spawn failed" }
            val pid = spawned[0]
            record = record.copy(phase = AndroidBuildPhase.BUILDING)
            bus.emit(record)

            var offset = 0L
            var exit = NativeSpawn.STILL_RUNNING
            while (true) {
                if (cancel) { NativeSpawn.kill(pid, 9); break }
                val avail = outputFile.length() - offset
                if (avail > 0) {
                    val n = minOf(avail, 32 * 1024).toInt()
                    val bytes = ByteArray(n)
                    val c = RandomAccessFile(outputFile, "r").use { it.seek(offset); it.read(bytes) }
                    if (c > 0) {
                        offset += c
                        val chunk = bytes.decodeToString(0, c)
                        log.append(chunk)
                        stage = inferBuildStage(chunk, stage)
                        record = record.copy(log = log.takeLast(20000).toString(), stage = stage)
                        bus.emit(record)
                    }
                }
                exit = NativeSpawn.waitFor(pid, true)
                if (exit != NativeSpawn.STILL_RUNNING && outputFile.length() <= offset) break
                delay(200)
            }

            if (cancel) {
                record = record.copy(phase = AndroidBuildPhase.CANCELLED)
            } else if (exit == 0) {
                val apk = findDebugApk(projectRoot)
                record = if (apk != null) {
                    record.copy(phase = AndroidBuildPhase.SUCCEEDED,
                        stage = AndroidBuildStage.COMPLETE,
                        apkPath = apk.absolutePath)
                } else {
                    record.copy(phase = AndroidBuildPhase.FAILED,
                        issues = listOf(AndroidBuildIssue(title = "No APK", detail = "Build succeeded but no APK found.")))
                }
            } else {
                record = record.copy(phase = AndroidBuildPhase.FAILED,
                    stage = AndroidBuildStage.FAILED,
                    issues = parseBuildIssues(log.toString(), projectRoot, exit))
            }
            bus.emit(record)

            // install via system installer
            if (record.phase == AndroidBuildPhase.SUCCEEDED && record.apkPath != null) {
                installApk(File(record.apkPath))
            }
            record
        } catch (e: Exception) {
            record = record.copy(phase = AndroidBuildPhase.FAILED,
                issues = listOf(AndroidBuildIssue(title = "Error", detail = e.message ?: "Failed")))
            bus.emit(record)
            record
        } finally {
            building = false
        }
    }

    fun cancel() { cancel = true }

    private fun installApk(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
