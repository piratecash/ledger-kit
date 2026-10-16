package cash.p.terminal.ledgerkit.consumersmoke

import cash.p.terminal.ledgerkit.transport.DesktopHidLedger
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.ledgerkit.transport.SpeculosTransport

// Compile-only smoke: proves the desktop HID and speculos transports resolve from the desktop variant.
internal suspend fun desktopSmoke(useEmulator: Boolean): LedgerTransport =
    if (useEmulator) SpeculosTransport.connect("127.0.0.1", 9999) else DesktopHidLedger.openFirst()
