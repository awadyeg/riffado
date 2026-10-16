package com.riffado.meetings.ui

import androidx.compose.ui.text.style.TextDirection

/**
 * Detects text direction based on the first strong directional character.
 * Returns RTL for Arabic, Hebrew, etc., and LTR otherwise.
 */
object TextDirectionDetector {
    
    /**
     * Detect direction for a single line or paragraph.
     * Uses the first strong directional character (Bidi class L, R, or AL).
     */
    fun detectDirection(text: String): TextDirection {
        for (char in text) {
            when (Character.getDirectionality(char)) {
                // Right-to-left (Arabic, Hebrew, etc.)
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return TextDirection.Rtl
                
                // Left-to-right (Latin, etc.)
                Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return TextDirection.Ltr
                
                // Continue scanning for neutral characters
                else -> continue
            }
        }
        // Default to content-based if no strong character found
        return TextDirection.Content
    }
    
    /**
     * Detect if text is primarily RTL based on character counts.
     * Useful for longer paragraphs with mixed content.
     */
    fun isPrimarilyRtl(text: String): Boolean {
        var rtlCount = 0
        var ltrCount = 0
        
        for (char in text) {
            when (Character.getDirectionality(char)) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> rtlCount++
                Character.DIRECTIONALITY_LEFT_TO_RIGHT -> ltrCount++
            }
        }
        
        return rtlCount > ltrCount
    }
}
