package org.orynnx.credex

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetTokenValueFormatterTest {
    @Test
    fun decimalUnitSystemUsesKThenMWithoutEscalatingToBillionUnit() {
        assertEquals("1.5K", WidgetTokenValueFormatter.format(BigDecimal("1500"), WidgetTokenUnitSystem.DECIMAL))
        assertEquals("4100M", WidgetTokenValueFormatter.format(BigDecimal("4100000000"), WidgetTokenUnitSystem.DECIMAL))
    }

    @Test
    fun binaryUnitSystemUses1024BasedConversion() {
        assertEquals("1K", WidgetTokenValueFormatter.format(BigDecimal("1024"), WidgetTokenUnitSystem.BINARY))
        assertEquals("9.9M", WidgetTokenValueFormatter.format(BigDecimal("10418748"), WidgetTokenUnitSystem.BINARY))
    }

    @Test
    fun valuesBelowTheSelectedBaseRemainFullNumbers() {
        assertEquals("999", WidgetTokenValueFormatter.format(BigDecimal("999"), WidgetTokenUnitSystem.DECIMAL))
        assertEquals("1,023", WidgetTokenValueFormatter.format(BigDecimal("1023"), WidgetTokenUnitSystem.BINARY))
    }
}
