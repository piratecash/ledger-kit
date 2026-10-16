package cash.p.terminal.ledgerkit.stellar

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.RecordingTransport
import cash.p.terminal.ledgerkit.ReplayTransport
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.apduData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// APDU logs: LedgerHQ/ledger-live libs/ledgerjs/packages/hw-app-str/tests/Str.test.ts.
class StellarAppClientTest {
    @Test
    fun getPublicKey_referenceLog_returnsKey() = runTest {
        val transport = ReplayTransport(
            """
            => e00200000d038000002c8000009480000000
            <= 9a222500cf47b03d05edec04ed3294cece1de727ccadb401f47d6b4b230e81a09000
            """,
        )

        val publicKey = StellarAppClient(transport).getPublicKey(PATH)

        assertEquals("9a222500cf47b03d05edec04ed3294cece1de727ccadb401f47d6b4b230e81a0", publicKey.toHexString())
        transport.assertAllReplayed()
    }

    @Test
    fun getPublicKey_display_sendsConfirmFlag() = runTest {
        val transport = ReplayTransport(
            """
            => e00200010d038000002c8000009480000000
            <= 9a222500cf47b03d05edec04ed3294cece1de727ccadb401f47d6b4b230e81a09000
            """,
        )

        StellarAppClient(transport).getPublicKey(PATH, display = true)

        transport.assertAllReplayed()
    }

    @Test
    fun getPublicKey_rejectedOnDevice_throwsUserRejected() = runTest {
        val transport = ReplayTransport(
            """
            => e00200010d038000002c8000009480000000
            <= 6985
            """,
        )

        assertFailsWith<LedgerException.UserRejected> { StellarAppClient(transport).getPublicKey(PATH, display = true) }
    }

    @Test
    fun getPublicKey_unhardenedIndex_sendsNothing() = runTest {
        val transport = RecordingTransport { error("nothing may be sent") }

        assertFailsWith<IllegalArgumentException> { StellarAppClient(transport).getPublicKey(DerivationPath.parse("44'/148'/0")) }
    }

    @Test
    fun getPublicKey_longKey_throwsMalformedResponse() = runTest {
        assertFailsWith<LedgerException.MalformedResponse> { client("11".repeat(33)).getPublicKey(PATH) }
    }

    @Test
    fun signTransaction_overFourChunks_sendsReferenceChunks() = runTest {
        val transport = ReplayTransport(
            """
            => e0040080ff038000002c80000094800000007ac33997544e3175d266bd022439b22cdb16508c01163f26e5cb2a3e1045a9790000000200000000e93388bbfd2fbd11806dd0bd59cea9079e7cc70ce7b1e154f114cdfe4e466ecd000071d7002d9fed000000010000000000000000000000010000000100000000e93388bbfd2fbd11806dd0bd59cea9079e7cc70ce7b1e154f114cdfe4e466ecd00000018000000020000024c0061736d0100000001150460027e7e017e60037e7e7e017e6000017e600000021904016c01300000016c01310000016c015f0001016c013800000305040203030305030100100619037f01418080c0000b7f00418080c0000b7f00418080
            <= 9000
            => e0048080ffc0000b073505066d656d6f7279020009696e6372656d656e740004015f00070a5f5f646174615f656e6403010b5f5f686561705f6261736503020aa70104920102017f017e41002100024002400240428ebad0af86d43942021080808080004201520d00428ebad0af86d4394202108180808000220142ff01834204520d012001422088a721000b200041016a2200450d01428ebad0af86d4392000ad422086420484220142021082808080001a4284808080a0064284808080c00c1083808080001a20010f0b00000b108580808000000b0900108680808000000b040000000b02000b00730e636f6e74726163747370656376300000000000000040496e
            <= 9000
            => e0048080ff6372656d656e7420696e6372656d656e747320616e20696e7465726e616c20636f756e7465722c20616e642072657475726e73207468652076616c75652e00000009696e6372656d656e74000000000000000000000100000004001e11636f6e7472616374656e766d657461763000000000000000140000003900730e636f6e74726163746d65746176300000000000000005727376657200000000000006312e37332e3000000000000000000008727373646b7665720000003332302e302e302d72633223303939323431336639623035653562666231663837326263653939653839643931323962326536310000000000000000010000000000000001
            <= 9000
            => e00480003c0000000713e16858bde4ab50a006dbf07172288f3ec19d8640d1a853016c60c15c2511170000000000186636000002b000000000000000000000000c
            <= 83968afc83b45d0672f5b5259a7bab68ee030f6ffda0c2f3fd550e3b5dd40b3a6bee1f246e760523ab6bd434f9e11f237ffd16bc8f18bb58a59eb94abe1a5a0c9000
            """,
        )

        val signature = StellarAppClient(transport).signTransaction(PATH, signatureBaseOf(transport.commands))

        assertEquals(
            "83968afc83b45d0672f5b5259a7bab68ee030f6ffda0c2f3fd550e3b5dd40b3a6bee1f246e760523ab6bd434f9e11f237ffd16bc8f18bb58a59eb94abe1a5a0c",
            signature.toHexString(),
        )
        transport.assertAllReplayed()
    }

    @Test
    fun signTransaction_oneChunk_sendsFirstAndLastFlags() = runTest {
        val transport = ReplayTransport(
            """
            => e0040000ff038000002c80000094800000007ac33997544e3175d266bd022439b22cdb16508c01163f26e5cb2a3e1045a9790000000200000000e93388bbfd2fbd11806dd0bd59cea9079e7cc70ce7b1e154f114cdfe4e466ecd000071d7002d9fed000000010000000000000000000000010000000100000000e93388bbfd2fbd11806dd0bd59cea9079e7cc70ce7b1e154f114cdfe4e466ecd00000018000000020000024c0061736d0100000001150460027e7e017e60037e7e7e017e6000017e600000021904016c01300000016c01310000016c015f0001016c013800000305040203030305030100100619037f01418080c0000b7f00418080c0000b7f00418080
            <= f30f5d707a6dbde681f9604832391295719130790578ecaca0b1d37a68923b15714b36f4507e56aff2d0e3256ed5af496a6b021896e13dca00a8283e426860089000
            """,
        )

        StellarAppClient(transport).signTransaction(PATH, signatureBaseOf(transport.commands))

        transport.assertAllReplayed()
    }

    @Test
    fun signTransaction_tooLarge_throwsDataTooLarge() = runTest {
        assertFailsWith<LedgerException.DataTooLarge> { client(status = "b004").signTransaction(PATH, SIGNATURE_BASE) }
    }

    @Test
    fun signTransaction_parsingFails_throwsInvalidRequest() = runTest {
        assertFailsWith<LedgerException.InvalidRequest> { client(status = "b005").signTransaction(PATH, SIGNATURE_BASE) }
    }

    @Test
    fun signTransaction_sorobanBlindSigningDisabled_throwsBlindSigningRequired() = runTest {
        assertFailsWith<LedgerException.BlindSigningRequired> { client(status = "6c66").signTransaction(PATH, SIGNATURE_BASE) }
    }

    @Test
    fun signTransaction_rejectedOnDevice_throwsUserRejected() = runTest {
        assertFailsWith<LedgerException.UserRejected> { client(status = "6985").signTransaction(PATH, SIGNATURE_BASE) }
    }

    @Test
    fun signTransaction_shortSignature_throwsMalformedResponse() = runTest {
        assertFailsWith<LedgerException.MalformedResponse> { client("ab".repeat(63)).signTransaction(PATH, SIGNATURE_BASE) }
    }

    @Test
    fun signTransaction_answerBeforeLastChunk_throwsMalformedResponse() = runTest {
        assertFailsWith<LedgerException.MalformedResponse> { client("ab".repeat(64)).signTransaction(PATH, ByteArray(300)) }
    }

    private fun client(answer: String = "", status: String = "9000") =
        StellarAppClient(RecordingTransport { (answer + status).hexToByteArray() })

    /** The signature base the replayed commands carry after the path. */
    private fun signatureBaseOf(commands: List<ByteArray>): ByteArray {
        val payload = commands.fold(ByteArray(0)) { bytes, command -> bytes + command.apduData() }
        return payload.copyOfRange(1 + 4 * payload[0], payload.size)
    }

    private companion object {
        val PATH = DerivationPath.parse("44'/148'/0'")
        val SIGNATURE_BASE = ByteArray(100)
    }
}
