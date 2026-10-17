package com.nerdproject.runtime

import java.io.File
import org.json.JSONObject

enum class AndroidBuildPhase { IDLE, PREPARING, BUILDING, SUCCEEDED, FAILED, CANCELLED }
enum class AndroidBuildStage {
    IDLE, PREPARING, RESOLVING, COMPILING, RESOURCES, PACKAGING, FINDING_APK,
    INSTALLING, LAUNCHING, COMPLETE, FAILED,
}
enum class AndroidIssueSeverity { INFO, WARNING, ERROR }

data class AndroidBuildIssue(
    val severity: AndroidIssueSeverity = AndroidIssueSeverity.ERROR,
    val title: String,
    val detail: String,
    val suggestion: String? = null,
    val filePath: String? = null,
    val line: Int? = null,
    val column: Int? = null,
)

data class AndroidBuildRecord(
    val phase: AndroidBuildPhase = AndroidBuildPhase.IDLE,
    val stage: AndroidBuildStage = AndroidBuildStage.IDLE,
    val log: String = "",
    val apkPath: String? = null,
    val issues: List<AndroidBuildIssue> = emptyList(),
)

/** Gradle command tuned for PRoot: no daemon, bounded workers, plain output. */
internal fun androidGradleCommand(root: File, task: String): String {
    val launcher = if (File(root, "gradlew").isFile) "bash ./gradlew" else "gradle"
    return "$launcher --no-daemon --max-workers=2 $task --console=plain --stacktrace"
}

internal fun findAndroidProjectRoot(workspace: File): File? {
    val settings = setOf("settings.gradle", "settings.gradle.kts")
    return workspace.walkTopDown().maxDepth(4)
        .filter { it.isFile && it.name in settings }
        .mapNotNull(File::getParentFile)
        .sortedBy { it.absolutePath.length }
        .firstOrNull { root ->
            root.walkTopDown().maxDepth(5)
                .any { it.isFile && it.invariantSeparatorsPath.endsWith("src/main/AndroidManifest.xml") }
        }
}

internal fun findDebugApk(projectRoot: File): File? {
    val meta = projectRoot.walkTopDown().maxDepth(8)
        .filter { it.isFile && it.name == "output-metadata.json" && "/outputs/apk/" in it.invariantSeparatorsPath }
        .maxByOrNull(File::lastModified)
    if (meta != null) {
        runCatching {
            val elements = JSONObject(meta.readText()).optJSONArray("elements") ?: return@runCatching null
            (0 until elements.length()).asSequence()
                .mapNotNull { elements.optJSONObject(it)?.optString("outputFile") }
                .map { File(meta.parentFile, it) }
                .firstOrNull { it.isFile && it.length() > 0 }
        }.getOrNull()?.let { return it }
    }
    return projectRoot.walkTopDown().maxDepth(10)
        .filter { it.isFile && it.extension.equals("apk", true) && "/outputs/apk/debug/" in it.invariantSeparatorsPath }
        .maxByOrNull(File::lastModified)
}

internal fun inferBuildStage(output: String, current: AndroidBuildStage): AndroidBuildStage {
    val t = output.lowercase()
    return when {
        "packagedebug" in t || "assembledebug" in t -> AndroidBuildStage.PACKAGING
        "processdebugresources" in t || "aapt2" in t -> AndroidBuildStage.RESOURCES
        "compiledebugkotlin" in t || "compiledebugjava" in t -> AndroidBuildStage.COMPILING
        "download" in t || "resolve" in t -> AndroidBuildStage.RESOLVING
        else -> current
    }
}

internal fun parseBuildIssues(output: String, projectRoot: File, exitCode: Int): List<AndroidBuildIssue> {
    val issues = mutableListOf<AndroidBuildIssue>()
    val loc = Regex("(?m)^(.+?\\.(?:kt|java|xml|gradle|kts)):(\\d+)(?::(\\d+))?[: ]+(?:error: )?(.+)$", RegexOption.IGNORE_CASE)
    loc.findAll(output).take(12).forEach { m ->
        val raw = m.groupValues[1].trim()
        val rel = when {
            raw.startsWith("/workspace/") -> raw.substringAfter("/workspace/").substringAfter('/', "")
            File(projectRoot, raw).isFile -> raw
            else -> raw
        }
        issues += AndroidBuildIssue(
            title = "Source error",
            detail = m.groupValues[4].trim().take(400),
            filePath = rel,
            line = m.groupValues[2].toIntOrNull(),
            column = m.groupValues[3].toIntOrNull(),
        )
    }
    if (issues.isNotEmpty()) return issues.distinctBy { Triple(it.filePath, it.line, it.detail) }
    val lower = output.lowercase()
    val summary = when {
        "no space left" in lower -> "Out of storage."
        "could not resolve" in lower -> "Dependency unavailable. Check network."
        "manifest merger failed" in lower -> "Manifest merge conflict."
        "aapt2" in lower -> "Resource compile failed."
        "outofmemory" in lower || "heap space" in lower -> "Gradle OOM. Close apps, retry."
        else -> "Gradle failed (exit $exitCode)."
    }
    return listOf(AndroidBuildIssue(title = "Build failed", detail = summary))
}
