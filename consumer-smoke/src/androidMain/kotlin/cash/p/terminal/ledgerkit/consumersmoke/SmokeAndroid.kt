package cash.p.terminal.ledgerkit.consumersmoke

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import cash.p.terminal.ledgerkit.transport.AndroidUsbLedger
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.ledgerkit.transport.SpeculosTransport

// Compile-only smoke: proves the Android USB and speculos transports resolve from the Android variant.
internal fun androidSmoke(usbManager: UsbManager, device: UsbDevice): LedgerTransport =
    AndroidUsbLedger.open(usbManager, device)

internal suspend fun androidEmulatorSmoke(): LedgerTransport = SpeculosTransport.connect("10.0.2.2", 9999)
