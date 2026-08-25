package com.nickwoluff.credex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RearDisplayPayloadTest {
    @Test
    fun codexSelectionHidesBalanceServices() {
        val quota = QuotaState(weeklyRemaining = 64, weeklyReset = "09-01 00:00")
        val balance = BalanceService(id = "deepseek", name = "DeepSeek", endpoint = "")

        val payload = rearDisplayPayload(quota, RearDisplayPreferences.CODEX_ID, listOf(balance))

        assertEquals(quota, payload.quota)
        assertTrue(payload.balances.isEmpty())
        assertEquals("codex", payload.sourceId)
        assertEquals("codex", payload.sourceKind)
        assertEquals("OpenAI Codex", payload.sourceName)
        assertEquals("signed_out", payload.sourceHealth)
    }

    @Test
    fun balanceSelectionHidesCodexQuota() {
        val quota = QuotaState(weeklyRemaining = 64, weeklyReset = "09-01 00:00")
        val balance = BalanceService(
            id = "deepseek",
            name = "DeepSeek",
            endpoint = "",
            status = "已连接",
            updatedAt = "12:30",
        )

        val payload = rearDisplayPayload(quota, balance.id, listOf(balance))

        assertEquals(-1, payload.quota.fiveHourRemaining)
        assertEquals(-1, payload.quota.weeklyRemaining)
        assertEquals("已连接", payload.quota.status)
        assertEquals("12:30", payload.quota.updatedAt)
        assertEquals(listOf(balance), payload.balances)
        assertEquals("deepseek", payload.sourceId)
        assertEquals("balance", payload.sourceKind)
        assertEquals("DeepSeek", payload.sourceName)
        assertEquals("not_connected", payload.sourceHealth)
    }
}
