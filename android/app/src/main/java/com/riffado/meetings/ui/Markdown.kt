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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Renders the small Markdown subset produced by the minutes prompt: headings and bullet lists. */
@Composable
fun SimpleMarkdown(text: String, modifier: Modifier = Modifier) {
    SelectionContainer(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            text.lines().forEach { raw ->
                val line = raw.trimEnd()
                
                // Skip blank lines
                if (line.isBlank()) return@forEach
                
                // Detect text direction for this line
                val textDirection = TextDirectionDetector.detectDirection(line)
                val isRtl = textDirection == androidx.compose.ui.text.style.TextDirection.Rtl
                val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                
                when {
                    line.startsWith("# ") -> {
                        val content = line.removePrefix("# ")
                        Text(
                            content,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                textDirection = textDirection,
                                textAlign = TextAlign.Start,
                            ),
                        )
                    }
                    line.startsWith("## ") -> {
                        val content = line.removePrefix("## ")
                        Text(
                            content,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                textDirection = textDirection,
                                textAlign = TextAlign.Start,
                            ),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    line.startsWith("### ") -> {
                        val content = line.removePrefix("### ")
                        Text(
                            content,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                textDirection = textDirection,
                                textAlign = TextAlign.Start,
                            ),
                        )
                    }
                    BULLET.matches(line) -> {
                        val match = BULLET.find(line)!!
                        val indent = match.groupValues[1].length
                        val content = match.groupValues[2]
                        val marker = when {
                            content.startsWith("[ ] ") -> "☐"
                            content.startsWith("[x] ", ignoreCase = true) -> "☑"
                            else -> "•"
                        }
                        val body = content.removePrefix("[ ] ").removePrefix("[x] ").removePrefix("[X] ")
                        
                        // Set layout direction for the row so marker appears on correct side
                        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                            Row(Modifier.padding(start = (indent * 8).dp)) {
                                Text(
                                    marker,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        textDirection = textDirection,
                                    ),
                                )
                                Text(" ", Modifier.width(4.dp))
                                Text(
                                    stripBold(body),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        textDirection = textDirection,
                                        textAlign = TextAlign.Start,
                                    ),
                                )
                            }
                        }
                    }
                    else -> {
                        // Regular paragraph - skip if it's just a period
                        val cleanLine = stripBold(line).trim()
                        if (cleanLine != ".") {
                            Text(
                                stripBold(line),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    textDirection = textDirection,
                                    textAlign = TextAlign.Start,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val BULLET = Regex("^(\\s*)[-*] (.*)$")

private fun stripBold(s: String) = s.replace("**", "")
