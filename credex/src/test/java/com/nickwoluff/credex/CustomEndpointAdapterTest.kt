package com.nickwoluff.credex

import java.net.URLDecoder
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class CustomEndpointAdapterTest {
    @Test
    fun knownApiRootUsesItsFixedContract() {
        val selection = CustomEndpointCatalog.selection("https://ai.091233.xyz/api/v1")

        assertTrue(selection.isKnownRoot)
        assertEquals(
            listOf(CustomEndpointContract.STANDARD_EMAIL_PASSWORD),
            selection.adapters.map { it.contract },
        )
        assertEquals("/auth/login", selection.adapters.single().loginSuffix)
        assertEquals(
            listOf("/auth/me", "/user/profile"),
            selection.adapters.single().profileSuffixes,
        )
    }

    @Test
    fun knownUsernameRootIgnoresTrailingSlash() {
        val selection = CustomEndpointCatalog.selection("https://zhiyaoapi.top/")

        assertTrue(selection.isKnownRoot)
        assertEquals(
            listOf(CustomEndpointContract.USERNAME_PASSWORD),
            selection.adapters.map { it.contract },
        )
        assertEquals("/user/login", selection.adapters.single().loginSuffix)
    }

    @Test
    fun unknownRootUsesPresetProbeOrder() {
        val selection = CustomEndpointCatalog.selection("https://new.example.com/api/v1")

        assertFalse(selection.isKnownRoot)
        assertEquals(
            listOf(
                CustomEndpointContract.STANDARD_EMAIL_PASSWORD,
                CustomEndpointContract.USERNAME_PASSWORD,
                CustomEndpointContract.NEW_API,
            ),
            selection.adapters.map { it.contract },
        )
    }

    @Test
    fun similarHostAndDifferentKnownPathAreNotTrustedAsKnownRoot() {
        assertFalse(CustomEndpointCatalog.selection("https://zhiyaoapi.top.evil").isKnownRoot)
        assertFalse(CustomEndpointCatalog.selection("https://ai.091233.xyz").isKnownRoot)
        assertFalse(
            CustomEndpointCatalog.selection("https://ai.091233.xyz/api/v1/user/profile").isKnownRoot,
        )
    }

    @Test
    fun storedContractKeepsTheSuccessfulProbeForRefresh() {
        val selection = CustomEndpointCatalog.selection(
            endpoint = "https://new.example.com",
            storedContract = CustomEndpointContract.USERNAME_PASSWORD.storageKey,
        )

        assertTrue(selection.isKnownRoot)
        assertEquals(CustomEndpointContract.USERNAME_PASSWORD, selection.adapters.single().contract)
    }

    @Test
    fun issueUrlIncludesRootButNoCredentialFields() {
        val url = CustomEndpointCatalog.issueUrl("https://new.example.com/api/v1")
        val decoded = URLDecoder.decode(url, Charsets.UTF_8.name())

        assertTrue(url.startsWith(CustomEndpointCatalog.NEW_ISSUE_URL))
        assertTrue(decoded.contains("https://new.example.com/api/v1"))
        assertTrue(decoded.contains("标准邮箱密码接口"))
        assertFalse(decoded.contains("password="))
        assertFalse(decoded.contains("access_token"))
    }

    @Test
    fun knownRootIssueRequestListsOnlyItsAttemptedContract() {
        val decoded = URLDecoder.decode(
            CustomEndpointCatalog.issueUrl("https://zhiyaoapi.top"),
            Charsets.UTF_8.name(),
        )

        assertTrue(decoded.contains("用户名密码接口"))
        assertFalse(decoded.contains("标准邮箱密码接口"))
    }

    @Test
    fun newApiRootUsesEncryptedLoginAndUserSelfContract() {
        val adapter = CustomEndpointCatalog.selection("https://yujianwudi.top/").adapters.single()

        assertEquals(CustomEndpointContract.NEW_API, adapter.contract)
        assertEquals("/api/user/login", adapter.loginSuffix)
        assertEquals("/api/user/self", adapter.profileSuffix)
        assertEquals(CustomEndpointPasswordMode.NEW_API_COMPATIBLE, adapter.passwordMode)
        assertEquals(CustomEndpointBalanceMode.NEW_API_QUOTA, adapter.balanceMode)
    }

    @Test
    fun newApiQuotaUsesConfiguredCurrencyAndExchangeRate() {
        val balance = parseNewApiQuotaDisplay(
            BigDecimal("195000000"),
            JSONObject(
                """{"quota_display_type":"CNY","display_in_currency":true,"quota_per_unit":500000,"usd_exchange_rate":1}""",
            ),
        )

        assertEquals("390", balance.value)
        assertEquals("CNY", balance.currency)
    }

    @Test
    fun newApiTokenDisplayKeepsRawQuota() {
        val balance = parseNewApiQuotaDisplay(
            BigDecimal("123456"),
            JSONObject(
                """{"quota_display_type":"TOKENS","display_in_currency":true,"quota_per_unit":500000}""",
            ),
        )

        assertEquals("123456", balance.value)
        assertEquals("TOKENS", balance.currency)
    }

    @Test
    fun httpAuthenticationRejectionIsNotReportedAsRootAddressFailure() {
        val failure = BalanceHttpException(401, "Unauthorized")

        assertTrue(isCustomEndpointAuthenticationFailure(failure))
    }

    @Test
    fun credentialAndAccountMessagesAreAuthenticationFailures() {
        assertTrue(
            isCustomEndpointAuthenticationFailure(
                IllegalStateException("Username or password is incorrect, or user has been banned"),
            ),
        )
        assertTrue(
            isCustomEndpointAuthenticationFailure(
                IllegalStateException("用户名或密码错误"),
            ),
        )
    }

    @Test
    fun invalidParametersStillIndicateAnAdaptationFailure() {
        assertFalse(
            isCustomEndpointAuthenticationFailure(
                IllegalStateException("Invalid parameters"),
            ),
        )
    }

    @Test
    fun standardLoginResponseCanSupplyBalanceWithoutProfileRequest() {
        val loginPayload = JSONObject(
            """{"access_token":"token","user":{"id":1,"balance":"12.50"}}""",
        )

        assertEquals("12.50", readGenericCustomEndpointBalance(loginPayload))
    }

    @Test
    fun emailPasswordRejectionIsAnAuthenticationFailure() {
        assertTrue(
            isCustomEndpointAuthenticationFailure(
                IllegalStateException("invalid email or password"),
            ),
        )
    }

    @Test
    fun customEndpointFirstPageRequiresNameAndHttpsRoot() {
        assertEquals(
            "请输入服务名称",
            customEndpointEndpointPageError("", "https://example.com"),
        )
        assertEquals(
            "请输入 API 根地址",
            customEndpointEndpointPageError("测试", ""),
        )
        assertEquals(
            "API 根地址必须使用 HTTPS",
            customEndpointEndpointPageError("测试", "http://example.com"),
        )
        assertEquals(
            "请输入有效的 API 根地址",
            customEndpointEndpointPageError("测试", "https://"),
        )
    }

    @Test
    fun customEndpointFirstPageAcceptsValidRoot() {
        assertEquals(
            null,
            customEndpointEndpointPageError("测试", "https://example.com/api/v1"),
        )
    }
}
