package com.riffado.meetings.ui

import com.riffado.meetings.data.Recording
import com.riffado.meetings.data.Speakers
import java.text.Normalizer

/**
 * Search utility for filtering recordings with Arabic-friendly text normalization.
 */
object RecordingSearch {
    
    /**
     * Normalize text for Arabic-friendly search:
     * - Normalize alef variants (أ إ آ ا -> ا)
     * - Normalize taa marbuta (ة -> ه)
     * - Remove diacritics (fatha, kasra, damma, sukun, shadda, etc.)
     * - Lowercase for case-insensitive search
     */
    fun normalizeArabic(text: String): String {
        // First, remove combining diacritics using Unicode normalization
        val noDiacritics = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}"), "") // Remove combining marks (diacritics)
        
        return noDiacritics
            // Normalize alef variants
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ٱ', 'ا')
            // Normalize taa marbuta
            .replace('ة', 'ه')
            // Normalize yaa variants
            .replace('ى', 'ي')
            // Remove remaining Arabic diacritics (U+0610 to U+061A, U+064B to U+065F)
            .replace(Regex("[\u0610-\u061A\u064B-\u065F]"), "")
            .lowercase()
    }
    
    /**
     * Search recordings by query string.
     * Searches in: title, transcript, summary, speaker names, call contact name, call number.
     * 
     * @param recordings List of recordings to search
     * @param query Search query string
     * @return Filtered list of recordings matching the query
     */
    fun search(recordings: List<Recording>, query: String): List<Recording> {
        if (query.isBlank()) return recordings
        
        val normalizedQuery = normalizeArabic(query)
        
        return recordings.filter { recording ->
            // Search in title
            if (normalizeArabic(recording.title).contains(normalizedQuery)) return@filter true
            
            // Search in transcript
            recording.transcript?.let {
                if (normalizeArabic(it).contains(normalizedQuery)) return@filter true
            }
            
            // Search in summary
            recording.summary?.let {
                if (normalizeArabic(it).contains(normalizedQuery)) return@filter true
            }
            
            // Search in speaker names
            recording.speakerNames.values.any { name ->
                normalizeArabic(name).contains(normalizedQuery)
            }?.let { if (it) return@filter true }
            
            // Search in call contact name
            recording.callContactName?.let {
                if (normalizeArabic(it).contains(normalizedQuery)) return@filter true
            }
            
            // Search in call number (no normalization needed for numbers)
            recording.callNumber?.let {
                if (it.contains(query, ignoreCase = true)) return@filter true
            }
            
            false
        }
    }
    
    /**
     * Extract all unique person names from recordings.
     * Includes call contact names and renamed speaker names.
     * 
     * @param recordings List of recordings to extract people from
     * @return Sorted list of unique person names
     */
    fun extractPeople(recordings: List<Recording>): List<String> {
        val people = mutableSetOf<String>()
        
        recordings.forEach { recording ->
            // Add call contact name
            recording.callContactName?.let { people.add(it) }
            
            // Add renamed speaker names (skip default "Speaker 1", "Speaker 2", etc.)
            recording.speakerNames.forEach { (id, name) ->
                if (name != "Speaker $id" && name != "المتحدث $id") {
                    people.add(name)
                }
            }
        }
        
        return people.sorted()
    }
    
    /**
     * Filter recordings by person name.
     * Matches against call contact name and speaker names.
     * 
     * @param recordings List of recordings to filter
     * @param personName Person name to filter by
     * @return Filtered list of recordings involving this person
     */
    fun filterByPerson(recordings: List<Recording>, personName: String): List<Recording> {
        if (personName.isBlank()) return recordings
        
        val normalizedPersonName = normalizeArabic(personName)
        
        return recordings.filter { recording ->
            // Match call contact name
            recording.callContactName?.let {
                if (normalizeArabic(it) == normalizedPersonName) return@filter true
            }
            
            // Match speaker names
            recording.speakerNames.values.any { name ->
                normalizeArabic(name) == normalizedPersonName
            }
        }
    }
}
