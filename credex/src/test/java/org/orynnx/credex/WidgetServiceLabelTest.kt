package org.orynnx.credex

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetServiceLabelTest {
    @Test
    fun pickerLabelsUseConfiguredServiceName() {
        assertEquals(
            "账户余额",
            widgetServiceLabel(BalanceService(id = "deepseek", name = "账户余额", endpoint = "", authMode = BalanceAuthMode.DEEPSEEK_CONSOLE)),
        )
        assertEquals(
            "Token Plan",
            widgetServiceLabel(BalanceService(id = "mimo-plan", name = "Token Plan", endpoint = "", authMode = BalanceAuthMode.MIMO_TOKEN_PLAN)),
        )
    }
}
