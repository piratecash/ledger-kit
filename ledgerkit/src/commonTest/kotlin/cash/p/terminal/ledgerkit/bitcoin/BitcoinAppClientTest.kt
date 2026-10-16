package cash.p.terminal.ledgerkit.bitcoin

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.RecordingTransport
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.ins
import cash.p.terminal.ledgerkit.sha256
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// Command layouts: LedgerHQ/app-bitcoin-new doc/bitcoin.md and bitcoin_client_rs/src/command.rs.
class BitcoinAppClientTest {
    private val policy = SignPsbtVectors.wpkh1to2.policy

    @Test
    fun getExtendedPubkey_path_sendsDisplayFlagAndBigEndianPath() = runTest {
        val transport = RecordingTransport { "tpubABC".encodeToByteArray() + OK }

        val xpub = BitcoinAppClient(transport).getExtendedPubkey(DerivationPath.parse("m/84'/1'/0'"), display = true)

        assertEquals("tpubABC", xpub)
        assertEquals("e10000010e0103800000548000000180000000", transport.sent.single().toHexString())
    }

    @Test
    fun getMasterFingerprint_answer_returnsFourBytes() = runTest {
        val transport = RecordingTransport { "f5acc2fd9000".hexToByteArray() }

        assertEquals("f5acc2fd", BitcoinAppClient(transport).getMasterFingerprint().toHexString())
        assertEquals("e105000100", transport.sent.single().toHexString())
    }

    @Test
    fun getWalletAddress_appAsksForPolicy_answersPreimageThroughContinueAndReturnsAddress() = runTest {
        val serialized = policy.serialize()
        val transport = RecordingTransport { apdu ->
            if (apdu[0] == 0xE1.toByte()) byteArrayOf(0x40, 0x00) + sha256(serialized) + INTERRUPTED
            else "tb1qaddress".encodeToByteArray() + OK
        }

        val address = BitcoinAppClient(transport).getWalletAddress(policy, change = true, addressIndex = 7)

        assertEquals("tb1qaddress", address)
        val request = transport.sent[0]
        assertEquals("e1030001", request.copyOfRange(0, 4).toHexString())
        assertContentEquals(byteArrayOf(0) + policy.id + ByteArray(32) + byteArrayOf(1, 0, 0, 0, 7), request.copyOfRange(5, request.size))
        assertContentEquals(
            "f8010001".hexToByteArray() + (serialized.size + 2).toByte() + serialized.size.toByte() + serialized.size.toByte() + serialized,
            transport.sent[1],
        )
    }

    @Test
    fun signPsbt_appYieldsSignature_returnsInputIndexKeyAndSignature() = runTest {
        val pubkey = "03ee2c3d98eb1f93c0a1aa8e5a4009b70eb7b44ead15f1666f136b012ad58d3068"
        val signature = "3045022100ab44f34dd7e87c9054591297a101e8500a0641d1d591878d0d23cf8096fa79e802205d12d1062d925e27b57bdcf994ecf332ad0a8e67b8fe407bab2101255da632aa01"
        val transport = RecordingTransport { apdu ->
            if (apdu[0] == 0xE1.toByte()) "100021$pubkey$signature".hexToByteArray() + INTERRUPTED else OK
        }

        val signatures = BitcoinAppClient(transport).signPsbt(PsbtV0Fixture(SignPsbtVectors.wpkh1to2.psbt).toPsbt(), policy)

        assertEquals(listOf(Triple(0, pubkey, signature)), signatures.map { Triple(it.inputIndex, it.publicKey.toHexString(), it.signature.toHexString()) })
        assertEquals("f801000100", transport.sent[1].toHexString())
    }

    @Test
    fun signPsbt_yieldForUnknownInput_throws() = runTest {
        val transport = RecordingTransport { apdu ->
            if (apdu[0] == 0xE1.toByte()) "10052102${"00".repeat(32)}3044".hexToByteArray() + INTERRUPTED else OK
        }

        assertFailsWith<IllegalStateException> {
            BitcoinAppClient(transport).signPsbt(PsbtV0Fixture(SignPsbtVectors.wpkh1to2.psbt).toPsbt(), policy)
        }
    }

    // While interrupted the app takes any APDU but CONTINUE as an abort and answers 6A80 (src/boilerplate/dispatcher.c).
    @Test
    fun getWalletAddress_requestClientCannotAnswer_abortsPendingCommandSoNextCommandRuns() = runTest {
        var waitingForContinue = false
        val transport = RecordingTransport { apdu ->
            when {
                waitingForContinue -> {
                    check(apdu[0] != 0xF8.toByte()) { "Unexpected CONTINUE" }
                    waitingForContinue = false
                    "6a80".hexToByteArray()
                }
                apdu.ins() == 0x03 -> {
                    waitingForContinue = true
                    byteArrayOf(0x40, 0x00) + ByteArray(32) + INTERRUPTED
                }
                else -> "f5acc2fd".hexToByteArray() + OK
            }
        }
        val client = BitcoinAppClient(transport)

        assertFailsWith<IllegalStateException> { client.getWalletAddress(policy, change = false, addressIndex = 0) }

        assertEquals("f5acc2fd", client.getMasterFingerprint().toHexString())
    }

    @Test
    fun signPsbt_emptyYieldRepeated_failsAndAbortsPendingCommand() = runTest {
        val transport = RecordingTransport { apdu -> if (apdu.ins() == 0x05) "6a80".hexToByteArray() else "10e000".hexToByteArray() }

        assertFailsWith<IllegalStateException> { signWpkh(transport) }

        assertEquals(listOf(0x04, 0x05), transport.sent.map { it.ins() })
    }

    @Test
    fun signPsbt_moreYieldsThanInputs_failsAndAbortsPendingCommand() = runTest {
        val yield = "100021${"02".repeat(33)}${VALID_DER}01".hexToByteArray() + INTERRUPTED
        val transport = RecordingTransport { apdu -> if (apdu.ins() == 0x05) "6a80".hexToByteArray() else yield }

        assertFailsWith<IllegalStateException> { signWpkh(transport) }

        assertEquals(listOf(0x04, 0x01, 0x05), transport.sent.map { it.ins() })
    }

    @Test
    fun signPsbt_taprootSignatureOfOneByte_throwsMalformedResponse() = runTest {
        val key = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
        val case = SignPsbtVectors.tr1to2

        assertFailsWith<LedgerException.MalformedResponse> {
            BitcoinAppClient(yieldingOnce("100020${key}00")).signPsbt(PsbtV0Fixture(case.psbt).toPsbt(), case.policy)
        }
    }

    @Test
    fun signPsbt_taprootSignatureWithForeignSighash_throwsMalformedResponse() = runTest {
        val case = SignPsbtVectors.tr1to2

        assertFailsWith<LedgerException.MalformedResponse> {
            BitcoinAppClient(yieldingOnce("100020${"11".repeat(32)}${"22".repeat(64)}83")).signPsbt(PsbtV0Fixture(case.psbt).toPsbt(), case.policy)
        }
    }

    @Test
    fun signPsbt_ecdsaSignatureNotStrictDer_throwsMalformedResponse() = runTest {
        assertFailsWith<LedgerException.MalformedResponse> {
            signWpkh(yieldingOnce("100021${"02".repeat(33)}300604010104010101"))
        }
    }

    @Test
    fun signPsbt_ecdsaSignatureWithoutSighashAll_throwsMalformedResponse() = runTest {
        assertFailsWith<LedgerException.MalformedResponse> {
            signWpkh(yieldingOnce("100021${"02".repeat(33)}${VALID_DER}83"))
        }
    }

    private suspend fun signWpkh(transport: RecordingTransport) =
        BitcoinAppClient(transport).signPsbt(PsbtV0Fixture(SignPsbtVectors.wpkh1to2.psbt).toPsbt(), policy)

    private fun yieldingOnce(yield: String) = RecordingTransport { apdu ->
        if (apdu[0] == 0xE1.toByte()) yield.hexToByteArray() + INTERRUPTED else OK
    }

    @Test
    fun signPsbt_appRefusesData_throwsInvalidRequest() = runTest {
        val transport = RecordingTransport { "6a80".hexToByteArray() }

        val error = assertFailsWith<LedgerException.InvalidRequest> {
            BitcoinAppClient(transport).signPsbt(PsbtV0Fixture(SignPsbtVectors.wpkh1to2.psbt).toPsbt(), policy)
        }
        assertEquals(0x6A80, error.statusWord)
    }

    @Test
    fun signPsbt_userRejects_throwsUserRejected() = runTest {
        val transport = RecordingTransport { "6985".hexToByteArray() }

        assertFailsWith<LedgerException.UserRejected> {
            BitcoinAppClient(transport).signPsbt(PsbtV0Fixture(SignPsbtVectors.wpkh1to2.psbt).toPsbt(), policy)
        }
    }

    private companion object {
        val OK = "9000".hexToByteArray()
        val INTERRUPTED = "e000".hexToByteArray()
        const val VALID_DER =
            "3045022100ab44f34dd7e87c9054591297a101e8500a0641d1d591878d0d23cf8096fa79e802205d12d1062d925e27b57bdcf994ecf332ad0a8e67b8fe407bab2101255da632aa"
    }
}
