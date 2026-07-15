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
)

internal object QuotaWidgetPresenter {
    // Below 280dp, the secondary lane would violate its text and touch-safe spacing.
    const val MEDIUM_MIN_WIDTH_DP = 280

    fun isCompact(minWidthDp: Int) = minWidthDp < MEDIUM_MIN_WIDTH_DP

    fun present(state: QuotaState, compact: Boolean): QuotaWidgetPresentation {
        val primaryWindow = when {
            state.hasWeekly -> WidgetWindow.WEEKLY
            state.hasFiveHour -> WidgetWindow.FIVE_HOUR
            else -> WidgetWindow.NONE
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
            showFiveHourSecondary = !compact && state.hasWeekly && state.hasFiveHour,
            fiveHourRemaining = state.fiveHourRemaining,
            fiveHourReset = state.fiveHourReset,
            fiveHourResetAtEpoch = state.fiveHourResetAtEpoch,
            health = state.health,
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
        appWidgetIds.forEach { updateOne(context, manager, it, state, refreshing = false) }
        enqueueRefresh(context, force = false)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        updateOne(context, manager, appWidgetId, QuotaRepository.current(context), refreshing = false)
    }

    override fun onEnabled(context: Context) {
        if (QuotaRepository.signedIn(context) && QuotaRepository.backgroundEnabled(context)) {
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
            ids.forEach { updateOne(appContext, manager, it, state, refreshing) }
        }

        private fun updateOne(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
            state: QuotaState,
            refreshing: Boolean,
        ) {
            val views = if (Build.VERSION.SDK_INT >= 31) {
                RemoteViews(
                    mapOf(
                        SizeF(120f, 110f) to createViews(context, R.layout.widget_quota_small, state, compact = true, refreshing),
                        SizeF(280f, 110f) to createViews(context, R.layout.widget_quota_medium, state, compact = false, refreshing),
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
            compact: Boolean,
            refreshing: Boolean,
        ): RemoteViews {
            val presentation = QuotaWidgetPresenter.present(state, compact)
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
                        if (presentation.showFiveHourSecondary) View.VISIBLE else View.GONE,
                    )
                    if (presentation.showFiveHourSecondary) {
                        setTextViewText(R.id.widget_secondary_value, "${presentation.fiveHourRemaining.coerceIn(0, 100)}%")
                        setTextViewText(
                            R.id.widget_secondary_reset,
                            context.getString(
                                R.string.widget_reset_at,
                                QuotaResetText.widgetCompact(presentation.fiveHourReset, presentation.fiveHourResetAtEpoch),
                            ),
                        )
                        setProgressBar(R.id.widget_secondary_progress, 100, presentation.fiveHourRemaining.coerceIn(0, 100), false)
                    }
                }

                val status = statusText(context, state, compact, refreshing)
                setTextViewText(R.id.widget_status, status.first)
                setTextViewText(R.id.widget_status_detail, status.second)
                setTextColor(R.id.widget_status_dot, context.getColor(statusColor(state.health, refreshing)))
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
            compact: Boolean,
            refreshing: Boolean,
        ): Pair<String, String> {
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
            return when (state.health) {
                QuotaHealth.FRESH -> if (compact) {
                    context.getString(R.string.widget_last_updated_at, state.updatedAt) to ""
                } else {
                    context.getString(R.string.widget_last_updated) to
                        listOf(state.updatedAt, countdown).filter { it.isNotBlank() && it != "--" }.joinToString(" · ")
                }
                QuotaHealth.EMPTY -> context.getString(R.string.widget_connected) to context.getString(R.string.widget_no_window)
                QuotaHealth.CACHED -> if (compact) {
                    context.getString(R.string.widget_last_updated_at, state.updatedAt) to ""
                } else {
                    context.getString(R.string.widget_cached) to
                        listOf(context.getString(R.string.widget_last_success, state.updatedAt), countdown)
                            .filter { it.isNotBlank() && it != "--" }
                            .joinToString(" · ")
                }
                QuotaHealth.AUTH_REQUIRED -> context.getString(R.string.widget_auth_required) to context.getString(R.string.widget_tap_to_open)
                QuotaHealth.SIGNED_OUT -> context.getString(R.string.widget_not_connected) to context.getString(R.string.widget_tap_to_open)
            }
        }

        @ColorRes
        private fun statusColor(health: QuotaHealth, refreshing: Boolean) = when {
            refreshing -> R.color.widget_status_refreshing
            health == QuotaHealth.FRESH || health == QuotaHealth.EMPTY -> R.color.widget_status_success
            health == QuotaHealth.AUTH_REQUIRED -> R.color.widget_status_error
            health == QuotaHealth.CACHED -> R.color.widget_status_warning
            else -> R.color.widget_text_muted
        }

        private fun requestRefresh(context: Context, force: Boolean) {
            val appContext = context.applicationContext
            if (!QuotaRepository.signedIn(appContext)) {
                updateAll(appContext, QuotaState())
                return
            }
            updateAll(appContext, QuotaRepository.current(appContext), refreshing = true)
            if (!QuotaRefreshScheduler.requestImmediate(appContext, force)) {
                updateAll(appContext)
            }
        }
    }
}
