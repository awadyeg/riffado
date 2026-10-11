package com.riffado.meetings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Renders the small Markdown subset produced by the minutes prompt: headings and bullet lists. */
@Composable
fun SimpleMarkdown(text: String, modifier: Modifier = Modifier) {
    SelectionContainer(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            text.lines().forEach { raw ->
                val line = raw.trimEnd()
                when {
                    line.isBlank() -> Unit
                    line.startsWith("# ") -> Text(
                        line.removePrefix("# "),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    line.startsWith("## ") -> Text(
                        line.removePrefix("## "),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    line.startsWith("### ") -> Text(
                        line.removePrefix("### "),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    BULLET.matches(line) -> {
                        val match = BULLET.find(line)!!
                        val content = match.groupValues[2]
                        val marker = when {
                            content.startsWith("[ ] ") -> "☐"
                            content.startsWith("[x] ", ignoreCase = true) -> "☑"
                            else -> "•"
                        }
                        val body = content.removePrefix("[ ] ").removePrefix("[x] ").removePrefix("[X] ")
                        Row(Modifier.padding(start = (match.groupValues[1].length * 8).dp)) {
                            Text(marker, style = MaterialTheme.typography.bodyMedium)
                            Text(" ", Modifier.width(4.dp))
                            Text(stripBold(body), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    else -> Text(stripBold(line), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private val BULLET = Regex("^(\\s*)[-*] (.*)$")

private fun stripBold(s: String) = s.replace("**", "")
