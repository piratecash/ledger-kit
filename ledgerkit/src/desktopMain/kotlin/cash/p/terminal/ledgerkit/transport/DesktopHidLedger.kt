package cash.p.terminal.ledgerkit.transport

import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.device.LedgerModel
import org.hid4java.HidDevice
import org.hid4java.HidManager
import org.hid4java.HidServicesSpecification
import org.hid4java.ScanMode

public object DesktopHidLedger {
    private val hidServices by lazy {
        HidManager.getHidServices(
            HidServicesSpecification().apply {
                isAutoStart = false
                scanMode = ScanMode.NO_SCAN
            }
        )
    }

    /** Opens the first connected Ledger. */
    public fun openFirst(): LedgerTransport {
        val device = hidServices.attachedHidDevices
            .firstOrNull { isLedgerApduInterface(it.vendorId, it.usagePage, it.interfaceNumber) }
            ?: throw LedgerException.TransportClosed()
        if (!device.open()) throw LedgerException.TransportClosed()
        return HidLedgerTransport(DesktopHidPipe(device))
    }
}

// macOS and Windows expose the APDU interface by usage page; Linux hidraw reports it as interface 0.
private const val APDU_USAGE_PAGE = 0xFFA0
private const val APDU_INTERFACE_NUMBER = 0

/** hid4java hands over hidapi's unsigned shorts sign-extended, so 0xFFA0 arrives as -96. */
internal fun isLedgerApduInterface(vendorId: Int, usagePage: Int, interfaceNumber: Int): Boolean =
    (vendorId and 0xFFFF) == LedgerModel.VENDOR_ID &&
        ((usagePage and 0xFFFF) == APDU_USAGE_PAGE || interfaceNumber == APDU_INTERFACE_NUMBER)

private class DesktopHidPipe(private val device: HidDevice) : HidPacketPipe {
    override fun write(packet: ByteArray) {
        if (device.write(packet, packet.size, REPORT_ID) < 0) throw LedgerException.TransportClosed()
    }

    override fun read(timeoutMillis: Int): ByteArray? {
        val buffer = ByteArray(LedgerHidFraming.PACKET_SIZE)
        val read = device.read(buffer, timeoutMillis)
        if (read < 0) throw LedgerException.TransportClosed()
        return if (read == 0) null else buffer.copyOf(read)
    }

    override fun close() {
        device.close()
    }

    private companion object {
        const val REPORT_ID: Byte = 0
    }
}
