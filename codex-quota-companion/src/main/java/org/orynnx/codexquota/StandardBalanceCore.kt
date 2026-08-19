package org.orynnx.codexquota

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The health of one user-configured standard balance service. */
enum class BalanceHealth { NOT_CONNECTED, FRESH, CACHED, AUTH_REQUIRED, ERROR }

enum class BalanceAuthMode { EMAIL_PASSWORD, API_KEY, DEEPSEEK_API_KEY, SILICONFLOW_CONSOLE }

/** A display host that can independently opt a balance service in or out. */
enum class BalanceSurface(val shortLabel: String, val label: String) {
    LAUNCHER("主屏", "Android 原生小组件"),
    MAML_DESKTOP("桌面", "小米桌面 MAML"),
    ASSISTANT_REAR("助手", "Assistant 背屏"),
    WALLPAPER_REAR("壁纸", "Wallpaper 背屏"),
}

/** Credential-free presentation data used by the app UI. */
data class BalanceService(
    val id: String,
    val name: String,
    val endpoint: String,
    val authMode: BalanceAuthMode = BalanceAuthMode.EMAIL_PASSWORD,
    val email: String = "",
    val balance: String = "--",
    val currency: String = "USD",
    val detail: String = "",
    val updatedAt: String = "--",
    val status: String = "未登录",
    val health: BalanceHealth = BalanceHealth.NOT_CONNECTED,
    val visible: Boolean = true,
    val displaySurfaces: Set<BalanceSurface> = BalanceSurface.values().toSet(),
    val includeVouchers: Boolean = false,
    val includeGrantedBalance: Boolean = true,
)

/** Display-only preferences. They never stop network refresh or delete credentials. */
object DashboardPreferences {
    private const val PREFS = "quota_display_preferences"
    private const val SHOW_CODEX = "show_codex"
    private const val SHOW_HEALTH = "show_health"

    fun showCodex(context: Context) = prefs(context).getBoolean(SHOW_CODEX, true)
    fun setShowCodex(context: Context, value: Boolean) = prefs(context).edit { putBoolean(SHOW_CODEX, value) }
    fun showHealth(context: Context) = prefs(context).getBoolean(SHOW_HEALTH, true)
    fun setShowHealth(context: Context, value: Boolean) = prefs(context).edit { putBoolean(SHOW_HEALTH, value) }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

private data class StoredBalanceService(
    val id: String,
    val name: String,
    val endpoint: String,
    val authMode: BalanceAuthMode = BalanceAuthMode.EMAIL_PASSWORD,
    val email: String = "",
    val password: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val subjectId: String = "",
    val sessionToken: String = "",
    val expiresAtMillis: Long = 0L,
    val balance: String = "--",
    val currency: String = "USD",
    val detail: String = "",
    val updatedAt: String = "--",
    val lastAttemptAtMillis: Long = 0L,
    val status: String = "未登录",
    val health: BalanceHealth = BalanceHealth.NOT_CONNECTED,
    val visible: Boolean = true,
    val displaySurfaces: Set<BalanceSurface> = BalanceSurface.values().toSet(),
    val includeVouchers: Boolean = false,
    val includeGrantedBalance: Boolean = true,
) {
    fun public() = BalanceService(
        id = id,
        name = name,
        endpoint = endpoint,
        authMode = authMode,
        email = email,
        balance = balance,
        currency = currency,
        detail = detail,
        updatedAt = updatedAt,
        status = status,
        health = health,
        visible = visible,
        displaySurfaces = displaySurfaces,
        includeVouchers = includeVouchers,
        includeGrantedBalance = includeGrantedBalance,
    )

    fun json(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("endpoint", endpoint)
        put("auth_mode", authMode.name)
        put("email", email)
        put("password", if (password.isBlank()) "" else BalanceSecretBox.seal(password))
        put("access_token", if (accessToken.isBlank()) "" else BalanceSecretBox.seal(accessToken))
        put("refresh_token", if (refreshToken.isBlank()) "" else BalanceSecretBox.seal(refreshToken))
        put("subject_id", subjectId)
        put("session_token", if (sessionToken.isBlank()) "" else BalanceSecretBox.seal(sessionToken))
        put("expires_at", expiresAtMillis)
        put("balance", balance)
        put("currency", currency)
        put("detail", detail)
        put("updated_at", updatedAt)
        put("last_attempt_at", lastAttemptAtMillis)
        put("status", status)
        put("health", health.name)
        put("visible", visible)
        put("display_surfaces", JSONArray().apply { displaySurfaces.forEach { put(it.name) } })
        put("include_vouchers", includeVouchers)
        put("include_granted_balance", includeGrantedBalance)
    }

    companion object {
        fun from(json: JSONObject): StoredBalanceService {
            val health = runCatching { BalanceHealth.valueOf(json.optString("health")) }
                .getOrDefault(BalanceHealth.NOT_CONNECTED)
            val authMode = runCatching { BalanceAuthMode.valueOf(json.optString("auth_mode")) }
                .getOrDefault(BalanceAuthMode.EMAIL_PASSWORD)
            return StoredBalanceService(
                id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = json.optString("name", "余额服务"),
                endpoint = json.optString("endpoint"),
                authMode = authMode,
                email = json.optString("email"),
                password = BalanceSecretBox.open(json.optString("password")),
                accessToken = BalanceSecretBox.open(json.optString("access_token")),
                refreshToken = BalanceSecretBox.open(json.optString("refresh_token")),
                subjectId = json.optString("subject_id"),
                sessionToken = BalanceSecretBox.open(json.optString("session_token")),
                expiresAtMillis = json.optLong("expires_at"),
                balance = json.optString("balance", "--"),
                currency = json.optString("currency", "USD"),
                detail = json.optString("detail"),
                updatedAt = json.optString("updated_at", "--"),
                lastAttemptAtMillis = json.optLong("last_attempt_at"),
                status = json.optString("status", "未登录"),
                health = health,
                visible = json.optBoolean("visible", true),
                displaySurfaces = readDisplaySurfaces(json),
                includeVouchers = json.optBoolean("include_vouchers", false),
                includeGrantedBalance = json.optBoolean("include_granted_balance", true),
            )
        }

        private fun readDisplaySurfaces(json: JSONObject): Set<BalanceSurface> {
            // Existing installations predate per-surface settings; keep their
            // services visible everywhere until the user changes a setting.
            if (!json.has("display_surfaces")) return BalanceSurface.values().toSet()
            val array = json.optJSONArray("display_surfaces") ?: return emptySet()
            return (0 until array.length()).mapNotNull { index ->
                runCatching { BalanceSurface.valueOf(array.optString(index)) }.getOrNull()
            }.toSet()
        }
    }
}

private object BalanceSecretBox {
    private const val KEY = "standard_balance_tokens"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(
            KEY,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec) }
            .generateKey()
    }

    fun seal(value: String): String {
        if (value.isBlank()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }

    fun open(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)))
        }.getOrDefault("")
    }
}

private class BalanceHttpException(val statusCode: Int, message: String) : Exception(message)

/**
 * Adapter for the standard balance contract plus SiliconFlow's API-key contract:
 * POST /auth/login, POST /auth/refresh, GET /user/profile, or GET /user/info.
 * Tokens and passwords never leave this repository and are not exposed to UI surfaces.
 */
object StandardBalanceRepository {
    private const val PREFS = "standard_balance_services"
    private const val SERVICES = "services_v1"
    private const val MIN_REFRESH_MILLIS = 60_000L
    private val lock = Any()
    private val clockFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    fun list(context: Context): List<BalanceService> = stored(context).map { it.public() }

    fun forSurface(context: Context, surface: BalanceSurface, limit: Int): List<BalanceService> =
        list(context)
            .filter { it.visible && surface in it.displaySurfaces }
            .take(limit.coerceAtLeast(0))

    fun hasConfiguredService(context: Context): Boolean = stored(context).isNotEmpty()

    fun hasAuthenticatedService(context: Context): Boolean = stored(context).any {
        it.accessToken.isNotBlank() ||
            (it.email.isNotBlank() && it.password.isNotBlank()) ||
            (it.subjectId.isNotBlank() && it.sessionToken.isNotBlank())
    }

    data class Credentials(val account: String, val secret: String)

    fun credentials(context: Context, id: String): Credentials {
        val service = requireStored(context, id)
        return when (service.authMode) {
            BalanceAuthMode.API_KEY -> Credentials("", service.accessToken)
            BalanceAuthMode.DEEPSEEK_API_KEY -> Credentials("", service.accessToken)
            BalanceAuthMode.SILICONFLOW_CONSOLE -> Credentials(service.subjectId, service.sessionToken)
            BalanceAuthMode.EMAIL_PASSWORD -> Credentials(service.email, service.password)
        }
    }

    fun setVisible(context: Context, id: String, visible: Boolean) {
        stored(context).firstOrNull { it.id == id }?.let { replace(context, it.copy(visible = visible)) }
        notifyChanged(context)
    }

    fun setSurfaceEnabled(context: Context, id: String, surface: BalanceSurface, enabled: Boolean) {
        stored(context).firstOrNull { it.id == id }?.let { service ->
            val next = service.displaySurfaces.toMutableSet().apply {
                if (enabled) add(surface) else remove(surface)
            }
            replace(context, service.copy(displaySurfaces = next))
        }
        notifyChanged(context)
    }

    fun move(context: Context, id: String, direction: Int) {
        if (direction == 0) return
        val values = stored(context).toMutableList()
        val index = values.indexOfFirst { it.id == id }
        val target = index + direction
        if (index < 0 || target !in values.indices) return
        val item = values.removeAt(index)
        values.add(target, item)
        saveStored(context, values)
        notifyChanged(context)
    }

    fun saveDefinition(
        context: Context,
        id: String?,
        name: String,
        endpoint: String,
        authMode: BalanceAuthMode = BalanceAuthMode.EMAIL_PASSWORD,
        includeVouchers: Boolean = false,
        includeGrantedBalance: Boolean = true,
    ): String {
        val cleanName = name.trim().ifBlank { throw IllegalArgumentException("请输入服务名称") }
        val cleanEndpoint = normalizeEndpoint(endpoint)
        val current = stored(context).toMutableList()
        val old = id?.let { value -> current.firstOrNull { it.id == value } }
        val serviceId = old?.id ?: UUID.randomUUID().toString()
        val replacement = if (old == null) {
            StoredBalanceService(serviceId, cleanName, cleanEndpoint, authMode = authMode, includeVouchers = includeVouchers, includeGrantedBalance = includeGrantedBalance)
        } else if (old.endpoint != cleanEndpoint || old.authMode != authMode) {
            old.copy(
                name = cleanName,
                endpoint = cleanEndpoint,
                authMode = authMode,
                includeVouchers = includeVouchers,
                includeGrantedBalance = includeGrantedBalance,
                email = "",
                password = "",
                accessToken = "",
                refreshToken = "",
                subjectId = "",
                sessionToken = "",
                expiresAtMillis = 0L,
                balance = "--",
                updatedAt = "--",
                status = "需要重新登录",
                health = BalanceHealth.NOT_CONNECTED,
            )
        } else {
            old.copy(name = cleanName, endpoint = cleanEndpoint, authMode = authMode, includeVouchers = includeVouchers, includeGrantedBalance = includeGrantedBalance)
        }
        current.removeAll { it.id == serviceId }
        current.add(replacement)
        saveStored(context, current)
        notifyChanged(context)
        return serviceId
    }

    fun delete(context: Context, id: String) {
        saveStored(context, stored(context).filterNot { it.id == id })
        notifyChanged(context)
    }

    fun login(context: Context, id: String, account: String, secret: CharArray): BalanceService {
        val service = requireStored(context, id)
        return when (service.authMode) {
            BalanceAuthMode.API_KEY -> loginApiKey(context, service, secret)
            BalanceAuthMode.DEEPSEEK_API_KEY -> loginDeepSeekApiKey(context, service, secret)
            BalanceAuthMode.SILICONFLOW_CONSOLE -> loginSiliconFlowConsole(context, service, account, secret)
            BalanceAuthMode.EMAIL_PASSWORD -> loginEmailPassword(context, service, account, secret)
        }
    }

    /** Connect a console service with credentials captured by the embedded browser. */
    fun connectSiliconFlowConsole(
        context: Context,
        id: String,
        subjectId: String,
        sessionToken: String,
    ): BalanceService {
        val secret = sessionToken.toCharArray()
        return try {
            val service = requireStored(context, id)
            check(service.authMode == BalanceAuthMode.SILICONFLOW_CONSOLE) { "不是 SiliconFlow 控制台服务" }
            login(context, id, subjectId, secret)
        } finally {
            secret.fill('\u0000')
        }
    }

    private fun loginEmailPassword(
        context: Context,
        service: StoredBalanceService,
        email: String,
        password: CharArray,
    ): BalanceService {
        val passwordText = String(password)
        val cleanEmail = email.trim()
        // Persist credentials encrypted before the request so a failed login can be retried
        // without asking the user to type them again.
        val withCredentials = service.copy(email = cleanEmail, password = passwordText)
        replace(context, withCredentials)
        val body = JSONObject().put("email", cleanEmail).put("password", passwordText)
        val payload = requestJson(join(service.endpoint, "/auth/login"), "POST", body, null)
        val data = unwrap(payload)
        val access = data.optString("access_token")
        check(access.isNotBlank()) { "登录响应中没有 access_token" }
        val refresh = data.optString("refresh_token")
        val expiresIn = data.optLong("expires_in", 86_400L).coerceAtLeast(60L)
        val user = data.optJSONObject("user")
        val balance = readBalance(data) ?: readBalance(user)
        val now = System.currentTimeMillis()
        val next = service.copy(
            email = cleanEmail,
            password = passwordText,
            accessToken = access,
            refreshToken = refresh,
            expiresAtMillis = now + expiresIn * 1_000L,
            balance = balance ?: service.balance,
            updatedAt = clock(),
            lastAttemptAtMillis = now,
            status = "已连接",
            health = BalanceHealth.FRESH,
        )
        replace(context, next)
        QuotaRefreshScheduler.schedule(context)
        notifyChanged(context)
        return next.public()
    }

    private fun loginApiKey(
        context: Context,
        service: StoredBalanceService,
        apiKey: CharArray,
    ): BalanceService {
        val keyText = String(apiKey)
        check(keyText.isNotBlank()) { "请输入 SiliconFlow API Key" }
        val withKey = service.copy(accessToken = keyText, refreshToken = "", expiresAtMillis = Long.MAX_VALUE)
        replace(context, withKey)
        val data = siliconFlowData(requestJson(siliconFlowInfoUrl(service.endpoint), "GET", null, keyText))
        val balance = readSiliconFlowTotalBalance(data) ?: error("SiliconFlow 响应中没有 totalBalance")
        val next = withKey.copy(
            balance = formatBalance(balance),
            currency = "CNY",
            detail = "",
            updatedAt = clock(),
            lastAttemptAtMillis = System.currentTimeMillis(),
            status = "已连接",
            health = BalanceHealth.FRESH,
        )
        replace(context, next)
        QuotaRefreshScheduler.schedule(context)
        notifyChanged(context)
        return next.public()
    }

    private fun loginDeepSeekApiKey(
        context: Context,
        service: StoredBalanceService,
        apiKey: CharArray,
    ): BalanceService {
        val keyText = String(apiKey)
        check(keyText.isNotBlank()) { "请输入 DeepSeek API Key" }
        val withKey = service.copy(accessToken = keyText, refreshToken = "", expiresAtMillis = Long.MAX_VALUE)
        replace(context, withKey)
        val snapshot = readDeepSeekBalance(
            requestJson(deepSeekBalanceUrl(service.endpoint), "GET", null, keyText),
            withKey.includeGrantedBalance,
        )
        val next = withKey.copy(
            balance = formatBalance(snapshot.total),
            currency = snapshot.currency,
            detail = snapshot.detail,
            updatedAt = clock(),
            lastAttemptAtMillis = System.currentTimeMillis(),
            status = if (snapshot.available) "已连接" else "余额不足",
            health = BalanceHealth.FRESH,
        )
        replace(context, next)
        QuotaRefreshScheduler.schedule(context)
        notifyChanged(context)
        return next.public()
    }

    private fun loginSiliconFlowConsole(
        context: Context,
        service: StoredBalanceService,
        subjectId: String,
        sessionToken: CharArray,
    ): BalanceService {
        val cleanSubjectId = subjectId.trim()
        val cleanSessionToken = normalizeSiliconFlowSessionToken(String(sessionToken))
        check(cleanSubjectId.isNotBlank()) { "请输入 SiliconFlow Subject ID" }
        check(cleanSessionToken.isNotBlank()) { "请输入 SiliconFlow session-token" }
        val withCredentials = service.copy(
            subjectId = cleanSubjectId,
            sessionToken = cleanSessionToken,
            accessToken = "",
            refreshToken = "",
            expiresAtMillis = Long.MAX_VALUE,
        )
        replace(context, withCredentials)
        val data = siliconFlowConsoleData(
            requestJson(
                siliconFlowConsoleProfileUrl(service.endpoint),
                "GET",
                null,
                null,
                siliconFlowConsoleHeaders(cleanSubjectId, cleanSessionToken),
            ),
        )
        val cashBalance = readSiliconFlowConsoleBalance(data)
            ?: error("SiliconFlow 控制台响应中没有 financialInfo.available")
        val voucherBalance = if (withCredentials.includeVouchers) {
            readSiliconFlowVoucherBalance(
                siliconFlowConsoleData(
                    requestJson(
                        siliconFlowConsoleWalletsUrl(service.endpoint),
                        "GET",
                        null,
                        null,
                        siliconFlowConsoleHeaders(cleanSubjectId, cleanSessionToken),
                    ),
                ),
            )
        } else {
            java.math.BigDecimal.ZERO
        }
        val next = withCredentials.copy(
            balance = formatBalance(cashBalance.add(voucherBalance)),
            currency = "¥",
            updatedAt = clock(),
            lastAttemptAtMillis = System.currentTimeMillis(),
            status = consoleStatus(withCredentials.includeVouchers),
            health = BalanceHealth.FRESH,
        )
        replace(context, next)
        QuotaRefreshScheduler.schedule(context)
        notifyChanged(context)
        return next.public()
    }

    fun refresh(context: Context, id: String, force: Boolean = false): BalanceService {
        val initial = requireStored(context, id)
        when (initial.authMode) {
            BalanceAuthMode.API_KEY -> {
                if (initial.accessToken.isBlank()) return initial.public()
                return refreshApiKey(context, initial, force)
            }
            BalanceAuthMode.DEEPSEEK_API_KEY -> {
                if (initial.accessToken.isBlank()) return initial.public()
                return refreshDeepSeekApiKey(context, initial, force)
            }
            BalanceAuthMode.SILICONFLOW_CONSOLE -> {
                if (initial.subjectId.isBlank() || initial.sessionToken.isBlank()) return initial.public()
                return refreshSiliconFlowConsole(context, initial, force)
            }
            BalanceAuthMode.EMAIL_PASSWORD -> Unit
        }
        if (initial.accessToken.isBlank()) {
            if (initial.email.isBlank() || initial.password.isBlank()) return initial.public()
            return relogin(context, id, initial).public()
        }
        val now = System.currentTimeMillis()
        if (!force && now - initial.lastAttemptAtMillis < MIN_REFRESH_MILLIS) return initial.public()
        replace(context, initial.copy(lastAttemptAtMillis = now))
        return try {
            var current = requireStored(context, id)
            if (current.expiresAtMillis in 1..(now + 60_000L)) current = refreshTokens(context, current)
            var payload = try {
                requestJson(join(current.endpoint, "/user/profile"), "GET", null, current.accessToken)
            } catch (error: BalanceHttpException) {
                if (error.statusCode != 401) throw error
                current = if (current.refreshToken.isNotBlank()) {
                    runCatching { refreshTokens(context, current) }.getOrElse {
                        if (current.email.isNotBlank() && current.password.isNotBlank()) relogin(context, id, current) else throw it
                    }
                } else if (current.email.isNotBlank() && current.password.isNotBlank()) {
                    relogin(context, id, current)
                } else {
                    throw error
                }
                requestJson(join(current.endpoint, "/user/profile"), "GET", null, current.accessToken)
            }
            val profile = unwrap(payload)
            val balance = readBalance(profile) ?: readBalance(profile.optJSONObject("user"))
                ?: error("余额响应中没有 balance")
            val success = current.copy(
                balance = formatBalance(balance),
                updatedAt = clock(),
                status = "已连接",
                health = BalanceHealth.FRESH,
            )
            replace(context, success)
            notifyChanged(context)
            success.public()
        } catch (error: Exception) {
            val latest = requireStored(context, id)
            val authRequired = error is BalanceHttpException && error.statusCode == 401
            val failed = latest.copy(
                status = if (authRequired) "需要重新登录" else "暂时无法更新",
                health = if (authRequired) BalanceHealth.AUTH_REQUIRED else if (latest.balance != "--") BalanceHealth.CACHED else BalanceHealth.ERROR,
            )
            replace(context, failed)
            notifyChanged(context)
            failed.public()
        }
    }

    fun refreshAll(context: Context, force: Boolean = false) {
        list(context).forEach { runCatching { refresh(context, it.id, force) } }
    }

    private fun refreshApiKey(context: Context, initial: StoredBalanceService, force: Boolean): BalanceService {
        val now = System.currentTimeMillis()
        if (!force && now - initial.lastAttemptAtMillis < MIN_REFRESH_MILLIS) return initial.public()
        replace(context, initial.copy(lastAttemptAtMillis = now))
        return try {
            val current = requireStored(context, initial.id)
            val data = siliconFlowData(requestJson(siliconFlowInfoUrl(current.endpoint), "GET", null, current.accessToken))
            val balance = readSiliconFlowTotalBalance(data) ?: error("SiliconFlow 响应中没有 totalBalance")
            val success = current.copy(
                balance = formatBalance(balance),
                currency = "CNY",
                updatedAt = clock(),
                status = "已连接",
                health = BalanceHealth.FRESH,
            )
            replace(context, success)
            notifyChanged(context)
            success.public()
        } catch (error: Exception) {
            val latest = requireStored(context, initial.id)
            val authRequired = error is BalanceHttpException && error.statusCode == 401
            val failed = latest.copy(
                status = if (authRequired) "API Key 无效" else "暂时无法更新",
                health = if (authRequired) BalanceHealth.AUTH_REQUIRED else if (latest.balance != "--") BalanceHealth.CACHED else BalanceHealth.ERROR,
            )
            replace(context, failed)
            notifyChanged(context)
            failed.public()
        }
    }

    private fun refreshDeepSeekApiKey(context: Context, initial: StoredBalanceService, force: Boolean): BalanceService {
        val now = System.currentTimeMillis()
        if (!force && now - initial.lastAttemptAtMillis < MIN_REFRESH_MILLIS) return initial.public()
        replace(context, initial.copy(lastAttemptAtMillis = now))
        return try {
            val current = requireStored(context, initial.id)
            val snapshot = readDeepSeekBalance(
                requestJson(deepSeekBalanceUrl(current.endpoint), "GET", null, current.accessToken),
                current.includeGrantedBalance,
            )
            val success = current.copy(
                balance = formatBalance(snapshot.total),
                currency = snapshot.currency,
                detail = snapshot.detail,
                updatedAt = clock(),
                status = if (snapshot.available) "已连接" else "余额不足",
                health = BalanceHealth.FRESH,
            )
            replace(context, success)
            notifyChanged(context)
            success.public()
        } catch (error: Exception) {
            val latest = requireStored(context, initial.id)
            val authRequired = error is BalanceHttpException && error.statusCode in setOf(401, 403)
            val failed = latest.copy(
                status = if (authRequired) "API Key 无效" else "暂时无法更新",
                health = if (authRequired) BalanceHealth.AUTH_REQUIRED else if (latest.balance != "--") BalanceHealth.CACHED else BalanceHealth.ERROR,
            )
            replace(context, failed)
            notifyChanged(context)
            failed.public()
        }
    }

    private fun refreshSiliconFlowConsole(
        context: Context,
        initial: StoredBalanceService,
        force: Boolean,
    ): BalanceService {
        val now = System.currentTimeMillis()
        if (!force && now - initial.lastAttemptAtMillis < MIN_REFRESH_MILLIS) return initial.public()
        replace(context, initial.copy(lastAttemptAtMillis = now))
        return try {
            val current = requireStored(context, initial.id)
            val data = siliconFlowConsoleData(
                requestJson(
                    siliconFlowConsoleProfileUrl(current.endpoint),
                    "GET",
                    null,
                    null,
                    siliconFlowConsoleHeaders(current.subjectId, current.sessionToken),
                ),
            )
            val cashBalance = readSiliconFlowConsoleBalance(data)
                ?: error("SiliconFlow 控制台响应中没有 financialInfo.available")
            val voucherBalance = if (current.includeVouchers) {
                readSiliconFlowVoucherBalance(
                    siliconFlowConsoleData(
                        requestJson(
                            siliconFlowConsoleWalletsUrl(current.endpoint),
                            "GET",
                            null,
                            null,
                            siliconFlowConsoleHeaders(current.subjectId, current.sessionToken),
                        ),
                    ),
                )
            } else {
                java.math.BigDecimal.ZERO
            }
            val success = current.copy(
                balance = formatBalance(cashBalance.add(voucherBalance)),
                currency = "¥",
                updatedAt = clock(),
                status = consoleStatus(current.includeVouchers),
                health = BalanceHealth.FRESH,
            )
            replace(context, success)
            notifyChanged(context)
            success.public()
        } catch (error: Exception) {
            val latest = requireStored(context, initial.id)
            val authRequired = error is BalanceHttpException && error.statusCode in setOf(401, 403)
            val failed = latest.copy(
                status = if (authRequired) "控制台会话已过期" else "暂时无法更新",
                health = if (authRequired) BalanceHealth.AUTH_REQUIRED else if (latest.balance != "--") BalanceHealth.CACHED else BalanceHealth.ERROR,
            )
            replace(context, failed)
            notifyChanged(context)
            failed.public()
        }
    }

    private fun refreshTokens(context: Context, service: StoredBalanceService): StoredBalanceService {
        val payload = requestJson(
            join(service.endpoint, "/auth/refresh"),
            "POST",
            JSONObject().put("refresh_token", service.refreshToken),
            null,
        )
        val data = unwrap(payload)
        val access = data.optString("access_token")
        check(access.isNotBlank()) { "刷新响应中没有 access_token" }
        val refresh = data.optString("refresh_token").ifBlank { service.refreshToken }
        val expiresIn = data.optLong("expires_in", 86_400L).coerceAtLeast(60L)
        return service.copy(
            accessToken = access,
            refreshToken = refresh,
            expiresAtMillis = System.currentTimeMillis() + expiresIn * 1_000L,
        ).also { replace(context, it) }
    }

    private fun relogin(context: Context, id: String, service: StoredBalanceService): StoredBalanceService {
        val password = service.password.toCharArray()
        return try {
            login(context, id, service.email, password)
            requireStored(context, id)
        } finally {
            password.fill('\u0000')
        }
    }

    private fun requestJson(
        url: String,
        method: String,
        body: JSONObject?,
        token: String?,
        headers: Map<String, String> = emptyMap(),
    ): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "OuterView-Quota/1.0")
        if (token.orEmpty().isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $token")
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val message = runCatching { JSONObject(raw).optString("message") }.getOrNull().orEmpty()
            throw BalanceHttpException(code, message.ifBlank { "HTTP $code" })
        }
        return JSONObject(raw)
    }

    private fun unwrap(payload: JSONObject): JSONObject {
        val code = payload.optInt("code", 0)
        check(code == 0) { payload.optString("message", "请求失败") }
        return payload.optJSONObject("data") ?: payload
    }

    private fun siliconFlowData(payload: JSONObject): JSONObject {
        val code = payload.optInt("code", 20000)
        check(code == 20000 || code == 0) { payload.optString("message", "SiliconFlow 请求失败") }
        check(payload.optBoolean("status", true)) { payload.optString("message", "SiliconFlow 请求失败") }
        return payload.optJSONObject("data") ?: JSONObject()
    }

    private fun siliconFlowConsoleData(payload: JSONObject): JSONObject {
        val code = payload.optInt("code", 0)
        check(code == 0 || code == 20000) { payload.optString("message", "SiliconFlow 控制台请求失败") }
        check(payload.optBoolean("status", true)) { payload.optString("message", "SiliconFlow 控制台请求失败") }
        return payload.optJSONObject("data") ?: payload
    }

    private data class DeepSeekBalanceSnapshot(
        val total: java.math.BigDecimal,
        val currency: String,
        val detail: String,
        val available: Boolean,
    )

    private fun readDeepSeekBalance(payload: JSONObject, includeGrantedBalance: Boolean): DeepSeekBalanceSnapshot {
        val infos = payload.optJSONArray("balance_infos") ?: JSONArray()
        check(infos.length() > 0) { "DeepSeek 响应中没有 balance_infos" }
        val snapshots = (0 until infos.length()).mapNotNull { index ->
            val info = infos.optJSONObject(index) ?: return@mapNotNull null
            val currency = info.optString("currency", "CNY").ifBlank { "CNY" }
            val total = amountDecimal(info.opt("total_balance")) ?: return@mapNotNull null
            val granted = amountDecimal(info.opt("granted_balance"))
            val toppedUp = amountDecimal(info.opt("topped_up_balance"))
            val paid = toppedUp ?: total.subtract(granted ?: java.math.BigDecimal.ZERO).max(java.math.BigDecimal.ZERO)
            val displayedTotal = if (includeGrantedBalance) total else paid
            val parts = buildList {
                if (includeGrantedBalance) granted?.let { add("赠送 ${currency} ${formatBalance(it)}") }
                toppedUp?.let { add("充值 ${currency} ${formatBalance(it)}") }
            }
            DeepSeekBalanceSnapshot(displayedTotal, currency, parts.joinToString(" · "), true)
        }
        check(snapshots.isNotEmpty()) { "DeepSeek 响应中没有有效余额" }
        val preferred = snapshots.firstOrNull { it.currency.equals("CNY", ignoreCase = true) } ?: snapshots.first()
        val detail = if (snapshots.size == 1) preferred.detail else {
            snapshots.joinToString(" · ") { "${it.currency} ${formatBalance(it.total)}" }
        }
        return preferred.copy(
            detail = detail,
            available = payload.optBoolean("is_available", true),
        )
    }

    /**
     * SiliconFlow 的主流余额读取方式是 data.totalBalance。
     * data.balance 仅作为旧响应或兼容代理的回退，不把 chargeBalance 当作总余额。
     */
    private fun readSiliconFlowTotalBalance(data: JSONObject): String? {
        return when {
            data.has("totalBalance") && !data.isNull("totalBalance") -> data.optString("totalBalance")
            data.has("balance") && !data.isNull("balance") -> data.optString("balance")
            else -> null
        }?.takeIf { it.isNotBlank() }
    }

    private fun readSiliconFlowConsoleBalance(data: JSONObject): java.math.BigDecimal? {
        val financialInfo = data.optJSONObject("financialInfo")
        val raw = listOf(
            financialInfo?.opt("available"),
            data.opt("available"),
            data.opt("balance"),
        ).asSequence()
            .mapNotNull(::amountDecimal)
            .firstOrNull()
        return raw?.let(::siliconFlowConsoleCashToYuan)
    }

    private fun readSiliconFlowVoucherBalance(data: JSONObject): java.math.BigDecimal {
        val wallets = data.optJSONArray("wallets") ?: data.optJSONArray("items") ?: JSONArray()
        val divisor = java.math.BigDecimal("1000000000000")
        var total = java.math.BigDecimal.ZERO
        for (index in 0 until wallets.length()) {
            val wallet = wallets.optJSONObject(index) ?: continue
            val balance = amountDecimal(wallet.opt("balance")) ?: continue
            if (balance.signum() > 0) total = total.add(balance.divide(divisor))
        }
        return total
    }

    private fun amountDecimal(value: Any?): java.math.BigDecimal? = when (value) {
        is Number -> value.toString().toBigDecimalOrNull()
        is String -> value.trim().toBigDecimalOrNull()
        else -> null
    }

    private fun readBalance(json: JSONObject?): String? {
        if (json == null) return null
        val value = when {
            json.has("balance") && !json.isNull("balance") -> json.opt("balance")
            json.has("available_balance") && !json.isNull("available_balance") -> json.opt("available_balance")
            else -> null
        } ?: return null
        return when (value) {
            is Number -> formatBalance(value.toString())
            is String -> value.trim().takeIf { it.isNotBlank() }
            else -> null
        }
    }

    private fun formatBalance(value: String): String = runCatching {
        java.math.BigDecimal(value).stripTrailingZeros().toPlainString()
    }.getOrDefault(value)

    private fun formatBalance(value: java.math.BigDecimal): String =
        value.stripTrailingZeros().toPlainString()

    private fun normalizeEndpoint(raw: String): String {
        val endpoint = raw.trim().trimEnd('/')
        require(endpoint.startsWith("https://")) { "Endpoint 必须使用 HTTPS" }
        require(endpoint.length > "https://".length) { "请输入有效的 Endpoint" }
        return endpoint
    }

    private fun join(endpoint: String, path: String) = endpoint.trimEnd('/') + "/" + path.trimStart('/')

    /**
     * Keep SiliconFlow's official endpoint compatible with CC Switch:
     * https://api.siliconflow.cn/v1/user/info (or the .com endpoint).
     * A user may still provide a custom HTTPS gateway, which keeps the configured path.
     */
    private fun siliconFlowInfoUrl(endpoint: String): String {
        val normalized = endpoint.trim().trimEnd('/')
        return when {
            normalized.startsWith("https://api.siliconflow.cn", ignoreCase = true) ->
                "https://api.siliconflow.cn/v1/user/info"
            normalized.startsWith("https://api.siliconflow.com", ignoreCase = true) ->
                "https://api.siliconflow.com/v1/user/info"
            else -> join(normalized, "/user/info")
        }
    }

    private fun deepSeekBalanceUrl(endpoint: String): String = join(endpoint.trim().trimEnd('/'), "/user/balance")

    private fun siliconFlowConsoleProfileUrl(endpoint: String): String {
        val normalized = endpoint.trim().trimEnd('/')
        return if (normalized.contains("cloud.siliconflow.cn", ignoreCase = true)) {
            "https://cloud.siliconflow.cn/walletd-server/api/v1/subject/profile/peek"
        } else {
            join(normalized, "/walletd-server/api/v1/subject/profile/peek")
        }
    }

    private fun siliconFlowConsoleWalletsUrl(endpoint: String): String {
        val normalized = endpoint.trim().trimEnd('/')
        val path = "/walletd-server/api/v1/subject/wallets?pageSize=10000&stage=3"
        return if (normalized.contains("cloud.siliconflow.cn", ignoreCase = true)) {
            "https://cloud.siliconflow.cn$path"
        } else {
            join(normalized, path)
        }
    }

    private fun siliconFlowConsoleHeaders(subjectId: String, sessionToken: String): Map<String, String> = mapOf(
        "x-subject-id" to subjectId,
        "Cookie" to "__SF_auth.session-token=${normalizeSiliconFlowSessionToken(sessionToken)}",
    )

    private fun normalizeSiliconFlowSessionToken(raw: String): String {
        val value = raw.trim()
            .removePrefix("Cookie:")
            .trim()
        return value
            .substringAfter("__SF_auth.session-token=", value)
            .substringBefore(';')
            .trim()
    }

    private fun consoleStatus(includeVouchers: Boolean) = if (includeVouchers) "控制台已连接 · 含代金券" else "控制台已连接"

    private fun clock() = clockFormatter.format(Instant.now())

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun stored(context: Context): List<StoredBalanceService> = synchronized(lock) {
        val raw = prefs(context).getString(SERVICES, "[]").orEmpty()
        runCatching {
            val json = JSONArray(raw)
            (0 until json.length()).map { StoredBalanceService.from(json.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun saveStored(context: Context, values: List<StoredBalanceService>) = synchronized(lock) {
        prefs(context).edit {
            putString(SERVICES, JSONArray().apply { values.forEach { put(it.json()) } }.toString())
        }
    }

    private fun replace(context: Context, value: StoredBalanceService) {
        saveStored(context, stored(context).map { if (it.id == value.id) value else it })
    }

    private fun requireStored(context: Context, id: String): StoredBalanceService =
        stored(context).firstOrNull { it.id == id } ?: error("找不到余额服务")

    private fun notifyChanged(context: Context) {
        QuotaDisplayContract.notifyAll(context)
    }

    private fun String.toUriCompat() = android.net.Uri.parse(this)
}

// SiliconFlow walletd returns console money in 1e-12 CNY units.
internal fun siliconFlowConsoleCashToYuan(raw: java.math.BigDecimal): java.math.BigDecimal =
    raw.divide(java.math.BigDecimal("1000000000000"))

/** Stable, credential-free balance text shared by the app, widgets and MAML. */
internal fun balanceDisplayValue(service: BalanceService): String {
    if (service.balance == "--") return "--"
    val amount = runCatching {
        java.math.BigDecimal(service.balance).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
    }.getOrDefault(service.balance)
    return when {
        service.currency.equals("USD", ignoreCase = true) -> "\$$amount"
        service.currency.equals("CNY", ignoreCase = true) -> "¥$amount"
        else -> "${service.currency} $amount"
    }
}
