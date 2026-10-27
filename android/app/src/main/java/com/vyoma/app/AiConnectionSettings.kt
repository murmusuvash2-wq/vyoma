package com.vyoma.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
internal fun AiConnectionSettings(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val initial = remember(context) { AiConnectionStore.load(context) }
    var endpoint by remember { mutableStateOf(initial.endpoint) }
    var model by remember { mutableStateOf(initial.model) }
    var apiKey by remember { mutableStateOf("") }
    var hasSavedKey by remember { mutableStateOf(initial.hasSavedKey) }
    var testPrompt by remember { mutableStateOf("Reply with exactly: Vyoma AI connection successful.") }
    var response by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("AI connection", color = Navy, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
        Text("Connect an OpenAI-compatible chat API. Your own provider account and API usage charges apply.", color = Muted, fontSize = 13.sp)
        Card(colors = CardDefaults.cardColors(containerColor = ColorWhite), shape = RoundedCornerShapeLarge) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it; error = null },
                    label = { Text("HTTPS API endpoint") },
                    supportingText = { Text("Example: https://api.openai.com/v1/chat/completions") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it; error = null },
                    label = { Text("Model name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; error = null },
                    label = { Text(if (hasSavedKey) "API key (saved; enter to replace)" else "API key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Text(
                    if (hasSavedKey) "A key is saved encrypted on this device. It is never displayed again." else "No API key saved yet.",
                    color = if (hasSavedKey) Green else Muted,
                    fontSize = 12.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            error = null
                            response = null
                            val cleanEndpoint = endpoint.trim()
                            val cleanModel = model.trim()
                            if (!AiConnectionStore.isValidEndpoint(cleanEndpoint)) {
                                error = "Enter a valid HTTPS endpoint."
                            } else if (cleanModel.isBlank()) {
                                error = "Enter the model name."
                            } else if (apiKey.isBlank() && !hasSavedKey) {
                                error = "Enter your API key first."
                            } else {
                                runCatching {
                                    AiConnectionStore.save(context, cleanEndpoint, cleanModel, apiKey.takeIf { it.isNotBlank() })
                                }.onSuccess {
                                    apiKey = ""
                                    hasSavedKey = AiConnectionStore.load(context).hasSavedKey
                                    error = null
                                    response = "Settings saved. Testing the provider…"
                                    busy = true
                                    scope.launch {
                                        try {
                                            response = OpenAiCompatibleClient.sendMessage(
                                                context, cleanEndpoint, cleanModel, testPrompt
                                            )
                                        } catch (t: Throwable) {
                                            error = t.message ?: "Connection failed. Try again."
                                            response = null
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }.onFailure { error = it.message ?: "Could not save the connection." }
                            }
                        },
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Navy)
                    ) {
                        if (busy) CircularProgressIndicator(strokeWidth = 2.dp) else Text("Save & test")
                    }
                    OutlinedButton(
                        onClick = {
                            AiConnectionStore.clearKey(context)
                            hasSavedKey = false
                            apiKey = ""
                            response = null
                            error = null
                        },
                        enabled = !busy && hasSavedKey
                    ) { Text("Remove key") }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = ColorWhite), shape = RoundedCornerShapeLarge) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Test chat", color = Navy, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = testPrompt,
                    onValueChange = { testPrompt = it },
                    label = { Text("Message to send") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Text("A test sends this message to your selected AI provider and may incur API charges.", color = Muted, fontSize = 12.sp)
                OutlinedButton(
                    onClick = {
                        error = null
                        response = null
                        busy = true
                        scope.launch {
                            try {
                                response = OpenAiCompatibleClient.sendMessage(context, endpoint, model, testPrompt)
                            } catch (t: Throwable) {
                                error = t.message ?: "Connection failed. Try again."
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && hasSavedKey && testPrompt.isNotBlank()
                ) { Text("Send test message") }
            }
        }

        if (error != null) {
            Surface(color = androidx.compose.ui.graphics.Color(0xFFFFECE8), shape = RoundedCornerShapeLarge) {
                Text(error!!, color = androidx.compose.ui.graphics.Color(0xFF9B2C20), modifier = Modifier.padding(14.dp), fontSize = 13.sp)
            }
        }
        if (response != null) {
            Surface(color = androidx.compose.ui.graphics.Color(0xFFE7F6EC), shape = RoundedCornerShapeLarge) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (busy) "Connection status" else "Provider response", color = Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(response!!, color = Navy, fontSize = 13.sp)
                }
            }
        }

        UpdateSettingsCard()

        Text(
            "Security note: the API key is encrypted at rest using Android Keystore. This MVP sends requests directly from this device to the provider. For a public production release, move provider calls behind an authenticated backend with quotas and server-side secret management.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}

private val ColorWhite get() = androidx.compose.ui.graphics.Color.White
private val RoundedCornerShapeLarge get() = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)
