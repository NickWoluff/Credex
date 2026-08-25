package com.nickwoluff.credex

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetServiceLabelTest {
    @Test
    fun pickerLabelsUseConfiguredServiceName() {
        assertEquals(
            "账户余额",
            widgetServiceLabel(BalanceService(id = "deepseek", name = "账户余额", endpoint = "", authMode = BalanceAuthMode.DEEPSEEK_API_KEY)),
        )
        assertEquals(
            "Token Plan",
            widgetServiceLabel(BalanceService(id = "mimo-plan", name = "Token Plan", endpoint = "", authMode = BalanceAuthMode.MIMO_TOKEN_PLAN)),
        )
    }

    @Test
    fun pickerOptionsIdentifyProvidersAndIncludeDisabledServices() {
        val services = listOf(
            BalanceService(
                id = "deepseek",
                name = "账户余额",
                endpoint = "",
                authMode = BalanceAuthMode.DEEPSEEK_API_KEY,
                visible = false,
            ),
            BalanceService(
                id = "mimo-plan",
                name = "Xiaomi MIMO Token Plan",
                endpoint = "",
                authMode = BalanceAuthMode.MIMO_TOKEN_PLAN,
            ),
        )

        assertEquals(
            listOf(
                WidgetServiceOption("codex", "OpenAI Codex · 配额"),
                WidgetServiceOption("deepseek", "DeepSeek · 账户余额"),
                WidgetServiceOption("mimo-plan", "Xiaomi MIMO Token Plan"),
            ),
            widgetPickerServiceOptions(codexAvailable = true, services = services),
        )
    }

    @Test
    fun selectionUsesStableIdsAndRepairsRemovedServices() {
        val options = listOf(
            WidgetServiceOption("deepseek", "DeepSeek · 账户余额"),
            WidgetServiceOption("sf", "SiliconFlow · 账户余额"),
        )

        assertEquals(
            NormalizedWidgetSelection("deepseek", "sf"),
            normalizeWidgetSelection(options, primaryId = "missing", secondaryId = "sf"),
        )
        assertEquals(
            NormalizedWidgetSelection("sf", ""),
            normalizeWidgetSelection(options, primaryId = "sf", secondaryId = "sf"),
        )
    }
}
