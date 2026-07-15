package org.orynnx.codexquota

import org.junit.Assert.assertEquals
import org.junit.Test

class QuotaResetTextTest {
    @Test
    fun appIncludesAbsoluteResetAndCountdownWhenTimestampIsKnown() {
        val now = 1_700_000_000L
        val reset = now + (6 * 24 * 60 + 14 * 60 + 22) * 60L

        assertEquals(
            "07-21 16:44 · 于6天14小时22分钟后更新",
            QuotaResetText.app("07-21 16:44", reset, now),
        )
    }

    @Test
    fun compactWidgetDropsAbsoluteTimestampToKeepOneLineShort() {
        val now = 1_700_000_000L
        val reset = now + (6 * 24 * 60 + 14 * 60 + 22) * 60L

        assertEquals(
            "6天14小时后更新",
            QuotaResetText.widgetCompact("07-21 16:44", reset, now),
        )
    }

    @Test
    fun mediumStatusKeepsThePrefixForTheRelativeCountdown() {
        val now = 1_700_000_000L
        val reset = now + 3 * 60L + 10

        assertEquals("于4分钟后更新", QuotaResetText.widgetStatus(reset, now))
    }

    @Test
    fun expiredOrLegacyResetKeepsOriginalText() {
        assertEquals("07-21 16:44", QuotaResetText.app("07-21 16:44", 0L, 1_700_000_000L))
        assertEquals("07-21 16:44", QuotaResetText.widgetCompact("07-21 16:44", 1_699_999_000L, 1_700_000_000L))
    }
}
