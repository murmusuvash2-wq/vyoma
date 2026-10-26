package com.vyoma.app

import android.content.Context
import android.content.SharedPreferences

enum class AiProvider(val displayName: String, val defaultModel: String) {
    GEMINI("Google Gemini", "gemini-1.5-flash"),
    OPENAI("OpenAI", "gpt-4o-mini"),
    CLAUDE("Anthropic Claude", "claude-3-5-sonnet-20241022"),
    CUSTOM("Custom / OpenRouter", "openai/gpt-4o-mini")
}

data class VyomaSettings(
    val provider: AiProvider = AiProvider.GEMINI,
    val apiKey: String = "",
    val modelName: String = AiProvider.GEMINI.defaultModel,
    val customEndpoint: String = "https://api.openai.com/v1",
    val githubToken: String = "",
    val githubOwner: String = "",
    val defaultRepoPrefix: String = "vyoma-app-",
    val isDeveloperMode: Boolean = false,
    val selectedPaletteId: String = "emerald"
)

object SettingsStore {
    private const val PREFS_NAME = "vyoma_settings"
    private const val KEY_PROVIDER = "ai_provider"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model_name"
    private const val KEY_ENDPOINT = "custom_endpoint"
    private const val KEY_GH_TOKEN = "github_token"
    private const val KEY_GH_OWNER = "github_owner"
    private const val KEY_GH_REPO_PREFIX = "github_repo_prefix"
    private const val KEY_DEV_MODE = "is_developer_mode"
    private const val KEY_PALETTE_ID = "selected_palette_id"

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSettings(context: Context): VyomaSettings {
        val prefs = getPrefs(context)
        val providerStr = prefs.getString(KEY_PROVIDER, AiProvider.GEMINI.name) ?: AiProvider.GEMINI.name
        val provider = try {
            AiProvider.valueOf(providerStr)
        } catch (_: Exception) {
            AiProvider.GEMINI
        }
        return VyomaSettings(
            provider = provider,
            apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
            modelName = prefs.getString(KEY_MODEL, provider.defaultModel) ?: provider.defaultModel,
            customEndpoint = prefs.getString(KEY_ENDPOINT, "https://api.openai.com/v1") ?: "https://api.openai.com/v1",
            githubToken = prefs.getString(KEY_GH_TOKEN, "") ?: "",
            githubOwner = prefs.getString(KEY_GH_OWNER, "") ?: "",
            defaultRepoPrefix = prefs.getString(KEY_GH_REPO_PREFIX, "vyoma-app-") ?: "vyoma-app-",
            isDeveloperMode = prefs.getBoolean(KEY_DEV_MODE, false),
            selectedPaletteId = prefs.getString(KEY_PALETTE_ID, "emerald") ?: "emerald"
        )
    }

    fun saveSettings(context: Context, settings: VyomaSettings) {
        getPrefs(context).edit().apply {
            putString(KEY_PROVIDER, settings.provider.name)
            putString(KEY_API_KEY, settings.apiKey.trim())
            putString(KEY_MODEL, settings.modelName.trim())
            putString(KEY_ENDPOINT, settings.customEndpoint.trim())
            putString(KEY_GH_TOKEN, settings.githubToken.trim())
            putString(KEY_GH_OWNER, settings.githubOwner.trim())
            putString(KEY_GH_REPO_PREFIX, settings.defaultRepoPrefix.trim())
            putBoolean(KEY_DEV_MODE, settings.isDeveloperMode)
            putString(KEY_PALETTE_ID, settings.selectedPaletteId)
            apply()
        }
    }
}
