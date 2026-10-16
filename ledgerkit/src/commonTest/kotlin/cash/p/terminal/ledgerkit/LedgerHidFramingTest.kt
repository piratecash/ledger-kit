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
        val packets = LedgerHidFraming.encode("b001000000".hexToByteArray())

        assertEquals(1, packets.size)
        assertEquals("0101050000" + "0005" + "b001000000" + "00".repeat(52), packets[0].toHexString())
    }

    @Test
    fun encode_apduFillsFirstPacketExactly_singlePacket() {
        val packets = LedgerHidFraming.encode(ByteArray(57) { 0x11 })

        assertEquals(1, packets.size)
        assertEquals("0101050000" + "0039" + "11".repeat(57), packets[0].toHexString())
    }

    @Test
    fun encode_apduOneByteOverFirstPacket_secondPacketWithSequenceOne() {
        val packets = LedgerHidFraming.encode(ByteArray(58) { 0x11 })

        assertEquals(2, packets.size)
        assertEquals("0101050001" + "11" + "00".repeat(58), packets[1].toHexString())
    }

    @Test
    fun encode_longApdu_everyPacketIs64Bytes() {
        val packets = LedgerHidFraming.encode(ByteArray(300) { it.toByte() })

        assertEquals(listOf(0, 1, 2, 3, 4, 5), packets.map { it.readUInt16(3) })
        packets.forEach { assertEquals(LedgerHidFraming.PACKET_SIZE, it.size) }
    }

    @Test
    fun encode_twoPacketReferenceVector_matchesSpec() {
        assertEquals(referencePackets, LedgerHidFraming.encode(referenceApdu.hexToByteArray()).map { it.toHexString() })
    }

    @Test
    fun accept_twoPacketReferenceVector_returnsMessage() {
        val decoder = LedgerHidFraming.ResponseDecoder()

        assertNull(decoder.accept(referencePackets[0].hexToByteArray()))
        assertEquals(referenceApdu, decoder.accept(referencePackets[1].hexToByteArray())?.toHexString())
    }

    @Test
    fun accept_singlePacketResponse_returnsData() {
        val decoder = LedgerHidFraming.ResponseDecoder()

        val response = decoder.accept(("0101050000" + "0002" + "9000" + "00".repeat(55)).hexToByteArray())

        assertEquals("9000", response?.toHexString())
    }

    @Test
    fun accept_multiPacketResponse_returnsDataAfterLastPacket() {
        val data = ByteArray(200) { it.toByte() }
        val decoder = LedgerHidFraming.ResponseDecoder()
        val packets = LedgerHidFraming.encode(data)

        packets.dropLast(1).forEach { assertNull(decoder.accept(it)) }

        assertContentEquals(data, decoder.accept(packets.last()))
    }

    @Test
    fun accept_wrongChannel_throws() {
        val packet = LedgerHidFraming.encode(byteArrayOf(1)).single().also { it[1] = 0x02 }

        assertFailsWith<IllegalStateException> { LedgerHidFraming.ResponseDecoder().accept(packet) }
    }

    @Test
    fun accept_wrongTag_throws() {
        val packet = LedgerHidFraming.encode(byteArrayOf(1)).single().also { it[2] = 0x06 }

        assertFailsWith<IllegalStateException> { LedgerHidFraming.ResponseDecoder().accept(packet) }
    }

    @Test
    fun accept_sequenceGap_throws() {
        val packets = LedgerHidFraming.encode(ByteArray(200))
        val decoder = LedgerHidFraming.ResponseDecoder()
        decoder.accept(packets[0])

        assertFailsWith<IllegalStateException> { decoder.accept(packets[2]) }
    }

    private companion object {
        // Hand-derived from the Ledger HID framing used by ledgerjs hw-transport (Apache-2.0):
        // header 0101|05|seq, length 003c only in packet 0, 57 then 59 payload bytes, zero padding.
        val referenceApdu = "e0c4000037" + "aa".repeat(55)
        val referencePackets = listOf(
            "0101050000" + "003c" + "e0c4000037" + "aa".repeat(52),
            "0101050001" + "aaaaaa" + "00".repeat(56),
        )
    }
}
