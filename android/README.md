# Riffado Meetings (Android)

Standalone Android app that turns the phone into a meeting recorder: record, transcribe, and summarize without a separate device or a Riffado server.

## Features

- Background recording in a foreground service (screen off, other apps open). Pause, resume, and stop from the notification.
- AAC/m4a, 16 kHz mono, 32 kbps (about 14 MB per hour). Long meetings roll over to a new file every 20 MB so each upload stays under the 25 MB limit of OpenAI and Groq.
- Transcription via any OpenAI-compatible `/audio/transcriptions` endpoint (OpenAI `whisper-1`, Groq `whisper-large-v3`, or a local Whisper server over `http://`).
- Speaker separation via ElevenLabs Scribe (`scribe_v2`): transcripts come back as `Speaker 1`, `Speaker 2`, ... Speakers can be renamed per recording, and the summary attributes points, decisions, and action items by name. Multi-part recordings are joined on the phone before upload so speaker numbers stay consistent.
- Meeting minutes via `/chat/completions`: summary, key points, decisions, action items, plus an auto-generated title. Written in the transcript language.
- Runs through WorkManager, so processing continues after the app is closed and retries on network errors and 429/5xx.
- English and Arabic UI.

## Privacy

Recordings, transcripts, and summaries stay in app-private storage on the phone. Audio is sent only to the provider configured in Settings. API keys are encrypted with an AES-256-GCM key held in the Android Keystore. App backup is disabled.

## Build

Requires JDK 17 and the Android SDK (compileSdk 35).

```bash
cd android
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

CI (`.github/workflows/android.yml`) builds the debug APK on every push that touches `android/` and uploads it as the `riffado-meetings-debug` artifact.

## Setup on the phone

1. Install the APK (allow installs from unknown sources).
2. Open Settings, pick OpenAI or Groq, paste an API key, save.
3. Tap the record button. Grant microphone and notification permissions.
4. Stop the recording. Transcript and summary appear when processing finishes.
