package cash.p.terminal.ledgerkit

import cash.p.terminal.ledgerkit.transport.HidLedgerTransport
import cash.p.terminal.ledgerkit.transport.HidPacketPipe
import cash.p.terminal.ledgerkit.transport.LedgerHidFraming
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HidLedgerTransportTest {
    @Test
    fun exchange_staleAnswerOfAnotherChannelQueuedFirst_returnsOwnAnswer() = runTest {
        val pipe = EchoingPipe(staleAnswer = "6985".hexToByteArray())

        assertEquals("9000", HidLedgerTransport(pipe).exchange(GET_APP).toHexString())
    }

    @Test
    fun exchange_newConnections_useDifferentChannels() = runTest {
        val pipe = EchoingPipe()

        repeat(10) { HidLedgerTransport(pipe).exchange(GET_APP) }

        assertTrue(pipe.channels.toSet().size > 1)
    }

    /** Answers on the request's channel, as the device does. */
    private class EchoingPipe(private val staleAnswer: ByteArray? = null) : HidPacketPipe {
        val channels = mutableListOf<Int>()
        private val replies = ArrayDeque<ByteArray>()

        override fun write(packet: ByteArray) {
            val channel = packet.readUInt16(0)
            channels += channel
            staleAnswer?.let { replies += LedgerHidFraming.encode(it, channel xor 1) }
            replies += LedgerHidFraming.encode("9000".hexToByteArray(), channel)
        }

        override fun read(timeoutMillis: Int): ByteArray? = replies.removeFirstOrNull()

        override fun close() = Unit
    }

    private companion object {
        val GET_APP = "b001000000".hexToByteArray()
    }
}
