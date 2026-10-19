package cash.p.terminal.ledgerkit

import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.apdu.StatusWord
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.ledgerkit.transport.LedgerTransportProvider
import kotlinx.coroutines.awaitCancellation

/** A device that re-enumerates on app switches: the transport that switched the app goes dead. */
class FakeLedger(
    var currentApp: String = "BOLOS",
    private val installedApps: Map<String, String> = mapOf("Bitcoin" to "2.1.0", "Ethereum" to "1.12.0"),
) {
    val commands = mutableListOf<String>()
    val transports = mutableListOf<Transport>()
    var failingReopens = 0
    var locksOnAppSwitch = false
    var silentReopens = false
    var afterAppInfo: () -> Unit = {}
    var locked = false
    var unplugged = false
    var answersAppCommands = true
    var truncatesAppAnswers = false

    val provider = LedgerTransportProvider {
        if (transports.isNotEmpty() && failingReopens > 0) {
            failingReopens--
            throw LedgerException.TransportClosed()
        }
        Transport(silent = transports.isNotEmpty() && silentReopens).also(transports::add)
    }

    inner class Transport(var silent: Boolean) : LedgerTransport {
        var closeCalls = 0
        var beforeClose: () -> Unit = {}
        val closed: Boolean get() = closeCalls > 0
        private var reenumerated = false

        override suspend fun exchange(apdu: ByteArray): ByteArray {
            if (closed || reenumerated || unplugged) throw LedgerException.TransportClosed()
            if (silent) awaitCancellation()
            return when ((apdu[0].toInt() and 0xFF) to (apdu[1].toInt() and 0xFF)) {
                0xB0 to 0x01 -> appAndVersion()
                0xB0 to 0xA7 -> switchTo("QUIT", "BOLOS")
                0xE0 to 0xD8 -> openApp(apdu.decodeToString(5, apdu.size))
                else -> {
                    commands += "APP"
                    if (!answersAppCommands) awaitCancellation()
                    if (truncatesAppAnswers) byteArrayOf(0x90.toByte()) else statusWord(StatusWord.OK)
                }
            }
        }

        override fun close() {
            closeCalls++
            beforeClose()
        }

        private fun appAndVersion(): ByteArray {
            commands += "GET"
            if (locked) return statusWord(StatusWord.DEVICE_LOCKED)
            val version = installedApps[currentApp] ?: "1.0.0"
            return (byteArrayOf(1) + field(currentApp) + field(version) + statusWord(StatusWord.OK))
                .also { afterAppInfo() }
        }

        private fun openApp(name: String): ByteArray =
            if (name in installedApps) switchTo("OPEN $name", name)
            else statusWord(StatusWord.APP_NOT_INSTALLED).also { commands += "OPEN $name" }

        private fun switchTo(command: String, app: String): ByteArray {
            commands += command
            currentApp = app
            reenumerated = true
            locked = locksOnAppSwitch
            return statusWord(StatusWord.OK)
        }
    }

    private fun field(value: String): ByteArray = byteArrayOf(value.length.toByte()) + value.encodeToByteArray()

    private fun statusWord(value: Int): ByteArray = value.toUInt16Bytes()
}
