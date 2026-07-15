package org.orynnx.codexquota

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaAppWidgetPresenterTest {
    @Test
    fun weeklyOnlyNeverInventsFiveHourSecondary() {
        val presentation = QuotaWidgetPresenter.present(
            QuotaState(weeklyRemaining = 64, weeklyReset = "07-20 09:00", weeklyResetAtEpoch = 1_700_000_000L, health = QuotaHealth.FRESH),
            compact = false,
        )

        assertEquals(WidgetWindow.WEEKLY, presentation.primaryWindow)
        assertEquals(64, presentation.primaryRemaining)
        assertEquals(1_700_000_000L, presentation.primaryResetAtEpoch)
        assertFalse(presentation.showFiveHourSecondary)
    }

    @Test
    fun dualWindowUsesSecondWindowOnlyAtMediumWidth() {
        val state = QuotaState(fiveHourRemaining = 82, weeklyRemaining = 64, health = QuotaHealth.FRESH)

        val compact = QuotaWidgetPresenter.present(state, compact = true)
        val medium = QuotaWidgetPresenter.present(state, compact = false)

        assertEquals(WidgetWindow.WEEKLY, compact.primaryWindow)
        assertFalse(compact.showFiveHourSecondary)
        assertTrue(medium.showFiveHourSecondary)
        assertEquals(82, medium.fiveHourRemaining)
    }

    @Test
    fun signedOutMapsToExplicitEmptyCallToActionState() {
        val presentation = QuotaWidgetPresenter.present(QuotaState(), compact = true)

        assertEquals(WidgetWindow.NONE, presentation.primaryWindow)
        assertEquals(QuotaHealth.SIGNED_OUT, presentation.health)
        assertFalse(presentation.showFiveHourSecondary)
    }

    @Test
    fun authorizationRequiredKeepsHealthDistinctFromSignedOut() {
        val presentation = QuotaWidgetPresenter.present(
            QuotaState(status = "Authorization required", health = QuotaHealth.AUTH_REQUIRED),
            compact = false,
        )

        assertEquals(WidgetWindow.NONE, presentation.primaryWindow)
        assertEquals(QuotaHealth.AUTH_REQUIRED, presentation.health)
        assertFalse(presentation.showFiveHourSecondary)
    }

    @Test
    fun widthPolicySelectsCompactAndMediumAtDocumentedBoundary() {
        assertTrue(QuotaWidgetPresenter.isCompact(279))
        assertFalse(QuotaWidgetPresenter.isCompact(280))
    }
}
