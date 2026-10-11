package com.riffado.meetings.data

/** Helpers for diarized transcripts made of `Speaker N: text` lines. */
object Speakers {
    private val LINE = Regex("^Speaker (\\d+): ?(.*)$")

    data class Turn(val speaker: String?, val text: String)

    fun parse(transcript: String): List<Turn> =
        transcript.lines().filter { it.isNotBlank() }.map { line ->
            val m = LINE.matchEntire(line.trim())
            if (m != null) Turn(m.groupValues[1], m.groupValues[2]) else Turn(null, line.trim())
        }

    /** Speaker numbers in order of first appearance. */
    fun ids(transcript: String?): List<String> =
        transcript?.let { parse(it).mapNotNull { t -> t.speaker }.distinct() } ?: emptyList()

    fun label(id: String, names: Map<String, String>): String =
        names[id]?.takeIf { it.isNotBlank() } ?: "Speaker $id"

    /** Rewrites `Speaker N:` prefixes with the user's names, for the summary prompt and sharing. */
    fun applyNames(transcript: String, names: Map<String, String>): String =
        parse(transcript).joinToString("\n") { t ->
            if (t.speaker == null) t.text else "${label(t.speaker, names)}: ${t.text}"
        }
}
