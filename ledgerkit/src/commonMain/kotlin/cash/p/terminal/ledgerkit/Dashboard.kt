package cash.p.terminal.ledgerkit

import cash.p.terminal.ledgerkit.apdu.Apdu
import cash.p.terminal.ledgerkit.apdu.send
import cash.p.terminal.ledgerkit.transport.LedgerTransport

public data class LedgerAppInfo(public val name: String, public val version: String) {
    public val isDashboard: Boolean get() = name in DASHBOARD_NAMES

    private companion object {
        // Older firmware reports the dashboard as "OLOS\0".
        val DASHBOARD_NAMES = setOf("BOLOS", "OLOS\u0000")
    }
}

internal object DashboardCommands {
    val getAppAndVersion = Apdu(cla = 0xB0, ins = 0x01, p1 = 0, p2 = 0)
    val quitApp = Apdu(cla = 0xB0, ins = 0xA7, p1 = 0, p2 = 0)

    fun openApp(name: String): Apdu = Apdu(cla = 0xE0, ins = 0xD8, p1 = 0, p2 = 0, data = name.encodeToByteArray())

    /** Layout: format byte, length-prefixed name, length-prefixed version, then flags we do not need. */
    fun parseAppAndVersion(data: ByteArray): LedgerAppInfo {
        check(data.isNotEmpty() && data[0].toInt() == APP_AND_VERSION_FORMAT) { "Unsupported app info format" }
        var offset = 1
        fun readField(): String {
            check(offset < data.size) { "Truncated app info" }
            val end = offset + 1 + (data[offset].toInt() and 0xFF)
            check(end <= data.size) { "Truncated app info" }
            return data.decodeToString(offset + 1, end).also { offset = end }
        }
        return LedgerAppInfo(name = readField(), version = readField())
    }

    private const val APP_AND_VERSION_FORMAT = 1
}

internal suspend fun LedgerTransport.getAppAndVersion(): LedgerAppInfo =
    DashboardCommands.parseAppAndVersion(send(DashboardCommands.getAppAndVersion))
