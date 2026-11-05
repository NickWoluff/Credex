package com.nickwoluff.credex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class QuotaBalanceDisplayTest {
    private val openCodeGo = BalanceService(
        id = "opencode-go",
        name = "OpenCode Go",
        endpoint = "https://opencode.ai",
        authMode = BalanceAuthMode.OPENCODE_GO,
        balance = "98",
        currency = "QUOTA",
        displayKind = BalanceDisplayKind.TOKEN_PLAN,
        used = "2",
        total = "100",
        quotaWindows = listOf(
            ServiceQuotaWindow(id = "rolling", label = "5 小时", used = "2", total = "100"),
        ),
    )

    @Test
    fun usedQuotaTextAndProgressUseTheConfiguredValue() {
        assertEquals("2%", balanceDisplayValue(openCodeGo))
        assertEquals(0, BigDecimal("0.02").compareTo(balanceDisplayRatio(openCodeGo)))
        assertEquals("5 小时 · 已用 2%", compactQuotaWindowSubtext(openCodeGo))
    }

    @Test
    fun remainingQuotaTextAndProgressFollowTheDisplayPreference() {
        val remaining = openCodeGo.copy(tokenPlanDisplay = TokenPlanDisplay.REMAINING)
        assertEquals("98%", balanceDisplayValue(remaining))
        assertEquals(0, BigDecimal("0.98").compareTo(balanceDisplayRatio(remaining)))
        assertEquals("5 小时 · 剩余 98%", compactQuotaWindowSubtext(remaining))
    }

    @Test
    fun amountServicesDoNotUseQuotaWindowProgressText() {
        val balance = BalanceService(
            id = "balance",
            name = "知遥API",
            endpoint = "https://zhiyaoapi.top",
            balance = "1.20",
            currency = "USD",
        )

        assertNull(balanceDisplayRatio(balance))
        assertNull(compactQuotaWindowSubtext(balance))
    }
}
