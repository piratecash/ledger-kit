package cash.p.terminal.ledgerkit.transport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** A platform HID handle that moves whole reports. */
internal interface HidPacketPipe {
    fun write(packet: ByteArray)

    /** Returns null when nothing arrived within [timeoutMillis]. */
    fun read(timeoutMillis: Int): ByteArray?

    fun close()
}

internal class HidLedgerTransport(private val pipe: HidPacketPipe) : LedgerTransport {
    // Reads poll in short slices: a user confirmation can take minutes and cancellation must still get through.
    override suspend fun exchange(apdu: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        LedgerHidFraming.encode(apdu).forEach(pipe::write)
        val decoder = LedgerHidFraming.ResponseDecoder()
        var response: ByteArray? = null
        while (response == null) {
            ensureActive()
            response = pipe.read(READ_POLL_MILLIS)?.let(decoder::accept)
        }
        response
    }

    override fun close() {
        pipe.close()
    }

    private companion object {
        const val READ_POLL_MILLIS = 500
    }
}
