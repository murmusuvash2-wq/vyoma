package com.vyoma.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class CodingAgentConnectionConfig(
    val backendUrl: String,
    val projectId: String,
    val bearerToken: String
)

/** Stores development connection settings; bearer tokens are encrypted with Android Keystore. */
internal object CodingAgentConnectionStore {
    private const val PREFS = "vyoma_coding_agent_connection_v1"
    private const val URL_KEY = "backend_url"
    private const val PROJECT_KEY = "project_id"
    private const val TOKEN_KEY = "encrypted_bearer_token"
    private const val KEY_ALIAS = "vyoma_agent_token_aes_v1"

    fun load(context: Context): CodingAgentConnectionConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString(TOKEN_KEY, null)?.let { encrypted ->
            runCatching { decrypt(encrypted) }.getOrNull()
        }.orEmpty()
        return CodingAgentConnectionConfig(
            backendUrl = prefs.getString(URL_KEY, "").orEmpty(),
            projectId = prefs.getString(PROJECT_KEY, "sample") ?: "sample",
            bearerToken = token
        )
    }

    fun save(context: Context, backendUrl: String, projectId: String, bearerToken: String) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(URL_KEY, backendUrl.trim())
            .putString(PROJECT_KEY, projectId.trim())
        if (bearerToken.isNotBlank()) {
            editor.putString(TOKEN_KEY, encrypt(bearerToken.trim()))
        }
        editor.apply()
    }

    fun clearToken(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(TOKEN_KEY).apply()
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
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
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > 12) { "Saved backend token is invalid. Enter it again." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
}
