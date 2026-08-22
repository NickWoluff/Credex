package com.nickwoluff.credex

import android.content.Context
import androidx.core.content.edit
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

internal enum class WidgetTokenUnitSystem(
    val divisor: Int,
    val label: String,
) {
    DECIMAL(1_000, "1K = 1000"),
    BINARY(1_024, "1K = 1024"),
}

/** Widget-only presentation preferences. They do not alter the stored quota values. */
internal object WidgetTokenDisplayPreferences {
    private const val PREFS = "quota_widget_token_display"
    private const val COLLAPSE_TOKEN_VALUES = "collapse_token_values"
    private const val UNIT_SYSTEM = "unit_system"

    fun collapseTokenValues(context: Context): Boolean =
        preferences(context).getBoolean(COLLAPSE_TOKEN_VALUES, false)

    fun unitSystem(context: Context): WidgetTokenUnitSystem =
        preferences(context)
            .getString(UNIT_SYSTEM, WidgetTokenUnitSystem.DECIMAL.name)
            ?.let { value -> WidgetTokenUnitSystem.entries.firstOrNull { it.name == value } }
            ?: WidgetTokenUnitSystem.DECIMAL

    fun setCollapseTokenValues(context: Context, enabled: Boolean) {
        preferences(context).edit { putBoolean(COLLAPSE_TOKEN_VALUES, enabled) }
    }

    fun setUnitSystem(context: Context, unitSystem: WidgetTokenUnitSystem) {
        preferences(context).edit { putString(UNIT_SYSTEM, unitSystem.name) }
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Formats Token Plan counts for space-constrained widget layouts only. */
internal object WidgetTokenValueFormatter {
    fun format(value: BigDecimal, unitSystem: WidgetTokenUnitSystem): String {
        val base = BigDecimal.valueOf(unitSystem.divisor.toLong())
        val absolute = value.abs()
        val (divisor, suffix) = when {
            absolute >= base.multiply(base) -> base.multiply(base) to "M"
            absolute >= base -> base to "K"
            else -> return DecimalFormat("#,###").format(value.setScale(0, RoundingMode.DOWN))
        }
        return value.divide(divisor, 1, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString() + suffix
    }
}
