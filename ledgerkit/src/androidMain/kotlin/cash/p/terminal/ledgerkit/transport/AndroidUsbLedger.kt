package cash.p.terminal.ledgerkit.transport

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.device.LedgerModel

public object AndroidUsbLedger {
    /** Opens the Ledger HID interface of [device]; the app must already hold USB permission for it. */
    public fun open(usbManager: UsbManager, device: UsbDevice): LedgerTransport {
        require(device.vendorId == LedgerModel.VENDOR_ID) { "Not a Ledger device" }
        val hidInterface = requireNotNull(
            (0 until device.interfaceCount).map(device::getInterface)
                .firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_HID }
        ) { "Ledger HID interface not found" }
        val inEndpoint = hidInterface.endpoint(UsbConstants.USB_DIR_IN)
        val outEndpoint = hidInterface.endpoint(UsbConstants.USB_DIR_OUT)
        val connection = usbManager.openDevice(device) ?: throw LedgerException.TransportClosed()
        if (!connection.claimInterface(hidInterface, true)) {
            connection.close()
            throw LedgerException.TransportClosed()
        }
        return HidLedgerTransport(
            AndroidUsbPipe(usbManager, device, connection, hidInterface, inEndpoint, outEndpoint)
        )
    }

    private fun UsbInterface.endpoint(direction: Int): UsbEndpoint = requireNotNull(
        (0 until endpointCount).map(::getEndpoint).firstOrNull { it.direction == direction }
    ) { "Ledger HID endpoint not found" }
}

private class AndroidUsbPipe(
    private val usbManager: UsbManager,
    private val device: UsbDevice,
    private val connection: UsbDeviceConnection,
    private val hidInterface: UsbInterface,
    private val inEndpoint: UsbEndpoint,
    private val outEndpoint: UsbEndpoint,
) : HidPacketPipe {
    @Volatile
    private var closed = false

    override fun write(packet: ByteArray) {
        val written = connection.bulkTransfer(outEndpoint, packet, packet.size, WRITE_TIMEOUT_MILLIS)
        if (written != packet.size) throw LedgerException.TransportClosed()
    }

    override fun read(timeoutMillis: Int): ByteArray? {
        val buffer = ByteArray(LedgerHidFraming.PACKET_SIZE)
        val read = connection.bulkTransfer(inEndpoint, buffer, buffer.size, timeoutMillis)
        if (read > 0) return buffer.copyOf(read)
        // bulkTransfer reports a timeout, a detached device and a closed connection the same way.
        if (closed || device !in usbManager.deviceList.values) throw LedgerException.TransportClosed()
        return null
    }

    override fun close() {
        closed = true
        connection.releaseInterface(hidInterface)
        connection.close()
    }

    private companion object {
        const val WRITE_TIMEOUT_MILLIS = 2_000
    }
}
