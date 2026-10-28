package com.vyoma.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
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
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class AiConnectionConfig(
    val endpoint: String,
    val model: String,
    val hasSavedKey: Boolean
)

/**
 * BYOK MVP: the user's API key is encrypted at rest with an Android Keystore AES-GCM key.
 * The key is never written to source code or logs. A later production backend should proxy
 * provider calls so model credentials and quotas can be controlled server-side.
 */
internal object AiConnectionStore {
    private const val PREFS = "vyoma_ai_connection_v1"
    private const val ENDPOINT = "endpoint"
    private const val MODEL = "model"
    private const val ENCRYPTED_KEY = "encrypted_api_key"
    private const val KEY_ALIAS = "vyoma_ai_api_key_aes_v1"
    private const val DEFAULT_ENDPOINT = "https://api.openai.com/v1/chat/completions"
    private const val DEFAULT_MODEL = "gpt-4o-mini"

    fun load(context: Context): AiConnectionConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AiConnectionConfig(
            endpoint = prefs.getString(ENDPOINT, DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT,
            model = prefs.getString(MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL,
            hasSavedKey = !prefs.getString(ENCRYPTED_KEY, null).isNullOrBlank()
        )
    }

    fun save(context: Context, endpoint: String, model: String, apiKey: String?) {
        require(isValidEndpoint(endpoint)) { "Enter a valid HTTPS chat-completions endpoint." }
        require(model.isNotBlank()) { "Enter a model name." }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit().putString(ENDPOINT, endpoint.trim()).putString(MODEL, model.trim())
        if (!apiKey.isNullOrBlank()) {
            editor.putString(ENCRYPTED_KEY, encrypt(apiKey.trim()))
        }
        editor.apply()
    }

    fun clearKey(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(ENCRYPTED_KEY).apply()
    }

    fun readKey(context: Context): String? {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(ENCRYPTED_KEY, null) ?: return null
        return runCatching { decrypt(stored) }.getOrNull()
    }

    fun isValidEndpoint(value: String): Boolean {
        val parsed = runCatching { URL(value.trim()) }.getOrNull() ?: return false
        return parsed.protocol.equals("https", ignoreCase = true) && !parsed.host.isNullOrBlank()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val combined = Base64.decode(value, Base64.NO_WRAP)
        require(combined.size > 12) { "Saved key is invalid. Please save it again." }
        val iv = combined.copyOfRange(0, 12)
        val ciphertext = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private const val MAX_DISCOVERY_RESPONSE_CHARS = 1024 * 1024
}

internal object OpenAiCompatibleClient {
    suspend fun discoverModels(
        context: Context,
        endpoint: String,
        apiKeyOverride: String? = null
    ): List<String> = withContext(Dispatchers.IO) {
        require(AiConnectionStore.isValidEndpoint(endpoint)) {
            "Use an HTTPS endpoint for model discovery."
        }
        val apiKey = apiKeyOverride?.trim()?.takeIf { it.isNotBlank() }
            ?: AiConnectionStore.readKey(context)
            ?: throw IllegalStateException("Enter or save your API key first.")
        val modelsEndpoint = AiModelEndpointResolver.resolve(endpoint)
        val connection = (URL(modelsEndpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                buildString {
                    val buffer = CharArray(8 * 1024)
                    while (length < MAX_DISCOVERY_RESPONSE_CHARS) {
                        val count = reader.read(buffer, 0, minOf(buffer.size, MAX_DISCOVERY_RESPONSE_CHARS - length))
                        if (count <= 0) break
                        append(buffer, 0, count)
                    }
                }
            }.orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException(
                    when (status) {
                        401, 403 -> "Provider rejected the API key or permissions (HTTP $status)."
                        404 -> "Model-list endpoint was not found. Enter the provider's chat-completions endpoint or set the model manually."
                        429 -> "Provider rate limit reached. Wait a moment and try again."
                        in 500..599 -> "Provider is temporarily unavailable (HTTP $status)."
                        else -> "Model discovery failed (HTTP $status)."
                    }
                )
            }
            AiModelResponseParser.parse(body).also { models ->
                if (models.isEmpty()) {
                    throw IllegalStateException("Provider responded, but no supported model IDs were found. You can enter a model manually.")
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun sendMessage(
        context: Context,
        endpoint: String,
        model: String,
        message: String
    ): String = withContext(Dispatchers.IO) {
        require(AiConnectionStore.isValidEndpoint(endpoint)) {
            "Use an HTTPS endpoint for an OpenAI-compatible chat-completions API."
        }
        require(model.isNotBlank()) { "Model name is required." }
        require(message.isNotBlank()) { "Message cannot be empty." }
        val apiKey = AiConnectionStore.readKey(context)
            ?: throw IllegalStateException("Save your API key in Settings first.")

        val connection = (URL(endpoint.trim()).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            val payload = JSONObject()
                .put("model", model.trim())
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", message)))
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.let { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            if (status !in 200..299) {
                val providerMessage = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty()
                throw IllegalStateException(
                    when (status) {
                        401, 403 -> "Provider rejected the API key or permissions (HTTP $status). Check your key."
                        429 -> "Provider rate limit or billing quota reached (HTTP 429). Check your provider account."
                        else -> "AI provider request failed (HTTP $status). " +
                            providerMessage.takeIf { it.isNotBlank() }?.take(240).orEmpty()
                    }
                )
            }
            val response = JSONObject(body)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
            response.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("The provider returned no text. Check the selected model and endpoint.")
        } finally {
            connection.disconnect()
        }
    }
}
