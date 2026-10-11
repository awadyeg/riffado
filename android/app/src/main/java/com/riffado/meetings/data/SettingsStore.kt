package com.riffado.meetings.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Connection settings for an OpenAI-compatible endpoint. */
data class ProviderConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
)

data class AppSettings(
    val transcription: ProviderConfig,
    val summary: ProviderConfig,
    val language: String,
    val autoProcess: Boolean,
) {
    val canTranscribe: Boolean
        get() = transcription.baseUrl.isNotBlank() && transcription.model.isNotBlank() &&
            (transcription.apiKey.isNotBlank() || isLocal(transcription.baseUrl))

    val canSummarize: Boolean
        get() = summary.baseUrl.isNotBlank() && summary.model.isNotBlank()

    /** Summary config with the transcription key filled in when the summary key is blank. */
    val effectiveSummary: ProviderConfig
        get() = if (summary.apiKey.isBlank()) summary.copy(apiKey = transcription.apiKey) else summary

    private fun isLocal(url: String): Boolean = url.startsWith("http://")
}

/** Settings persisted in SharedPreferences; API keys are encrypted via [KeystoreCipher]. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    val settings: StateFlow<AppSettings> = state.asStateFlow()

    fun save(value: AppSettings) {
        prefs.edit {
            putString(K_TX_URL, value.transcription.baseUrl.trim().trimEnd('/'))
            putString(K_TX_KEY, KeystoreCipher.encrypt(value.transcription.apiKey.trim()))
            putString(K_TX_MODEL, value.transcription.model.trim())
            putString(K_SUM_URL, value.summary.baseUrl.trim().trimEnd('/'))
            putString(K_SUM_KEY, KeystoreCipher.encrypt(value.summary.apiKey.trim()))
            putString(K_SUM_MODEL, value.summary.model.trim())
            putString(K_LANG, value.language.trim())
            putBoolean(K_AUTO, value.autoProcess)
        }
        state.value = read()
    }

    private fun read(): AppSettings = AppSettings(
        transcription = ProviderConfig(
            baseUrl = prefs.getString(K_TX_URL, null) ?: OPENAI_URL,
            apiKey = KeystoreCipher.decrypt(prefs.getString(K_TX_KEY, "") ?: ""),
            model = prefs.getString(K_TX_MODEL, null) ?: "whisper-1",
        ),
        summary = ProviderConfig(
            baseUrl = prefs.getString(K_SUM_URL, null) ?: OPENAI_URL,
            apiKey = KeystoreCipher.decrypt(prefs.getString(K_SUM_KEY, "") ?: ""),
            model = (prefs.getString(K_SUM_MODEL, null) ?: "gpt-4o-mini").let { RETIRED_MODELS[it] ?: it },
        ),
        language = prefs.getString(K_LANG, "") ?: "",
        autoProcess = prefs.getBoolean(K_AUTO, true),
    )

    companion object {
        const val OPENAI_URL = "https://api.openai.com/v1"
        const val GROQ_URL = "https://api.groq.com/openai/v1"
        const val GROQ_TRANSCRIPTION_MODEL = "whisper-large-v3"
        const val GROQ_SUMMARY_MODEL = "openai/gpt-oss-120b"

        private val RETIRED_MODELS = mapOf("llama-3.3-70b-versatile" to GROQ_SUMMARY_MODEL)

        private const val K_TX_URL = "tx_url"
        private const val K_TX_KEY = "tx_key"
        private const val K_TX_MODEL = "tx_model"
        private const val K_SUM_URL = "sum_url"
        private const val K_SUM_KEY = "sum_key"
        private const val K_SUM_MODEL = "sum_model"
        private const val K_LANG = "language"
        private const val K_AUTO = "auto_process"
    }
}
