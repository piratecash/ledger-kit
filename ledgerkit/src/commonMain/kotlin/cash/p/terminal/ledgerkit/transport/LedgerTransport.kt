package cash.p.terminal.ledgerkit.transport

/** One channel to a Ledger. Not safe for concurrent exchanges: [cash.p.terminal.ledgerkit.LedgerSession] serializes them. */
public interface LedgerTransport {
    /** Returns the response data followed by the 2-byte status word. */
    public suspend fun exchange(apdu: ByteArray): ByteArray

    public fun close()
}

/** Opens a fresh transport; asked again after the device re-enumerates on an app switch. */
public fun interface LedgerTransportProvider {
    public suspend fun open(): LedgerTransport
}
