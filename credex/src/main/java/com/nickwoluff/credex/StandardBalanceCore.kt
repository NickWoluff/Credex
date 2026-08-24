package com.nickwoluff.credex

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
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

enum class BalanceAuthMode {
    EMAIL_PASSWORD,
    API_KEY,
    DEEPSEEK_API_KEY,
    SILICONFLOW_CONSOLE,
    MIMO_BALANCE,
    MIMO_TOKEN_PLAN,
    VOLCENGINE_BALANCE,
    VOLCENGINE_CODING_PLAN,
    VOLCENGINE_AGENT_PLAN,
    OPENCODE_ZEN,
    OPENCODE_GO,
    KIMI,
    KIMI_BALANCE,
    GLM_BALANCE,
    GLM_CODING_PLAN,
}

enum class BalanceDisplayKind { AMOUNT, TOKEN_PLAN }

internal fun BalanceAuthMode.isPlan(): Boolean = when (this) {
    BalanceAuthMode.MIMO_TOKEN_PLAN,
    BalanceAuthMode.VOLCENGINE_CODING_PLAN,
    BalanceAuthMode.VOLCENGINE_AGENT_PLAN,
    BalanceAuthMode.OPENCODE_GO,
    BalanceAuthMode.KIMI,
    BalanceAuthMode.GLM_CODING_PLAN -> true
    else -> false
}

internal fun BalanceAuthMode.usesBrowserLogin(): Boolean = when (this) {
    BalanceAuthMode.SILICONFLOW_CONSOLE,
    BalanceAuthMode.MIMO_BALANCE,
    BalanceAuthMode.MIMO_TOKEN_PLAN,
    BalanceAuthMode.VOLCENGINE_BALANCE,
    BalanceAuthMode.VOLCENGINE_CODING_PLAN,
    BalanceAuthMode.VOLCENGINE_AGENT_PLAN,
    BalanceAuthMode.OPENCODE_ZEN,
    BalanceAuthMode.GLM_BALANCE,
    BalanceAuthMode.GLM_CODING_PLAN -> true
    else -> false
}

internal fun BalanceAuthMode.usesApiToken(): Boolean = when (this) {
    BalanceAuthMode.API_KEY,
    BalanceAuthMode.DEEPSEEK_API_KEY,
    BalanceAuthMode.OPENCODE_GO,
    BalanceAuthMode.KIMI,
    BalanceAuthMode.KIMI_BALANCE -> true
    else -> false
}

/** Token Plan values can be presented as consumption or remaining quota. */
enum class TokenPlanDisplay { USED, REMAINING }

/** 一个订阅服务可同时包含 5 小时、周、月等多个官方配额窗口。 */
data class ServiceQuotaWindow(
    val id: String,
    val label: String,
    val used: String,
    val total: String,
    val resetAt: String = "",
)

data class KimiDeviceAuthorization(
    val deviceCode: String,
    val userCode: String,
    val verificationUriComplete: String,
    val expiresInSeconds: Long,
    val intervalSeconds: Long,
)

sealed interface KimiDevicePollResult {
    data object Pending : KimiDevicePollResult
    data class Success(
        val accessToken: String,
        val refreshToken: String,
        val expiresInSeconds: Long,
    ) : KimiDevicePollResult
    data class Failed(val message: String) : KimiDevicePollResult
}

enum class UiStyle { MATERIAL, MIUIX }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class MaterialAccent { BLUE, PURPLE, GREEN, ORANGE, RED }

enum class MaterialPaletteStyle { TONAL_SPOT, VIBRANT, EXPRESSIVE, NEUTRAL }

/** The two rear-display hosts that can each show one selected balance service. */
enum class RearDisplaySurface {
    ASSISTANT,
    WALLPAPER,
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
    val includeVouchers: Boolean = false,
    val includeGrantedBalance: Boolean = true,
    val displayKind: BalanceDisplayKind = BalanceDisplayKind.AMOUNT,
    val used: String = "",
    val total: String = "",
    val resetAt: String = "",
    val tokenPlanDisplay: TokenPlanDisplay = TokenPlanDisplay.USED,
    val quotaWindows: List<ServiceQuotaWindow> = emptyList(),
)

/** Display-only preferences. They never stop network refresh or delete credentials. */
object DashboardPreferences {
    private const val PREFS = "quota_display_preferences"
    private const val SHOW_CODEX = "show_codex"
    private const val SHOW_HEALTH = "show_health"
    private const val SHOW_PROVIDER_ICONS = "show_provider_icons"
    private const val UI_STYLE = "ui_style"
    private const val MATERIAL_DYNAMIC_COLOR = "material_dynamic_color"
    private const val THEME_MODE = "theme_mode"
    private const val MATERIAL_ACCENT = "material_accent"
    private const val MATERIAL_PALETTE_STYLE = "material_palette_style"
    private const val MIUIX_BLUR = "miuix_blur"

    fun showCodex(context: Context) = prefs(context).getBoolean(SHOW_CODEX, true)
    fun setShowCodex(context: Context, value: Boolean) = prefs(context).edit { putBoolean(SHOW_CODEX, value) }
    fun showHealth(context: Context) = prefs(context).getBoolean(SHOW_HEALTH, true)
    fun setShowHealth(context: Context, value: Boolean) = prefs(context).edit { putBoolean(SHOW_HEALTH, value) }
    fun showProviderIcons(context: Context) = prefs(context).getBoolean(SHOW_PROVIDER_ICONS, true)
    fun setShowProviderIcons(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(SHOW_PROVIDER_ICONS, value) }
    fun uiStyle(context: Context): UiStyle = runCatching {
        UiStyle.valueOf(prefs(context).getString(UI_STYLE, UiStyle.MATERIAL.name).orEmpty())
    }.getOrDefault(UiStyle.MATERIAL)
    fun setUiStyle(context: Context, value: UiStyle) = prefs(context).edit { putString(UI_STYLE, value.name) }
    fun materialDynamicColor(context: Context) = prefs(context).getBoolean(MATERIAL_DYNAMIC_COLOR, true)
    fun setMaterialDynamicColor(context: Context, value: Boolean) = prefs(context).edit { putBoolean(MATERIAL_DYNAMIC_COLOR, value) }
    fun themeMode(context: Context): ThemeMode = enumPreference(context, THEME_MODE, ThemeMode.SYSTEM)
    fun setThemeMode(context: Context, value: ThemeMode) = prefs(context).edit { putString(THEME_MODE, value.name) }
    fun materialAccent(context: Context): MaterialAccent = enumPreference(context, MATERIAL_ACCENT, MaterialAccent.BLUE)
    fun setMaterialAccent(context: Context, value: MaterialAccent) = prefs(context).edit { putString(MATERIAL_ACCENT, value.name) }
    fun materialPaletteStyle(context: Context): MaterialPaletteStyle =
        enumPreference(context, MATERIAL_PALETTE_STYLE, MaterialPaletteStyle.TONAL_SPOT)
    fun setMaterialPaletteStyle(context: Context, value: MaterialPaletteStyle) =
        prefs(context).edit { putString(MATERIAL_PALETTE_STYLE, value.name) }
    fun miuixBlur(context: Context) = prefs(context).getBoolean(MIUIX_BLUR, true)
    fun setMiuixBlur(context: Context, value: Boolean) = prefs(context).edit { putBoolean(MIUIX_BLUR, value) }
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private inline fun <reified T : Enum<T>> enumPreference(context: Context, key: String, fallback: T): T =
        runCatching { enumValueOf<T>(prefs(context).getString(key, fallback.name).orEmpty()) }.getOrDefault(fallback)
}

/** One selected balance service per rear-display host. */
object RearDisplayPreferences {
    private const val PREFS = "rear_display_preferences"
    private const val ASSISTANT_SERVICE_ID = "assistant_service_id"
    private const val WALLPAPER_SERVICE_ID = "wallpaper_service_id"

    fun selectedServiceId(context: Context, surface: RearDisplaySurface): String =
        prefs(context).getString(key(surface), "").orEmpty()

    fun setSelectedService(context: Context, surface: RearDisplaySurface, serviceId: String) {
        prefs(context).edit { putString(key(surface), serviceId) }
    }

    fun ensureDefaults(context: Context, services: List<BalanceService>) {
        val firstVisibleId = services.firstOrNull { it.visible }?.id.orEmpty()
        if (firstVisibleId.isBlank()) return
        val visibleIds = services.filter { it.visible }.map(BalanceService::id).toSet()
        val preferences = prefs(context)
        preferences.edit {
            if (preferences.getString(ASSISTANT_SERVICE_ID, "").orEmpty() !in visibleIds) {
                putString(ASSISTANT_SERVICE_ID, firstVisibleId)
            }
            if (preferences.getString(WALLPAPER_SERVICE_ID, "").orEmpty() !in visibleIds) {
                putString(WALLPAPER_SERVICE_ID, firstVisibleId)
            }
        }
    }

    private fun key(surface: RearDisplaySurface): String = when (surface) {
        RearDisplaySurface.ASSISTANT -> ASSISTANT_SERVICE_ID
        RearDisplaySurface.WALLPAPER -> WALLPAPER_SERVICE_ID
    }

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
    val includeVouchers: Boolean = false,
    val includeGrantedBalance: Boolean = true,
    val displayKind: BalanceDisplayKind = BalanceDisplayKind.AMOUNT,
    val used: String = "",
    val total: String = "",
    val resetAt: String = "",
    val tokenPlanDisplay: TokenPlanDisplay = TokenPlanDisplay.USED,
    val quotaWindows: List<ServiceQuotaWindow> = emptyList(),
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
        includeVouchers = includeVouchers,
        includeGrantedBalance = includeGrantedBalance,
        displayKind = displayKind,
        used = used,
        total = total,
        resetAt = resetAt,
        tokenPlanDisplay = tokenPlanDisplay,
        quotaWindows = quotaWindows,
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
        put("include_vouchers", includeVouchers)
        put("include_granted_balance", includeGrantedBalance)
        put("display_kind", displayKind.name)
        put("used", used)
        put("total", total)
        put("reset_at", resetAt)
        put("token_plan_display", tokenPlanDisplay.name)
        put("quota_windows", JSONArray().apply {
            quotaWindows.forEach { window ->
                put(JSONObject().apply {
                    put("id", window.id)
                    put("label", window.label)
                    put("used", window.used)
                    put("total", window.total)
                    put("reset_at", window.resetAt)
                })
            }
        })
    }

    companion object {
        fun from(json: JSONObject, decryptSecrets: Boolean = true): StoredBalanceService {
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
                password = json.optString("password").let { if (decryptSecrets) BalanceSecretBox.open(it) else it },
                accessToken = json.optString("access_token").let { if (decryptSecrets) BalanceSecretBox.open(it) else it },
                refreshToken = json.optString("refresh_token").let { if (decryptSecrets) BalanceSecretBox.open(it) else it },
                subjectId = json.optString("subject_id"),
                sessionToken = json.optString("session_token").let { if (decryptSecrets) BalanceSecretBox.open(it) else it },
                expiresAtMillis = json.optLong("expires_at"),
                balance = json.optString("balance", "--"),
                currency = json.optString("currency", "USD"),
                detail = json.optString("detail"),
                updatedAt = json.optString("updated_at", "--"),
                lastAttemptAtMillis = json.optLong("last_attempt_at"),
                status = json.optString("status", "未登录"),
                health = health,
                visible = json.optBoolean("visible", true),
                includeVouchers = json.optBoolean("include_vouchers", false),
                includeGrantedBalance = json.optBoolean("include_granted_balance", true),
                displayKind = runCatching { BalanceDisplayKind.valueOf(json.optString("display_kind")) }
                    .getOrDefault(BalanceDisplayKind.AMOUNT),
                used = json.optString("used"),
                total = json.optString("total"),
                resetAt = json.optString("reset_at"),
                tokenPlanDisplay = runCatching { TokenPlanDisplay.valueOf(json.optString("token_plan_display")) }
                    .getOrDefault(TokenPlanDisplay.USED),
                quotaWindows = json.optJSONArray("quota_windows")?.let { array ->
                    (0 until array.length()).mapNotNull { index ->
                        array.optJSONObject(index)?.let { item ->
                            ServiceQuotaWindow(
                                id = item.optString("id", index.toString()),
                                label = item.optString("label"),
                                used = item.optString("used"),
                                total = item.optString("total"),
                                resetAt = item.optString("reset_at"),
                            )
                        }
                    }
                }.orEmpty(),
            )
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
    private const val KIMI_OAUTH_HOST = "https://auth.kimi.com"
    private const val KIMI_CLIENT_ID = "17e5f671-d194-4dfb-9706-5516cb48c098"
    private val lock = Any()
    private val clockFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    fun list(context: Context): List<BalanceService> = stored(context, decryptSecrets = false)
        .map { it.public() }

    fun forRearSurface(context: Context, surface: RearDisplaySurface, limit: Int): List<BalanceService> {
        val services = list(context)
        RearDisplayPreferences.ensureDefaults(context, services)
        val selectedId = RearDisplayPreferences.selectedServiceId(context, surface)
        return services
            .filter { it.visible && it.id == selectedId }
            .take(limit.coerceAtLeast(0))
    }

    fun hasConfiguredService(context: Context): Boolean = stored(context, decryptSecrets = false).isNotEmpty()

    fun hasAuthenticatedService(context: Context): Boolean = stored(context, decryptSecrets = false).any {
        it.accessToken.isNotBlank() ||
            (it.email.isNotBlank() && it.password.isNotBlank()) ||
            (it.subjectId.isNotBlank() && it.sessionToken.isNotBlank()) ||
            (it.authMode.usesBrowserLogin() && it.sessionToken.isNotBlank())
    }

    data class Credentials(val account: String, val secret: String)

    fun credentials(context: Context, id: String): Credentials {
        val service = requireStored(context, id)
        return when (service.authMode) {
            BalanceAuthMode.API_KEY,
            BalanceAuthMode.DEEPSEEK_API_KEY,
            BalanceAuthMode.OPENCODE_GO,
            BalanceAuthMode.KIMI,
            BalanceAuthMode.KIMI_BALANCE -> Credentials("", service.accessToken)
            BalanceAuthMode.SILICONFLOW_CONSOLE -> Credentials(service.subjectId, service.sessionToken)
            BalanceAuthMode.MIMO_BALANCE,
            BalanceAuthMode.MIMO_TOKEN_PLAN,
            BalanceAuthMode.VOLCENGINE_BALANCE,
            BalanceAuthMode.VOLCENGINE_CODING_PLAN,
            BalanceAuthMode.VOLCENGINE_AGENT_PLAN,
            BalanceAuthMode.OPENCODE_ZEN,
            BalanceAuthMode.GLM_BALANCE,
            BalanceAuthMode.GLM_CODING_PLAN -> Credentials("", service.sessionToken)
            BalanceAuthMode.EMAIL_PASSWORD -> Credentials(service.email, service.password)
        }
    }

    fun setVisible(context: Context, id: String, visible: Boolean) {
        stored(context).firstOrNull { it.id == id }?.let { replace(context, it.copy(visible = visible)) }
        notifyChanged(context)
    }

    fun setTokenPlanDisplay(context: Context, id: String, display: TokenPlanDisplay) {
        stored(context).firstOrNull { it.id == id }?.let { service ->
            replace(context, service.copy(tokenPlanDisplay = display))
            notifyChanged(context)
        }
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

    fun reorder(context: Context, orderedIds: List<String>) {
        if (orderedIds.isEmpty()) return
        val current = stored(context)
        val currentById = current.associateBy { it.id }
        val ordered = orderedIds.mapNotNull(currentById::get)
        if (ordered.isEmpty()) return
        val orderedSet = orderedIds.toSet()
        var orderedIndex = 0
        val next = current.map { service ->
            if (service.id in orderedSet) ordered[orderedIndex++] else service
        }
        saveStored(context, next)
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
        if (current.any { service ->
                service.id != id && service.name.trim().equals(cleanName, ignoreCase = true)
            }
        ) {
            throw IllegalArgumentException("服务名称已存在")
        }
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
                displayKind = if (authMode.isPlan()) BalanceDisplayKind.TOKEN_PLAN else BalanceDisplayKind.AMOUNT,
                used = "",
                total = "",
                resetAt = "",
                quotaWindows = emptyList(),
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
            BalanceAuthMode.OPENCODE_GO -> loginBearerPlan(context, service, secret, ::fetchOpenCodeGo)
            BalanceAuthMode.KIMI -> loginBearerPlan(context, service, secret) { fetchKimi(context, it) }
            BalanceAuthMode.KIMI_BALANCE -> loginKimiBalance(context, service, secret)
            BalanceAuthMode.SILICONFLOW_CONSOLE -> loginSiliconFlowConsole(context, service, account, secret)
            BalanceAuthMode.MIMO_BALANCE, BalanceAuthMode.MIMO_TOKEN_PLAN -> loginMimo(context, service, secret)
            BalanceAuthMode.VOLCENGINE_BALANCE,
            BalanceAuthMode.VOLCENGINE_CODING_PLAN,
            BalanceAuthMode.VOLCENGINE_AGENT_PLAN,
            BalanceAuthMode.OPENCODE_ZEN,
            BalanceAuthMode.GLM_BALANCE,
            BalanceAuthMode.GLM_CODING_PLAN -> error("该服务需要先完成内置浏览器登录")
            BalanceAuthMode.EMAIL_PASSWORD -> loginEmailPassword(context, service, account, secret)
        }
    }

    private fun loginBearerPlan(
        context: Context,
        service: StoredBalanceService,
        token: CharArray,
        fetch: (StoredBalanceService) -> StoredBalanceService,
    ): BalanceService {
        val tokenText = String(token).trim()
        check(tokenText.isNotBlank()) { "请输入访问令牌或 API Key" }
        val withToken = service.copy(
            accessToken = tokenText,
            refreshToken = "",
            expiresAtMillis = Long.MAX_VALUE,
            displayKind = BalanceDisplayKind.TOKEN_PLAN,
        )
        replace(context, withToken)
        val next = fetch(withToken)
        replace(context, next)
        QuotaRefreshScheduler.schedule(context)
        notifyChanged(context)
        return next.public()
    }

    private fun loginKimiBalance(
        context: Context,
        service: StoredBalanceService,
        token: CharArray,
    ): BalanceService {
        val tokenText = String(token).trim()
        check(tokenText.isNotBlank()) { "请输入 Kimi API Key" }
        val withToken = service.copy(
            accessToken = tokenText,
            refreshToken = "",
            expiresAtMillis = Long.MAX_VALUE,
            displayKind = BalanceDisplayKind.AMOUNT,
        )
        replace(context, withToken)
        val next = fetchKimiBalance(withToken)
        replace(context, next)
        QuotaRefreshScheduler.schedule(context)
        notifyChanged(context)
        return next.public()
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

    fun connectMimo(context: Context, id: String, sessionToken: String): BalanceService {
        val secret = sessionToken.toCharArray()
        return try {
            val service = requireStored(context, id)
            check(service.authMode == BalanceAuthMode.MIMO_BALANCE || service.authMode == BalanceAuthMode.MIMO_TOKEN_PLAN) {
                "不是 Xiaomi MIMO 服务"
            }
            loginMimo(context, service, secret)
        } finally {
            secret.fill('\u0000')
        }
    }

    fun connectConsoleSession(
        context: Context,
        id: String,
        sessionToken: String,
        capturedBalance: String = "",
    ): BalanceService {
        val service = requireStored(context, id)
        check(service.authMode.usesBrowserLogin()) { "该服务不使用控制台登录" }
        check(service.authMode != BalanceAuthMode.SILICONFLOW_CONSOLE && service.authMode != BalanceAuthMode.MIMO_BALANCE && service.authMode != BalanceAuthMode.MIMO_TOKEN_PLAN) {
            "请使用对应平台的专用登录流程"
        }
        val cookie = sessionToken.trim()
        check(cookie.isNotBlank()) { "没有获取到控制台登录会话" }
        val withSession = service.copy(
            sessionToken = cookie,
            accessToken = "",
            refreshToken = "",
            expiresAtMillis = Long.MAX_VALUE,
            displayKind = if (service.authMode.isPlan()) BalanceDisplayKind.TOKEN_PLAN else BalanceDisplayKind.AMOUNT,
        )
        replace(context, withSession)
        val capturedMode = service.authMode in setOf(
            BalanceAuthMode.OPENCODE_ZEN,
            BalanceAuthMode.VOLCENGINE_BALANCE,
            BalanceAuthMode.GLM_BALANCE,
        )
        val next = if (capturedMode && capturedBalance.toBigDecimalOrNull() != null) {
            withSession.copy(
                balance = formatBalance(capturedBalance.toBigDecimal()),
                currency = if (service.authMode == BalanceAuthMode.OPENCODE_ZEN) "USD" else "CNY",
                detail = when (service.authMode) {
                    BalanceAuthMode.OPENCODE_ZEN -> "OpenCode Zen 当前余额"
                    BalanceAuthMode.VOLCENGINE_BALANCE -> "火山引擎账户可用余额"
                    BalanceAuthMode.GLM_BALANCE -> "GLM 账户可用余额"
                    else -> "账户余额"
                },
                updatedAt = clock(),
                lastAttemptAtMillis = System.currentTimeMillis(),
                status = "已连接",
                health = BalanceHealth.FRESH,
            )
        } else {
            fetchConsoleService(withSession)
        }
        replace(context, next)
        QuotaRefreshScheduler.schedule(context)
        notifyChanged(context)
        return next.public()
    }

    fun requestKimiDeviceAuthorization(context: Context): KimiDeviceAuthorization {
        val (_, data) = requestForm(
            "$KIMI_OAUTH_HOST/api/oauth/device_authorization",
            mapOf("client_id" to KIMI_CLIENT_ID),
            kimiIdentityHeaders(context),
        )
        val deviceCode = data.optString("device_code")
        val userCode = data.optString("user_code")
        val verificationUriComplete = data.optString("verification_uri_complete")
        check(deviceCode.isNotBlank() && userCode.isNotBlank() && verificationUriComplete.isNotBlank()) {
            "Kimi 授权服务未返回完整的设备登录信息"
        }
        return KimiDeviceAuthorization(
            deviceCode = deviceCode,
            userCode = userCode,
            verificationUriComplete = verificationUriComplete,
            expiresInSeconds = data.optLong("expires_in", 600L).coerceAtLeast(60L),
            intervalSeconds = data.optLong("interval", 5L).coerceAtLeast(2L),
        )
    }

    fun pollKimiDeviceAuthorization(context: Context, deviceCode: String): KimiDevicePollResult {
        val (status, data) = requestForm(
            "$KIMI_OAUTH_HOST/api/oauth/token",
            mapOf(
                "client_id" to KIMI_CLIENT_ID,
                "device_code" to deviceCode,
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
            ),
            kimiIdentityHeaders(context),
        )
        if (status in 200..299) {
            val access = data.optString("access_token")
            val refresh = data.optString("refresh_token")
            val expiresIn = data.optLong("expires_in", 3600L).coerceAtLeast(60L)
            return if (access.isNotBlank() && refresh.isNotBlank()) {
                KimiDevicePollResult.Success(access, refresh, expiresIn)
            } else {
                KimiDevicePollResult.Failed("Kimi 授权响应缺少令牌")
            }
        }
        return when (data.optString("error")) {
            "authorization_pending", "slow_down" -> KimiDevicePollResult.Pending
            "expired_token" -> KimiDevicePollResult.Failed("登录二维码已过期，请重试")
            "access_denied" -> KimiDevicePollResult.Failed("Kimi 授权已被取消")
            else -> KimiDevicePollResult.Failed(data.optString("error_description").ifBlank { "Kimi 授权失败（HTTP $status）" })
        }
    }

    fun connectKimiOAuth(
        context: Context,
        id: String,
        accessToken: String,
        refreshToken: String,
        expiresInSeconds: Long,
    ): BalanceService {
        val service = requireStored(context, id)
        check(service.authMode == BalanceAuthMode.KIMI) { "不是 Kimi 服务" }
        val authenticated = service.copy(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtMillis = System.currentTimeMillis() + expiresInSeconds.coerceAtLeast(60L) * 1_000L,
            displayKind = BalanceDisplayKind.TOKEN_PLAN,
        )
        replace(context, authenticated)
        return fetchKimi(context, authenticated).also {
            replace(context, it)
            notifyChanged(context)
        }.public()
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

    private fun loginMimo(
        context: Context,
        service: StoredBalanceService,
        sessionToken: CharArray,
    ): BalanceService {
        val cookie = normalizeMimoCookie(String(sessionToken))
        check(cookie.isNotBlank()) { "没有获取到 Xiaomi MIMO 登录会话" }
        val withCredentials = service.copy(
            sessionToken = cookie,
            accessToken = "",
            refreshToken = "",
            subjectId = "",
            expiresAtMillis = Long.MAX_VALUE,
            displayKind = if (service.authMode == BalanceAuthMode.MIMO_TOKEN_PLAN) BalanceDisplayKind.TOKEN_PLAN else BalanceDisplayKind.AMOUNT,
        )
        replace(context, withCredentials)
        val next = fetchMimo(withCredentials)
        replace(context, next)
        QuotaRefreshScheduler.schedule(context)
        notifyChanged(context)
        return next.public()
    }

    private fun refreshMimo(context: Context, initial: StoredBalanceService, force: Boolean): BalanceService {
        val now = System.currentTimeMillis()
        if (!force && now - initial.lastAttemptAtMillis < MIN_REFRESH_MILLIS) return initial.public()
        replace(context, initial.copy(lastAttemptAtMillis = now))
        return try {
            val current = requireStored(context, initial.id)
            val success = fetchMimo(current)
            replace(context, success)
            notifyChanged(context)
            success.public()
        } catch (error: Exception) {
            val latest = requireStored(context, initial.id)
            val authRequired = error is BalanceHttpException && error.statusCode in setOf(401, 403)
            val failed = latest.copy(
                status = if (authRequired) "Xiaomi MIMO 会话已过期" else "暂时无法更新",
                health = if (authRequired) BalanceHealth.AUTH_REQUIRED else if (latest.balance != "--") BalanceHealth.CACHED else BalanceHealth.ERROR,
            )
            replace(context, failed)
            notifyChanged(context)
            failed.public()
        }
    }

    private fun fetchMimo(service: StoredBalanceService): StoredBalanceService {
        val headers = mimoHeaders(service.sessionToken)
        return if (service.authMode == BalanceAuthMode.MIMO_BALANCE) {
            val data = unwrap(requestJson(mimoBalanceUrl(service.endpoint), "GET", null, null, headers))
            val snapshot = readMimoPayAsYouGo(data)
            service.copy(
                balance = formatBalance(snapshot.cash),
                currency = "CNY",
                detail = buildString {
                    append("现金 ¥").append(formatBalance(snapshot.cash))
                    snapshot.gift?.let { append(" · 赠送 ¥").append(formatBalance(it)) }
                },
                displayKind = BalanceDisplayKind.AMOUNT,
                used = "",
                total = "",
                resetAt = "",
                updatedAt = clock(),
                lastAttemptAtMillis = System.currentTimeMillis(),
                status = "Xiaomi MIMO 已连接",
                health = BalanceHealth.FRESH,
            )
        } else {
            val detail = unwrap(requestJson(mimoTokenPlanDetailUrl(service.endpoint), "GET", null, null, headers))
            val usage = unwrap(requestJson(mimoTokenPlanUsageUrl(service.endpoint), "GET", null, null, headers))
            val snapshot = readMimoTokenPlan(detail, usage)
            service.copy(
                balance = formatBalance(snapshot.remaining),
                currency = "TOKEN",
                detail = buildString {
                    append(snapshot.plan)
                    append(" · 剩余 ").append(formatTokenCount(snapshot.remaining))
                    append(" / ").append(formatTokenCount(snapshot.limit)).append(" Credits")
                    snapshot.expiresAt.takeIf { it.isNotBlank() }?.let { append(" · 有效期至 ").append(it) }
                },
                displayKind = BalanceDisplayKind.TOKEN_PLAN,
                used = formatBalance(snapshot.used),
                total = formatBalance(snapshot.limit),
                resetAt = snapshot.expiresAt,
                updatedAt = clock(),
                lastAttemptAtMillis = System.currentTimeMillis(),
                status = "Xiaomi MIMO Token Plan",
                health = BalanceHealth.FRESH,
            )
        }
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
            BalanceAuthMode.MIMO_BALANCE, BalanceAuthMode.MIMO_TOKEN_PLAN -> {
                if (initial.sessionToken.isBlank()) return initial.public()
                return refreshMimo(context, initial, force)
            }
            BalanceAuthMode.OPENCODE_GO, BalanceAuthMode.KIMI -> {
                if (initial.accessToken.isBlank()) return initial.public()
                return refreshBearerPlan(context, initial, force)
            }
            BalanceAuthMode.KIMI_BALANCE -> {
                if (initial.accessToken.isBlank()) return initial.public()
                return refreshKimiBalance(context, initial, force)
            }
            BalanceAuthMode.VOLCENGINE_BALANCE,
            BalanceAuthMode.VOLCENGINE_CODING_PLAN,
            BalanceAuthMode.VOLCENGINE_AGENT_PLAN,
            BalanceAuthMode.OPENCODE_ZEN,
            BalanceAuthMode.GLM_BALANCE,
            BalanceAuthMode.GLM_CODING_PLAN -> {
                if (initial.sessionToken.isBlank()) return initial.public()
                return refreshConsoleService(context, initial, force)
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

    private fun refreshBearerPlan(context: Context, initial: StoredBalanceService, force: Boolean): BalanceService {
        val now = System.currentTimeMillis()
        if (!force && now - initial.lastAttemptAtMillis < MIN_REFRESH_MILLIS) return initial.public()
        replace(context, initial.copy(lastAttemptAtMillis = now))
        return try {
            val current = requireStored(context, initial.id)
            val success = when (current.authMode) {
                BalanceAuthMode.OPENCODE_GO -> fetchOpenCodeGo(current)
                BalanceAuthMode.KIMI -> {
                    val ready = if (current.refreshToken.isNotBlank() && current.expiresAtMillis <= now + 60_000L) {
                        refreshKimiTokens(context, current)
                    } else {
                        current
                    }
                    try {
                        fetchKimi(context, ready)
                    } catch (error: BalanceHttpException) {
                        if (error.statusCode !in setOf(401, 403) || ready.refreshToken.isBlank()) throw error
                        fetchKimi(context, refreshKimiTokens(context, ready))
                    }
                }
                BalanceAuthMode.KIMI_BALANCE -> fetchKimiBalance(requireStored(context, initial.id))
                else -> error("不是 Bearer 配额服务")
            }
            replace(context, success)
            notifyChanged(context)
            success.public()
        } catch (error: Exception) {
            val latest = requireStored(context, initial.id)
            val authRequired = error is BalanceHttpException && error.statusCode in setOf(401, 403)
            val failed = latest.copy(
                status = if (authRequired) "访问凭据已失效" else "暂时无法更新",
                health = if (authRequired) BalanceHealth.AUTH_REQUIRED else if (latest.balance != "--") BalanceHealth.CACHED else BalanceHealth.ERROR,
            )
            replace(context, failed)
            notifyChanged(context)
            failed.public()
        }
    }

    private fun refreshKimiBalance(context: Context, initial: StoredBalanceService, force: Boolean): BalanceService {
        val now = System.currentTimeMillis()
        if (!force && now - initial.lastAttemptAtMillis < MIN_REFRESH_MILLIS) return initial.public()
        replace(context, initial.copy(lastAttemptAtMillis = now))
        return try {
            val success = fetchKimiBalance(requireStored(context, initial.id))
            replace(context, success)
            notifyChanged(context)
            success.public()
        } catch (error: Exception) {
            val latest = requireStored(context, initial.id)
            val authRequired = error is BalanceHttpException && error.statusCode in setOf(401, 403)
            val failed = latest.copy(
                status = if (authRequired) "Kimi API Key 已失效" else "暂时无法更新",
                health = if (authRequired) BalanceHealth.AUTH_REQUIRED else if (latest.balance != "--") BalanceHealth.CACHED else BalanceHealth.ERROR,
            )
            replace(context, failed)
            notifyChanged(context)
            failed.public()
        }
    }

    private fun refreshConsoleService(context: Context, initial: StoredBalanceService, force: Boolean): BalanceService {
        val now = System.currentTimeMillis()
        if (!force && now - initial.lastAttemptAtMillis < MIN_REFRESH_MILLIS) return initial.public()
        replace(context, initial.copy(lastAttemptAtMillis = now))
        return try {
            val success = fetchConsoleService(requireStored(context, initial.id))
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

    private fun fetchConsoleService(service: StoredBalanceService): StoredBalanceService = when (service.authMode) {
        BalanceAuthMode.VOLCENGINE_BALANCE -> fetchVolcengineBalance(service)
        BalanceAuthMode.VOLCENGINE_CODING_PLAN -> fetchVolcengineCodingPlan(service)
        BalanceAuthMode.VOLCENGINE_AGENT_PLAN -> fetchVolcengineAgentPlan(service)
        BalanceAuthMode.GLM_BALANCE -> fetchGlmBalance(service)
        BalanceAuthMode.GLM_CODING_PLAN -> fetchGlmCodingPlan(service)
        BalanceAuthMode.OPENCODE_ZEN -> service.copy(
            updatedAt = clock(),
            lastAttemptAtMillis = System.currentTimeMillis(),
            status = "请重新打开登录页更新 Zen 余额",
            health = if (service.balance != "--") BalanceHealth.CACHED else BalanceHealth.AUTH_REQUIRED,
        )
        else -> error("不是通用控制台服务")
    }

    private fun fetchVolcengineBalance(service: StoredBalanceService): StoredBalanceService {
        return service.copy(
            lastAttemptAtMillis = System.currentTimeMillis(),
            status = "请重新打开登录页更新账户余额",
            health = if (service.balance != "--") BalanceHealth.CACHED else BalanceHealth.AUTH_REQUIRED,
        )
    }

    private fun fetchVolcengineCodingPlan(service: StoredBalanceService): StoredBalanceService {
        val response = requestJson(
            "https://ark.cn-beijing.volces.com/api/plan/GetCodingPlanUsage",
            "POST",
            JSONObject(),
            null,
            consoleCookieHeaders(service.sessionToken),
        )
        val array = findArray(response, "QuotaUsage") ?: error("Coding Plan 响应中没有 QuotaUsage")
        val labels = mapOf("session" to "5 小时", "weekly" to "周配额", "monthly" to "月配额")
        val windows = (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val level = item.optString("Level").lowercase()
            val percent = item.optDouble("Percent", Double.NaN)
            if (percent.isNaN()) return@mapNotNull null
            ServiceQuotaWindow(
                id = level.ifBlank { index.toString() },
                label = labels[level] ?: level,
                used = formatPercentNumber(percent),
                total = "100",
                resetAt = epochSecondsToIso(item.optLong("ResetTimestamp")),
            )
        }
        check(windows.isNotEmpty()) { "Coding Plan 响应中没有有效配额" }
        return service.withQuotaWindows(windows, "火山引擎 Coding Plan")
    }

    private fun fetchVolcengineAgentPlan(service: StoredBalanceService): StoredBalanceService {
        val response = requestJson(
            "https://ark.cn-beijing.volces.com/api/plan/GetAgentPlanAFPUsage",
            "POST",
            JSONObject(),
            null,
            consoleCookieHeaders(service.sessionToken),
        )
        val root = findObjectWithAnyKey(response, "AFPFiveHour", "AFPWeekly", "AFPMonthly") ?: response
        val windows = listOf(
            "AFPFiveHour" to "5 小时",
            "AFPWeekly" to "周配额",
            "AFPMonthly" to "月配额",
        ).mapNotNull { (key, label) ->
            root.optJSONObject(key)?.let { item ->
                ServiceQuotaWindow(
                    id = key,
                    label = label,
                    used = item.optString("Used"),
                    total = item.optString("Quota"),
                    resetAt = epochMillisToIso(item.optLong("ResetTime")),
                )
            }
        }.filter { it.total.toBigDecimalOrNull()?.signum() == 1 }
        check(windows.isNotEmpty()) { "Agent Plan 响应中没有有效配额" }
        return service.withQuotaWindows(windows, "火山引擎 Agent Plan")
    }

    private fun fetchGlmBalance(service: StoredBalanceService): StoredBalanceService {
        val response = requestJson(
            "https://www.bigmodel.cn/api/biz/account/query-customer-account-report",
            "GET",
            null,
            null,
            consoleCookieHeaders(service.sessionToken),
        )
        val value = findDecimal(response, "availableBalance", "balance") ?: error("GLM 响应中没有账户余额")
        return service.copy(
            balance = formatBalance(value),
            currency = "CNY",
            detail = "GLM 账户可用余额",
            updatedAt = clock(),
            lastAttemptAtMillis = System.currentTimeMillis(),
            status = "GLM 已连接",
            health = BalanceHealth.FRESH,
        )
    }

    private fun fetchGlmCodingPlan(service: StoredBalanceService): StoredBalanceService {
        val response = requestJson(
            "https://www.bigmodel.cn/api/monitor/usage/quota/limit",
            "GET",
            null,
            null,
            consoleCookieHeaders(service.sessionToken),
        )
        val limits = findArray(response, "limits") ?: error("GLM 响应中没有 limits")
        val windows = (0 until limits.length()).mapNotNull { index ->
            val item = limits.optJSONObject(index) ?: return@mapNotNull null
            if (item.optString("type") != "TOKENS_LIMIT") return@mapNotNull null
            val unit = item.optInt("unit")
            val label = when (unit) { 3 -> "5 小时"; 6 -> "周配额"; else -> "配额" }
            val current = item.optString("currentValue").ifBlank { item.optString("usage") }
            val total = item.optString("usage").takeIf { value -> value.toBigDecimalOrNull()?.signum() == 1 }
                ?: item.optDouble("percentage", Double.NaN).takeUnless { it.isNaN() }?.let { "100" }
                ?: return@mapNotNull null
            val used = if (total == "100") formatPercentNumber(item.optDouble("percentage")) else current
            ServiceQuotaWindow(index.toString(), label, used, total, item.optString("nextResetTime"))
        }
        check(windows.isNotEmpty()) { "GLM 响应中没有有效配额" }
        return service.withQuotaWindows(windows, "GLM Coding Plan")
    }

    private fun consoleCookieHeaders(cookie: String): Map<String, String> {
        val csrf = cookie.split(';').map(String::trim).firstOrNull { it.startsWith("csrfToken=") }
            ?.substringAfter('=')
        return buildMap {
            put("Cookie", cookie)
            put("Accept", "application/json")
            if (!csrf.isNullOrBlank()) put("X-Csrf-Token", csrf)
        }
    }

    private fun findDecimal(root: Any?, vararg keys: String): java.math.BigDecimal? {
        when (root) {
            is JSONObject -> {
                keys.forEach { key -> root.opt(key)?.toString()?.toBigDecimalOrNull()?.let { return it } }
                val names = root.keys()
                while (names.hasNext()) findDecimal(root.opt(names.next()), *keys)?.let { return it }
            }
            is JSONArray -> for (index in 0 until root.length()) findDecimal(root.opt(index), *keys)?.let { return it }
        }
        return null
    }

    private fun findArray(root: Any?, key: String): JSONArray? {
        when (root) {
            is JSONObject -> {
                root.optJSONArray(key)?.let { return it }
                val names = root.keys()
                while (names.hasNext()) findArray(root.opt(names.next()), key)?.let { return it }
            }
            is JSONArray -> for (index in 0 until root.length()) findArray(root.opt(index), key)?.let { return it }
        }
        return null
    }

    private fun findObjectWithAnyKey(root: Any?, vararg keys: String): JSONObject? {
        when (root) {
            is JSONObject -> {
                if (keys.any(root::has)) return root
                val names = root.keys()
                while (names.hasNext()) findObjectWithAnyKey(root.opt(names.next()), *keys)?.let { return it }
            }
            is JSONArray -> for (index in 0 until root.length()) findObjectWithAnyKey(root.opt(index), *keys)?.let { return it }
        }
        return null
    }

    private fun epochSecondsToIso(value: Long): String = if (value > 0) Instant.ofEpochSecond(value).toString() else ""
    private fun epochMillisToIso(value: Long): String = if (value > 0) Instant.ofEpochMilli(value).toString() else ""

    private fun fetchOpenCodeGo(service: StoredBalanceService): StoredBalanceService {
        val root = requestJson(join(service.endpoint, "/zen/go/v1/usage"), "GET", null, service.accessToken)
        val usage = root.optJSONObject("usage") ?: root
        val windows = listOf(
            "rolling" to "5 小时",
            "weekly" to "周配额",
            "monthly" to "月配额",
        ).mapNotNull { (key, label) ->
            usage.optJSONObject(key)?.let { item ->
                ServiceQuotaWindow(
                    id = key,
                    label = label,
                    used = item.optDouble("percent", Double.NaN).takeUnless(Double::isNaN)?.let(::formatPercentNumber) ?: return@let null,
                    total = "100",
                    resetAt = item.optString("resetsAt"),
                )
            }
        }
        check(windows.isNotEmpty()) { "OpenCode Go 响应中没有 usage 窗口" }
        return service.withQuotaWindows(windows, "OpenCode Go")
    }

    private fun fetchKimi(context: Context, service: StoredBalanceService): StoredBalanceService {
        val root = requestJson(
            join(service.endpoint, "/usages"),
            "GET",
            null,
            service.accessToken,
            kimiIdentityHeaders(context),
        )
        val windows = buildList {
            root.optJSONObject("usage")?.let { usage ->
                add(
                    ServiceQuotaWindow(
                        id = "weekly",
                        label = "周配额",
                        used = usage.optString("used"),
                        total = usage.optString("limit"),
                        resetAt = usage.optString("resetTime"),
                    ),
                )
            }
            root.optJSONArray("limits")?.let { limits ->
                for (index in 0 until limits.length()) {
                    val item = limits.optJSONObject(index) ?: continue
                    val window = item.optJSONObject("window")
                    val detail = item.optJSONObject("detail") ?: continue
                    val duration = window?.optLong("duration") ?: 0L
                    val label = item.optString("name").ifBlank {
                        if (duration == 300L) "5 小时" else "${duration} 分钟"
                    }
                    add(
                        ServiceQuotaWindow(
                            id = "limit-$index",
                            label = label,
                            used = detail.optString("used"),
                            total = detail.optString("limit"),
                            resetAt = detail.optString("resetTime"),
                        ),
                    )
                }
            }
        }.filter { it.total.toBigDecimalOrNull()?.signum() == 1 }
        check(windows.isNotEmpty()) { "Kimi 响应中没有 usage 窗口" }
        return service.withQuotaWindows(windows, "Kimi")
    }

    /** Kimi API 官方账户余额：GET /v1/users/me/balance。金额单位为人民币。 */
    private fun fetchKimiBalance(service: StoredBalanceService): StoredBalanceService {
        val root = requestJson(
            join(service.endpoint, "/users/me/balance"),
            "GET",
            null,
            service.accessToken,
        )
        val available = findDecimal(root, "available_balance", "availableBalance")
            ?: error("Kimi 响应中没有 available_balance")
        val cash = findDecimal(root, "cash_balance", "cashBalance")
        val voucher = findDecimal(root, "voucher_balance", "voucherBalance")
        return service.copy(
            balance = formatBalance(available),
            currency = "CNY",
            detail = buildList {
                cash?.let { add("现金 ¥${formatBalance(it)}") }
                voucher?.let { add("代金券 ¥${formatBalance(it)}") }
            }.joinToString(" · "),
            displayKind = BalanceDisplayKind.AMOUNT,
            updatedAt = clock(),
            lastAttemptAtMillis = System.currentTimeMillis(),
            status = "Kimi 已连接",
            health = BalanceHealth.FRESH,
        )
    }

    private fun refreshKimiTokens(context: Context, service: StoredBalanceService): StoredBalanceService {
        val (status, data) = requestForm(
            "$KIMI_OAUTH_HOST/api/oauth/token",
            mapOf(
                "client_id" to KIMI_CLIENT_ID,
                "grant_type" to "refresh_token",
                "refresh_token" to service.refreshToken,
            ),
            kimiIdentityHeaders(context),
        )
        if (status !in 200..299) throw BalanceHttpException(status, data.optString("error_description").ifBlank { "Kimi 令牌刷新失败" })
        val access = data.optString("access_token")
        val refresh = data.optString("refresh_token").ifBlank { service.refreshToken }
        check(access.isNotBlank() && refresh.isNotBlank()) { "Kimi 令牌刷新响应不完整" }
        return service.copy(
            accessToken = access,
            refreshToken = refresh,
            expiresAtMillis = System.currentTimeMillis() + data.optLong("expires_in", 3600L).coerceAtLeast(60L) * 1_000L,
        ).also { replace(context, it) }
    }

    private fun StoredBalanceService.withQuotaWindows(windows: List<ServiceQuotaWindow>, statusText: String): StoredBalanceService {
        val primary = windows.first()
        val usedValue = primary.used.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
        val totalValue = primary.total.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
        return copy(
            balance = formatBalance(totalValue.subtract(usedValue).coerceAtLeast(java.math.BigDecimal.ZERO)),
            currency = "QUOTA",
            detail = windows.joinToString(" · ") { window -> "${window.label} ${formatQuotaPercent(window)}" },
            displayKind = BalanceDisplayKind.TOKEN_PLAN,
            used = primary.used,
            total = primary.total,
            resetAt = primary.resetAt,
            quotaWindows = windows,
            updatedAt = clock(),
            lastAttemptAtMillis = System.currentTimeMillis(),
            status = statusText,
            health = BalanceHealth.FRESH,
        )
    }

    private fun formatQuotaPercent(window: ServiceQuotaWindow): String {
        val usedValue = window.used.toBigDecimalOrNull() ?: return "--"
        val totalValue = window.total.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return "--"
        return "${usedValue.multiply(java.math.BigDecimal(100)).divide(totalValue, 1, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()}%"
    }

    private fun formatPercentNumber(value: Double): String =
        java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

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
        connection.setRequestProperty("User-Agent", "Credex/1.0")
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

    private fun requestForm(
        url: String,
        params: Map<String, String>,
        headers: Map<String, String>,
    ): Pair<Int, JSONObject> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.doOutput = true
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        val body = params.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, Charsets.UTF_8.name())}=${URLEncoder.encode(value, Charsets.UTF_8.name())}"
        }
        connection.outputStream.use { it.write(body.toByteArray()) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        return code to runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
    }

    private fun kimiIdentityHeaders(context: Context): Map<String, String> {
        val prefs = context.getSharedPreferences("kimi_device_identity", Context.MODE_PRIVATE)
        val deviceId = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also { generated ->
            prefs.edit { putString("device_id", generated) }
        }
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "1.0" }
        return mapOf(
            "User-Agent" to "Credex/$version (Android)",
            "X-Msh-Platform" to "credex_android",
            "X-Msh-Version" to version,
            "X-Msh-Device-Name" to Build.DEVICE.asciiHeader(),
            "X-Msh-Device-Model" to Build.MODEL.asciiHeader(),
            "X-Msh-Os-Version" to Build.VERSION.RELEASE.asciiHeader(),
            "X-Msh-Device-Id" to deviceId,
        )
    }

    private fun String.asciiHeader(): String = replace(Regex("[^\\x20-\\x7E]"), "").trim().ifBlank { "unknown" }

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

    private fun mimoHeaders(cookie: String): Map<String, String> = mapOf(
        "Cookie" to normalizeMimoCookie(cookie),
        "Accept-Language" to "zh-CN",
        "x-timeZone" to ZoneId.systemDefault().id,
    )

    private fun normalizeMimoCookie(raw: String): String = raw.trim()
        .removePrefix("Cookie:")
        .trim()

    private fun mimoBalanceUrl(endpoint: String): String = mimoApiUrl(endpoint, "/balance")
    private fun mimoTokenPlanDetailUrl(endpoint: String): String = mimoApiUrl(endpoint, "/tokenPlan/detail")
    private fun mimoTokenPlanUsageUrl(endpoint: String): String = mimoApiUrl(endpoint, "/tokenPlan/usage")

    private fun mimoApiUrl(endpoint: String, path: String): String {
        val normalized = endpoint.trim().trimEnd('/')
        return if (normalized.contains("platform.xiaomimimo.com", ignoreCase = true)) {
            "https://platform.xiaomimimo.com/api/v1$path"
        } else {
            join(normalized, "/api/v1$path")
        }
    }

    private fun clock() = clockFormatter.format(Instant.now())

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun stored(context: Context, decryptSecrets: Boolean = true): List<StoredBalanceService> = synchronized(lock) {
        val raw = prefs(context).getString(SERVICES, "[]").orEmpty()
        runCatching {
            val json = JSONArray(raw)
            (0 until json.length()).map { StoredBalanceService.from(json.getJSONObject(it), decryptSecrets) }
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

internal data class MimoPayAsYouGoSnapshot(
    val cash: java.math.BigDecimal,
    val gift: java.math.BigDecimal?,
)

internal data class MimoTokenPlanSnapshot(
    val plan: String,
    val used: java.math.BigDecimal,
    val limit: java.math.BigDecimal,
    val remaining: java.math.BigDecimal,
    val expiresAt: String,
)

internal fun readMimoPayAsYouGo(payload: JSONObject): MimoPayAsYouGoSnapshot {
    val data = payload.optJSONObject("data") ?: payload
    val cash = listOf("balance", "cashBalance", "cash_balance", "availableBalance", "available_balance")
        .asSequence().mapNotNull { amountDecimalValue(data.opt(it)) }.firstOrNull()
        ?: error("Xiaomi MIMO 响应中没有余额")
    val gift = listOf("giftBalance", "gift_balance", "赠送余额")
        .asSequence().mapNotNull { amountDecimalValue(data.opt(it)) }.firstOrNull()
    return MimoPayAsYouGoSnapshot(cash, gift)
}

internal fun readMimoTokenPlan(detailPayload: JSONObject, usagePayload: JSONObject): MimoTokenPlanSnapshot {
    val detail = detailPayload.optJSONObject("data") ?: detailPayload
    val usage = usagePayload.optJSONObject("data") ?: usagePayload
    val pair = findTokenUsagePair(usage) ?: findTokenUsagePair(detail)
        ?: error("Xiaomi MIMO Token Plan 响应中没有用量")
    val plan = listOf("planName", "plan_name", "name", "planCode", "plan_code")
        .asSequence().map { detail.optString(it).trim() }.firstOrNull { it.isNotBlank() } ?: "Token Plan"
    val expires = listOf("expireTime", "expire_time", "validUntil", "valid_until", "endTime", "end_time")
        .asSequence().map { detail.optString(it).trim() }.firstOrNull { it.isNotBlank() }.orEmpty()
    val used = pair.first.max(java.math.BigDecimal.ZERO)
    val limit = pair.second.max(used)
    return MimoTokenPlanSnapshot(plan, used, limit, limit.subtract(used).max(java.math.BigDecimal.ZERO), expires)
}

private fun findTokenUsagePair(value: Any?): Pair<java.math.BigDecimal, java.math.BigDecimal>? {
    when (value) {
        is JSONObject -> {
            val used = listOf("used", "usedTokens", "used_tokens", "usedCredits", "used_credits", "usage", "consumed")
                .asSequence().mapNotNull { amountDecimalValue(value.opt(it)) }.firstOrNull()
            val limit = listOf("limit", "total", "totalTokens", "total_tokens", "totalCredits", "total_credits", "quota", "credits")
                .asSequence().mapNotNull { amountDecimalValue(value.opt(it)) }.firstOrNull()
            if (used != null && limit != null && limit.signum() > 0) return used to limit
            val keys = value.keys()
            while (keys.hasNext()) findTokenUsagePair(value.opt(keys.next()))?.let { return it }
        }
        is JSONArray -> for (index in 0 until value.length()) findTokenUsagePair(value.opt(index))?.let { return it }
    }
    return null
}

private fun amountDecimalValue(value: Any?): java.math.BigDecimal? = when (value) {
    is Number -> value.toString().toBigDecimalOrNull()
    is String -> value.trim().toBigDecimalOrNull()
    else -> null
}

private fun formatTokenCount(value: java.math.BigDecimal): String = runCatching {
    val rounded = value.setScale(0, java.math.RoundingMode.DOWN).toBigInteger()
    java.text.DecimalFormat("#,###").format(rounded)
}.getOrDefault(value.stripTrailingZeros().toPlainString())

/** Stable, credential-free balance text shared by the app, widgets, and rear-display surfaces. */
internal fun balanceDisplayValue(service: BalanceService): String {
    if (service.balance == "--") return "--"
    if (service.displayKind == BalanceDisplayKind.TOKEN_PLAN) {
        val total = service.total.toBigDecimalOrNull() ?: return "--"
        val remaining = service.balance.toBigDecimalOrNull() ?: return "--"
        if (total.signum() <= 0) return "--"
        val used = service.used.toBigDecimalOrNull() ?: total.subtract(remaining)
        val presented = when (service.tokenPlanDisplay) {
            TokenPlanDisplay.USED -> used.max(java.math.BigDecimal.ZERO)
            TokenPlanDisplay.REMAINING -> remaining.max(java.math.BigDecimal.ZERO)
        }
        return "${presented.divide(total, 4, java.math.RoundingMode.HALF_UP).multiply(java.math.BigDecimal(100)).setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()}%"
    }
    val amount = runCatching {
        java.math.BigDecimal(service.balance).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
    }.getOrDefault(service.balance)
    return when {
        service.currency.equals("USD", ignoreCase = true) -> "\$$amount"
        service.currency.equals("CNY", ignoreCase = true) -> "¥$amount"
        else -> "${service.currency} $amount"
    }
}
