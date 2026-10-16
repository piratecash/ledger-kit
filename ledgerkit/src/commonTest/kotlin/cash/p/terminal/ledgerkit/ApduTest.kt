package cash.p.terminal.ledgerkit

import cash.p.terminal.ledgerkit.apdu.Apdu
import cash.p.terminal.ledgerkit.apdu.ApduResponse
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.apdu.chunkedForApdu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ApduTest {
    @Test
    fun toBytes_withData_headerLengthAndData() {
        val apdu = Apdu(cla = 0xE0, ins = 0x02, p1 = 0x01, p2 = 0x00, data = "0102".hexToByteArray())

        assertEquals("e0020100020102", apdu.toBytes().toHexString())
    }

    @Test
    fun toBytes_withoutData_sendsZeroLength() {
        assertEquals("b001000000", Apdu(0xB0, 0x01, 0, 0).toBytes().toHexString())
    }

    @Test
    fun init_dataOverLimit_throws() {
        assertFailsWith<IllegalArgumentException> { Apdu(0xE0, 0x02, 0, 0, ByteArray(256)) }
    }

    @Test
    fun init_headerByteOutOfRange_throws() {
        assertFailsWith<IllegalArgumentException> { Apdu(0x100, 0x02, 0, 0) }
    }

    @Test
    fun chunkedForApdu_longPayload_fullChunksThenRemainder() {
        assertEquals(listOf(255, 255, 90), ByteArray(600).chunkedForApdu().map { it.size })
    }

    @Test
    fun chunkedForApdu_exactlyOneChunk_singleChunk() {
        assertEquals(listOf(255), ByteArray(255).chunkedForApdu().map { it.size })
    }

    @Test
    fun chunkedForApdu_empty_oneEmptyChunk() {
        assertEquals(listOf(0), ByteArray(0).chunkedForApdu().map { it.size })
    }

    @Test
    fun parse_dataAndStatusWord_splitsThem() {
        val response = ApduResponse.parse("abcd6985".hexToByteArray())

        assertEquals("abcd", response.data.toHexString())
        assertEquals(0x6985, response.statusWord)
    }

    @Test
    fun parse_shorterThanStatusWord_throws() {
        assertFailsWith<IllegalStateException> { ApduResponse.parse(byteArrayOf(0x90.toByte())) }
    }

    @Test
    fun requireSuccess_ok_returnsData() {
        assertEquals("ab", ApduResponse.parse("ab9000".hexToByteArray()).requireSuccess().toHexString())
    }

    @Test
    fun requireSuccess_userRejected_throwsUserRejected() {
        assertFailsWith<LedgerException.UserRejected> { ApduResponse.parse("6985".hexToByteArray()).requireSuccess() }
    }

    @Test
    fun fromStatusWord_genericTable_mapsToTypedErrors() {
        val expected = mapOf(
            0x6985 to LedgerException.UserRejected::class,
            0x6807 to LedgerException.AppNotInstalled::class,
            0x5515 to LedgerException.DeviceLocked::class,
            0x6982 to LedgerException.DeviceLocked::class,
            0x6E00 to LedgerException.WrongAppOpen::class,
            0x6D00 to LedgerException.WrongAppOpen::class,
            0x6511 to LedgerException.WrongAppOpen::class,
            0x6A84 to LedgerException.DataTooLarge::class,
        )

        expected.forEach { (statusWord, type) ->
            assertEquals(type, LedgerException.fromStatusWord(statusWord)::class, "0x${statusWord.toString(16)}")
        }
    }

    @Test
    fun fromStatusWord_unmapped_unknownWithStatusWord() {
        val error = assertIs<LedgerException.Unknown>(LedgerException.fromStatusWord(0x6A80))

        assertEquals(0x6A80, error.statusWord)
    }
}
