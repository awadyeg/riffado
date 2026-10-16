package com.riffado.meetings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.riffado.meetings.R
import com.riffado.meetings.data.AppSettings
import com.riffado.meetings.data.ProviderConfig
import com.riffado.meetings.data.SettingsStore

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(initial: AppSettings, onSave: (AppSettings) -> Unit, onBack: () -> Unit) {
    var txUrl by remember { mutableStateOf(initial.transcription.baseUrl) }
    var txKey by remember { mutableStateOf(initial.transcription.apiKey) }
    var txModel by remember { mutableStateOf(initial.transcription.model) }
    var sumUrl by remember { mutableStateOf(initial.summary.baseUrl) }
    var sumKey by remember { mutableStateOf(initial.summary.apiKey) }
    var sumModel by remember { mutableStateOf(initial.summary.model) }
    var language by remember { mutableStateOf(initial.language) }
    var auto by remember { mutableStateOf(initial.autoProcess) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.settings_presets), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {
                        if (txUrl.contains("elevenlabs.io") || txUrl.contains("sarvam.ai") || txUrl.contains("munsit.com") || txUrl.contains("faseeh.ai")) txKey = sumKey
                        txUrl = SettingsStore.OPENAI_URL
                        txModel = "whisper-1"
                        sumUrl = SettingsStore.OPENAI_URL
                        sumModel = "gpt-4o-mini"
                    },
                    label = { Text("OpenAI") },
                )
                AssistChip(
                    onClick = {
                        if (txUrl.contains("elevenlabs.io") || txUrl.contains("sarvam.ai") || txUrl.contains("munsit.com") || txUrl.contains("faseeh.ai")) txKey = sumKey
                        txUrl = SettingsStore.GROQ_URL
                        txModel = SettingsStore.GROQ_TRANSCRIPTION_MODEL
                        sumUrl = SettingsStore.GROQ_URL
                        sumModel = SettingsStore.GROQ_SUMMARY_MODEL
                    },
                    label = { Text("Groq") },
                )
                AssistChip(
                    onClick = {
                        if (!txUrl.contains("elevenlabs.io") && !txUrl.contains("sarvam.ai") && !txUrl.contains("munsit.com") && !txUrl.contains("faseeh.ai") && sumKey.isBlank() && sumUrl == txUrl) sumKey = txKey
                        if (!txUrl.contains("elevenlabs.io") && !txUrl.contains("sarvam.ai") && !txUrl.contains("munsit.com") && !txUrl.contains("faseeh.ai")) txKey = ""
                        txUrl = SettingsStore.ELEVENLABS_URL
                        txModel = SettingsStore.ELEVENLABS_MODEL
                    },
                    label = { Text(stringResource(R.string.settings_preset_speakers)) },
                )
                AssistChip(
                    onClick = {
                        if (!txUrl.contains("sarvam.ai") && sumKey.isBlank() && sumUrl == SettingsStore.GROQ_URL) sumKey = txKey
                        if (!txUrl.contains("sarvam.ai")) txKey = ""
                        txUrl = SettingsStore.SARVAM_URL
                        txModel = SettingsStore.SARVAM_MODEL
                        if (sumUrl != SettingsStore.GROQ_URL) {
                            sumUrl = SettingsStore.GROQ_URL
                            sumModel = SettingsStore.GROQ_SUMMARY_MODEL
                        }
                    },
                    label = { Text(stringResource(R.string.settings_preset_sarvam)) },
                )
                AssistChip(
                    onClick = {
                        if (!txUrl.contains("munsit.com") && !txUrl.contains("faseeh.ai") && sumKey.isBlank() && sumUrl == SettingsStore.GROQ_URL) sumKey = txKey
                        if (!txUrl.contains("munsit.com") && !txUrl.contains("faseeh.ai")) txKey = ""
                        txUrl = SettingsStore.MUNSIT_URL
                        txModel = SettingsStore.MUNSIT_MODEL
                        if (sumUrl != SettingsStore.GROQ_URL) {
                            sumUrl = SettingsStore.GROQ_URL
                            sumModel = SettingsStore.GROQ_SUMMARY_MODEL
                        }
                    },
                    label = { Text(stringResource(R.string.settings_preset_munsit)) },
                )
            }
            Text(
                stringResource(R.string.settings_speakers_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()
            Text(stringResource(R.string.settings_transcription), style = MaterialTheme.typography.titleMedium)
            UrlField(txUrl) { txUrl = it }
            KeyField(txKey, null) { txKey = it }
            ModelField(txModel) { txModel = it }
            OutlinedTextField(
                value = language,
                onValueChange = { language = it },
                label = { Text(stringResource(R.string.settings_language)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()
            Text(stringResource(R.string.settings_summary), style = MaterialTheme.typography.titleMedium)
            UrlField(sumUrl) { sumUrl = it }
            KeyField(sumKey, stringResource(R.string.settings_api_key_summary_hint)) { sumKey = it }
            ModelField(sumModel) { sumModel = it }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_auto), modifier = Modifier.weight(1f))
                Switch(checked = auto, onCheckedChange = { auto = it })
            }

            Text(
                stringResource(R.string.settings_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    onSave(
                        AppSettings(
                            transcription = ProviderConfig(txUrl, txKey, txModel),
                            summary = ProviderConfig(sumUrl, sumKey, sumModel),
                            language = language,
                            autoProcess = auto,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}

@Composable
private fun UrlField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.settings_base_url)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun KeyField(value: String, hint: String?, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.settings_api_key)) },
        supportingText = hint?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ModelField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.settings_model)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
