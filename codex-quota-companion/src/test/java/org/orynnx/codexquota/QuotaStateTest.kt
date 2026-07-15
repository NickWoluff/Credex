package org.orynnx.codexquota

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaStateTest {
    @Test
    fun weeklyOnlyRoundTripKeepsWindowAndHealth() {
        val state = QuotaState(
            weeklyRemaining = 64,
            weeklyReset = "07-20 09:00",
            weeklyResetAtEpoch = 1_700_000_000L,
            plan = "plus",
            status = "OK",
            updatedAt = "21:57",
            lastAttemptAt = "21:57",
            health = QuotaHealth.FRESH,
        )

        val restored = QuotaState.from(state.json())

        assertFalse(restored.hasFiveHour)
        assertTrue(restored.hasWeekly)
        assertEquals(64, restored.weeklyRemaining)
        assertEquals(1_700_000_000L, restored.weeklyResetAtEpoch)
        assertEquals(QuotaHealth.FRESH, restored.health)
    }

    @Test
    fun cachedStatePreservesLastKnownGoodWindows() {
        val cached = QuotaState(
            fiveHourRemaining = 72,
            weeklyRemaining = 41,
            status = "Refresh failed",
            updatedAt = "21:57",
            lastAttemptAt = "22:12",
            health = QuotaHealth.CACHED,
        )

        val restored = QuotaState.from(cached.json())

        assertTrue(restored.hasFiveHour)
        assertTrue(restored.hasWeekly)
        assertEquals("21:57", restored.updatedAt)
        assertEquals("22:12", restored.lastAttemptAt)
        assertEquals(QuotaHealth.CACHED, restored.health)
    }

    @Test
    fun legacySuccessfulStateMigratesToFresh() {
        val restored = QuotaState.from("""{"five":80,"week":60,"status":"OK","updated":"12:00"}""")
        assertEquals(QuotaHealth.FRESH, restored.health)
    }
}
