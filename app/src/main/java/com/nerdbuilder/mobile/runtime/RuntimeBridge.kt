package com.nerdbuilder.mobile.runtime

import com.nerdbuilder.mobile.model.ChatMessage
import com.nerdbuilder.mobile.model.ChangeItem
import com.nerdbuilder.mobile.model.ProviderProfile
import com.nerdbuilder.mobile.model.ProjectKind
import com.nerdbuilder.mobile.model.RuntimeEvent
import com.nerdbuilder.mobile.model.ToolRequest
import kotlinx.coroutines.flow.Flow

data class RuntimeLaunchConfig(
    val executable: String,
    val arguments: List<String>,
    val environment: Map<String, String>,
)

interface RuntimeBridge {
    val events: Flow<RuntimeEvent>
    suspend fun startSession(
        projectId: String,
        projectSlug: String,
        projectKind: ProjectKind,
        prompt: String,
        conversationHistory: List<ChatMessage>,
        provider: ProviderProfile,
    ): String
    suspend fun respondToApproval(request: ToolRequest, approved: Boolean)
    suspend fun stopSession(sessionId: String)
    suspend fun stopActiveSession()
    suspend fun undoLastChanges(projectId: String): Boolean
    suspend fun acceptLastChanges(projectId: String)
    suspend fun loadPendingChanges(projectId: String): List<ChangeItem>
}
