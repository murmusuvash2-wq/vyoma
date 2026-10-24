package com.vyoma.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
internal fun CodingAgentScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val taskPrefs = remember(context) { context.getSharedPreferences("vyoma_coding_agent_v1", android.content.Context.MODE_PRIVATE) }
    val savedConnection = remember(context) { CodingAgentConnectionStore.load(context) }
    var backendUrl by remember { mutableStateOf(savedConnection.backendUrl) }
    var bearerToken by remember { mutableStateOf(savedConnection.bearerToken) }
    var projectId by remember { mutableStateOf(savedConnection.projectId) }
    var goal by remember { mutableStateOf("") }
    var consentToSendSource by remember { mutableStateOf(false) }
    var task by remember { mutableStateOf<CodingAgentTask?>(null) }
    var taskIdInput by remember { mutableStateOf(taskPrefs.getString("last_task_id", "") ?: "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Coding Agent", color = Navy, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "Describe one small code change. Vyoma will ask the backend for a proposed diff; nothing is applied until you approve it.",
            color = Muted,
            fontSize = 13.sp
        )
        Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)) {
            Column(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                OutlinedTextField(
                    value = backendUrl,
                    onValueChange = {
                        backendUrl = it
                        error = null
                        CodingAgentConnectionStore.save(context, it, projectId, bearerToken)
                    },
                    label = { Text("Backend HTTPS URL") },
                    placeholder = { Text("https://your-private-backend.example") },
                    supportingText = { Text("Use a deployed HTTPS backend. Do not expose the development server publicly.") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = bearerToken,
                    onValueChange = {
                        bearerToken = it
                        error = null
                        CodingAgentConnectionStore.save(context, backendUrl, projectId, it)
                    },
                    label = { Text("Development bearer token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                OutlinedTextField(
                    value = projectId,
                    onValueChange = {
                        projectId = it
                        error = null
                        CodingAgentConnectionStore.save(context, backendUrl, it, bearerToken)
                    },
                    label = { Text("Backend project ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = taskIdInput,
                    onValueChange = { taskIdInput = it; error = null },
                    label = { Text("Task ID (saved on this device)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedButton(
                    onClick = {
                        error = null
                        busy = true
                        scope.launch {
                            try {
                                val refreshed = CodingAgentApiClient.refresh(backendUrl, bearerToken, taskIdInput)
                                task = refreshed
                                taskIdInput = refreshed.taskId
                                taskPrefs.edit().putString("last_task_id", refreshed.taskId).apply()
                            } catch (t: Throwable) {
                                error = t.message ?: "Could not load this task."
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && backendUrl.isNotBlank() && bearerToken.isNotBlank() && taskIdInput.isNotBlank()
                ) { Text("Load / refresh saved task") }
                OutlinedButton(
                    onClick = {
                        task = null
                        taskIdInput = ""
                        taskPrefs.edit().remove("last_task_id").apply()
                        error = null
                    },
                    enabled = !busy && taskIdInput.isNotBlank()
                ) { Text("Clear saved task ID") }
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it; error = null },
                    label = { Text("What should Vyoma change?") },
                    placeholder = { Text("Example: Update the home screen subtitle") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Surface(
                    color = androidx.compose.ui.graphics.Color(0xFFFFF4DB),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("Privacy before planning", color = androidx.compose.ui.graphics.Color(0xFF754B00), fontWeight = FontWeight.Bold)
                        Text(
                            "When you create a plan, Vyoma sends the task description and selected workspace source-file contents to the AI provider configured on your backend. Do not use private or sensitive projects in this development build.",
                            color = androidx.compose.ui.graphics.Color(0xFF754B00),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(
                                checked = consentToSendSource,
                                onCheckedChange = { consentToSendSource = it }
                            )
                            Text("I understand and allow source files to be sent for AI planning.", color = Navy, fontSize = 12.sp)
                        }
                    }
                }
                Button(
                    onClick = {
                        error = null
                        task = null
                        busy = true
                        scope.launch {
                            try {
                                val planned = CodingAgentApiClient.plan(backendUrl, bearerToken, projectId, goal)
                                task = planned
                                taskIdInput = planned.taskId
                                taskPrefs.edit().putString("last_task_id", planned.taskId).apply()
                            } catch (t: Throwable) {
                                error = t.message ?: "Could not create a coding plan."
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && backendUrl.isNotBlank() && bearerToken.isNotBlank() &&
                        projectId.isNotBlank() && goal.isNotBlank() && consentToSendSource,
                    colors = ButtonDefaults.buttonColors(containerColor = Navy)
                ) {
                    if (busy) CircularProgressIndicator(strokeWidth = 2.dp) else Text("Create plan")
                }
            }
        }

        if (busy) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(strokeWidth = 2.dp)
                Text("Vyoma is preparing a reviewable plan…", color = Muted)
            }
        }

        error?.let { message ->
            Surface(
                color = androidx.compose.ui.graphics.Color(0xFFFFECE8),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            ) {
                Text(message, color = androidx.compose.ui.graphics.Color(0xFF9B2C20), modifier = Modifier.padding(14.dp))
            }
        }

        task?.let { current ->
            Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)) {
                Column(
                    Modifier.fillMaxWidth().padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Plan result", color = Navy, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Status: ${current.status}", color = Green, fontWeight = FontWeight.SemiBold)
                    Text(current.summary, color = Navy)
                    Text("Task ID: ${current.taskId}", color = Muted, fontSize = 11.sp)
                    OutlinedButton(
                        onClick = {
                            error = null
                            busy = true
                            scope.launch {
                                try {
                                    task = CodingAgentApiClient.refresh(backendUrl, bearerToken, current.taskId)
                                } catch (t: Throwable) {
                                    error = t.message ?: "Could not refresh this task."
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy && backendUrl.isNotBlank() && bearerToken.isNotBlank()
                    ) { Text("Refresh task status") }
                    if (current.changes.isEmpty()) {
                        Text("No file changes were proposed. Refine the goal and try again.", color = Muted)
                    }
                    current.changes.forEachIndexed { index, change ->
                        Text(change.path, color = Navy, fontWeight = FontWeight.Bold)
                        if (change.rationale.isNotBlank()) Text(change.rationale, color = Muted, fontSize = 12.sp)
                        Surface(
                            color = androidx.compose.ui.graphics.Color(0xFFF1F4F2),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                change.diff.ifBlank { "(No diff returned)" }.take(12000),
                                color = Navy,
                                fontSize = 11.sp,
                                modifier = Modifier.fillMaxWidth().padding(10.dp)
                            )
                        }
                        if (index < current.changes.lastIndex) {
                            Text("Each file is listed separately.", color = Muted, fontSize = 11.sp)
                        }
                    }
                    if (current.status == "WAITING_FOR_APPROVAL" && current.changes.size == 1) {
                        Text(
                            "Review carefully. Approving applies this one-file change to the configured backend workspace.",
                            color = androidx.compose.ui.graphics.Color(0xFF8A4B00),
                            fontSize = 12.sp
                        )
                        OutlinedButton(
                            onClick = {
                                error = null
                                busy = true
                                scope.launch {
                                    try {
                                        val updated = CodingAgentApiClient.approve(backendUrl, bearerToken, current.taskId)
                                        task = updated
                                        taskIdInput = updated.taskId
                                        taskPrefs.edit().putString("last_task_id", updated.taskId).apply()
                                    } catch (t: Throwable) {
                                        error = t.message ?: "Could not apply the approved change."
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy
                        ) { Text("Approve & apply one-file change") }

                        OutlinedButton(
                            onClick = {
                                error = null
                                busy = true
                                scope.launch {
                                    try {
                                        val updated = CodingAgentApiClient.reject(backendUrl, bearerToken, current.taskId)
                                        task = updated
                                        taskIdInput = updated.taskId
                                        taskPrefs.edit().putString("last_task_id", updated.taskId).apply()
                                    } catch (t: Throwable) {
                                        error = t.message ?: "Could not reject the plan."
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy
                        ) { Text("Reject plan") }
                    } else if (current.status == "WAITING_FOR_APPROVAL" && current.changes.size > 1) {
                        Text(
                            "This backend currently refuses multi-file apply. Split the request into smaller tasks.",
                            color = androidx.compose.ui.graphics.Color(0xFF8A4B00),
                            fontSize = 12.sp
                        )
                    }
                    if (current.status == "SUCCEEDED") {
                        Text("Change applied to the backend workspace. Build/test checks have not run yet.", color = Green, fontWeight = FontWeight.SemiBold)
                    }
                    if (current.status == "REJECTED") {
                        Text("Plan rejected. No workspace files were changed.", color = Muted, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Text(
            "Phase B is an early development workflow. The backend bearer token is not per-user authentication. Use only a private test backend and disposable workspace; never put an AI provider key or GitHub token here.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}
