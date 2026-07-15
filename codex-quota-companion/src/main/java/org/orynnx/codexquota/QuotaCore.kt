package org.orynnx.codexquota

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import androidx.core.net.toUri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class QuotaHealth { SIGNED_OUT, FRESH, EMPTY, CACHED, AUTH_REQUIRED }

data class QuotaState(
    val fiveHourRemaining: Int = -1, val fiveHourReset: String = "--",
    val fiveHourResetAtEpoch: Long = 0L,
    val weeklyRemaining: Int = -1, val weeklyReset: String = "--",
    val weeklyResetAtEpoch: Long = 0L,
    val plan: String = "", val status: String = "Not signed in", val updatedAt: String = "--",
    val lastAttemptAt: String = "--", val health: QuotaHealth = QuotaHealth.SIGNED_OUT,
) {
    val hasFiveHour: Boolean get() = fiveHourRemaining >= 0
    val hasWeekly: Boolean get() = weeklyRemaining >= 0
    fun json() = JSONObject().apply {
        put("five", fiveHourRemaining); put("fiveReset", fiveHourReset); put("fiveResetAt", fiveHourResetAtEpoch)
        put("week", weeklyRemaining); put("weekReset", weeklyReset); put("weekResetAt", weeklyResetAtEpoch)
        put("plan", plan); put("status", status); put("updated", updatedAt); put("attempted", lastAttemptAt); put("health", health.name)
    }.toString()
    companion object {
        fun from(raw: String?) = runCatching {
            JSONObject(raw ?: "{}").let {
                val status = it.optString("status", "Not signed in")
                QuotaState(
                    fiveHourRemaining = it.optInt("five", -1),
                    fiveHourReset = it.optString("fiveReset", "--"),
                    fiveHourResetAtEpoch = it.optLong("fiveResetAt", 0L),
                    weeklyRemaining = it.optInt("week", -1),
                    weeklyReset = it.optString("weekReset", "--"),
                    weeklyResetAtEpoch = it.optLong("weekResetAt", 0L),
                    plan = it.optString("plan"),
                    status = status,
                    updatedAt = it.optString("updated", "--"),
                    lastAttemptAt = it.optString("attempted", "--"),
                    health = runCatching { QuotaHealth.valueOf(it.optString("health")) }.getOrDefault(
                        when {
                            status == "OK" -> QuotaHealth.FRESH
                            status == "Not signed in" -> QuotaHealth.SIGNED_OUT
                            else -> QuotaHealth.CACHED
                        },
                    ),
                )
            }
        }.getOrDefault(QuotaState())
    }
}

/**
 * Formats a reset timestamp for surfaces that have room for a useful countdown.
 * The absolute timestamp stays visible so the value remains meaningful when a
 * surface is cached or the countdown is not available.
 */
internal object QuotaResetText {
    fun app(reset: String, resetAtEpoch: Long, nowEpoch: Long = Instant.now().epochSecond): String =
        withCountdown(reset, resetAtEpoch, nowEpoch)

    fun widgetStatus(resetAtEpoch: Long, nowEpoch: Long = Instant.now().epochSecond): String =
        countdown(resetAtEpoch, nowEpoch).orEmpty()

    /** One line for the compact widget: prefer the relative value when it fits. */
    fun widgetCompact(reset: String, resetAtEpoch: Long, nowEpoch: Long = Instant.now().epochSecond): String {
        return compactCountdown(resetAtEpoch, nowEpoch) ?: reset
    }

    private fun withCountdown(reset: String, resetAtEpoch: Long, nowEpoch: Long): String {
        val countdown = countdown(resetAtEpoch, nowEpoch) ?: return reset
        return "$reset · $countdown"
    }

    private fun countdown(resetAtEpoch: Long, nowEpoch: Long): String? {
        if (resetAtEpoch <= nowEpoch) return null
        val totalMinutes = ((resetAtEpoch - nowEpoch) + 59L) / 60L
        val days = totalMinutes / (24L * 60L)
        val hours = (totalMinutes % (24L * 60L)) / 60L
        val minutes = totalMinutes % 60L
        val text = buildString {
            if (days > 0) append(days).append("天")
            if (hours > 0 || days > 0) append(hours).append("小时")
            append(minutes).append("分钟")
        }
        return "于${text}后更新"
    }

    private fun compactCountdown(resetAtEpoch: Long, nowEpoch: Long): String? {
        if (resetAtEpoch <= nowEpoch) return null
        val totalMinutes = ((resetAtEpoch - nowEpoch) + 59L) / 60L
        val days = totalMinutes / (24L * 60L)
        val hours = (totalMinutes % (24L * 60L)) / 60L
        val minutes = totalMinutes % 60L
        val text = when {
            days > 0 && hours > 0 -> "${days}天${hours}小时后更新"
            days > 0 -> "${days}天后更新"
            hours > 0 -> "${hours}小时后更新"
            else -> "${minutes}分钟后更新"
        }
        return text
    }
}

internal data class QuotaWindow(
    val remaining: Int,
    val resetAtEpoch: Long,
)

internal data class ClassifiedQuotaWindows(
    val fiveHour: QuotaWindow? = null,
    val weekly: QuotaWindow? = null,
)

/**
 * The API's primary/secondary ordering is not a duration contract. In particular,
 * an account with only a weekly limit can return that week as primary_window.
 */
internal object QuotaWindowClassifier {
    private const val FIVE_HOURS_SECONDS = 5L * 60L * 60L
    private const val WEEK_SECONDS = 7L * 24L * 60L * 60L

    fun classify(rateLimit: JSONObject): ClassifiedQuotaWindows {
        var fiveHour: QuotaWindow? = null
        var weekly: QuotaWindow? = null

        listOf("primary_window", "secondary_window").forEach { key ->
            val window = rateLimit.optJSONObject(key) ?: return@forEach
            val parsed = parse(window) ?: return@forEach
            when (durationSeconds(window)) {
                FIVE_HOURS_SECONDS -> if (fiveHour == null) fiveHour = parsed
                WEEK_SECONDS -> if (weekly == null) weekly = parsed
            }
        }

        return ClassifiedQuotaWindows(fiveHour = fiveHour, weekly = weekly)
    }

    private fun durationSeconds(window: JSONObject): Long? {
        val seconds = window.optLong("limit_window_seconds", 0L)
        if (seconds > 0L) return seconds
        val minutes = window.optLong("window_minutes", 0L)
        return minutes.takeIf { it > 0L }?.times(60L)
    }

    private fun parse(window: JSONObject): QuotaWindow? {
        val remaining = when {
            window.has("remaining_percentage") && !window.isNull("remaining_percentage") ->
                window.optDouble("remaining_percentage", Double.NaN)
            window.has("used_percent") && !window.isNull("used_percent") ->
                100.0 - window.optDouble("used_percent", Double.NaN)
            else -> Double.NaN
        }
        if (!remaining.isFinite()) return null
        return QuotaWindow(
            remaining = remaining.toInt().coerceIn(0, 100),
            resetAtEpoch = window.optLong("reset_at", 0L),
        )
    }
}

/** Cancellation gate whose critical section is serialized with cancel(). */
internal class RefreshLease {
    private val monitor = Any()
    private var active = true

    fun cancel() = synchronized(monitor) { active = false }

    fun <T : Any> runIfActive(block: () -> T): T? = synchronized(monitor) {
        if (active) block() else null
    }
}

/** Uses an Android Keystore AES key; only this app can decrypt the persisted OAuth token. */
private object SecretBox {
    private const val KEY = "codex_quota_oauth_key"
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
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(spec) }.generateKey()
    }
    fun seal(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }
    fun open(value: String): String = runCatching {
        val bytes = Base64.decode(value, Base64.NO_WRAP); val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)))
    }.getOrDefault("")
}

object QuotaRepository {
    private const val PREFS = "quota"; private const val TOKEN = "token"; private const val REFRESH_TOKEN = "refresh_token"; private const val ACCOUNT = "account"; private const val STATE = "state_v2"; private const val LAST = "last_v2"; private const val BACKGROUND = "background_refresh"; private const val NOTIFICATION_SYNC = "notification_sync"; private const val NOTIFICATION_EDUCATED = "notification_educated"; private const val SESSION_EPOCH = "session_epoch"
    private val sessionLock = Any()

    private data class RefreshSession(
        val epoch: Long,
        val token: String,
        val account: String,
        val previous: QuotaState,
    )

    private sealed interface RefreshStart {
        data class Complete(val state: QuotaState) : RefreshStart
        data class Request(val session: RefreshSession) : RefreshStart
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun current(context: Context) = QuotaState.from(prefs(context).getString(STATE, null))
    internal fun sessionEpoch(context: Context) = prefs(context).getLong(SESSION_EPOCH, 0L)
    fun signedIn(context: Context) = SecretBox.open(prefs(context).getString(TOKEN, "").orEmpty()).isNotBlank()
    fun backgroundEnabled(context: Context) = prefs(context).getBoolean(BACKGROUND, true)
    /** Whether the reliable foreground worker (and its ongoing notification) is enabled. */
    fun notificationSyncEnabled(context: Context) = prefs(context).getBoolean(NOTIFICATION_SYNC, true)
    fun notificationEducationSeen(context: Context) = prefs(context).getBoolean(NOTIFICATION_EDUCATED, false)
    fun markNotificationEducationSeen(context: Context) = prefs(context).edit { putBoolean(NOTIFICATION_EDUCATED, true) }
    fun setBackgroundEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(BACKGROUND, enabled) }
        if (enabled && signedIn(context)) QuotaRefreshScheduler.schedule(context) else QuotaRefreshScheduler.cancel(context)
        if (!enabled) QuotaForegroundService.stop(context)
    }
    fun setNotificationSyncEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(NOTIFICATION_SYNC, enabled) }
        if (!enabled) QuotaForegroundService.stop(context)
    }
    fun saveTokens(context: Context, tokens: OAuthTokens) = saveTokens(context, tokens, expectedEpoch = null)

    private fun saveTokens(context: Context, tokens: OAuthTokens, expectedEpoch: Long?) {
        synchronized(sessionLock) {
            val p = prefs(context)
            if (expectedEpoch != null) {
                check(p.getLong(SESSION_EPOCH, 0L) == expectedEpoch) { "Session changed" }
            }
            p.edit {
                putString(TOKEN, SecretBox.seal(tokens.accessToken))
                if (expectedEpoch == null) {
                    // A fresh authorization must never inherit another account's cache.
                    putLong(SESSION_EPOCH, p.getLong(SESSION_EPOCH, 0L) + 1L)
                    remove(REFRESH_TOKEN)
                    remove(ACCOUNT)
                    remove(STATE)
                    remove(LAST)
                }
                // Empty fields on refresh mean "unchanged"; on fresh auth the old fields
                // were removed above before accepting the new account's non-empty values.
                if (tokens.refreshToken.isNotBlank()) putString(REFRESH_TOKEN, SecretBox.seal(tokens.refreshToken))
                if (tokens.accountId.isNotBlank()) putString(ACCOUNT, tokens.accountId)
            }
            if (backgroundEnabled(context)) QuotaRefreshScheduler.schedule(context)
            QuotaAppWidgetProvider.updateAll(context, current(context))
            notifyRearSurfaces(context)
        }
    }

    fun saveAccessToken(context: Context, token: String) {
        synchronized(sessionLock) {
            val p = prefs(context)
            p.edit {
                putString(TOKEN, SecretBox.seal(token))
                putLong(SESSION_EPOCH, p.getLong(SESSION_EPOCH, 0L) + 1L)
                remove(REFRESH_TOKEN)
                remove(ACCOUNT)
                remove(STATE)
                remove(LAST)
            }
            if (backgroundEnabled(context)) QuotaRefreshScheduler.schedule(context)
            QuotaAppWidgetProvider.updateAll(context, QuotaState())
            notifyRearSurfaces(context)
        }
    }

    fun clear(context: Context) {
        synchronized(sessionLock) {
            val p = prefs(context)
            val nextEpoch = p.getLong(SESSION_EPOCH, 0L) + 1L
            // Keep a monotonic tombstone so an in-flight request cannot resurrect old data.
            p.edit { clear(); putLong(SESSION_EPOCH, nextEpoch) }
            QuotaForegroundService.stop(context)
            QuotaRefreshScheduler.cancelAll(context)
            // Never leave a previous account's quota visible on either display surface.
            QuotaAppWidgetProvider.updateAll(context, QuotaState())
            notifyRearSurfaces(context)
        }
    }

    @Synchronized internal fun refresh(
        context: Context,
        force: Boolean = false,
        lease: RefreshLease = RefreshLease(),
    ): QuotaState {
        val p = prefs(context)
        val start = lease.runIfActive {
            synchronized(sessionLock) {
                if (!force && System.currentTimeMillis() - p.getLong(LAST, 0) < 60_000) {
                    val state = current(context)
                    QuotaAppWidgetProvider.updateAll(context, state)
                    RefreshStart.Complete(state)
                } else {
                    val token = SecretBox.open(p.getString(TOKEN, "").orEmpty())
                    if (token.isBlank()) {
                        val state = QuotaState()
                        QuotaAppWidgetProvider.updateAll(context, state)
                        RefreshStart.Complete(state)
                    } else {
                        p.edit { putLong(LAST, System.currentTimeMillis()) }
                        RefreshStart.Request(
                            RefreshSession(
                                epoch = p.getLong(SESSION_EPOCH, 0L),
                                token = token,
                                account = p.getString(ACCOUNT, "").orEmpty(),
                                previous = current(context),
                            ),
                        )
                    }
                }
            }
        } ?: return current(context)
        if (start is RefreshStart.Complete) return start.state
        val session = (start as RefreshStart.Request).session
        val state = runCatching { usage(session.token, session.account) }.recoverCatching { error ->
            if (!error.message.orEmpty().startsWith("HTTP 401")) throw error
            val refresh = lease.runIfActive {
                synchronized(sessionLock) {
                    check(p.getLong(SESSION_EPOCH, 0L) == session.epoch) { "Session changed" }
                    SecretBox.open(p.getString(REFRESH_TOKEN, "").orEmpty())
                }
            } ?: error("Refresh cancelled")
            check(refresh.isNotBlank()) { "HTTP 401 - sign in again" }
            val tokens = CodexOAuth.refreshToken(refresh)
            lease.runIfActive {
                saveTokens(context, tokens, expectedEpoch = session.epoch)
                tokens
            } ?: error("Refresh cancelled")
            usage(tokens.accessToken, tokens.accountId.ifBlank { session.account })
        }.getOrElse { error ->
            val authRequired = error.message.orEmpty().contains("401")
            session.previous.copy(
                status = if (authRequired) "Authorization required" else "Refresh failed",
                lastAttemptAt = clock(),
                health = if (authRequired) QuotaHealth.AUTH_REQUIRED else QuotaHealth.CACHED,
            )
        }
        return lease.runIfActive {
            synchronized(sessionLock) {
                if (p.getLong(SESSION_EPOCH, 0L) != session.epoch) {
                    // Logout or account replacement won while HTTP was in flight.
                    current(context).also { QuotaAppWidgetProvider.updateAll(context, it) }
                } else {
                    p.edit { putString(STATE, state.json()) }
                    // Success, cached fallback, and authorization expiry update placed widgets.
                    state.also { QuotaAppWidgetProvider.updateAll(context, it) }
                }
            }
        } ?: current(context)
    }
    private fun usage(token: String, accountId: String): QuotaState {
        val connection = URL("https://chatgpt.com/backend-api/wham/usage").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"; connection.connectTimeout = 15_000; connection.readTimeout = 15_000
        connection.setRequestProperty("Authorization", "Bearer $token"); connection.setRequestProperty("Accept", "application/json")
        if (accountId.isNotBlank()) connection.setRequestProperty("ChatGPT-Account-Id", accountId)
        check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val limit = json.optJSONObject("rate_limit") ?: JSONObject()
        val windows = QuotaWindowClassifier.classify(limit)
        val five = windows.fiveHour?.remaining ?: -1
        val week = windows.weekly?.remaining ?: -1
        val now = clock()
        return QuotaState(
            fiveHourRemaining = five,
            fiveHourReset = reset(windows.fiveHour?.resetAtEpoch),
            fiveHourResetAtEpoch = windows.fiveHour?.resetAtEpoch ?: 0L,
            weeklyRemaining = week,
            weeklyReset = reset(windows.weekly?.resetAtEpoch),
            weeklyResetAtEpoch = windows.weekly?.resetAtEpoch ?: 0L,
            plan = json.optString("plan_type"),
            status = "OK",
            updatedAt = now,
            lastAttemptAt = now,
            health = if (five < 0 && week < 0) QuotaHealth.EMPTY else QuotaHealth.FRESH,
        )
    }
    private fun reset(epoch: Long?): String = if (epoch != null && epoch > 0) {
        DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(epoch))
    } else {
        "--"
    }
    private fun clock() = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.now())
    private fun notifyRearSurfaces(context: Context) {
        context.contentResolver.notifyChange("content://org.orynnx.codexquota/quota".toUri(), null)
    }
}

data class AuthSession(val url: String, val state: String, val verifier: String)
data class OAuthTokens(val accessToken: String, val refreshToken: String, val accountId: String)

/** OAuth Authorization Code + PKCE through the public Codex client, without running Codex on Android. */
object CodexOAuth {
    private const val CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
    private const val REDIRECT_URI = "http://localhost:1455/auth/callback"

    @Volatile
    private var activeSocket: ServerSocket? = null

    fun createSession(): AuthSession {
        val state = random()
        val verifier = random(64)
        val challenge = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val url = "https://auth.openai.com/oauth/authorize?response_type=code&client_id=$CLIENT_ID&redirect_uri=${enc(REDIRECT_URI)}&scope=${enc("openid profile email offline_access")}&code_challenge=$challenge&code_challenge_method=S256&id_token_add_organizations=true&codex_cli_simplified_flow=true&state=$state&originator=codex_quota_android"
        return AuthSession(url, state, verifier)
    }

    fun exchangeToken(code: String, verifier: String, onResult: (Result<OAuthTokens>) -> Unit) {
        Thread {
            runCatching { exchange(code, verifier) }.also(onResult)
        }.start()
    }

    fun cancel() {
        runCatching { activeSocket?.close() }
        activeSocket = null
    }

    fun listen(session: AuthSession, onReady: () -> Unit, onResult: (Result<OAuthTokens>) -> Unit) {
        cancel()
        Thread {
            runCatching {
                ServerSocket().use { server ->
                    server.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 1455))
                    activeSocket = server
                    server.soTimeout = 120_000
                    onReady()
                    val client = server.accept(); val request = client.getInputStream().bufferedReader().readLine(); val parts = request.split(' ')
                    check(parts.size >= 2 && parts[0] == "GET" && parts[1].startsWith("/auth/callback?")) { "Invalid OAuth callback" }
                    val query = parts[1].substringAfter('?', "")
                    client.getOutputStream().bufferedWriter().use { it.write("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n<h3>Codex Quota sign-in complete. You may return to the app.</h3>") }
                    client.close()
                    val params = query.split('&').associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8") }
                    check(params["state"] == session.state) { "OAuth state mismatch" }; val code = params["code"] ?: error("Authorization cancelled")
                    exchange(code, session.verifier)
                }
            }.also {
                activeSocket = null
                onResult(it)
            }
        }.start()
    }
    fun refreshToken(refreshToken: String): OAuthTokens = tokenRequest("grant_type=refresh_token&refresh_token=${enc(refreshToken)}")
    private fun exchange(code: String, verifier: String): OAuthTokens = tokenRequest("grant_type=authorization_code&code=${enc(code)}&redirect_uri=${enc(REDIRECT_URI)}&code_verifier=${enc(verifier)}")
    private fun tokenRequest(grant: String): OAuthTokens {
        val body = "$grant&client_id=$CLIENT_ID"
        val connection = URL("https://auth.openai.com/oauth/token").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"; connection.doOutput = true
        connection.connectTimeout = 15_000; connection.readTimeout = 15_000
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.outputStream.use { it.write(body.toByteArray()) }; check(connection.responseCode == 200) { "OAuth HTTP ${connection.responseCode}" }
        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        return OAuthTokens(json.getString("access_token"), json.optString("refresh_token"), json.optString("account_id"))
    }
    private fun random(size: Int = 32) = ByteArray(size).also { SecureRandom().nextBytes(it) }.let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
}
