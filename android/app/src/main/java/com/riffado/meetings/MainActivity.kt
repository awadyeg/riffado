package com.riffado.meetings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riffado.meetings.ui.AppViewModel
import com.riffado.meetings.ui.DetailScreen
import com.riffado.meetings.ui.HomeScreen
import com.riffado.meetings.ui.MeetingsTheme
import com.riffado.meetings.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MeetingsTheme { App(viewModel) }
        }
    }
}

private const val SETTINGS = "settings"

@Composable
private fun App(viewModel: AppViewModel) {
    val context = LocalContext.current
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val live by viewModel.live.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            viewModel.startRecording()
        } else {
            Toast.makeText(context, R.string.permission_needed, Toast.LENGTH_LONG).show()
        }
    }

    val startRecording = {
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) viewModel.startRecording() else permissionLauncher.launch(needed.toTypedArray())
    }

    BackHandler(enabled = screen != null) { screen = null }

    when (val current = screen) {
        null -> HomeScreen(
            recordings = recordings,
            live = live,
            configured = settings.canTranscribe,
            onRecord = startRecording,
            onPause = viewModel::pauseRecording,
            onResume = viewModel::resumeRecording,
            onStop = viewModel::stopRecording,
            onOpen = { screen = it },
            onSettings = { screen = SETTINGS },
        )
        SETTINGS -> SettingsScreen(
            initial = settings,
            onSave = {
                viewModel.saveSettings(it)
                Toast.makeText(context, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                screen = null
            },
            onBack = { screen = null },
        )
        else -> {
            val recording = recordings.firstOrNull { it.id == current }
            if (recording == null) {
                LaunchedEffect(current) { screen = null }
            } else {
                DetailScreen(
                    recording = recording,
                    segmentPaths = viewModel.segmentPaths(recording.id),
                    canProcess = settings.canTranscribe,
                    onBack = { screen = null },
                    onProcess = { summaryOnly -> viewModel.process(recording.id, summaryOnly) },
                    onRename = { viewModel.rename(recording.id, it) },
                    onRenameSpeaker = { speaker, name -> viewModel.renameSpeaker(recording.id, speaker, name) },
                    onDelete = {
                        viewModel.delete(recording.id)
                        screen = null
                    },
                )
            }
        }
    }
}
