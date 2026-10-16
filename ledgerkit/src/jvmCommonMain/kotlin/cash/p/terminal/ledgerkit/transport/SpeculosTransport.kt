package cash.p.terminal.ledgerkit.transport

import cash.p.terminal.ledgerkit.apdu.LedgerException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Speculos emulator APDU port: length-prefixed APDU out, length-prefixed data plus status word back.
 * For tests and debug builds only; an Android emulator reaches the host at 10.0.2.2.
 */
public class SpeculosTransport private constructor(private val socket: Socket) : LedgerTransport {
    private val input = DataInputStream(socket.getInputStream())
    private val output = DataOutputStream(socket.getOutputStream())

    override suspend fun exchange(apdu: ByteArray): ByteArray = socket.cancellableIo { blockingExchange(apdu) }

    override fun close() {
        socket.close()
    }

    private fun blockingExchange(apdu: ByteArray): ByteArray {
        output.writeInt(apdu.size)
        output.write(apdu)
        output.flush()
        val length = input.readInt()
        if (length !in 0..MAX_RESPONSE_DATA_LENGTH) throw IOException("Invalid response length $length")
        val data = ByteArray(length).also(input::readFully)
        val statusWord = ByteArray(STATUS_WORD_SIZE).also(input::readFully)
        return data + statusWord
    }

    public companion object {
        private const val STATUS_WORD_SIZE = 2

        /** Same bound as one Ledger HID message. */
        private const val MAX_RESPONSE_DATA_LENGTH = 0xFFFF
        private const val CONNECT_TIMEOUT_MILLIS = 5_000

        public suspend fun connect(host: String, port: Int): SpeculosTransport {
            val socket = Socket()
            socket.cancellableIo { socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS) }
            return SpeculosTransport(socket)
        }
    }
}

/**
 * Blocking socket calls ignore cancellation; closing the socket is what unblocks them. Any failure,
 * cancellation included (also one landing after the call returned), leaves the socket closed.
 */
private suspend fun <T> Socket.cancellableIo(block: () -> T): T = closingOnFailure {
    coroutineScope {
        val result = async(Dispatchers.IO) {
            try {
                block()
            } catch (error: IOException) {
                // An I/O failure caused by the cancellation close is a cancellation, not a lost device.
                ensureActive()
                throw LedgerException.TransportClosed(error)
            }
        }
        closingOnFailure { result.await() }
    }
}

private inline fun <T> Socket.closingOnFailure(block: () -> T): T =
    try {
        block()
    } catch (error: Throwable) {
        close()
        throw error
    }
