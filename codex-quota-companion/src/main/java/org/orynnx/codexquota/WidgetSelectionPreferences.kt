package org.orynnx.codexquota

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.core.content.edit

internal object WidgetSelectionPreferences {
    const val CODEX_ID = "codex"
    private const val PREFS = "quota_widget_selections"
    private const val GLOBAL_PRIMARY = "global_primary"
    private const val GLOBAL_SECONDARY = "global_secondary"
    private const val GLOBAL_SHOW_SECONDARY = "global_show_secondary"

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
        preferences(context).contains(GLOBAL_PRIMARY)

    fun globalPrimary(context: Context): String =
        preferences(context).getString(GLOBAL_PRIMARY, CODEX_ID).orEmpty().ifBlank { CODEX_ID }

    fun globalSecondary(context: Context): String =
        preferences(context).getString(GLOBAL_SECONDARY, "").orEmpty()

    fun showSecondary(context: Context): Boolean =
        preferences(context).getBoolean(GLOBAL_SHOW_SECONDARY, true)

    fun setGlobal(
        context: Context,
        primaryId: String,
        secondaryId: String,
        showSecondary: Boolean,
    ) {
        preferences(context).edit {
            putString(GLOBAL_PRIMARY, primaryId.ifBlank { CODEX_ID })
            putString(GLOBAL_SECONDARY, secondaryId.takeUnless { it == primaryId }.orEmpty())
            putBoolean(GLOBAL_SHOW_SECONDARY, showSecondary)
        }
    }

    fun globalSelection(context: Context, compact: Boolean): List<String> = buildList {
        add(globalPrimary(context))
        if (!compact && showSecondary(context)) {
            globalSecondary(context).takeIf { it.isNotBlank() && it != first() }?.let(::add)
        }
    }

    fun clear(context: Context, appWidgetId: Int) {
        preferences(context).edit { remove(key(appWidgetId)) }
    }

    fun remap(context: Context, oldWidgetIds: IntArray?, newWidgetIds: IntArray?) {
        if (oldWidgetIds == null || newWidgetIds == null) return
        oldWidgetIds.zip(newWidgetIds).forEach { (oldId, newId) ->
            val selection = get(context, oldId)
            if (selection.isNotEmpty()) set(context, newId, selection)
            clear(context, oldId)
        }
    }

    private fun key(appWidgetId: Int) = "widget_$appWidgetId"

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
