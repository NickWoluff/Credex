package com.nickwoluff.credex

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class SiliconFlowBalanceTest {
    @Test
    fun consoleCashUsesSiliconFlowWalletUnit() {
        assertEquals(
            BigDecimal("0.0340662"),
            siliconFlowConsoleCashToYuan(BigDecimal("34066200000")),
        )
    }

    @Test
    fun consoleDetailShowsBalanceAndVoucherWhenIncluded() {
        assertEquals(
            "余额 ¥12.5 · 代金券 ¥3.25",
            siliconFlowConsoleBalanceDetail(BigDecimal("12.50"), BigDecimal("3.250"), true),
        )
    }

    @Test
    fun consoleDetailOmitsVoucherWhenNotIncluded() {
        assertEquals(
            "余额 ¥12.5",
            siliconFlowConsoleBalanceDetail(BigDecimal("12.50"), BigDecimal("3.25"), false),
        )
    }

    @Test
    fun yuanSymbolDoesNotAddASeparator() {
        assertEquals(
            "¥12.50",
            balanceDisplayValue(
                BalanceService(
                    id = "sf",
                    name = "SiliconFlow",
                    endpoint = "https://example.test",
                    balance = "12.5",
                    currency = "¥",
                ),
            ),
        )
    }
}
