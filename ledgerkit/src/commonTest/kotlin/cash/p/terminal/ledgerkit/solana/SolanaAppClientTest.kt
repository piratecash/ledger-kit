package cash.p.terminal.ledgerkit.solana

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.RecordingTransport
import cash.p.terminal.ledgerkit.ReplayTransport
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.apduData
import cash.p.terminal.ledgerkit.p2
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// APDU logs: LedgerHQ/ledger-live libs/ledgerjs/packages/hw-app-solana/tests/Solana.test.ts unless noted.
class SolanaAppClientTest {
    @Test
    fun getAppConfiguration_referenceLog_parsesFlagAndVersion() = runTest {
        val transport = ReplayTransport(
            """
            => e004000000
            <= 00000100069000
            """,
        )

        val configuration = SolanaAppClient(transport).getAppConfiguration()

        assertEquals(false to "1.0.6", configuration.blindSigningEnabled to configuration.version)
        transport.assertAllReplayed()
    }

    @Test
    fun getAppConfiguration_unknownBlindSigningFlag_throwsMalformedResponse() = runTest {
        assertFailsWith<LedgerException.MalformedResponse> { client("0200010f02").getAppConfiguration() }
    }

    @Test
    fun getPublicKey_referenceLog_returnsKey() = runTest {
        val transport = ReplayTransport(
            """
            => e005000015058000002c800001f5800000008000000080000000
            <= 4d65a10662b9759d62bb59048366705454654cf4f9b4b3525cf314429e46c6919000
            """,
        )

        val publicKey = SolanaAppClient(transport).getPublicKey(DerivationPath.parse("44'/501'/0'/0'/0'"))

        assertEquals("4d65a10662b9759d62bb59048366705454654cf4f9b4b3525cf314429e46c691", publicKey.toHexString())
        transport.assertAllReplayed()
    }

    @Test
    fun getPublicKey_display_sendsConfirmFlag() = runTest {
        val transport = ReplayTransport(
            """
            => e005010015058000002c800001f5800000008000000080000000
            <= 4d65a10662b9759d62bb59048366705454654cf4f9b4b3525cf314429e46c6919000
            """,
        )

        SolanaAppClient(transport).getPublicKey(DerivationPath.parse("44'/501'/0'/0'/0'"), display = true)

        transport.assertAllReplayed()
    }

    @Test
    fun getPublicKey_unhardenedIndex_sendsNothing() = runTest {
        val transport = RecordingTransport { error("nothing may be sent") }

        assertFailsWith<IllegalArgumentException> { SolanaAppClient(transport).getPublicKey(DerivationPath.parse("44'/501'/0")) }
    }

    @Test
    fun getPublicKey_shortKey_throwsMalformedResponse() = runTest {
        assertFailsWith<LedgerException.MalformedResponse> { client("11".repeat(31)).getPublicKey(PATH) }
    }

    @Test
    fun signTransaction_referenceLog_returnsSignature() = runTest {
        val transport = ReplayTransport(
            """
            => e0060100a001028000002c800001f5010001035eb9862fe23e544a2a0969cc157cb31fd72901cc2824d536a67fb8ee911e02363b9ba3a2ebaf40c1cd672a80a8e1932b982cca8264be33c39359701e113c3da20000000000000000000000000000000000000000000000000000000000000000030303030303030303030303030303030303030303030303030303030303030301020200010c020000002a00000000000000
            <= 1ad0702faddc8b2072c59547637c10e6affad2f186b69cf3288f2b029de2e309e1d73b73eb925a79f7b0d026ee07203d714e15807267001fbd3914de76a5490e9000
            """,
        )

        val signature = SolanaAppClient(transport).signTransaction(DerivationPath.parse("44'/501'"), messageOf(transport.commands))

        assertEquals(SIGNATURE_1AD0, signature.toHexString())
        transport.assertAllReplayed()
    }

    @Test
    fun signTransaction_overTwoChunks_sendsReferenceChunks() = runTest {
        val transport = ReplayTransport(
            """
            => e0060102ff01028000002c800001f5010001035eb9862fe23e544a2a0969cc157cb31fd72901cc2824d536a67fb8ee911e02363b9ba3a2ebaf40c1cd672a80a8e1932b982cca8264be33c39359701e113c3da20000000000000000000000000000000000000000000000000000000000000000030303030303030303030303030303030303030303030303030303030303030307020200010c020000002a00000000000000020200010c020000002a00000000000000020200010c020000002a00000000000000020200010c020000002a00000000000000020200010c020000002a00000000000000020200010c020000002a00000000000000020200010c020000002a
            <= 9000
            => e00601010700000000000000
            <= d9ed529ab24ab4e796c006cf85e7e51db85825b31d3477dd3bf8350745b7a9cdc29840442d96dbeca73289a06841655d9f5c342bd6f697dcfdb6dadf8a0784049000
            """,
        )

        val signature = SolanaAppClient(transport).signTransaction(DerivationPath.parse("44'/501'"), messageOf(transport.commands))

        assertEquals(64, signature.size)
        transport.assertAllReplayed()
    }

    // P2 flags follow sendToDevice in Solana.ts: MORE on the first chunk, EXTEND | MORE in between, EXTEND last.
    @Test
    fun signTransaction_overThreeChunks_marksFirstMiddleAndLastChunks() = runTest {
        val transport = RecordingTransport { apdu -> if (apdu.p2() == P2_EXTEND) "${"ab".repeat(64)}9000".hexToByteArray() else "9000".hexToByteArray() }

        SolanaAppClient(transport).signTransaction(PATH, ByteArray(600) { it.toByte() })

        assertEquals(listOf(0x02, 0x03, 0x01), transport.sent.map { it.p2() })
        assertEquals(listOf(255, 255, 104), transport.sent.map { it.apduData().size })
    }

    @Test
    fun signTransaction_blindSigningDisabled_throwsBlindSigningRequired() = runTest {
        assertFailsWith<LedgerException.BlindSigningRequired> { client(status = "6808").signTransaction(PATH, MESSAGE) }
    }

    @Test
    fun signTransaction_messageTooLarge_throwsDataTooLarge() = runTest {
        assertFailsWith<LedgerException.DataTooLarge> { client(status = "6a83").signTransaction(PATH, MESSAGE) }
    }

    // In app-solana 0x6A84 is an invalid derivation path, not the shared "data too large".
    @Test
    fun signTransaction_invalidPathStatus_throwsInvalidRequest() = runTest {
        val error = assertFailsWith<LedgerException.InvalidRequest> { client(status = "6a84").signTransaction(PATH, MESSAGE) }

        assertEquals(0x6A84, error.statusWord)
    }

    @Test
    fun signTransaction_userRejects_throwsUserRejected() = runTest {
        assertFailsWith<LedgerException.UserRejected> { client(status = "6985").signTransaction(PATH, MESSAGE) }
    }

    @Test
    fun signTransaction_shortSignature_throwsMalformedResponse() = runTest {
        assertFailsWith<LedgerException.MalformedResponse> { client("ab".repeat(63)).signTransaction(PATH, MESSAGE) }
    }

    @Test
    fun signTransaction_answerBeforeLastChunk_throwsMalformedResponse() = runTest {
        assertFailsWith<LedgerException.MalformedResponse> { client("ab".repeat(64)).signTransaction(PATH, ByteArray(300)) }
    }

    @Test
    fun signOffchainMessage_referenceLog_returnsSignature() = runTest {
        val transport = ReplayTransport(
            """
            => e00701003a01028000002c800001f5ff736f6c616e61206f6666636861696e00001c004c6f6e67204f66662d436861696e2054657374204d6573736167652e
            <= dd23d8f798aeb0085f97f3aae20e52866bf529e0d880959784ff0e91beaf6f2eb73b7b0ef484801399066b92561f6c523b27f9579e17d0f8106b33edc5f45b079000
            """,
        )

        val signature = SolanaAppClient(transport).signOffchainMessage(DerivationPath.parse("44'/501'"), messageOf(transport.commands))

        assertEquals(64, signature.size)
        transport.assertAllReplayed()
    }

    @Test
    fun provideTrustedDynamicDescriptor_referenceLog_appendsSignatureTlv() = runTest {
        val transport = ReplayTransport(
            """
            => e02200000711220803334455
            <= 9000
            """,
        )

        SolanaAppClient(transport).provideTrustedDynamicDescriptor("1122".hexToByteArray(), "334455".hexToByteArray())

        transport.assertAllReplayed()
    }

    // hw-app-solana tests/descriptor.test.ts: a signature must fit a one-byte TLV length.
    @Test
    fun provideTrustedDynamicDescriptor_signatureOver255Bytes_sendsNothing() = runTest {
        val transport = RecordingTransport { error("nothing may be sent") }

        assertFailsWith<IllegalArgumentException> {
            SolanaAppClient(transport).provideTrustedDynamicDescriptor("000102030405".hexToByteArray(), ByteArray(256))
        }
    }

    @Test
    fun provideTrustedName_validDescriptor_sendsItAsIs() = runTest {
        val transport = RecordingTransport { "9000".hexToByteArray() }

        SolanaAppClient(transport).provideTrustedName("0102".hexToByteArray())

        assertEquals("e0210000020102", transport.sent.single().toHexString())
    }

    @Test
    fun provideTrustedName_invalidTrustedInfo_throwsInvalidRequest() = runTest {
        assertFailsWith<LedgerException.InvalidRequest> { client(status = "6c00").provideTrustedName("0102".hexToByteArray()) }
    }

    @Test
    fun getChallenge_fourBytes_returnsChallenge() = runTest {
        val transport = RecordingTransport { "a1b2c3d49000".hexToByteArray() }

        val challenge = SolanaAppClient(transport).getChallenge()

        assertEquals("a1b2c3d4", challenge.toHexString())
        assertEquals("e020000000", transport.sent.single().toHexString())
    }

    @Test
    fun getChallenge_shortAnswer_throwsMalformedResponse() = runTest {
        assertFailsWith<LedgerException.MalformedResponse> { client("a1b2c3").getChallenge() }
    }

    private fun client(answer: String = "", status: String = "9000") =
        SolanaAppClient(RecordingTransport { (answer + status).hexToByteArray() })

    /** The message the replayed sign commands carry after the signer count and the path. */
    private fun messageOf(commands: List<ByteArray>): ByteArray {
        val payload = commands.fold(ByteArray(0)) { bytes, command -> bytes + command.apduData() }
        return payload.copyOfRange(2 + 4 * payload[1], payload.size)
    }

    private companion object {
        val PATH = DerivationPath.parse("44'/501'/0'")
        val MESSAGE = ByteArray(100)
        const val P2_EXTEND = 0x01
        const val SIGNATURE_1AD0 =
            "1ad0702faddc8b2072c59547637c10e6affad2f186b69cf3288f2b029de2e309e1d73b73eb925a79f7b0d026ee07203d714e15807267001fbd3914de76a5490e"
    }
}
