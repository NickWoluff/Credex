package org.orynnx.codexquota

import android.content.Context
import androidx.core.content.edit

/** Stores the launcher-widget card height independently from launcher measurements. */
internal object WidgetHeightPreferences {
    private const val PREFS = "quota_widget_height"
    private const val CUSTOM_HEIGHT_INPUT = "custom_height_input"
    private const val CUSTOM_CORNER_RADIUS_INPUT = "custom_corner_radius_input"
    private const val RECOMMENDED_HEIGHT_DP = "recommended_height_dp"
    private const val FALLBACK_RECOMMENDED_HEIGHT_DP = 153
    const val MIN_HEIGHT_DP = 48
    const val MAX_HEIGHT_DP = 1_000
    const val DEFAULT_CORNER_RADIUS_DP = 18
    const val MAX_CORNER_RADIUS_DP = 96

    fun customInput(context: Context): String =
        preferences(context).getString(CUSTOM_HEIGHT_INPUT, "").orEmpty()

    fun recommendedHeightDp(context: Context): Int =
        recordedRecommendationHeightDp(context) ?: FALLBACK_RECOMMENDED_HEIGHT_DP

    fun hasRecordedRecommendation(context: Context): Boolean =
        recordedRecommendationHeightDp(context) != null

    fun preferredHeightDp(context: Context): Int =
        customInput(context).toIntOrNull()?.takeIf(::isValidHeight)
            ?: recommendedHeightDp(context)

    fun setCustomInput(context: Context, input: String) {
        preferences(context).edit {
            if (input.isBlank()) remove(CUSTOM_HEIGHT_INPUT)
            else putString(CUSTOM_HEIGHT_INPUT, input.trim())
        }
    }

    fun customCornerRadiusInput(context: Context): String =
        preferences(context).getString(CUSTOM_CORNER_RADIUS_INPUT, "").orEmpty()

    fun preferredCornerRadiusDp(context: Context): Int =
        customCornerRadiusInput(context).toIntOrNull()?.takeIf(::isValidCornerRadius)
            ?: DEFAULT_CORNER_RADIUS_DP

    fun setCustomCornerRadiusInput(context: Context, input: String) {
        preferences(context).edit {
            if (input.isBlank()) remove(CUSTOM_CORNER_RADIUS_INPUT)
            else putString(CUSTOM_CORNER_RADIUS_INPUT, input.trim())
        }
    }

    /** Clears values that were temporarily entered but cannot be applied safely. */
    fun sanitizeCustomInputs(context: Context) {
        val preferences = preferences(context)
        preferences.edit {
            if (preferences.getString(CUSTOM_HEIGHT_INPUT, "").orEmpty().toIntOrNull()?.let(::isValidHeight) != true) {
                remove(CUSTOM_HEIGHT_INPUT)
            }
            if (preferences.getString(CUSTOM_CORNER_RADIUS_INPUT, "").orEmpty().toIntOrNull()?.let(::isValidCornerRadius) != true) {
                remove(CUSTOM_CORNER_RADIUS_INPUT)
            }
        }
    }

    fun recordRecommendationIfAbsent(context: Context, widthDp: Int) {
        if (!isValidHeight(widthDp) || hasRecordedRecommendation(context)) return
        preferences(context).edit { putInt(RECOMMENDED_HEIGHT_DP, widthDp) }
    }

    private fun recordedRecommendationHeightDp(context: Context): Int? =
        preferences(context).getInt(RECOMMENDED_HEIGHT_DP, 0).takeIf(::isValidHeight)

    private fun isValidHeight(value: Int): Boolean = value in MIN_HEIGHT_DP..MAX_HEIGHT_DP

    private fun isValidCornerRadius(value: Int): Boolean = value in 0..MAX_CORNER_RADIUS_DP

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
