package com.riffado.meetings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffado.meetings.R
import com.riffado.meetings.data.Recording
import com.riffado.meetings.data.RecordingStatus
import com.riffado.meetings.recording.LiveRecording

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    recordings: List<Recording>,
    live: LiveRecording,
    configured: Boolean,
    onRecord: () -> Unit,
    onImport: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf("all") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedPerson by rememberSaveable { mutableStateOf<String?>(null) }
    
    // Extract people from recordings for the person filter
    val people = remember(recordings) { RecordingSearch.extractPeople(recordings) }
    
    // Apply filters in order: type filter -> person filter -> search
    val filteredRecordings = recordings
        .let { list ->
            when (filter) {
                "meetings" -> list.filter { !it.isCall }
                "calls" -> list.filter { it.isCall }
                else -> list
            }
        }
        .let { list ->
            selectedPerson?.let { person ->
                RecordingSearch.filterByPerson(list, person)
            } ?: list
        }
        .let { list ->
            if (searchQuery.isBlank()) list else RecordingSearch.search(list, searchQuery)
        }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { RecorderCard(live, onRecord, onPause, onResume, onStop) }
            item {
                TextButton(
                    onClick = onImport,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_import))
                }
            }
            
            // Search bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_clear))
                            }
                        }
                    },
                    singleLine = true,
                )
            }
            
            // Filter chips
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilterChip(
                        selected = filter == "all",
                        onClick = { filter = "all" },
                        label = { Text(stringResource(R.string.filter_all)) },
                    )
                    FilterChip(
                        selected = filter == "meetings",
                        onClick = { filter = "meetings" },
                        label = { Text(stringResource(R.string.filter_meetings)) },
                    )
                    FilterChip(
                        selected = filter == "calls",
                        onClick = { filter = "calls" },
                        label = { Text(stringResource(R.string.filter_calls)) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Call,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
            
            // Person filter dropdown
            if (people.isNotEmpty()) {
                item {
                    PersonFilterDropdown(
                        people = people,
                        selectedPerson = selectedPerson,
                        onSelectPerson = { selectedPerson = it },
                    )
                }
            }
            
            if (!configured) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.home_not_configured))
                            TextButton(onClick = onSettings) { Text(stringResource(R.string.action_open_settings)) }
                        }
                    }
                }
            }
            if (filteredRecordings.isEmpty()) {
                item {
                    Text(
                        if (searchQuery.isNotEmpty() || selectedPerson != null) {
                            stringResource(R.string.search_no_results)
                        } else {
                            stringResource(R.string.home_empty)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    )
                }
            }
            items(filteredRecordings, key = { it.id }) { rec ->
                RecordingRow(rec, live, onClick = { onOpen(rec.id) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonFilterDropdown(
    people: List<String>,
    selectedPerson: String?,
    onSelectPerson: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedPerson ?: "",
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            placeholder = { Text(stringResource(R.string.filter_by_person)) },
            trailingIcon = {
                Row {
                    if (selectedPerson != null) {
                        IconButton(onClick = { onSelectPerson(null) }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            people.forEach { person ->
                DropdownMenuItem(
                    text = { Text(person) },
                    onClick = {
                        onSelectPerson(person)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RecorderCard(
    live: LiveRecording,
    onRecord: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!live.isActive) {
                FilledIconButton(
                    onClick = onRecord,
                    modifier = Modifier.size(96.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.action_record), Modifier.size(48.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.action_record), style = MaterialTheme.typography.titleMedium)
            } else {
                Text(
                    stringResource(if (live.isPaused) R.string.recording_paused else R.string.recording_live),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (live.isPaused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
                Text(formatDuration(live.elapsedMs), style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { live.level },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    FilledIconButton(
                        onClick = if (live.isPaused) onResume else onPause,
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape,
                    ) {
                        if (live.isPaused) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.action_resume))
                        } else {
                            Icon(Icons.Filled.Pause, contentDescription = stringResource(R.string.action_pause))
                        }
                    }
                    FilledIconButton(
                        onClick = onStop,
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.action_stop))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingRow(rec: Recording, live: LiveRecording, onClick: () -> Unit) {
    val isLive = rec.id == live.recordingId
    Card(modifier = Modifier.fillMaxWidth().clickable(enabled = !isLive, onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (rec.isCall) {
                Icon(
                    Icons.Filled.Call,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp).padding(end = 8.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                // Show display name (contact name or number for calls, title for meetings)
                Text(
                    rec.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // For calls with contact name, show number as subtitle
                if (rec.isCall && rec.callContactName != null && rec.callNumber != null) {
                    Text(
                        rec.callNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Text(
                    "${formatDate(rec.createdAt)} · ${formatDuration(if (isLive) live.elapsedMs else rec.durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    statusLabel(rec.status),
                    style = MaterialTheme.typography.labelMedium,
                    color = when (rec.status) {
                        RecordingStatus.FAILED, RecordingStatus.INTERRUPTED -> MaterialTheme.colorScheme.error
                        RecordingStatus.DONE -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (rec.status.isBusy) {
                Box(Modifier.padding(start = 12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}
