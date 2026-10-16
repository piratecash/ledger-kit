package cash.p.terminal.ledgerkit

import cash.p.terminal.ledgerkit.transport.LedgerTransport

/** Answers each APDU with [respond] (response data plus status word) and keeps every APDU it was sent. */
class RecordingTransport(private val respond: (ByteArray) -> ByteArray) : LedgerTransport {
    val sent = mutableListOf<ByteArray>()

    override suspend fun exchange(apdu: ByteArray): ByteArray {
        sent += apdu
        return respond(apdu)
    }

    override fun close() = Unit
}

fun ByteArray.ins(): Int = this[1].toInt() and 0xFF

fun ByteArray.p1(): Int = this[2].toInt() and 0xFF

fun ByteArray.p2(): Int = this[3].toInt() and 0xFF

fun ByteArray.apduData(): ByteArray = copyOfRange(5, size)
