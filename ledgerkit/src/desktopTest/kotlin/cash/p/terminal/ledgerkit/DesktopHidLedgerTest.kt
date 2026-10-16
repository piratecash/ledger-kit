package cash.p.terminal.ledgerkit

import cash.p.terminal.ledgerkit.transport.isLedgerApduInterface
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopHidLedgerTest {
    @Test
    fun isLedgerApduInterface_signExtendedUsagePageWithoutInterfaceNumber_matches() {
        assertTrue(isLedgerApduInterface(vendorId = 0x2c97, usagePage = -96, interfaceNumber = -1))
    }

    @Test
    fun isLedgerApduInterface_unsignedUsagePage_matches() {
        assertTrue(isLedgerApduInterface(vendorId = 0x2c97, usagePage = 0xFFA0, interfaceNumber = -1))
    }

    @Test
    fun isLedgerApduInterface_linuxInterfaceZero_matches() {
        assertTrue(isLedgerApduInterface(vendorId = 0x2c97, usagePage = 0, interfaceNumber = 0))
    }

    @Test
    fun isLedgerApduInterface_fidoInterface_rejected() {
        assertFalse(isLedgerApduInterface(vendorId = 0x2c97, usagePage = 0xF1D0.toShort().toInt(), interfaceNumber = 1))
    }

    @Test
    fun isLedgerApduInterface_otherVendor_rejected() {
        assertFalse(isLedgerApduInterface(vendorId = 0x1209, usagePage = -96, interfaceNumber = 0))
    }
}
