package com.vyoma.app

/**
 * Phase B contract only: this describes a safe boundary between the Android UI and a
 * future authenticated coding-agent service. It does not execute tools or modify files.
 */
internal enum class CodingTaskStatus {
    DRAFT, READY_FOR_REVIEW, RUNNING, WAITING_FOR_APPROVAL, SUCCEEDED, FAILED, CANCELLED
}

internal enum class ProposedActionKind {
    READ_FILE, LIST_FILES, SEARCH_CODE, WRITE_FILE, DELETE_FILE, RUN_CHECK
}

internal data class CodingAgentRequest(
    val projectId: String,
    val taskId: String,
    val userGoal: String,
    val approvedBlueprintId: String? = null,
    val baseRevision: String? = null
)

internal data class ProposedFileChange(
    val path: String,
    val operation: String,
    val unifiedDiff: String,
    val rationale: String
)

internal data class ProposedAgentAction(
    val id: String,
    val kind: ProposedActionKind,
    val summary: String,
    val targetPath: String? = null,
    val requiresUserApproval: Boolean = true
)

internal data class CodingAgentResult(
    val taskId: String,
    val status: CodingTaskStatus,
    val summary: String,
    val proposedChanges: List<ProposedFileChange> = emptyList(),
    val proposedActions: List<ProposedAgentAction> = emptyList(),
    val validationSummary: String? = null,
    val revision: String? = null,
    val errorMessage: String? = null
)

/**
 * Implement this interface with an authenticated backend adapter in a later step.
 * Do not put GitHub tokens, provider secrets, or workspace credentials in the Android app.
 */
internal interface CodingAgentGateway {
    suspend fun plan(request: CodingAgentRequest): CodingAgentResult
    suspend fun getTask(taskId: String): CodingAgentResult
    suspend fun cancelTask(taskId: String)
}
