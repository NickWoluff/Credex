package org.orynnx.codexquota

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RefreshLeaseTest {
    @Test
    fun cancelledLeaseRejectsLaterCommitSection() {
        val lease = RefreshLease()
        var committed = false

        lease.cancel()
        val result = lease.runIfActive {
            committed = true
            Unit
        }

        assertNull(result)
        assertFalse(committed)
    }
}
