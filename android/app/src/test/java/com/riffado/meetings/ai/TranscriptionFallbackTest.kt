package com.riffado.meetings.ai

import com.riffado.meetings.data.TranscriptionProviderConfig
import com.riffado.meetings.data.TranscriptionSettings
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

/**
 * Tests for provider fallback and reset date logic.
 */
class TranscriptionFallbackTest {
    
    @Test
    fun `providers ordered by providerOrder list`() {
        val providers = listOf(
            TranscriptionProviderConfig("a", "A", "http://a", "key1", "model", enabled = true),
            TranscriptionProviderConfig("b", "B", "http://b", "key2", "model", enabled = true),
            TranscriptionProviderConfig("c", "C", "http://c", "key3", "model", enabled = true),
        )
        val settings = TranscriptionSettings(providers, listOf("c", "a", "b"))
        
        val available = settings.availableProvidersInOrder()
        
        assertEquals(3, available.size)
        assertEquals("c", available[0].id)
        assertEquals("a", available[1].id)
        assertEquals("b", available[2].id)
    }
    
    @Test
    fun `exhausted providers excluded from available list`() {
        val now = System.currentTimeMillis()
        val future = now + 86400000 // +1 day
        
        val providers = listOf(
            TranscriptionProviderConfig("a", "A", "http://a", "key", "model", enabled = true, exhaustedUntil = future),
            TranscriptionProviderConfig("b", "B", "http://b", "key", "model", enabled = true),
        )
        val settings = TranscriptionSettings(providers, listOf("a", "b"))
        
        val available = settings.availableProvidersInOrder()
        
        assertEquals(1, available.size)
        assertEquals("b", available[0].id)
    }
    
    @Test
    fun `disabled providers excluded from available list`() {
        val providers = listOf(
            TranscriptionProviderConfig("a", "A", "http://a", "key", "model", enabled = false),
            TranscriptionProviderConfig("b", "B", "http://b", "key", "model", enabled = true),
        )
        val settings = TranscriptionSettings(providers, listOf("a", "b"))
        
        val available = settings.availableProvidersInOrder()
        
        assertEquals(1, available.size)
        assertEquals("b", available[0].id)
    }
    
    @Test
    fun `providers without API key excluded from available list`() {
        val providers = listOf(
            TranscriptionProviderConfig("a", "A", "http://a", "", "model", enabled = true),
            TranscriptionProviderConfig("b", "B", "http://b", "key", "model", enabled = true),
        )
        val settings = TranscriptionSettings(providers, listOf("a", "b"))
        
        val available = settings.availableProvidersInOrder()
        
        assertEquals(1, available.size)
        assertEquals("b", available[0].id)
    }
    
    @Test
    fun `markExhausted updates provider exhaustion timestamp`() {
        val provider = TranscriptionProviderConfig("a", "A", "http://a", "key", "model")
        val settings = TranscriptionSettings(listOf(provider), listOf("a"))
        
        val exhaustedUntil = System.currentTimeMillis() + 86400000
        val updated = settings.markExhausted("a", exhaustedUntil)
        
        val updatedProvider = updated.findProvider("a")
        assertNotNull(updatedProvider)
        assertEquals(exhaustedUntil, updatedProvider!!.exhaustedUntil)
        assertTrue(updatedProvider.isExhausted)
    }
    
    @Test
    fun `markRecovered clears exhaustion timestamp`() {
        val exhaustedUntil = System.currentTimeMillis() + 86400000
        val provider = TranscriptionProviderConfig("a", "A", "http://a", "key", "model", exhaustedUntil = exhaustedUntil)
        val settings = TranscriptionSettings(listOf(provider), listOf("a"))
        
        val updated = settings.markRecovered("a")
        
        val updatedProvider = updated.findProvider("a")
        assertNotNull(updatedProvider)
        assertNull(updatedProvider!!.exhaustedUntil)
        assertFalse(updatedProvider.isExhausted)
    }
    
    @Test
    fun `exhaustion end calculation for reset day in current month`() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, 5) // 5 days in future
        val resetDay = cal.get(Calendar.DAY_OF_MONTH)
        
        val now = System.currentTimeMillis()
        val exhaustionEnd = calculateExhaustionEndTest(resetDay)
        
        // Should be in the current month
        assertTrue(exhaustionEnd > now)
        val endCal = Calendar.getInstance()
        endCal.timeInMillis = exhaustionEnd
        assertEquals(resetDay, endCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, endCal.get(Calendar.MINUTE))
    }
    
    @Test
    fun `exhaustion end calculation for reset day already passed`() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, -5) // 5 days ago
        val resetDay = cal.get(Calendar.DAY_OF_MONTH)
        
        val exhaustionEnd = calculateExhaustionEndTest(resetDay)
        
        // Should be next month
        val now = System.currentTimeMillis()
        assertTrue(exhaustionEnd > now)
        
        val endCal = Calendar.getInstance()
        endCal.timeInMillis = exhaustionEnd
        assertEquals(resetDay, endCal.get(Calendar.DAY_OF_MONTH))
        assertTrue(endCal.get(Calendar.MONTH) > cal.get(Calendar.MONTH) || 
                  (endCal.get(Calendar.MONTH) == 0 && cal.get(Calendar.MONTH) == 11)) // Year boundary
    }
    
    /**
     * Test helper that mirrors TranscriptionCoordinator's calculation.
     */
    private fun calculateExhaustionEndTest(billingResetDay: Int): Long {
        val now = Calendar.getInstance()
        val exhaustionEnd = Calendar.getInstance()
        
        exhaustionEnd.set(Calendar.DAY_OF_MONTH, billingResetDay.coerceIn(1, 28))
        exhaustionEnd.set(Calendar.HOUR_OF_DAY, 0)
        exhaustionEnd.set(Calendar.MINUTE, 0)
        exhaustionEnd.set(Calendar.SECOND, 0)
        exhaustionEnd.set(Calendar.MILLISECOND, 0)
        
        if (exhaustionEnd.timeInMillis <= now.timeInMillis) {
            exhaustionEnd.add(Calendar.MONTH, 1)
        }
        
        return exhaustionEnd.timeInMillis
    }
}
