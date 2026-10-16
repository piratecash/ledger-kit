package cash.p.terminal.ledgerkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DashboardTest {
    @Test
    fun getAppAndVersion_bytes() {
        assertEquals("b001000000", DashboardCommands.getAppAndVersion.toBytes().toHexString())
    }

    @Test
    fun quitApp_bytes() {
        assertEquals("b0a7000000", DashboardCommands.quitApp.toBytes().toHexString())
    }

    @Test
    fun openApp_asciiName_nameAsData() {
        assertEquals("e0d8000007426974636f696e", DashboardCommands.openApp("Bitcoin").toBytes().toHexString())
    }

    @Test
    fun parseAppAndVersion_appResponse_nameAndVersion() {
        val data = ("01" + "07" + "Bitcoin".encodeToByteArray().toHexString() +
            "05" + "2.1.0".encodeToByteArray().toHexString() + "0102").hexToByteArray()

        assertEquals(LedgerAppInfo("Bitcoin", "2.1.0"), DashboardCommands.parseAppAndVersion(data))
    }

    @Test
    fun parseAppAndVersion_truncatedVersion_throws() {
        val data = ("01" + "05" + "BOLOS".encodeToByteArray().toHexString() + "05" + "312e30").hexToByteArray()

        assertFailsWith<IllegalStateException> { DashboardCommands.parseAppAndVersion(data) }
    }

    @Test
    fun parseAppAndVersion_unknownFormat_throws() {
        assertFailsWith<IllegalStateException> { DashboardCommands.parseAppAndVersion("02".hexToByteArray()) }
    }

    @Test
    fun isDashboard_bolos_true() {
        assertTrue(LedgerAppInfo("BOLOS", "1.1.0").isDashboard)
        assertFalse(LedgerAppInfo("Bitcoin", "2.1.0").isDashboard)
    }

    @Test
    fun isVersionAtLeast_comparesNumericParts() {
        assertTrue(isVersionAtLeast("2.1.0", "2.1.0"))
        assertTrue(isVersionAtLeast("2.10.0", "2.9.0"))
        assertTrue(isVersionAtLeast("1.10", "1.9.9"))
        assertFalse(isVersionAtLeast("2.0.9", "2.1.0"))
        assertFalse(isVersionAtLeast("1.9", "1.9.1"))
    }

    @Test
    fun isVersionAtLeast_suffixes_ignored() {
        assertTrue(isVersionAtLeast("1.2.3-rc", "1.2.3"))
        assertTrue(isVersionAtLeast("1.2.3+build5", "1.2.3"))
        assertFalse(isVersionAtLeast("1.2.2-rc", "1.2.3"))
    }
}
