package org.orynnx.codexquota

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuotaWindowClassifierTest {
    @Test
    fun singlePrimaryWeeklyWindowDoesNotBecomeFiveHour() {
        val windows = classify(
            """
            {
              "primary_window": {
                "limit_window_seconds": 604800,
                "used_percent": 4,
                "reset_at": 1784547600
              }
            }
            """,
        )

        assertNull(windows.fiveHour)
        assertEquals(96, windows.weekly?.remaining)
        assertEquals(1784547600L, windows.weekly?.resetAtEpoch)
    }

    @Test
    fun singlePrimaryFiveHourWindowIsFiveHour() {
        val windows = classify(
            """{"primary_window":{"limit_window_seconds":18000,"used_percent":18}}""",
        )

        assertEquals(82, windows.fiveHour?.remaining)
        assertNull(windows.weekly)
    }

    @Test
    fun twoDeclaredWindowsAreClassifiedByDuration() {
        val windows = classify(
            """
            {
              "primary_window":{"limit_window_seconds":18000,"used_percent":10},
              "secondary_window":{"limit_window_seconds":604800,"used_percent":40}
            }
            """,
        )

        assertEquals(90, windows.fiveHour?.remaining)
        assertEquals(60, windows.weekly?.remaining)
    }

    @Test
    fun reversedWindowOrderDoesNotChangeMeaning() {
        val windows = classify(
            """
            {
              "primary_window":{"limit_window_seconds":604800,"used_percent":25},
              "secondary_window":{"limit_window_seconds":18000,"used_percent":50}
            }
            """,
        )

        assertEquals(50, windows.fiveHour?.remaining)
        assertEquals(75, windows.weekly?.remaining)
    }

    @Test
    fun windowMinutesIsAcceptedAsDurationFallback() {
        val windows = classify(
            """{"primary_window":{"window_minutes":10080,"remaining_percentage":63}}""",
        )

        assertNull(windows.fiveHour)
        assertEquals(63, windows.weekly?.remaining)
    }

    @Test
    fun unknownSingleWindowIsNotGuessedFromPrimaryPosition() {
        val windows = classify(
            """{"primary_window":{"used_percent":4,"reset_at":1784547600}}""",
        )

        assertNull(windows.fiveHour)
        assertNull(windows.weekly)
    }

    @Test
    fun invalidOrOutOfRangePercentagesDoNotLeakIntoUi() {
        val over = classify(
            """{"primary_window":{"limit_window_seconds":604800,"remaining_percentage":120}}""",
        )
        val missing = classify(
            """{"primary_window":{"limit_window_seconds":604800}}""",
        )

        assertEquals(100, over.weekly?.remaining)
        assertNull(missing.weekly)
    }

    private fun classify(raw: String) = QuotaWindowClassifier.classify(JSONObject(raw))
}
