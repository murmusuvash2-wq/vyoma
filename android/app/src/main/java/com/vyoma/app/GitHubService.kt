package com.vyoma.app

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class GitHubUser(
    val login: String,
    val name: String?,
    val avatarUrl: String?
)

data class WorkflowRunSummary(
    val id: Long,
    val name: String,
    val status: String, // "queued", "in_progress", "completed"
    val conclusion: String?, // "success", "failure", null
    val htmlUrl: String,
    val createdAt: String
)

object GitHubService {

    private const val GITHUB_API_URL = "https://api.github.com"

    suspend fun getAuthenticatedUser(token: String): Result<GitHubUser> = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext Result.failure(Exception("GitHub token is empty."))
        try {
            val url = URL("$GITHUB_API_URL/user")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Vyoma-Android-Builder")
                connectTimeout = 15000
                readTimeout = 15000
            }

            if (conn.responseCode !in 200..299) {
                val error = conn.errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: "HTTP ${conn.responseCode}"
                return@withContext Result.failure(Exception("GitHub Auth Failed: $error"))
            }

            val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            val json = JSONObject(body)
            Result.success(
                GitHubUser(
                    login = json.getString("login"),
                    name = json.optString("name", null),
                    avatarUrl = json.optString("avatar_url", null)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun ensureRepository(token: String, owner: String, repoName: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // First check if repo exists
            val checkUrl = URL("$GITHUB_API_URL/repos/$owner/$repoName")
            val checkConn = (checkUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Vyoma-Android-Builder")
                connectTimeout = 15000
                readTimeout = 15000
            }

            if (checkConn.responseCode in 200..299) {
                return@withContext Result.success(true)
            }

            // Create repo if not found
            val createUrl = URL("$GITHUB_API_URL/user/repos")
            val createConn = (createUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Vyoma-Android-Builder")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 15000
                readTimeout = 15000
            }

            val payload = JSONObject().apply {
                put("name", repoName)
                put("description", "Native Android App built with Vyoma AI Builder")
                put("private", false)
                put("auto_init", true)
            }

            OutputStreamWriter(createConn.outputStream).use { it.write(payload.toString()) }

            if (createConn.responseCode in 200..299) {
                Result.success(true)
            } else {
                val error = createConn.errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: "HTTP ${createConn.responseCode}"
                Result.failure(Exception("Failed to create repo $repoName: $error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushFile(
        token: String,
        owner: String,
        repoName: String,
        filePath: String,
        content: String,
        commitMessage: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Check if file already exists to get its SHA
            var sha: String? = null
            val getUrl = URL("$GITHUB_API_URL/repos/$owner/$repoName/contents/$filePath")
            val getConn = (getUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Vyoma-Android-Builder")
            }
            if (getConn.responseCode in 200..299) {
                val resp = getConn.inputStream.bufferedReader().use(BufferedReader::readText)
                sha = JSONObject(resp).optString("sha", null)
            }

            // Put file content
            val putConn = (getUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Vyoma-Android-Builder")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }

            val base64Content = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val putBody = JSONObject().apply {
                put("message", commitMessage)
                put("content", base64Content)
                if (sha != null) {
                    put("sha", sha)
                }
            }

            OutputStreamWriter(putConn.outputStream).use { it.write(putBody.toString()) }

            if (putConn.responseCode in 200..299) {
                Result.success(true)
            } else {
                val error = putConn.errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: "HTTP ${putConn.responseCode}"
                Result.failure(Exception("Failed to push $filePath: $error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushMultipleFiles(
        token: String,
        owner: String,
        repoName: String,
        files: Map<String, String>,
        onProgress: (current: Int, total: Int, fileName: String) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        var count = 0
        val total = files.size
        for ((path, content) in files) {
            onProgress(count + 1, total, path)
            val res = pushFile(token, owner, repoName, path, content, "Add $path via Vyoma Mobile Studio")
            if (res.isFailure) {
                return@withContext Result.failure(res.exceptionOrNull() ?: Exception("Failed on $path"))
            }
            count++
        }
        Result.success(count)
    }

    suspend fun getWorkflowRuns(
        token: String,
        owner: String,
        repoName: String
    ): Result<List<WorkflowRunSummary>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$GITHUB_API_URL/repos/$owner/$repoName/actions/runs?per_page=5")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Vyoma-Android-Builder")
            }

            if (conn.responseCode !in 200..299) {
                val error = conn.errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: "HTTP ${conn.responseCode}"
                return@withContext Result.failure(Exception("Failed to fetch workflow runs: $error"))
            }

            val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            val json = JSONObject(body)
            val runsArray = json.optJSONArray("workflow_runs") ?: JSONArray()
            val list = mutableListOf<WorkflowRunSummary>()

            for (i in 0 until runsArray.length()) {
                val obj = runsArray.getJSONObject(i)
                list.add(
                    WorkflowRunSummary(
                        id = obj.getLong("id"),
                        name = obj.optString("name", "Build"),
                        status = obj.optString("status", "unknown"),
                        conclusion = if (obj.isNull("conclusion")) null else obj.getString("conclusion"),
                        htmlUrl = obj.optString("html_url", ""),
                        createdAt = obj.optString("created_at", "")
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
