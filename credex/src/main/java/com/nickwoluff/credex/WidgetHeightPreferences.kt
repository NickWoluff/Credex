package com.nickwoluff.credex

import android.content.Context
import androidx.core.content.edit

/** Stores user-controlled launcher-widget card dimensions. */
internal object WidgetHeightPreferences {
    private const val PREFS = "quota_widget_height"
    private const val CUSTOM_HEIGHT_INPUT = "custom_height_input"
    private const val VERTICAL_OFFSET_DP = "vertical_offset_dp"
    const val DEFAULT_HEIGHT_DP = 150
    const val MIN_HEIGHT_DP = 48
    const val MAX_HEIGHT_DP = 1_000
    const val DEFAULT_CORNER_RADIUS_DP = 18
    const val MIN_VERTICAL_OFFSET_DP = -10
    const val MAX_VERTICAL_OFFSET_DP = 10

    fun customInput(context: Context): String =
        preferences(context).getString(CUSTOM_HEIGHT_INPUT, "").orEmpty()

    fun preferredHeightDp(context: Context): Int =
        customInput(context).toIntOrNull()?.takeIf(::isValidHeight)
            ?: DEFAULT_HEIGHT_DP

    fun setCustomInput(context: Context, input: String) {
        preferences(context).edit {
            if (input.isBlank()) remove(CUSTOM_HEIGHT_INPUT)
            else putString(CUSTOM_HEIGHT_INPUT, input.trim())
        }
    }

    fun verticalOffsetDp(context: Context): Int =
        preferences(context).getInt(VERTICAL_OFFSET_DP, 0).coerceIn(MIN_VERTICAL_OFFSET_DP, MAX_VERTICAL_OFFSET_DP)

    fun setVerticalOffsetDp(context: Context, value: Int) {
        preferences(context).edit {
            putInt(VERTICAL_OFFSET_DP, value.coerceIn(MIN_VERTICAL_OFFSET_DP, MAX_VERTICAL_OFFSET_DP))
        }
    }

    /** Clears values that were temporarily entered but cannot be applied safely. */
    fun sanitizeCustomInputs(context: Context) {
        val preferences = preferences(context)
        preferences.edit {
            if (preferences.getString(CUSTOM_HEIGHT_INPUT, "").orEmpty().toIntOrNull()?.let(::isValidHeight) != true) {
                remove(CUSTOM_HEIGHT_INPUT)
            }
            remove("custom_corner_radius_input")
        }
    }

    private fun isValidHeight(value: Int): Boolean = value in MIN_HEIGHT_DP..MAX_HEIGHT_DP

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
