package com.riffado.meetings.ai

/** Prompts for turning a meeting transcript into minutes. */
object Prompts {
    const val MEETING_SYSTEM = """You write meeting minutes from a raw transcript.
Write in the same language as the transcript (for Arabic transcripts, write in Arabic).
Use this Markdown structure and nothing else:

# <short meeting title, max 8 words>

## Summary
2-4 sentences.

## Key points
- bullet list

## Decisions
- bullet list, or "-" if none

## Action items
- [ ] task (owner if mentioned, due date if mentioned)

When transcript lines start with a speaker label ("Ahmed: ..." or "Speaker 2: ..."), that label identifies who said the line.
Attribute opinions, decisions, and action items to the right speaker using those labels exactly as written.
Be factual. Do not invent names, numbers, or commitments that are not in the transcript."""

    const val CHUNK_SUMMARY_SYSTEM = """You are summarizing part of a longer meeting transcript.
Extract the key information from this segment in the same language as the transcript.
Include:
- Main topics discussed
- Important points raised
- Any decisions or action items mentioned
- Speaker attributions when relevant

Keep the summary concise but preserve important details and context."""

    const val MERGE_SUMMARIES_SYSTEM = """You are merging multiple summaries of a long meeting transcript into final meeting minutes.
Each summary below covers a different part of the meeting.
Write in the same language as the summaries.
Use this Markdown structure and nothing else:

# <short meeting title, max 8 words>

## Summary
2-4 sentences covering the full meeting.

## Key points
- bullet list of all important points

## Decisions
- bullet list of all decisions, or "-" if none

## Action items
- [ ] all tasks with owner and due date if mentioned

Merge redundant information. Preserve speaker attributions. Be factual."""

    /** Extracts the `# title` line from generated minutes, if present. */
    fun titleFrom(summary: String): String? =
        summary.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("# ") }
            ?.removePrefix("# ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= 120 }
}
