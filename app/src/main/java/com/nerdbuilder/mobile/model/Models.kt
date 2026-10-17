package com.nerdbuilder.mobile.model

enum class ProviderProtocol { CLAUDE_LOGIN, ANTHROPIC, ANTHROPIC_GATEWAY, OPENROUTER, OPENAI_RESPONSES, OPENAI_CHAT }

enum class ProviderKind(
    val title: String,
    val subtitle: String,
    val protocol: ProviderProtocol,
    val defaultBaseUrl: String,
    val defaultModel: String,
) {
    CLAUDE("Claude subscription", "Pro, Max, Team", ProviderProtocol.CLAUDE_LOGIN, "", "default"),
    ANTHROPIC("Anthropic API", "Console billing", ProviderProtocol.ANTHROPIC, "https://api.anthropic.com", "claude-sonnet-4-6"),
    OPENROUTER("OpenRouter", "One key, many models", ProviderProtocol.OPENROUTER, "https://openrouter.ai/api", "anthropic/claude-sonnet-4"),
    DEEPSEEK("DeepSeek", "DeepSeek API key", ProviderProtocol.ANTHROPIC_GATEWAY, "https://api.deepseek.com/anthropic", "deepseek-chat"),
    CUSTOM("Custom API", "Anthropic-compatible", ProviderProtocol.ANTHROPIC_GATEWAY, "", ""),
}

enum class AgentKind(val stableId: String, val title: String) {
    OPENCLAUDE("openclaude", "OpenClaude"),
    CLAUDE_CODE("claude-code", "Claude Code"),
    ;
    companion object {
        fun fromStored(v: String?): AgentKind = entries.firstOrNull { it.stableId == v } ?: OPENCLAUDE
    }
}

data class ProviderProfile(
    val id: String = "",
    val kind: ProviderKind = ProviderKind.ANTHROPIC,
    val baseUrl: String = "",
    val model: String = "",
) {
    val resolvedBaseUrl: String get() = baseUrl.ifBlank { kind.defaultBaseUrl }
}

data class ChatMessage(val role: String, val text: String, val id: String = "")

data class ChangeItem(
    val path: String,
    val additions: Int = 0,
    val deletions: Int = 0,
    val diffLines: List<DiffLine> = emptyList(),
    val binary: Boolean = false,
)

data class DiffLine(val type: DiffLineType, val text: String, val oldLine: Int? = null, val newLine: Int? = null)
enum class DiffLineType { CONTEXT, ADDITION, DELETION, INFO }

data class ToolRequest(val sessionId: String, val approvalId: String, val toolName: String, val detail: String)

sealed class RuntimeEvent {
    data class SessionStarted(val sessionId: String) : RuntimeEvent()
    data class SessionCompleted(val sessionId: String, val result: String) : RuntimeEvent()
    data class SessionFailed(val sessionId: String, val message: String) : RuntimeEvent()
    data class TextDelta(val sessionId: String, val text: String) : RuntimeEvent()
    data class ToolCall(val sessionId: String, val request: ToolRequest) : RuntimeEvent()
    data class ToolApproved(val sessionId: String, val approvalId: String) : RuntimeEvent()
    data class ToolRejected(val sessionId: String, val approvalId: String) : RuntimeEvent()
    data class FilesChanged(val sessionId: String, val changes: List<ChangeItem>) : RuntimeEvent()
    data class RuntimeLog(val sessionId: String, val title: String, val detail: String) : RuntimeEvent()
}

enum class ProjectKind { GENERIC, ANDROID, WEB, PYTHON }
