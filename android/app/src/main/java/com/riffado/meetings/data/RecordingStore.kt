package com.riffado.meetings.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Process-wide store for recording metadata, persisted as JSON in app-private storage. */
class RecordingStore(context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }
    private val dir = File(context.filesDir, "recordings").apply { mkdirs() }
    private val indexFile = File(context.filesDir, "recordings.json")
    private val listSerializer = ListSerializer(Recording.serializer())
    private val mutex = Mutex()
    private val state = MutableStateFlow(load())

    val recordings: StateFlow<List<Recording>> = state.asStateFlow()

    fun get(id: String): Recording? = state.value.firstOrNull { it.id == id }

    fun segmentFile(name: String): File = File(dir, name)

    fun segmentFiles(recording: Recording): List<File> = recording.segments.map(::segmentFile)

    suspend fun upsert(recording: Recording) = mutex.withLock {
        val list = state.value.filterNot { it.id == recording.id } + recording
        commit(list)
    }

    suspend fun update(id: String, transform: (Recording) -> Recording): Recording? = mutex.withLock {
        val current = state.value.firstOrNull { it.id == id } ?: return@withLock null
        val next = transform(current)
        commit(state.value.map { if (it.id == id) next else it })
        next
    }

    suspend fun delete(id: String) = mutex.withLock {
        val target = state.value.firstOrNull { it.id == id } ?: return@withLock
        target.segments.forEach { segmentFile(it).delete() }
        commit(state.value.filterNot { it.id == id })
    }

    /** Marks recordings left in RECORDING state by a killed process as interrupted. */
    suspend fun recoverInterrupted(activeId: String?) = mutex.withLock {
        val fixed = state.value.map {
            if (it.status == RecordingStatus.RECORDING && it.id != activeId) {
                it.copy(status = RecordingStatus.INTERRUPTED)
            } else {
                it
            }
        }
        if (fixed != state.value) commit(fixed)
    }

    private fun commit(list: List<Recording>) {
        val sorted = list.sortedByDescending { it.createdAt }
        val tmp = File(indexFile.parentFile, "${indexFile.name}.tmp")
        tmp.writeText(json.encodeToString(listSerializer, sorted))
        if (!tmp.renameTo(indexFile)) {
            indexFile.writeText(tmp.readText())
            tmp.delete()
        }
        state.value = sorted
    }

    private fun load(): List<Recording> {
        if (!indexFile.exists()) return emptyList()
        return runCatching { json.decodeFromString(listSerializer, indexFile.readText()) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.createdAt }
    }
}
