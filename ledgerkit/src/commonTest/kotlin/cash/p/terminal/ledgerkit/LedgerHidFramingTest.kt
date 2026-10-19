package cash.p.terminal.ledgerkit

import cash.p.terminal.ledgerkit.transport.LedgerHidFraming
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LedgerHidFramingTest {
    @Test
    fun encode_shortApdu_singlePaddedPacket() {
        val packets = LedgerHidFraming.encode("b001000000".hexToByteArray(), CHANNEL)

        assertEquals(1, packets.size)
        assertEquals("0101050000" + "0005" + "b001000000" + "00".repeat(52), packets[0].toHexString())
    }

    @Test
    fun encode_apduFillsFirstPacketExactly_singlePacket() {
        val packets = LedgerHidFraming.encode(ByteArray(57) { 0x11 }, CHANNEL)

        assertEquals(1, packets.size)
        assertEquals("0101050000" + "0039" + "11".repeat(57), packets[0].toHexString())
    }

    @Test
    fun encode_apduOneByteOverFirstPacket_secondPacketWithSequenceOne() {
        val packets = LedgerHidFraming.encode(ByteArray(58) { 0x11 }, CHANNEL)

        assertEquals(2, packets.size)
        assertEquals("0101050001" + "11" + "00".repeat(58), packets[1].toHexString())
    }

    @Test
    fun encode_givenChannel_everyPacketCarriesIt() {
        val packets = LedgerHidFraming.encode(ByteArray(100), 0xBEEF)

        assertEquals(listOf("beef0500", "beef0500"), packets.map { it.copyOf(4).toHexString() })
    }

    @Test
    fun encode_longApdu_everyPacketIs64Bytes() {
        val packets = LedgerHidFraming.encode(ByteArray(300) { it.toByte() }, CHANNEL)

        assertEquals(listOf(0, 1, 2, 3, 4, 5), packets.map { it.readUInt16(3) })
        packets.forEach { assertEquals(LedgerHidFraming.PACKET_SIZE, it.size) }
    }

    @Test
    fun encode_twoPacketReferenceVector_matchesSpec() {
        val packets = LedgerHidFraming.encode(referenceApdu.hexToByteArray(), CHANNEL)

        assertEquals(referencePackets, packets.map { it.toHexString() })
    }

    @Test
    fun accept_twoPacketReferenceVector_returnsMessage() {
        val decoder = LedgerHidFraming.ResponseDecoder(CHANNEL)

        assertNull(decoder.accept(referencePackets[0].hexToByteArray()))
        assertEquals(referenceApdu, decoder.accept(referencePackets[1].hexToByteArray())?.toHexString())
    }

    @Test
    fun accept_singlePacketResponse_returnsData() {
        val decoder = LedgerHidFraming.ResponseDecoder(CHANNEL)

        val response = decoder.accept(("0101050000" + "0002" + "9000" + "00".repeat(55)).hexToByteArray())

        assertEquals("9000", response?.toHexString())
    }

    @Test
    fun accept_multiPacketResponse_returnsDataAfterLastPacket() {
        val data = ByteArray(200) { it.toByte() }
        val decoder = LedgerHidFraming.ResponseDecoder(CHANNEL)
        val packets = LedgerHidFraming.encode(data, CHANNEL)

        packets.dropLast(1).forEach { assertNull(decoder.accept(it)) }

        assertContentEquals(data, decoder.accept(packets.last()))
    }

    @Test
    fun accept_packetOfAnotherChannel_skippedAndOwnResponseAssembled() {
        val data = ByteArray(200) { it.toByte() }
        val decoder = LedgerHidFraming.ResponseDecoder(CHANNEL)
        val packets = LedgerHidFraming.encode(data, CHANNEL)

        assertNull(decoder.accept(LedgerHidFraming.encode("6985".hexToByteArray(), 0xBEEF).single()))
        packets.dropLast(1).forEach { assertNull(decoder.accept(it)) }
        assertNull(decoder.accept(LedgerHidFraming.encode(ByteArray(100), 0x0102)[1]))

        assertContentEquals(data, decoder.accept(packets.last()))
    }

    @Test
    fun accept_wrongTag_throws() {
        val packet = LedgerHidFraming.encode(byteArrayOf(1), CHANNEL).single().also { it[2] = 0x06 }

        assertFailsWith<IllegalStateException> { LedgerHidFraming.ResponseDecoder(CHANNEL).accept(packet) }
    }

    @Test
    fun accept_sequenceGap_throws() {
        val packets = LedgerHidFraming.encode(ByteArray(200), CHANNEL)
        val decoder = LedgerHidFraming.ResponseDecoder(CHANNEL)
        decoder.accept(packets[0])

        assertFailsWith<IllegalStateException> { decoder.accept(packets[2]) }
    }

    private companion object {
        const val CHANNEL = 0x0101

        // Hand-derived from the Ledger HID framing used by ledgerjs hw-transport (Apache-2.0):
        // header 0101|05|seq, length 003c only in packet 0, 57 then 59 payload bytes, zero padding.
        val referenceApdu = "e0c4000037" + "aa".repeat(55)
        val referencePackets = listOf(
            "0101050000" + "003c" + "e0c4000037" + "aa".repeat(52),
            "0101050001" + "aaaaaa" + "00".repeat(56),
        )
    }
}
