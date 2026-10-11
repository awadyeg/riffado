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

Be factual. Do not invent names, numbers, or commitments that are not in the transcript."""

    /** Extracts the `# title` line from generated minutes, if present. */
    fun titleFrom(summary: String): String? =
        summary.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("# ") }
            ?.removePrefix("# ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= 120 }
}
