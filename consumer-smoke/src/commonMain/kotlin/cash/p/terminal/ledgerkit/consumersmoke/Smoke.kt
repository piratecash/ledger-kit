package cash.p.terminal.ledgerkit.consumersmoke

import cash.p.terminal.ledgerkit.LedgerSession
import cash.p.terminal.ledgerkit.apdu.Apdu
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.apdu.send
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.ledgerkit.transport.LedgerTransportProvider

// Compile-only smoke: never executed. Proves the common API resolves from the published artifact.
internal suspend fun commonSmoke(open: suspend () -> LedgerTransport): ByteArray =
    LedgerSession(LedgerTransportProvider { open() }).withApp("Bitcoin", "2.1.0") { transport ->
        transport.send(Apdu(cla = 0xE1, ins = 0x00, p1 = 0, p2 = 0))
    }

internal fun isUserRejection(failure: Throwable): Boolean = failure is LedgerException.UserRejected
