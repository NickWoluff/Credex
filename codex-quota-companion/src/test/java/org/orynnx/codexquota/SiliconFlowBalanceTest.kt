package org.orynnx.codexquota

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
}
