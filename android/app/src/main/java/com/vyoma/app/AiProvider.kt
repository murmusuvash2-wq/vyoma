package com.vyoma.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

/**
 * Protocol and endpoint helpers for provider integrations.
 * Phase B intentionally starts with OpenAI-compatible model discovery; additional
 * protocols must be added with their own request/response adapters, not guessed.
 */
internal enum class AiApiProtocol {
    OPENAI_CHAT_COMPLETIONS
}

internal data class AiProviderPreset(
    val id: String,
    val displayName: String,
    val protocol: AiApiProtocol,
    val defaultEndpoint: String,
    val supportsModelDiscovery: Boolean
)

internal object AiProviderCatalog {
    val presets = listOf(
        AiProviderPreset(
            id = "openai",
            displayName = "OpenAI-compatible API",
            protocol = AiApiProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultEndpoint = "https://api.openai.com/v1/chat/completions",
            supportsModelDiscovery = true
        ),
        AiProviderPreset(
            id = "custom",
            displayName = "Custom OpenAI-compatible endpoint",
            protocol = AiApiProtocol.OPENAI_CHAT_COMPLETIONS,
            defaultEndpoint = "",
            supportsModelDiscovery = true
        )
    )
}

internal object AiModelEndpointResolver {
    fun resolve(chatEndpoint: String): String {
        val parsed = URL(chatEndpoint.trim())
        require(parsed.protocol.equals("https", ignoreCase = true) && parsed.host.isNotBlank()) {
            "Enter a valid HTTPS endpoint."
        }
        val origin = "${parsed.protocol}://${parsed.authority}"
        val path = parsed.path.trimEnd('/')
        val modelsPath = when {
            path.endsWith("/chat/completions", ignoreCase = true) ->
                path.removeSuffix("/chat/completions") + "/models"
            path.endsWith("/responses", ignoreCase = true) ->
                path.removeSuffix("/responses") + "/models"
            path.endsWith("/v1", ignoreCase = true) -> "$path/models"
            path.isBlank() || path == "/" -> "/v1/models"
            else -> "$path/models"
        }
        return origin + modelsPath
    }
}

internal object AiModelResponseParser {
    fun parse(json: String): List<String> = runCatching {
        val root = JSONObject(json)
        val values = root.optJSONArray("data") ?: root.optJSONArray("models") ?: JSONArray()
        buildList {
            for (index in 0 until values.length()) {
                when (val item = values.opt(index)) {
                    is JSONObject -> {
                        val id = item.optString("id").ifBlank { item.optString("name") }
                        if (id.isNotBlank()) add(id)
                    }
                    is String -> if (item.isNotBlank()) add(item)
                }
            }
        }.distinct().sorted()
    }.getOrDefault(emptyList())
}
