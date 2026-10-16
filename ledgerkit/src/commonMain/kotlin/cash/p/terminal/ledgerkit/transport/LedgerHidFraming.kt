package cash.p.terminal.ledgerkit.transport

import cash.p.terminal.ledgerkit.readUInt16
import cash.p.terminal.ledgerkit.toUInt16Bytes

/** Ledger USB HID framing: 64-byte reports on channel 0x0101 with tag 0x05; the first one carries the APDU length. */
public object LedgerHidFraming {
    public const val PACKET_SIZE: Int = 64

    private const val CHANNEL = 0x0101
    private const val TAG_APDU = 0x05
    private const val HEADER_SIZE = 5
    private const val LENGTH_PREFIX_SIZE = 2
    private const val MAX_MESSAGE_LENGTH = 0xFFFF

    public fun encode(apdu: ByteArray): List<ByteArray> {
        require(apdu.size <= MAX_MESSAGE_LENGTH) { "APDU too long: ${apdu.size}" }
        val payload = apdu.size.toUInt16Bytes() + apdu
        return payload.asList().chunked(PACKET_SIZE - HEADER_SIZE).mapIndexed { sequence, chunk ->
            ByteArray(PACKET_SIZE).also { packet ->
                header(sequence).copyInto(packet)
                chunk.toByteArray().copyInto(packet, HEADER_SIZE)
            }
        }
    }

    private fun header(sequence: Int): ByteArray =
        CHANNEL.toUInt16Bytes() + TAG_APDU.toByte() + sequence.toUInt16Bytes()

    /** Reassembles one response from the packets of a single exchange. */
    public class ResponseDecoder {
        private var payload = ByteArray(0)
        private var nextSequence = 0

        /** Returns the complete response once its last packet arrived, otherwise null. */
        public fun accept(packet: ByteArray): ByteArray? {
            check(packet.size >= HEADER_SIZE + LENGTH_PREFIX_SIZE) { "HID packet too short: ${packet.size}" }
            check(packet.readUInt16(0) == CHANNEL) { "Unexpected HID channel" }
            check(packet[2].toInt() == TAG_APDU) { "Unexpected HID tag" }
            check(packet.readUInt16(3) == nextSequence) { "Unexpected HID sequence" }
            nextSequence++
            payload += packet.copyOfRange(HEADER_SIZE, packet.size)
            val end = LENGTH_PREFIX_SIZE + payload.readUInt16(0)
            return if (payload.size >= end) payload.copyOfRange(LENGTH_PREFIX_SIZE, end) else null
        }
    }
}
