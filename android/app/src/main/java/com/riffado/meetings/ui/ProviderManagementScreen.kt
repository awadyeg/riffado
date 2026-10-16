package com.riffado.meetings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.riffado.meetings.R
import com.riffado.meetings.data.TranscriptionProviderConfig
import com.riffado.meetings.data.TranscriptionSettings
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderManagementScreen(
    settings: TranscriptionSettings,
    onBack: () -> Unit,
    onSave: (TranscriptionSettings) -> Unit,
) {
    var providers by remember { mutableStateOf(settings.providers) }
    var order by remember { mutableStateOf(settings.providerOrder) }
    var editingProvider by remember { mutableStateOf<String?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transcription Providers") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        onSave(TranscriptionSettings(providers, order))
                        onBack()
                    }) {
                        Text("Save")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Providers are tried in order. If one fails with quota exhaustion, the next is used automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            
            order.forEach { providerId ->
                val provider = providers.find { it.id == providerId } ?: return@forEach
                ProviderCard(
                    provider = provider,
                    onEdit = { editingProvider = providerId },
                    onToggle = { enabled ->
                        providers = providers.map {
                            if (it.id == providerId) it.copy(enabled = enabled) else it
                        }
                    },
                )
            }
        }
    }
    
    editingProvider?.let { id ->
        val provider = providers.find { it.id == id } ?: return@let
        ProviderEditDialog(
            provider = provider,
            onDismiss = { editingProvider = null },
            onSave = { updated ->
                providers = providers.map { if (it.id == id) updated else it }
                editingProvider = null
            },
        )
    }
}

@Composable
private fun ProviderCard(
    provider: TranscriptionProviderConfig,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(provider.name, style = MaterialTheme.typography.titleMedium)
                Text(provider.model, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                if (provider.apiKey.isEmpty()) {
                    Text("No API key", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                } else if (provider.isExhausted) {
                    val resetDate = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(provider.exhaustedUntil!!))
                    Text("Quota exhausted until $resetDate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                } else if (provider.isAvailable) {
                    Text("Active", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Switch(
                checked = provider.enabled,
                onCheckedChange = onToggle,
            )
        }
    }
}

@Composable
private fun ProviderEditDialog(
    provider: TranscriptionProviderConfig,
    onDismiss: () -> Unit,
    onSave: (TranscriptionProviderConfig) -> Unit,
) {
    var apiKey by remember { mutableStateOf(provider.apiKey) }
    var model by remember { mutableStateOf(provider.model) }
    var resetDay by remember { mutableStateOf(provider.billingResetDay.toString()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${provider.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = resetDay,
                    onValueChange = { resetDay = it },
                    label = { Text("Billing Reset Day (1-28)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Day of month when quota resets (e.g., 1 for first of month)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val day = resetDay.toIntOrNull()?.coerceIn(1, 28) ?: provider.billingResetDay
                onSave(provider.copy(
                    apiKey = apiKey.trim(),
                    model = model.trim(),
                    billingResetDay = day,
                ))
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
