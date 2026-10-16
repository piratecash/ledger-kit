package cash.p.terminal.ledgerkit.tron

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.RecordingTransport
import cash.p.terminal.ledgerkit.ReplayTransport
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.apduData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// APDU logs: LedgerHQ/ledger-live libs/ledgerjs/packages/hw-app-trx/tests/Trx.test.ts unless noted.
class TronAppClientTest {
    @Test
    fun getAddress_referenceLog_parsesKeyAndAddress() = runTest {
        val transport = ReplayTransport(
            """
            => e002000015058000002c800000c3800000000000000000000000
            <= 41040357bda0e415396eab766d392d5b996eb4a0bec6ccbb166d581341ebb50ebb54c30b365823884d8169e4c784373f0d3b871f3d16bca0b33a292d98f6cf07855a225457646e57427a4664425031623873715a3552634644626b563373426d6e787359759000
            """,
        )

        val address = TronAppClient(transport).getAddress(PATH)

        assertEquals("TWdnWBzFdBP1b8sqZ5RcFDbkV3sBmnxsYu", address.address)
        assertEquals(
            "040357bda0e415396eab766d392d5b996eb4a0bec6ccbb166d581341ebb50ebb54c30b365823884d8169e4c784373f0d3b871f3d16bca0b33a292d98f6cf07855a",
            address.publicKey.toHexString(),
        )
        transport.assertAllReplayed()
    }

    @Test
    fun getAddress_display_sendsConfirmFlag() = runTest {
        val transport = RecordingTransport { (ADDRESS_ANSWER + "9000").hexToByteArray() }

        TronAppClient(transport).getAddress(PATH, display = true)

        assertEquals("e002010015058000002c800000c3800000000000000000000000", transport.sent.single().toHexString())
    }

    @Test
    fun getAddress_notBase58Address_throwsMalformedResponse() = runTest {
        val answer = ADDRESS_ANSWER.dropLast(2) + "30"

        assertFailsWith<LedgerException.MalformedResponse> { client(answer).getAddress(PATH) }
    }

    @Test
    fun getAddress_trailingBytes_throwsMalformedResponse() = runTest {
        assertFailsWith<LedgerException.MalformedResponse> { client(ADDRESS_ANSWER + "00").getAddress(PATH) }
    }

    @Test
    fun signTransaction_referenceLog_returnsSignatureWithRecoveryId() = runTest {
        val transport = ReplayTransport(
            """
            => e0041000c9058000002c800000c38000000000000000000000000a023dce220895da42177db0050740d8e0a5feed2d522c43727970746f436861696e2d54726f6e5352204c6564676572205472616e73616374696f6e732054657374735a68080112640a2d747970652e676f6f676c65617069732e636f6d2f70726f746f636f6c2e5472616e73666572436f6e747261637412330a1541c8599111f29c1e1e061265b4af93ea1f274ad78a121541c8599111f29c1e1e061265b4af93ea1f274ad78a1880c2d72f709d94a2feed2d
            <= 3816b17b81c0a528b9f7506029473c82a3931945999426550a18d788651cb59d2d674a2386501107af2d51a106a67f8cf2e6adf2aded3220b6d1fc9847aec7c0009000
            """,
        )

        val rawData = transport.commands.single().apduData().let { it.copyOfRange(PATH_SIZE, it.size) }

        val signature = TronAppClient(transport).signTransaction(PATH, rawData)

        assertEquals(
            "3816b17b81c0a528b9f7506029473c82a3931945999426550a18d788651cb59d2d674a2386501107af2d51a106a67f8cf2e6adf2aded3220b6d1fc9847aec7c000",
            signature.toHexString(),
        )
        transport.assertAllReplayed()
    }

    // signTransaction in Trx.ts: fields are packed up to 250 bytes, P1 0x00 first, 0x80 in between, 0x90 last.
    @Test
    fun signTransaction_overThreeChunks_splitsBetweenFields() = runTest {
        val fields = listOf(lengthDelimited(10, 200), varint(8, 1_760_000_060_000), lengthDelimited(10, 230), lengthDelimited(11, 100))
        val transport = RecordingTransport { apdu -> (if (apdu[2] == P1_LAST) SIGNATURE + "9000" else "9000").hexToByteArray() }

        TronAppClient(transport).signTransaction(PATH, fields.reduce(ByteArray::plus))

        assertEquals(listOf(0x00, 0x80, 0x90), transport.sent.map { it[2].toInt() and 0xFF })
        assertEquals(
            listOf(PATH_SIZE + fields[0].size + fields[1].size, fields[2].size, fields[3].size),
            transport.sent.map { it.apduData().size },
        )
    }

    @Test
    fun signTransaction_chunkFullAt250Bytes_startsNextFieldInNewChunk() = runTest {
        val transport = RecordingTransport { apdu -> (if (apdu[2] == P1_LAST) SIGNATURE + "9000" else "9000").hexToByteArray() }

        TronAppClient(transport).signTransaction(PATH, lengthDelimited(10, 226) + varint(8, 1))

        assertEquals(listOf(250, 2), transport.sent.map { it.apduData().size })
    }

    @Test
    fun signTransaction_transferWithMemoOver250Bytes_throwsDataTooLargeBeforeSending() = runTest {
        val transport = RecordingTransport { error("nothing may be sent") }
        val rawData = TRANSFER_HEADER.hexToByteArray() + lengthDelimited(10, 248) + TRANSFER_CONTRACT_AND_TIMESTAMP.hexToByteArray()

        assertFailsWith<LedgerException.DataTooLarge> { TronAppClient(transport).signTransaction(PATH, rawData) }
    }

    @Test
    fun signTransaction_truncatedField_sendsNothing() = runTest {
        val transport = RecordingTransport { error("nothing may be sent") }

        assertFailsWith<IllegalArgumentException> {
            TronAppClient(transport).signTransaction(PATH, lengthDelimited(10, 20).copyOfRange(0, 15))
        }
    }

    @Test
    fun signTransaction_fixed64Field_sendsNothing() = runTest {
        val transport = RecordingTransport { error("nothing may be sent") }

        assertFailsWith<IllegalArgumentException> { TronAppClient(transport).signTransaction(PATH, "4101".hexToByteArray()) }
    }

    @Test
    fun signTransaction_dataNotAllowed_throwsBlindSigningRequired() = runTest {
        assertFailsWith<LedgerException.BlindSigningRequired> { client(status = "6a8b").signTransaction(PATH, TRANSACTION) }
    }

    @Test
    fun signTransaction_customContractNotAllowed_throwsBlindSigningRequired() = runTest {
        assertFailsWith<LedgerException.BlindSigningRequired> { client(status = "6a8d").signTransaction(PATH, TRANSACTION) }
    }

    @Test
    fun signTransaction_incorrectData_throwsInvalidRequest() = runTest {
        assertFailsWith<LedgerException.InvalidRequest> { client(status = "6a80").signTransaction(PATH, TRANSACTION) }
    }

    @Test
    fun signTransaction_recoveryIdAboveOne_throwsMalformedResponse() = runTest {
        assertFailsWith<LedgerException.MalformedResponse> { client(SIGNATURE.dropLast(2) + "1b").signTransaction(PATH, TRANSACTION) }
    }

    @Test
    fun signTransaction_shortSignature_throwsMalformedResponse() = runTest {
        assertFailsWith<LedgerException.MalformedResponse> { client(SIGNATURE.dropLast(2)).signTransaction(PATH, TRANSACTION) }
    }

    private fun client(answer: String = "", status: String = "9000") =
        TronAppClient(RecordingTransport { (answer + status).hexToByteArray() })

    private fun lengthDelimited(field: Int, size: Int): ByteArray =
        byteArrayOf((field shl 3 or 2).toByte()) + varintBytes(size.toLong()) + ByteArray(size) { 0x61 }

    private fun varint(field: Int, value: Long): ByteArray = byteArrayOf((field shl 3).toByte()) + varintBytes(value)

    private fun varintBytes(value: Long): ByteArray {
        val bytes = mutableListOf<Byte>()
        var rest = value
        while (rest >= 0x80) {
            bytes += (rest and 0x7F or 0x80).toByte()
            rest = rest shr 7
        }
        return (bytes + rest.toByte()).toByteArray()
    }

    private companion object {
        val PATH = DerivationPath.parse("44'/195'/0'/0/0")
        const val PATH_SIZE = 21
        val TRANSACTION = "0a023dce".hexToByteArray()
        const val P1_LAST = 0x90.toByte()
        const val SIGNATURE =
            "3816b17b81c0a528b9f7506029473c82a3931945999426550a18d788651cb59d2d674a2386501107af2d51a106a67f8cf2e6adf2aded3220b6d1fc9847aec7c000"
        // The reference log's transfer split around its memo (field 10).
        const val TRANSFER_HEADER = "0a023dce220895da42177db0050740d8e0a5feed2d"
        const val TRANSFER_CONTRACT_AND_TIMESTAMP =
            "5a68080112640a2d747970652e676f6f676c65617069732e636f6d2f70726f746f636f6c2e5472616e73666572436f6e747261637412330a1541c8599111f29c1e1e061265b4af93ea1f274ad78a121541c8599111f29c1e1e061265b4af93ea1f274ad78a1880c2d72f709d94a2feed2d"
        const val ADDRESS_ANSWER =
            "41040357bda0e415396eab766d392d5b996eb4a0bec6ccbb166d581341ebb50ebb54c30b365823884d8169e4c784373f0d3b871f3d16bca0b33a292d98f6cf07855a225457646e57427a4664425031623873715a3552634644626b563373426d6e78735975"
    }
}
