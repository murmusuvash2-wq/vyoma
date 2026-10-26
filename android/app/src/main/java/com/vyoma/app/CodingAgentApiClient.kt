package com.vyoma.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

internal data class CodingAgentChange(
    val path: String,
    val diff: String,
    val rationale: String,
    val originalExists: Boolean
)

internal data class CodingAgentTask(
    val taskId: String,
    val projectId: String,
    val status: String,
    val summary: String,
    val changes: List<CodingAgentChange>,
    val error: String? = null
)

internal object CodingAgentApiClient {
    suspend fun plan(
        backendUrl: String,
        bearerToken: String,
        projectId: String,
        goal: String,
        engine: String = "planner",
        designImageDataUrl: String? = null
    ): CodingAgentTask {
        require(projectId.matches(Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$"))) {
            "Project ID may contain only letters, numbers, underscores and hyphens."
        }
        return request(
            backendUrl = backendUrl,
            bearerToken = bearerToken,
            path = "/v1/projects/${projectId.trim()}/tasks/plan",
            method = "POST",
            body = JSONObject().put("goal", goal.trim()).put("engine", engine).apply {
                if (!designImageDataUrl.isNullOrBlank()) put("design_image_data_url", designImageDataUrl)
            }
        )
    }

    suspend fun createProject(
        backendUrl: String,
        bearerToken: String,
        projectId: String
    ): String {
        require(projectId.matches(Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$"))) {
            "Project ID may contain only letters, numbers, underscores and hyphens."
        }
        val created = request(
            backendUrl = backendUrl,
            bearerToken = bearerToken,
            path = "/v1/projects",
            method = "POST",
            body = JSONObject().put("project_id", projectId.trim())
        )
        return created.projectId
    }

    suspend fun refresh(
        backendUrl: String,
        bearerToken: String,
        taskId: String
    ): CodingAgentTask {
        require(taskId.matches(Regex("^[0-9a-fA-F-]{36}$"))) { "Enter a valid task ID." }
        return request(
            backendUrl = backendUrl,
            bearerToken = bearerToken,
            path = "/v1/tasks/${taskId.trim()}",
            method = "GET",
            body = JSONObject()
        )
    }

    suspend fun approve(
        backendUrl: String,
        bearerToken: String,
        taskId: String
    ): CodingAgentTask = request(
        backendUrl = backendUrl,
        bearerToken = bearerToken,
        path = "/v1/tasks/${taskId.trim()}/approve",
        method = "POST",
        body = JSONObject()
    )

    suspend fun reject(
        backendUrl: String,
        bearerToken: String,
        taskId: String
    ): CodingAgentTask = request(
        backendUrl = backendUrl,
        bearerToken = bearerToken,
        path = "/v1/tasks/${taskId.trim()}/reject",
        method = "POST",
        body = JSONObject()
    )

    suspend fun undo(
        backendUrl: String,
        bearerToken: String,
        taskId: String
    ): CodingAgentTask {
        require(taskId.matches(Regex("^[0-9a-fA-F-]{36}$"))) { "Enter a valid task ID." }
        return request(
            backendUrl = backendUrl,
            bearerToken = bearerToken,
            path = "/v1/tasks/${taskId.trim()}/undo",
            method = "POST",
            body = JSONObject()
        )
    }


    suspend fun importProjectZip(
        backendUrl: String,
        bearerToken: String,
        projectId: String,
        archiveBase64: String
    ): Int = withContext(Dispatchers.IO) {
        require(projectId.matches(Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$"))) {
            "Project ID may contain only letters, numbers, underscores and hyphens."
        }
        require(archiveBase64.isNotBlank()) { "Choose a ZIP source archive first." }
        require(archiveBase64.length <= 14_000_000) { "ZIP archive exceeds the upload limit." }
        val base = backendUrl.trim().trimEnd('/')
        val parsed = runCatching { URL(base) }.getOrNull()
            ?: throw IllegalArgumentException("Enter a valid backend URL.")
        require(parsed.protocol.equals("https", ignoreCase = true) && parsed.host.isNotBlank()) {
            "Backend URL must use HTTPS."
        }
        require(bearerToken.isNotBlank()) { "Enter the backend bearer token." }
        val connection = (URL(base + "/v1/projects/${projectId.trim()}/import-zip").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${bearerToken.trim()}")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use {
                it.write(JSONObject().put("archive_base64", archiveBase64).toString())
            }
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.let { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            if (statusCode !in 200..299) {
                val detail = runCatching { JSONObject(raw).optString("detail") }.getOrNull().orEmpty()
                val message = when (statusCode) {
                    400 -> "The ZIP was rejected. Check that it contains supported UTF-8 source files."
                    401 -> "Backend rejected the bearer token (401)."
                    404 -> "Project not found (404). Check the project ID."
                    409 -> "Import requires an existing empty project workspace."
                    413 -> "ZIP archive or its contents exceed the allowed size."
                    503 -> "Backend configuration is incomplete or unavailable (503)."
                    else -> "Project import failed (HTTP $statusCode)."
                }
                throw IllegalStateException(if (detail.isBlank()) message else "$message $detail".take(500))
            }
            JSONObject(raw).optInt("imported_files", 0).also {
                if (it <= 0) throw IllegalStateException("Backend returned no imported files.")
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun request(
        backendUrl: String,
        bearerToken: String,
        path: String,
        method: String,
        body: JSONObject
    ): CodingAgentTask = withContext(Dispatchers.IO) {
        val base = backendUrl.trim().trimEnd('/')
        val parsed = runCatching { URL(base) }.getOrNull()
            ?: throw IllegalArgumentException("Enter a valid backend URL.")
        require(parsed.protocol.equals("https", ignoreCase = true)) {
            "Backend URL must use HTTPS. Do not expose the development server over public HTTP."
        }
        require(parsed.host.isNotBlank()) { "Enter a valid backend URL." }
        require(bearerToken.isNotBlank()) { "Enter the backend bearer token." }
        require(path.split('/').none { it == ".." }) { "Invalid API path." }

        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 90_000
            setRequestProperty("Authorization", "Bearer ${bearerToken.trim()}")
            setRequestProperty("Accept", "application/json")
            if (method == "POST") {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (method == "POST") {
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            }
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.let { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            if (statusCode !in 200..299) {
                val detail = runCatching {
                    val json = JSONObject(raw)
                    json.optString("detail").ifBlank { json.optString("message") }
                }.getOrNull().orEmpty()
                val message = when (statusCode) {
                    401 -> "Backend rejected the bearer token (401)."
                    404 -> "Project or task not found (404). Check the project ID."
                    409 -> "Task cannot be applied safely (409). Re-plan and review the latest diff."
                    502 -> "AI planning failed. Check the backend provider settings."
                    503 -> "Backend configuration is incomplete or unavailable (503)."
                    else -> "Coding Agent request failed (HTTP $statusCode)."
                }
                throw IllegalStateException(if (detail.isBlank()) message else "$message $detail".take(500))
            }
            parseTask(JSONObject(raw))
        } finally {
            connection.disconnect()
        }
    }

    private fun parseTask(json: JSONObject): CodingAgentTask {
        val changesJson = json.optJSONArray("changes")
        val changes = buildList {
            if (changesJson != null) {
                for (index in 0 until changesJson.length()) {
                    val item = changesJson.optJSONObject(index) ?: continue
                    add(
                        CodingAgentChange(
                            path = item.optString("path"),
                            diff = item.optString("diff"),
                            rationale = item.optString("rationale"),
                            originalExists = item.optBoolean("original_exists", false)
                        )
                    )
                }
            }
        }
        return CodingAgentTask(
            taskId = json.optString("task_id"),
            projectId = json.optString("project_id"),
            status = json.optString("status", "UNKNOWN"),
            summary = json.optString("summary", "No summary returned."),
            changes = changes,
            error = json.optString("error").takeIf { it.isNotBlank() && it != "null" }
        )
    }
}
