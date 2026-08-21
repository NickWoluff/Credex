package org.orynnx.codexquota

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorRes

internal enum class WidgetWindow { WEEKLY, FIVE_HOUR, NONE }

/** Pure presentation model, shared by RemoteViews rendering and unit tests. */
internal data class QuotaWidgetPresentation(
    val primaryWindow: WidgetWindow,
    val primaryRemaining: Int,
    val primaryReset: String,
    val primaryResetAtEpoch: Long,
    val showFiveHourSecondary: Boolean,
    val fiveHourRemaining: Int,
    val fiveHourReset: String,
    val fiveHourResetAtEpoch: Long,
    val health: QuotaHealth,
    val primaryBalance: BalanceService? = null,
    val secondaryBalance: BalanceService? = null,
    val balanceHint: BalanceService? = null,
    val showSecondary: Boolean = showFiveHourSecondary,
)

internal object QuotaWidgetPresenter {
    // Below 280dp, the secondary lane would violate its text and touch-safe spacing.
    const val MEDIUM_MIN_WIDTH_DP = 280

    fun isCompact(minWidthDp: Int) = minWidthDp < MEDIUM_MIN_WIDTH_DP

    fun present(state: QuotaState, compact: Boolean): QuotaWidgetPresentation =
        present(state, emptyList(), compact, showCodex = true)

    fun present(
        state: QuotaState,
        balances: List<BalanceService>,
        compact: Boolean,
        showCodex: Boolean,
        allowCodexSecondary: Boolean = true,
    ): QuotaWidgetPresentation {
        val hasCodexWindow = showCodex && (state.hasWeekly || state.hasFiveHour)
        val primaryWindow = when {
            hasCodexWindow && state.hasWeekly -> WidgetWindow.WEEKLY
            hasCodexWindow && state.hasFiveHour -> WidgetWindow.FIVE_HOUR
            else -> WidgetWindow.NONE
        }
        val primaryBalance = if (primaryWindow == WidgetWindow.NONE) balances.firstOrNull() else null
        // 4×2 只选 Codex 时利用第二栏展示 5 小时窗口；若还选了另一服务，第二栏应让给该服务。
        val showFiveHourSecondary = allowCodexSecondary && !compact && balances.isEmpty() &&
            hasCodexWindow && state.hasWeekly && state.hasFiveHour
        val secondaryBalance = if (!compact && !showFiveHourSecondary) {
            if (primaryBalance != null) balances.getOrNull(1) else balances.firstOrNull()
        } else {
            null
        }
        return QuotaWidgetPresentation(
            primaryWindow = primaryWindow,
            primaryRemaining = when (primaryWindow) {
                WidgetWindow.WEEKLY -> state.weeklyRemaining
                WidgetWindow.FIVE_HOUR -> state.fiveHourRemaining
                WidgetWindow.NONE -> -1
            },
            primaryReset = when (primaryWindow) {
                WidgetWindow.WEEKLY -> state.weeklyReset
                WidgetWindow.FIVE_HOUR -> state.fiveHourReset
                WidgetWindow.NONE -> "--"
            },
            primaryResetAtEpoch = when (primaryWindow) {
                WidgetWindow.WEEKLY -> state.weeklyResetAtEpoch
                WidgetWindow.FIVE_HOUR -> state.fiveHourResetAtEpoch
                WidgetWindow.NONE -> 0L
            },
            // A compact 2-cell widget deliberately shows one window only.
            showFiveHourSecondary = showFiveHourSecondary,
            fiveHourRemaining = state.fiveHourRemaining,
            fiveHourReset = state.fiveHourReset,
            fiveHourResetAtEpoch = state.fiveHourResetAtEpoch,
            health = state.health,
            primaryBalance = primaryBalance,
            secondaryBalance = secondaryBalance,
            balanceHint = if (primaryWindow != WidgetWindow.NONE) balances.firstOrNull() else null,
            showSecondary = showFiveHourSecondary || secondaryBalance != null,
        )
    }
}

/**
 * Front-launcher widget. Unlike the rear display, this uses touch-first, launcher-safe
 * RemoteViews with responsive compact/medium compositions and no continuous animation.
 */
open class QuotaAppWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val state = QuotaRepository.current(context)
        appWidgetIds.forEach { id ->
            updateOne(context, manager, id, state, compact = isCompact(manager, id), refreshing = false)
        }
        enqueueVisibleRefresh(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        migrateMiuiWidgetIds(context, manager, newOptions)
        updateOne(
            context,
            manager,
            appWidgetId,
            QuotaRepository.current(context),
            compact = QuotaWidgetPresenter.isCompact(
                newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 120),
            ),
            refreshing = false,
        )
        enqueueVisibleRefresh(context)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        WidgetSelectionPreferences.remap(context, oldWidgetIds, newWidgetIds)
        updateAll(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetSelectionPreferences.clear(context, it) }
        super.onDeleted(context, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
        if ((QuotaRepository.signedIn(context) || StandardBalanceRepository.hasAuthenticatedService(context)) && QuotaRepository.backgroundEnabled(context)) {
            QuotaRefreshScheduler.schedule(context)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PINNED -> {
                updateAll(context)
                enqueueVisibleRefresh(context)
                return
            }
            ACTION_MIUI_UPDATE -> {
                val manager = AppWidgetManager.getInstance(context)
                val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                    ?: manager.getAppWidgetIds(ComponentName(context, javaClass))
                onUpdate(context, manager, ids)
                return
            }
        }
        super.onReceive(context, intent)
    }

    private fun migrateMiuiWidgetIds(
        context: Context,
        manager: AppWidgetManager,
        options: android.os.Bundle,
    ) {
        if (!options.getBoolean(MIUI_ID_CHANGED) || options.getBoolean(MIUI_ID_CHANGED_COMPLETE)) return
        val newIds = options.getIntArray(MIUI_NEW_IDS)
        WidgetSelectionPreferences.remap(context, options.getIntArray(MIUI_OLD_IDS), newIds)
        options.putBoolean(MIUI_ID_CHANGED_COMPLETE, true)
        (newIds ?: IntArray(0)).forEach { manager.updateAppWidgetOptions(it, options) }
    }

    /**
     * Android exposes no general foreground/visibility callback for App Widgets. Launchers call
     * [onUpdate] when a widget is first shown, while HyperOS additionally forwards its exposure
     * update broadcast. Both routes arrive here and share one persisted five-minute lease.
     */
    private fun enqueueVisibleRefresh(context: Context) {
        if (WidgetExposureRefreshGate.tryClaim(context)) requestRefresh(context, force = false)
    }

    companion object {
        const val ACTION_PINNED = "org.orynnx.codexquota.action.WIDGET_PINNED"
        const val ACTION_MIUI_UPDATE = "miui.appwidget.action.APPWIDGET_UPDATE"
        private const val MIUI_ID_CHANGED = "miuiIdChanged"
        private const val MIUI_ID_CHANGED_COMPLETE = "miuiIdChangedComplete"
        private const val MIUI_OLD_IDS = "miuiOldIds"
        private const val MIUI_NEW_IDS = "miuiNewIds"

        fun updateAll(context: Context, state: QuotaState = QuotaRepository.current(context), refreshing: Boolean = false) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            updateAllFor(appContext, manager, QuotaAppWidgetProvider::class.java, state, refreshing)
            updateAllFor(appContext, manager, XiaomiQuotaWidgetProvider::class.java, state, refreshing)
        }

        private fun updateAllFor(
            context: Context,
            manager: AppWidgetManager,
            provider: Class<out AppWidgetProvider>,
            state: QuotaState,
            refreshing: Boolean,
        ) {
            manager.getAppWidgetIds(ComponentName(context, provider)).forEach { id ->
                updateOne(
                    context,
                    manager,
                    id,
                    state,
                    compact = isCompact(manager, id),
                    refreshing = refreshing,
                )
            }
        }

        internal fun updateOne(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
            state: QuotaState,
            compact: Boolean,
            refreshing: Boolean,
        ) {
            captureRecommendedHeight(context, manager, appWidgetId)
            val configured = if (WidgetSelectionPreferences.hasGlobalSelection(context)) {
                WidgetSelectionPreferences.globalSelection(context, compact)
            } else {
                WidgetSelectionPreferences.get(context, appWidgetId)
            }
            val allBalances = StandardBalanceRepository.forSurface(context, BalanceSurface.LAUNCHER, Int.MAX_VALUE)
            val selected = configured.ifEmpty {
                buildList {
                    if (DashboardPreferences.showCodex(context) && (state.hasWeekly || state.hasFiveHour)) add(WidgetSelectionPreferences.CODEX_ID)
                    if (isEmpty()) allBalances.firstOrNull()?.let { add(it.id) }
                }
            }
            val byId = allBalances.associateBy(BalanceService::id)
            val primaryId = selected.firstOrNull()
            val showCodex = primaryId == WidgetSelectionPreferences.CODEX_ID
            val balances = selected.mapNotNull { id ->
                when {
                    id == WidgetSelectionPreferences.CODEX_ID && !showCodex -> codexAsBalance(state)
                    else -> byId[id]
                }
            }.take(if (compact) 1 else 2)
            val views = createViews(
                context,
                if (compact) R.layout.widget_quota_small else R.layout.widget_quota_medium,
                state,
                balances,
                compact,
                refreshing,
                showCodex,
                allowCodexSecondary = !WidgetSelectionPreferences.hasGlobalSelection(context),
                cardHeightDp = WidgetHeightPreferences.preferredHeightDp(context),
            )
            manager.updateAppWidget(appWidgetId, views)
        }

        private fun createViews(
            context: Context,
            layoutId: Int,
            state: QuotaState,
            balances: List<BalanceService>,
            compact: Boolean,
            refreshing: Boolean,
            showCodex: Boolean,
            allowCodexSecondary: Boolean,
            cardHeightDp: Int,
        ): RemoteViews {
            val presentation = QuotaWidgetPresenter.present(
                state,
                balances,
                compact,
                showCodex = showCodex,
                allowCodexSecondary = allowCodexSecondary,
            )
            // A 4x2 widget with one service reuses the full-width layout so the hidden
            // second column never consumes half of the available text space.
            val resolvedLayoutId = if (compact || !presentation.showSecondary) {
                R.layout.widget_quota_small
            } else {
                layoutId
            }
            return RemoteViews(context.packageName, resolvedLayoutId).apply {
                // Leave width entirely to the launcher. Height is applied only after
                // a 2x2 recommendation or a valid user override is available.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && cardHeightDp > 0) {
                    setViewLayoutHeight(
                        R.id.widget_card_surface,
                        cardHeightDp.toFloat(),
                        TypedValue.COMPLEX_UNIT_DIP,
                    )
                    setViewLayoutHeight(
                        R.id.widget_card_content,
                        cardHeightDp.toFloat(),
                        TypedValue.COMPLEX_UNIT_DIP,
                    )
                    if (resolvedLayoutId == R.layout.widget_quota_medium) {
                        setViewLayoutHeight(
                            R.id.widget_service_divider,
                            minOf(108, (cardHeightDp - 36).coerceAtLeast(32)).toFloat(),
                            TypedValue.COMPLEX_UNIT_DIP,
                        )
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setViewOutlinePreferredRadius(
                        R.id.widget_card_surface,
                        WidgetHeightPreferences.preferredCornerRadiusDp(context).toFloat(),
                        TypedValue.COMPLEX_UNIT_DIP,
                    )
                }
                val openApp = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                setOnClickPendingIntent(android.R.id.background, openApp)

                val compactDetails = compact || presentation.showSecondary
                bindPrimary(context, presentation, state, compactDetails)
                val showHealth = DashboardPreferences.showHealth(context)
                setTextColor(
                    R.id.widget_primary_status_dot,
                    context.getColor(statusColor(state.health, presentation.primaryBalance, refreshing = false)),
                )
                setViewVisibility(R.id.widget_primary_status_dot, if (showHealth) View.VISIBLE else View.GONE)
                if (!compact && presentation.showSecondary) {
                    val showSecondary = presentation.showSecondary
                    setViewVisibility(
                        R.id.widget_secondary_group,
                        if (showSecondary) View.VISIBLE else View.GONE,
                    )
                    setViewVisibility(
                        R.id.widget_secondary_title_group,
                        if (showSecondary) View.VISIBLE else View.GONE,
                    )
                    setViewVisibility(
                        R.id.widget_service_divider,
                        if (showSecondary) View.VISIBLE else View.GONE,
                    )
                    if (presentation.showFiveHourSecondary) {
                        setTextViewText(R.id.widget_secondary_label, context.getString(R.string.widget_five_hour))
                        setTextViewText(R.id.widget_secondary_value, "${presentation.fiveHourRemaining.coerceIn(0, 100)}%")
                        setTextViewText(
                            R.id.widget_secondary_reset,
                            context.getString(
                                R.string.widget_reset_at,
                                QuotaResetText.widgetCompact(presentation.fiveHourReset, presentation.fiveHourResetAtEpoch),
                            ),
                        )
                        setProgressBar(R.id.widget_secondary_progress, 100, presentation.fiveHourRemaining.coerceIn(0, 100), false)
                        setViewVisibility(R.id.widget_secondary_progress, View.VISIBLE)
                    } else if (presentation.secondaryBalance != null) {
                        val service = presentation.secondaryBalance
                        setTextViewText(R.id.widget_secondary_label, service.name)
                        setTextViewText(R.id.widget_secondary_value, balanceDisplayValue(service))
                        setTextViewText(R.id.widget_secondary_reset, balanceSubtext(service, compact = true))
                        val percent = balanceDisplayPercent(service)
                        if (percent != null) {
                            setProgressBar(R.id.widget_secondary_progress, 100, percent, false)
                            setViewVisibility(R.id.widget_secondary_progress, View.VISIBLE)
                        } else {
                            setViewVisibility(R.id.widget_secondary_progress, View.INVISIBLE)
                        }
                    }
                    val secondaryBalance = presentation.secondaryBalance
                    setTextColor(
                        R.id.widget_secondary_status_dot,
                        context.getColor(statusColor(state.health, secondaryBalance, refreshing = false)),
                    )
                    setViewVisibility(R.id.widget_secondary_status_dot, if (showHealth) View.VISIBLE else View.GONE)
                }

                setTextViewText(R.id.widget_status, lastUpdatedText(context, state, presentation, refreshing))
                setViewVisibility(R.id.widget_status_group, View.VISIBLE)
            }
        }

        private fun RemoteViews.bindPrimary(
            context: Context,
            presentation: QuotaWidgetPresentation,
            state: QuotaState,
            compact: Boolean,
        ) {
            if (presentation.primaryBalance != null) {
                val service = presentation.primaryBalance
                setTextViewText(R.id.widget_primary_label, service.name)
                setTextViewText(R.id.widget_primary_value, balanceDisplayValue(service))
                setTextViewText(R.id.widget_primary_reset, balanceSubtext(service, compact))
                val percent = balanceDisplayPercent(service)
                if (percent != null) {
                    setProgressBar(R.id.widget_primary_progress, 100, percent, false)
                    setViewVisibility(R.id.widget_primary_progress, View.VISIBLE)
                } else {
                    setViewVisibility(R.id.widget_primary_progress, View.INVISIBLE)
                }
                return
            }
            when (presentation.primaryWindow) {
                WidgetWindow.WEEKLY, WidgetWindow.FIVE_HOUR -> {
                    setTextViewText(
                        R.id.widget_primary_label,
                        context.getString(
                            if (presentation.primaryWindow == WidgetWindow.WEEKLY) R.string.widget_weekly
                            else R.string.widget_five_hour,
                        ),
                    )
                    setTextViewText(R.id.widget_primary_value, "${presentation.primaryRemaining.coerceIn(0, 100)}%")
                        setTextViewText(
                            R.id.widget_primary_reset,
                            context.getString(
                                R.string.widget_reset_at,
                                if (compact) {
                                    QuotaResetText.widgetCompact(presentation.primaryReset, presentation.primaryResetAtEpoch)
                                } else {
                                    presentation.primaryReset
                                },
                            ),
                    )
                    setProgressBar(R.id.widget_primary_progress, 100, presentation.primaryRemaining.coerceIn(0, 100), false)
                    setViewVisibility(R.id.widget_primary_progress, View.VISIBLE)
                }
                WidgetWindow.NONE -> {
                    setTextViewText(R.id.widget_primary_label, context.getString(R.string.widget_codex_usage))
                    setTextViewText(
                        R.id.widget_primary_value,
                        context.getString(
                            when (state.health) {
                                QuotaHealth.AUTH_REQUIRED -> R.string.widget_authorize
                                QuotaHealth.SIGNED_OUT -> R.string.widget_sign_in
                                else -> R.string.widget_no_data
                            },
                        ),
                    )
                    setTextViewText(
                        R.id.widget_primary_reset,
                        context.getString(
                            if (state.health == QuotaHealth.AUTH_REQUIRED) R.string.widget_tap_reauthorize
                            else R.string.widget_tap_to_open,
                        ),
                    )
                    setViewVisibility(R.id.widget_primary_progress, View.INVISIBLE)
                }
            }
        }

        private fun lastUpdatedText(
            context: Context,
            state: QuotaState,
            presentation: QuotaWidgetPresentation,
            refreshing: Boolean,
        ): String {
            val updatedAt = presentation.primaryBalance?.updatedAt
                ?.takeUnless { it == "--" }
                ?: state.updatedAt
            val text = context.getString(R.string.widget_last_updated_at, updatedAt)
            return if (refreshing) "${context.getString(R.string.widget_refreshing)} · $text" else text
        }

        @ColorRes
        private fun statusColor(health: QuotaHealth, balance: BalanceService?, refreshing: Boolean) = when {
            refreshing -> R.color.widget_status_refreshing
            balance?.health == BalanceHealth.FRESH -> R.color.widget_status_success
            balance?.health == BalanceHealth.CACHED -> R.color.widget_status_warning
            balance?.health == BalanceHealth.AUTH_REQUIRED || balance?.health == BalanceHealth.ERROR -> R.color.widget_status_error
            health == QuotaHealth.FRESH || health == QuotaHealth.EMPTY -> R.color.widget_status_success
            health == QuotaHealth.AUTH_REQUIRED -> R.color.widget_status_error
            health == QuotaHealth.CACHED -> R.color.widget_status_warning
            else -> R.color.widget_text_muted
        }

        private fun requestRefresh(context: Context, force: Boolean) {
            val appContext = context.applicationContext
            val hasConnection = QuotaRepository.signedIn(appContext) || StandardBalanceRepository.hasAuthenticatedService(appContext)
            if (!hasConnection) {
                updateAll(appContext, QuotaState())
                return
            }
            updateAll(appContext, QuotaRepository.current(appContext), refreshing = true)
            if (!QuotaRefreshScheduler.requestImmediate(appContext, force)) {
                updateAll(appContext)
            }
        }

        private fun balanceSubtext(service: BalanceService, compact: Boolean): String {
            val fallback = service.detail.ifBlank {
                listOf(service.status, service.updatedAt.takeUnless { it == "--" })
                    .filterNotNull()
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
            }
            if (service.displayKind != BalanceDisplayKind.TOKEN_PLAN) return fallback
            if (!compact) return fallback

            val total = service.total.toBigDecimalOrNull() ?: return fallback.replaceFirst(" · ", "\n")
            val remaining = service.balance.toBigDecimalOrNull() ?: return fallback.replaceFirst(" · ", "\n")
            val used = service.used.toBigDecimalOrNull() ?: total.subtract(remaining)
            val displayed = if (service.tokenPlanDisplay == TokenPlanDisplay.REMAINING) remaining else used
            val label = if (service.tokenPlanDisplay == TokenPlanDisplay.REMAINING) "剩余" else "已用"
            val plan = service.detail.substringBefore(" · ").ifBlank { "Token Plan" }
            val unit = when {
                service.detail.contains("Credits", ignoreCase = true) -> "Credits"
                service.detail.contains("Tokens", ignoreCase = true) -> "Tokens"
                else -> "Tokens"
            }
            return "$plan · $label ${formatWidgetTokenCount(displayed)}/${formatWidgetTokenCount(total)} $unit"
        }

        private fun formatWidgetTokenCount(value: java.math.BigDecimal): String {
            val absolute = value.abs()
            val (divisor, suffix) = when {
                absolute >= java.math.BigDecimal("1000000000") -> java.math.BigDecimal("1000000000") to "B"
                absolute >= java.math.BigDecimal("1000000") -> java.math.BigDecimal("1000000") to "M"
                absolute >= java.math.BigDecimal("1000") -> java.math.BigDecimal("1000") to "K"
                else -> return value.setScale(0, java.math.RoundingMode.DOWN).toPlainString()
            }
            return value.divide(divisor, 1, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString() + suffix
        }

        private fun balanceDisplayPercent(service: BalanceService): Int? =
            balanceDisplayValue(service)
                .takeIf { service.displayKind == BalanceDisplayKind.TOKEN_PLAN && it.endsWith('%') }
                ?.dropLast(1)
                ?.toDoubleOrNull()
                ?.let { kotlin.math.ceil(it).toInt() }
                ?.coerceIn(0, 100)

        internal fun isCompact(manager: AppWidgetManager, appWidgetId: Int): Boolean =
            QuotaWidgetPresenter.isCompact(
                manager.getAppWidgetOptions(appWidgetId)
                    .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 120),
            )

        private fun codexAsBalance(state: QuotaState): BalanceService? {
            val remaining = when {
                state.hasWeekly -> state.weeklyRemaining
                state.hasFiveHour -> state.fiveHourRemaining
                else -> return null
            }.coerceIn(0, 100)
            val reset = if (state.hasWeekly) state.weeklyReset else state.fiveHourReset
            return BalanceService(
                id = WidgetSelectionPreferences.CODEX_ID,
                name = "OpenAI Codex",
                endpoint = "",
                balance = remaining.toString(),
                detail = reset,
                updatedAt = state.updatedAt,
                status = if (state.health == QuotaHealth.CACHED) "缓存" else "已连接",
                health = if (state.health == QuotaHealth.CACHED) BalanceHealth.CACHED else BalanceHealth.FRESH,
                displayKind = BalanceDisplayKind.TOKEN_PLAN,
                used = (100 - remaining).toString(),
                total = "100",
                tokenPlanDisplay = TokenPlanDisplay.REMAINING,
                )
            }
        private fun captureRecommendedHeight(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            if (WidgetHeightPreferences.hasRecordedRecommendation(context)) return
            val options = manager.getAppWidgetOptions(appWidgetId)
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            // A launcher reports cell bounds in dp. Only capture an initial square
            // (2x2) instance; resized and wide instances must not change the baseline.
            if (width > 0 && height > 0 && kotlin.math.abs(width - height) <= 32) {
                WidgetHeightPreferences.recordRecommendationIfAbsent(context, width)
            }
        }
    }
}

/** Persists visibility-driven refreshes across process recreation without retaining user data. */
internal object WidgetExposureRefreshGate {
    private const val PREFERENCES = "widget_exposure_refresh"
    private const val LAST_REFRESH_ELAPSED = "last_refresh_elapsed"
    internal const val MIN_INTERVAL_MS = 5 * 60 * 1_000L

    @Synchronized
    fun tryClaim(context: Context, nowElapsed: Long = SystemClock.elapsedRealtime()): Boolean {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val previous = preferences.getLong(LAST_REFRESH_ELAPSED, 0L)
        if (!canRefresh(previous, nowElapsed)) return false
        preferences.edit().putLong(LAST_REFRESH_ELAPSED, nowElapsed).apply()
        return true
    }

    internal fun canRefresh(previousElapsed: Long, nowElapsed: Long): Boolean =
        previousElapsed <= 0L || nowElapsed < previousElapsed || nowElapsed - previousElapsed >= MIN_INTERVAL_MS
}

/** Xiaomi HyperOS widget provider. It is intentionally a separate receiver so the
 * native Android widget pool and Xiaomi's widget center can evolve independently. */
class XiaomiQuotaWidgetProvider : QuotaAppWidgetProvider()
