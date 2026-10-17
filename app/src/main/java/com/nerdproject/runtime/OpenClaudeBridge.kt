package com.nerdproject.runtime

import android.content.Context
import android.util.Log
import com.nerdproject.model.ChatMessage
import com.nerdproject.model.ChangeItem
import com.nerdproject.model.ProjectKind
import com.nerdproject.model.ProviderProfile
import com.nerdproject.model.RuntimeEvent
import com.nerdproject.model.ToolRequest
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Runs OpenClaude CLI inside PRoot, parses stream-json output,
 * emits RuntimeEvents. Port of ClaudeRuntimeBridge pattern,
 * pointed at openclaude binary instead of claude.
 */
class OpenClaudeBridge(
    private val context: Context,
    private val secretFor: (ProviderProfile) -> String?,
) : RuntimeBridge {
    private val installer = RuntimeInstaller(context)
    private val checkpoints = WorkspaceCheckpoints(File(context.filesDir, ""))
    private val eventBus = MutableSharedFlow<RuntimeEvent>(extraBufferCapacity = 64)
    override val events: Flow<RuntimeEvent> = eventBus

    @Volatile private var activeProcess: Process? = null
    @Volatile private var activeSessionId: String? = null
    @Volatile private var userStop = false

    override suspend fun startSession(
        projectId: String, projectSlug: String, projectKind: ProjectKind,
        prompt: String, conversationHistory: List<ChatMessage>, provider: ProviderProfile,
    ): String = withContext(Dispatchers.IO + NonCancellable) {
        val sessionId = UUID.randomUUID().toString()
        activeSessionId = sessionId
        userStop = false
        eventBus.emit(RuntimeEvent.SessionStarted(sessionId))

        val secret = secretFor(provider).orEmpty()
        if (secret.isBlank()) {
            eventBus.emit(RuntimeEvent.SessionFailed(sessionId, "No API key saved for ${provider.kind.title}"))
            return@withContext sessionId
        }

        runCatching {
            val installed = installer.installedRuntime()
            val workspace = File(context.filesDir, "workspaces/$projectId").apply { mkdirs() }
            checkpoints.createCheckpoint(projectId, workspace)
            val before = checkpoints.snapshot(workspace)

            val env = linkedMapOf(
                "ANTHROPIC_BASE_URL" to provider.resolvedBaseUrl.trimEnd('/'),
                "ANTHROPIC_API_KEY" to secret,
                "ANTHROPIC_MODEL" to provider.model.ifBlank { provider.kind.defaultModel },
                "DISABLE_TELEMETRY" to "1",
            )
            val guestWorkspace = "/workspace/$projectSlug"
            val fullPrompt = buildString {
                conversationHistory.takeLast(10).forEach { appendLine("${it.role}: ${it.text}") }
                append("user: $prompt")
            }
            val guestCmd = listOf(
                "/usr/local/bin/openclaude", "-p", fullPrompt,
                "--output-format", "stream-json", "--verbose",
            )
            val argv = installer.prootArgv(installed.proot, installed.rootfs, workspace, guestWorkspace, guestCmd)
            val outputFile = File(context.cacheDir, "session-$sessionId.log")
            val spawned = NativeSpawn.spawn(
                argv.toTypedArray(), env.map { "${it.key}=${it.value}" }.toTypedArray(),
                workspace.absolutePath, outputFile.absolutePath, false, 40, 120,
            )
            check(spawned[0] > 0) { "Spawn failed" }
            val proc = SpawnedProcess(spawned[0], outputFile)
            activeProcess = proc

            coroutineScope {
                var offset = 0L
                val pending = StringBuilder()
                while (proc.isAlive || outputFile.length() > offset) {
                    val avail = outputFile.length() - offset
                    if (avail <= 0) { delay(50); continue }
                    val n = minOf(avail, 16 * 1024).toInt()
                    val bytes = ByteArray(n)
                    val count = RandomAccessFile(outputFile, "r").use { it.seek(offset); it.read(bytes) }
                    if (count > 0) {
                        offset += count
                        pending.append(bytes.decodeToString(0, count))
                        var nl = pending.indexOf("\n")
                        while (nl >= 0) {
                            val line = pending.substring(0, nl).trimEnd('\r')
                            pending.delete(0, nl + 1)
                            if (line.isNotBlank()) consumeLine(sessionId, line)
                            nl = pending.indexOf("\n")
                        }
                    }
                }
                val exit = proc.waitFor()
                val changed = checkpoints.changedFiles(workspace, before)
                if (changed.isNotEmpty()) {
                    checkpoints.saveChangedPaths(projectId, changed)
                    val details = checkpoints.buildChangeDetails(projectId, workspace, changed)
                    eventBus.emit(RuntimeEvent.FilesChanged(sessionId, details))
                }
                if (exit == 0) eventBus.emit(RuntimeEvent.SessionCompleted(sessionId, "Done"))
                else if (!userStop) eventBus.emit(RuntimeEvent.SessionFailed(sessionId, "Exit $exit"))
            }
        }.onFailure { e ->
            Log.e("OpenClaudeBridge", "fail", e)
            eventBus.emit(RuntimeEvent.SessionFailed(sessionId, e.message ?: "Failed"))
        }
        activeProcess = null
        activeSessionId = null
        sessionId
    }

    private suspend fun consumeLine(sessionId: String, line: String) {
        val json = runCatching { JSONObject(line) }.getOrNull() ?: run {
            eventBus.emit(RuntimeEvent.RuntimeLog(sessionId, "log", line.take(500)))
            return
        }
        when (json.optString("type")) {
            "assistant" -> {
                val msg = json.optJSONObject("message")
                val content = msg?.optJSONArray("content")
                if (content != null) for (i in 0 until content.length()) {
                    val block = content.optJSONObject(i) ?: continue
                    when (block.optString("type")) {
                        "text" -> eventBus.emit(RuntimeEvent.TextDelta(sessionId, block.optString("text")))
                        "tool_use" -> {
                            val req = ToolRequest(sessionId, block.optString("id"), block.optString("name"), block.optString("input").take(300))
                            eventBus.emit(RuntimeEvent.ToolCall(sessionId, req))
                        }
                    }
                }
            }
            "result" -> {
                if (!json.optBoolean("is_error")) {
                    eventBus.emit(RuntimeEvent.TextDelta(sessionId, json.optString("result")))
                }
            }
        }
    }

    override suspend fun respondToApproval(request: ToolRequest, approved: Boolean) {
        eventBus.emit(if (approved) RuntimeEvent.ToolApproved(request.sessionId, request.approvalId)
        else RuntimeEvent.ToolRejected(request.sessionId, request.approvalId))
    }

    override suspend fun stopSession(sessionId: String) = withContext(Dispatchers.IO) {
        if (activeSessionId == sessionId) {
            userStop = true
            activeProcess?.destroy()
            delay(500)
            if (activeProcess?.isAlive == true) activeProcess?.destroyForcibly()
        }
    }

    override suspend fun stopActiveSession() { activeSessionId?.let { stopSession(it) } }

    override suspend fun undoLastChanges(projectId: String): Boolean = withContext(Dispatchers.IO) {
        val cp = File(context.filesDir, "checkpoints/$projectId/latest")
        val backup = File(cp, "project")
        if (!backup.isDirectory) return@withContext false
        val workspace = File(context.filesDir, "workspaces/$projectId")
        backup.copyRecursively(workspace, overwrite = true)
        cp.deleteRecursively()
        true
    }

    override suspend fun acceptLastChanges(projectId: String) = withContext(Dispatchers.IO) {
        File(context.filesDir, "checkpoints/$projectId/latest").deleteRecursively()
    }

    override suspend fun loadPendingChanges(projectId: String): List<ChangeItem> = withContext(Dispatchers.IO) {
        val workspace = File(context.filesDir, "workspaces/$projectId")
        val paths = checkpoints.readChangedPaths(projectId)
        checkpoints.buildChangeDetails(projectId, workspace, paths)
    }

    /** Minimal Process wrapper around native pid. */
    private inner class SpawnedProcess(val pid: Int, val outputFile: File) : Process() {
        override fun getOutputStream() = throw UnsupportedOperationException()
        override fun getInputStream() = outputFile.inputStream()
        override fun getErrorStream() = ByteArray(0).inputStream()
        override fun waitFor(): Int = NativeSpawn.waitFor(pid, false)
        override fun exitValue(): Int {
            val s = NativeSpawn.waitFor(pid, true)
            if (s == NativeSpawn.STILL_RUNNING) throw IllegalThreadStateException()
            return s
        }
        override fun destroy() { NativeSpawn.kill(pid, 15) }
        override fun destroyForcibly(): Process { NativeSpawn.kill(pid, 9); return this }
        override fun isAlive(): Boolean = runCatching { exitValue(); false }.getOrDefault(true)
    }
}
