package com.nickwoluff.credex

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MimoBalanceTest {
    @Test
    fun parsesRmbBalanceAndGiftBalance() {
        val snapshot = readMimoPayAsYouGo(JSONObject("""{"data":{"balance":"12.50","giftBalance":3.25}}"""))
        assertEquals(BigDecimal("12.50"), snapshot.cash)
        assertEquals(BigDecimal("3.25"), snapshot.gift)
    }

    @Test
    fun parsesTokenPlanUsageAndRemaining() {
        val snapshot = readMimoTokenPlan(
            JSONObject("""{"data":{"planName":"Lite","expireTime":"2026-09-01T00:00:00Z"}}"""),
            JSONObject("""{"data":{"used":25,"limit":100}}"""),
        )
        assertEquals("Lite", snapshot.plan)
        assertEquals(BigDecimal("25"), snapshot.used)
        assertEquals(BigDecimal("75"), snapshot.remaining)
        assertEquals("2026-09-01T00:00:00Z", snapshot.expiresAt)
    }

    @Test
    fun tokenPlanDisplaysUsedPercentByDefaultAndRemainingWhenConfigured() {
        val service = BalanceService(
            id = "test",
            name = "MIMO Token Plan",
            endpoint = "https://platform.xiaomimimo.com",
            authMode = BalanceAuthMode.MIMO_TOKEN_PLAN,
            balance = "75",
            currency = "TOKEN",
            displayKind = BalanceDisplayKind.TOKEN_PLAN,
            used = "25",
            total = "100",
        )
        assertEquals("25%", balanceDisplayValue(service))
        assertEquals("75%", balanceDisplayValue(service.copy(tokenPlanDisplay = TokenPlanDisplay.REMAINING)))
    }

    @Test
    fun missingGiftBalanceIsAllowed() {
        val snapshot = readMimoPayAsYouGo(JSONObject("""{"balance":1}"""))
        assertNull(snapshot.gift)
    }

    @Test
    fun rotatedMimoCookiesReplaceOnlyTheMatchingSessionCookie() {
        val merged = mergeMimoCookieHeader(
            "api-platform_ph=old-ph; api-platform_serviceToken=old-token; api-platform_slh=old-slh; userId=old-user",
            listOf(
                "api-platform_serviceToken=new-token; Path=/; Max-Age=86400; Secure; HttpOnly",
                "userId=new-user; Path=/; Max-Age=86400; Secure",
            ),
        )

        assertEquals(
            "api-platform_ph=old-ph; api-platform_serviceToken=new-token; api-platform_slh=old-slh; userId=new-user",
            merged,
        )
    }

    @Test
    fun recognizesMimoBusinessResponseThatRequiresSessionRefresh() {
        val payload = JSONObject(
            """{"code":401,"loginUrl":"https://account.xiaomi.com/pass/serviceLogin?sid=api-platform"}""",
        )

        assertTrue(isMimoSessionExpiredPayload(payload))
        assertEquals(
            "https://account.xiaomi.com/pass/serviceLogin?sid=api-platform",
            mimoSessionLoginUrl(payload),
        )
        assertFalse(isMimoSessionExpiredPayload(JSONObject("""{"code":50001,"message":"余额服务暂不可用"}""")))
    }

    @Test
    fun acceptsOnlyTrustedMimoSessionRefreshUrls() {
        val signedLoginUrl = "https://account.xiaomi.com/pass/serviceLogin?sid=api-platform"

        assertEquals(signedLoginUrl, validatedMimoSessionRefreshUrl(signedLoginUrl))
        assertEquals(
            "https://platform.xiaomimimo.com/console/balance",
            validatedMimoSessionRefreshUrl("https://example.com/steal-session"),
        )
    }
}
