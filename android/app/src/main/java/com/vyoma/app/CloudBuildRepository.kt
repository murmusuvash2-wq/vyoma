package com.vyoma.app

/**
 * Contract for the remote build service. Implementations must use an authenticated API.
 *
 * This interface deliberately has no fake/local implementation that reports a successful
 * build. The cloud backend must persist jobs and return authoritative server-side states.
 */
internal interface CloudBuildRepository {
    suspend fun enqueueBuild(projectId: String, revision: Int): CloudBuildSnapshot
    suspend fun getBuild(jobId: String): CloudBuildSnapshot
    suspend fun getLatestBuild(projectId: String): CloudBuildSnapshot?
    suspend fun cancelBuild(jobId: String): CloudBuildSnapshot
}

/**
 * Events emitted by a realtime connection (WebSocket/SSE). Clients should reconnect and
 * call getBuild(jobId) after a disconnect so the UI recovers from missed events.
 */
internal sealed interface CloudBuildEvent {
    data class Snapshot(val build: CloudBuildSnapshot) : CloudBuildEvent
    data class ConnectionState(val connected: Boolean) : CloudBuildEvent
}

/**
 * A project revision is immutable once a build is enqueued. New chat edits must create a
 * new revision rather than mutate the source currently being compiled.
 */
internal data class ProjectRevision(
    val projectId: String,
    val revision: Int,
    val sourceCommit: String?,
    val createdAt: String
)
