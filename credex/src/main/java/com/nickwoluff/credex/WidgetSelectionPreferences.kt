package com.nickwoluff.credex

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.core.content.edit

internal object WidgetSelectionPreferences {
    const val CODEX_ID = "codex"
    private const val PREFS = "quota_widget_selections"
    private const val GLOBAL_PRIMARY = "global_primary"
    private const val GLOBAL_SECONDARY = "global_secondary"
    private const val LAYOUT_MODE_PREFIX = "layout_mode_"

    private enum class LayoutMode { COMPACT, WIDE }

    fun get(context: Context, appWidgetId: Int): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(appWidgetId), null)
            ?.split('|')
            ?.filter(String::isNotBlank)
            .orEmpty()

    fun set(context: Context, appWidgetId: Int, ids: List<String>) {
        require(appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(key(appWidgetId), ids.distinct().joinToString("|"))
        }
    }

    fun hasGlobalSelection(context: Context): Boolean =
        globalPrimary(context).isNotBlank()

    fun globalPrimary(context: Context): String =
        preferences(context).getString(GLOBAL_PRIMARY, "").orEmpty()

    fun globalSecondary(context: Context): String =
        preferences(context).getString(GLOBAL_SECONDARY, "").orEmpty()

    fun setGlobal(
        context: Context,
        primaryId: String,
        secondaryId: String,
    ) {
        preferences(context).edit {
            putString(GLOBAL_PRIMARY, primaryId)
            putString(GLOBAL_SECONDARY, secondaryId.takeUnless { it == primaryId }.orEmpty())
        }
    }

    fun globalSelection(context: Context, compact: Boolean): List<String> = buildList {
        globalPrimary(context).takeIf { it.isNotBlank() }?.let(::add)
        if (!compact) {
            globalSecondary(context).takeIf { it.isNotBlank() && it !in this }?.let(::add)
        }
    }

    fun clear(context: Context, appWidgetId: Int) {
        preferences(context).edit {
            remove(key(appWidgetId))
            remove(layoutModeKey(appWidgetId))
        }
    }

    /**
     * Some launchers briefly report 0dp while they redraw a widget. Preserve the last valid
     * width classification so a configured 4x2 widget never collapses to the compact layout.
     */
    fun resolveCompact(context: Context, appWidgetId: Int, reportedWidthDp: Int): Boolean {
        val preferences = preferences(context)
        val detected = when {
            reportedWidthDp >= QuotaWidgetPresenter.MEDIUM_MIN_WIDTH_DP -> LayoutMode.WIDE
            reportedWidthDp in 1 until QuotaWidgetPresenter.MEDIUM_MIN_WIDTH_DP -> LayoutMode.COMPACT
            else -> preferences.getString(layoutModeKey(appWidgetId), null)
                ?.let { runCatching { LayoutMode.valueOf(it) }.getOrNull() }
                ?: LayoutMode.COMPACT
        }
        if (reportedWidthDp > 0) {
            preferences.edit { putString(layoutModeKey(appWidgetId), detected.name) }
        }
        return detected == LayoutMode.COMPACT
    }

    fun remap(context: Context, oldWidgetIds: IntArray?, newWidgetIds: IntArray?) {
        if (oldWidgetIds == null || newWidgetIds == null) return
        oldWidgetIds.zip(newWidgetIds).forEach { (oldId, newId) ->
            val selection = get(context, oldId)
            if (selection.isNotEmpty()) set(context, newId, selection)
            preferences(context).getString(layoutModeKey(oldId), null)?.let { mode ->
                preferences(context).edit { putString(layoutModeKey(newId), mode) }
            }
            clear(context, oldId)
        }
    }

    private fun key(appWidgetId: Int) = "widget_$appWidgetId"

    private fun layoutModeKey(appWidgetId: Int) = "$LAYOUT_MODE_PREFIX$appWidgetId"

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** 小部件内容和常驻通知使用用户为服务配置的原始名称。 */
internal fun widgetServiceLabel(service: BalanceService): String = service.name

internal data class WidgetServiceOption(
    val id: String,
    val label: String,
)

/**
 * Selection controls need a provider-qualified label. Several built-in providers use the same
 * service name (for example "账户余额"), so showing only that name makes the dropdown ambiguous.
 */
internal fun widgetPickerServiceLabel(service: BalanceService): String {
    val name = service.name.trim()
    val provider = platformBrand(service.authMode).displayName
    return when {
        name.isBlank() -> provider
        name.contains(provider, ignoreCase = true) -> name
        else -> "$provider · $name"
    }
}

/** Build one stable ID-backed option list for both Settings and launcher configuration. */
internal fun widgetPickerServiceOptions(
    codexAvailable: Boolean,
    services: List<BalanceService>,
): List<WidgetServiceOption> = buildList {
    if (codexAvailable) {
        add(WidgetServiceOption(WidgetSelectionPreferences.CODEX_ID, "OpenAI Codex · 配额"))
    }
    services.forEach { service ->
        add(WidgetServiceOption(service.id, widgetPickerServiceLabel(service)))
    }
}.distinctBy(WidgetServiceOption::id)

internal data class NormalizedWidgetSelection(
    val primaryId: String,
    val secondaryId: String,
)

/** Keep persisted IDs valid when services are removed without silently selecting by label/index. */
internal fun normalizeWidgetSelection(
    options: List<WidgetServiceOption>,
    primaryId: String,
    secondaryId: String,
): NormalizedWidgetSelection {
    val ids = options.mapTo(linkedSetOf(), WidgetServiceOption::id)
    val normalizedPrimary = primaryId.takeIf(ids::contains) ?: options.firstOrNull()?.id.orEmpty()
    val normalizedSecondary = secondaryId.takeIf { it in ids && it != normalizedPrimary }.orEmpty()
    return NormalizedWidgetSelection(normalizedPrimary, normalizedSecondary)
}
