package com.nickwoluff.credex

import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 自定义接口按“请求路径格式”分类，而不是为每个中转站编写一整套独立适配器。
 * 根地址只负责提供 origin/base path，具体请求后缀由对应分类决定。
 */
internal enum class CustomEndpointContract(
    val storageKey: String,
    val displayName: String,
) {
    STANDARD_EMAIL_PASSWORD(
        storageKey = "standard_email_password",
        displayName = "标准邮箱密码接口",
    ),
    USERNAME_PASSWORD(
        storageKey = "username_password",
        displayName = "用户名密码接口",
    ),
    NEW_API(
        storageKey = "new_api",
        displayName = "New API 接口",
    ),
}

internal enum class CustomEndpointPasswordMode {
    PLAIN_TEXT,
    NEW_API_COMPATIBLE,
}

internal enum class CustomEndpointBalanceMode {
    GENERIC_BALANCE_FIELD,
    NEW_API_QUOTA,
}

internal data class CustomEndpointAdapter(
    val contract: CustomEndpointContract,
    val loginSuffix: String,
    val refreshSuffix: String?,
    val profileSuffix: String,
    val accountField: String,
    val passwordMode: CustomEndpointPasswordMode,
    val balanceMode: CustomEndpointBalanceMode,
)

internal data class CustomEndpointBalance(
    val value: String,
    val currency: String,
)

internal data class CustomEndpointSelection(
    val adapters: List<CustomEndpointAdapter>,
    val isKnownRoot: Boolean,
)

internal class CustomEndpointAdaptationException(
    val endpoint: String,
    attemptedContracts: List<CustomEndpointContract>,
    cause: Throwable?,
) : Exception(
    buildString {
        append("已尝试")
        append(attemptedContracts.joinToString("、") { it.displayName })
        append("，均未返回可解析的余额。请检查 API 根地址是否填写正确；若确认无误，可前往 GitHub 提交适配申请。")
    },
    cause,
)

internal class CustomEndpointAuthenticationException(
    cause: Throwable?,
) : Exception(
    "账号或密码错误。请检查后重试。",
    cause,
)

internal fun isCustomEndpointAuthenticationFailure(error: Throwable): Boolean {
    var current: Throwable? = error
    while (current != null) {
        if (current is BalanceHttpException && current.statusCode in setOf(401, 403)) return true
        if (customEndpointMessageIndicatesAuthentication(current.message.orEmpty())) return true
        current = current.cause
    }
    return false
}

internal fun customEndpointMessageIndicatesAuthentication(message: String): Boolean {
    val normalized = message.trim().lowercase()
    if (normalized.isBlank()) return false
    return listOf(
        "incorrect username or password",
        "invalid username or password",
        "invalid credentials",
        "password is incorrect",
        "用户名或密码",
        "帐号或密码",
        "账号或密码",
        "密码错误",
        "用户名错误",
        "用户不存在",
        "账号不存在",
        "帐号不存在",
        "认证失败",
        "账号验证失败",
        "帐号验证失败",
        "封禁",
        "banned",
    ).any(normalized::contains)
}

internal object CustomEndpointCatalog {
    const val DOCUMENTATION_URL =
        "https://github.com/NickWoluff/Credex/blob/main/doc/custom-endpoint-adapters.md"
    const val NEW_ISSUE_URL = "https://github.com/NickWoluff/Credex/issues/new"

    private val standardEmailPassword = CustomEndpointAdapter(
        contract = CustomEndpointContract.STANDARD_EMAIL_PASSWORD,
        loginSuffix = "/auth/login",
        refreshSuffix = "/auth/refresh",
        profileSuffix = "/user/profile",
        accountField = "email",
        passwordMode = CustomEndpointPasswordMode.PLAIN_TEXT,
        balanceMode = CustomEndpointBalanceMode.GENERIC_BALANCE_FIELD,
    )

    private val usernamePassword = CustomEndpointAdapter(
        contract = CustomEndpointContract.USERNAME_PASSWORD,
        loginSuffix = "/user/login",
        refreshSuffix = null,
        profileSuffix = "/user/me?include_statistics=false",
        accountField = "username",
        passwordMode = CustomEndpointPasswordMode.PLAIN_TEXT,
        balanceMode = CustomEndpointBalanceMode.GENERIC_BALANCE_FIELD,
    )

    private val newApi = CustomEndpointAdapter(
        contract = CustomEndpointContract.NEW_API,
        loginSuffix = "/api/user/login",
        refreshSuffix = null,
        profileSuffix = "/api/user/self",
        accountField = "username",
        passwordMode = CustomEndpointPasswordMode.NEW_API_COMPATIBLE,
        balanceMode = CustomEndpointBalanceMode.NEW_API_QUOTA,
    )

    private val adaptersByContract = listOf(standardEmailPassword, usernamePassword, newApi)
        .associateBy { it.contract }

    /**
     * 已确认根地址只使用其固定分类；未知根地址按此顺序探测。
     * 新增中转站时通常只需在此登记根地址和分类，不需要复制请求实现。
     */
    private val fallbackOrder = listOf(
        standardEmailPassword,
        usernamePassword,
        newApi,
    )

    private val knownRoots = mapOf(
        "https://ai.091233.xyz/api/v1" to CustomEndpointContract.STANDARD_EMAIL_PASSWORD,
        "https://zhiyaoapi.top" to CustomEndpointContract.USERNAME_PASSWORD,
        "https://yujianwudi.top" to CustomEndpointContract.NEW_API,
    )

    fun selection(endpoint: String, storedContract: String = ""): CustomEndpointSelection {
        contractFromStorage(storedContract)?.let { contract ->
            return CustomEndpointSelection(
                adapters = listOf(requireNotNull(adaptersByContract[contract])),
                isKnownRoot = true,
            )
        }
        val knownContract = normalizeRoot(endpoint)?.let(knownRoots::get)
        if (knownContract != null) {
            return CustomEndpointSelection(
                adapters = listOf(requireNotNull(adaptersByContract[knownContract])),
                isKnownRoot = true,
            )
        }
        return CustomEndpointSelection(adapters = fallbackOrder, isKnownRoot = false)
    }

    fun adapterForStoredService(endpoint: String, storedContract: String): CustomEndpointAdapter {
        val selection = selection(endpoint, storedContract)
        return selection.adapters.first()
    }

    fun issueUrl(endpoint: String): String {
        val normalizedEndpoint = endpoint.trim().ifBlank { "未填写" }
        val attemptedContracts = selection(normalizedEndpoint).adapters.map { it.contract }
        val title = "适配自定义接口：${customEndpointLabel(normalizedEndpoint)}"
        val body = """
            请先确认 API 根地址填写正确，再提交此适配申请。

            API 根地址：
            $normalizedEndpoint

            已尝试接口分类：
            ${attemptedContracts.joinToString("、") { it.displayName }}

            请求表现：
            <!-- 请勿填写密码、访问令牌或账号。 -->
            <!-- 可补充登录接口、余额接口和余额字段名称。 -->
        """.trimIndent()
        return "$NEW_ISSUE_URL?title=${urlEncode(title)}&body=${urlEncode(body)}"
    }

    private fun contractFromStorage(value: String): CustomEndpointContract? =
        CustomEndpointContract.entries.firstOrNull { it.storageKey == value }

    /**
     * 仅接受精确的 HTTPS origin + path，避免把凭据发送给名称相似或子路径不同的主机。
     */
    internal fun normalizeRoot(endpoint: String): String? {
        val uri = runCatching { URI(endpoint.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null) {
            return null
        }
        return buildString {
            append("https://")
            append(uri.host.lowercase())
            if (uri.port != -1) {
                append(':')
                append(uri.port)
            }
            append(uri.path.orEmpty().trimEnd('/'))
        }
    }

    private fun customEndpointLabel(endpoint: String): String =
        normalizeRoot(endpoint)?.removePrefix("https://") ?: "未知根地址"

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}

/**
 * New API 返回的 quota 是内部额度单位。按服务器公开的显示配置换算为用户看到的余额。
 */
internal fun parseNewApiQuotaDisplay(
    quota: BigDecimal,
    status: JSONObject,
): CustomEndpointBalance {
    val displayType = status.optString("quota_display_type", "USD").trim().uppercase()
    if (!status.optBoolean("display_in_currency", true) || displayType == "TOKENS") {
        return CustomEndpointBalance(quota.stripTrailingZeros().toPlainString(), "TOKENS")
    }
    val quotaPerUnit = status.optDouble("quota_per_unit", 500_000.0)
        .takeIf { it > 0 }
        ?.toBigDecimal()
        ?: BigDecimal(500_000)
    val exchangeRate = when (displayType) {
        "CNY" -> status.optDouble("usd_exchange_rate", 1.0)
        "CUSTOM" -> status.optDouble("custom_currency_exchange_rate", 1.0)
        else -> 1.0
    }.takeIf { it > 0 }?.toBigDecimal() ?: BigDecimal.ONE
    val value = quota.divide(quotaPerUnit, 10, RoundingMode.HALF_UP).multiply(exchangeRate)
    val currency = when (displayType) {
        "CNY" -> "CNY"
        "CUSTOM" -> status.optString("custom_currency_symbol").trim().ifBlank { "CUSTOM" }
        else -> "USD"
    }
    return CustomEndpointBalance(value.stripTrailingZeros().toPlainString(), currency)
}
