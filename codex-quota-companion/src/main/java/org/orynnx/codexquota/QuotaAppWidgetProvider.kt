package org.orynnx.codexquota

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.SizeF
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
    ): QuotaWidgetPresentation {
        val hasCodexWindow = showCodex && (state.hasWeekly || state.hasFiveHour)
        val primaryWindow = when {
            hasCodexWindow && state.hasWeekly -> WidgetWindow.WEEKLY
            hasCodexWindow && state.hasFiveHour -> WidgetWindow.FIVE_HOUR
            else -> WidgetWindow.NONE
        }
        val primaryBalance = if (primaryWindow == WidgetWindow.NONE) balances.firstOrNull() else null
        val showFiveHourSecondary = !compact && hasCodexWindow && state.hasWeekly && state.hasFiveHour
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
class QuotaAppWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val state = QuotaRepository.current(context)
        val balances = StandardBalanceRepository.forSurface(context, BalanceSurface.LAUNCHER, 2)
        appWidgetIds.forEach { updateOne(context, manager, it, state, balances, refreshing = false) }
        enqueueRefresh(context, force = false)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        updateOne(
            context,
            manager,
            appWidgetId,
            QuotaRepository.current(context),
            StandardBalanceRepository.forSurface(context, BalanceSurface.LAUNCHER, 2),
            refreshing = false,
        )
    }

    override fun onEnabled(context: Context) {
        if ((QuotaRepository.signedIn(context) || StandardBalanceRepository.hasAuthenticatedService(context)) && QuotaRepository.backgroundEnabled(context)) {
            QuotaRefreshScheduler.schedule(context)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH -> {
                enqueueRefresh(context, force = true)
                return
            }
            ACTION_PINNED -> {
                updateAll(context)
                enqueueRefresh(context, force = false)
                return
            }
        }
        super.onReceive(context, intent)
    }

    /** Broadcast receivers only enqueue durable work; HTTP is performed by JobService. */
    private fun enqueueRefresh(context: Context, force: Boolean) {
        requestRefresh(context, force)
    }

    companion object {
        const val ACTION_REFRESH = "org.orynnx.codexquota.action.REFRESH_WIDGET"
        const val ACTION_PINNED = "org.orynnx.codexquota.action.WIDGET_PINNED"

        fun updateAll(context: Context, state: QuotaState = QuotaRepository.current(context), refreshing: Boolean = false) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(ComponentName(appContext, QuotaAppWidgetProvider::class.java))
            val balances = StandardBalanceRepository.forSurface(appContext, BalanceSurface.LAUNCHER, 2)
            ids.forEach { updateOne(appContext, manager, it, state, balances, refreshing) }
        }

        private fun updateOne(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
            state: QuotaState,
            balances: List<BalanceService>,
            refreshing: Boolean,
        ) {
            val views = if (Build.VERSION.SDK_INT >= 31) {
                RemoteViews(
                    mapOf(
                        SizeF(120f, 110f) to createViews(context, R.layout.widget_quota_small, state, balances, compact = true, refreshing),
                        SizeF(280f, 110f) to createViews(context, R.layout.widget_quota_medium, state, balances, compact = false, refreshing),
                    ),
                )
            } else {
                val minWidth = manager.getAppWidgetOptions(appWidgetId)
                    .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 120)
                val compact = QuotaWidgetPresenter.isCompact(minWidth)
                createViews(
                    context,
                    if (compact) R.layout.widget_quota_small else R.layout.widget_quota_medium,
                    state,
                    balances,
                    compact,
                    refreshing,
                )
            }
            manager.updateAppWidget(appWidgetId, views)
        }

        private fun createViews(
            context: Context,
            layoutId: Int,
            state: QuotaState,
            balances: List<BalanceService>,
            compact: Boolean,
            refreshing: Boolean,
        ): RemoteViews {
            val presentation = QuotaWidgetPresenter.present(
                state,
                balances,
                compact,
                showCodex = DashboardPreferences.showCodex(context),
            )
            return RemoteViews(context.packageName, layoutId).apply {
                val openApp = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val refresh = PendingIntent.getBroadcast(
                    context,
                    1,
                    Intent(context, QuotaAppWidgetProvider::class.java)
                        .setAction(ACTION_REFRESH)
                        .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                setOnClickPendingIntent(R.id.widget_root, openApp)
                setOnClickPendingIntent(R.id.widget_refresh, refresh)

                bindPrimary(context, presentation, state, compact)
                if (!compact) {
                    setViewVisibility(
                        R.id.widget_secondary_group,
                        if (presentation.showSecondary) View.VISIBLE else View.GONE,
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
                        setTextViewText(R.id.widget_secondary_reset, balanceSubtext(service))
                        setViewVisibility(R.id.widget_secondary_progress, View.INVISIBLE)
                    }
                }

                val status = statusText(context, state, presentation, compact, refreshing)
                setTextViewText(R.id.widget_status, status.first)
                setTextViewText(R.id.widget_status_detail, status.second)
                setTextColor(R.id.widget_status_dot, context.getColor(statusColor(state.health, presentation.primaryBalance, refreshing)))
                setContentDescription(
                    R.id.widget_refresh,
                    context.getString(if (refreshing) R.string.widget_refreshing else R.string.widget_refresh),
                )
                setBoolean(R.id.widget_refresh, "setEnabled", !refreshing)
                setViewVisibility(R.id.widget_refresh_progress, if (refreshing) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.widget_refresh_icon, if (refreshing) View.INVISIBLE else View.VISIBLE)
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
                setTextViewText(R.id.widget_primary_reset, balanceSubtext(service))
                setViewVisibility(R.id.widget_primary_progress, View.INVISIBLE)
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

        private fun statusText(
            context: Context,
            state: QuotaState,
            presentation: QuotaWidgetPresentation,
            compact: Boolean,
            refreshing: Boolean,
        ): Pair<String, String> {
            presentation.primaryBalance?.let { service ->
                if (refreshing) return context.getString(R.string.widget_refreshing) to service.updatedAt
                val detail = listOf(service.status, service.updatedAt.takeUnless { it == "--" }).filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
                return if (compact) service.name to detail else service.name to (service.detail.ifBlank { detail })
            }
            if (refreshing) {
                return context.getString(R.string.widget_refreshing) to
                    state.updatedAt.takeUnless { it == "--" }.orEmpty()
            }
            val primaryReset = when {
                state.hasWeekly -> state.weeklyReset to state.weeklyResetAtEpoch
                state.hasFiveHour -> state.fiveHourReset to state.fiveHourResetAtEpoch
                else -> "--" to 0L
            }
            val countdown = QuotaResetText.widgetStatus(primaryReset.second)
            val balanceHint = presentation.balanceHint?.let { "${it.name} ${balanceDisplayValue(it)}" }
            return when (state.health) {
                QuotaHealth.FRESH -> if (compact) {
                    (balanceHint ?: context.getString(R.string.widget_last_updated_at, state.updatedAt)) to ""
                } else {
                    if (balanceHint != null) {
                        context.getString(R.string.widget_balance) to balanceHint
                    } else {
                        context.getString(R.string.widget_last_updated) to
                            listOf(state.updatedAt, countdown).filter { it.isNotBlank() && it != "--" }.joinToString(" · ")
                    }
                }
                QuotaHealth.EMPTY -> context.getString(R.string.widget_connected) to context.getString(R.string.widget_no_window)
                QuotaHealth.CACHED -> if (compact) {
                    (balanceHint ?: context.getString(R.string.widget_last_updated_at, state.updatedAt)) to ""
                } else {
                    if (balanceHint != null) {
                        context.getString(R.string.widget_cached) to balanceHint
                    } else {
                        context.getString(R.string.widget_cached) to
                            listOf(context.getString(R.string.widget_last_success, state.updatedAt), countdown)
                                .filter { it.isNotBlank() && it != "--" }
                                .joinToString(" · ")
                    }
                }
                QuotaHealth.AUTH_REQUIRED -> context.getString(R.string.widget_auth_required) to context.getString(R.string.widget_tap_to_open)
                QuotaHealth.SIGNED_OUT -> context.getString(R.string.widget_not_connected) to context.getString(R.string.widget_tap_to_open)
            }
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

        private fun balanceSubtext(service: BalanceService): String =
            service.detail.ifBlank {
                listOf(service.status, service.updatedAt.takeUnless { it == "--" })
                    .filterNotNull()
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
            }
    }
}
