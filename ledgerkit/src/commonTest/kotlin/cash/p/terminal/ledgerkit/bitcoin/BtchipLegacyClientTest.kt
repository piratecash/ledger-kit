package cash.p.terminal.ledgerkit.bitcoin

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.RecordingTransport
import cash.p.terminal.ledgerkit.ReplayTransport
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.apduData
import cash.p.terminal.ledgerkit.compactSize
import cash.p.terminal.ledgerkit.ins
import cash.p.terminal.ledgerkit.p2
import cash.p.terminal.ledgerkit.sha256
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// APDU logs: LedgerHQ/ledger-live libs/ledgerjs/packages/hw-app-btc/tests (Btc.test.ts.disabled, trustedInputs.test.ts.disabled).
class BtchipLegacyClientTest {
    @Test
    fun getWalletPublicKey_referenceLog_parsesAddressAndChainCode() = runTest {
        val transport = ReplayTransport(
            """
            => e040000011048000002c800000008000000000000000
            <= 410486b865b52b753d0a84d09bc20063fab5d8453ec33c215d4019a5801c9c6438b917770b2782e29a9ecc6edb67cd1f0fbf05ec4c1236884b6d686d6be3b1588abb2231334b453654666641724c683466564d36756f517a7673597135767765744a63564dbce80dd580792cd18af542790e56aa813178dc28644bb5f03dbd44c85f2d2e7a9000
            """,
        )

        val key = BtchipLegacyClient(transport).getWalletPublicKey(DerivationPath.parse("44'/0'/0'/0"))

        assertEquals("13KE6TffArLh4fVM6uoQzvsYq5vwetJcVM", key.address)
        assertEquals("bce80dd580792cd18af542790e56aa813178dc28644bb5f03dbd44c85f2d2e7a", key.chainCode.toHexString())
        transport.assertAllReplayed()
    }

    // The signed transaction in the same log carries the compressed form of this key.
    @Test
    fun getWalletPublicKey_uncompressedAnswer_returnsCompressedKey() = runTest {
        val transport = ReplayTransport(
            """
            => e04000000d03800000000000000000000000
            <= 41046666422d00f1b308fc7527198749f06fedb028b979c09f60d0348ef79c985e4138b86996b354774c434488d61c7fb20a83293ef3195d422fde9354e6cf2a74ce223137383731457244716465764c544c57424836577a6a556331454b4744517a434d41612d17bc55b7aa153ae07fba348692c2976e6889b769783d475ba7488fb547709000
            """,
        )

        val key = BtchipLegacyClient(transport).getWalletPublicKey(DerivationPath.parse("0'/0/0"))

        assertEquals("026666422d00f1b308fc7527198749f06fedb028b979c09f60d0348ef79c985e41", key.publicKey.toHexString())
    }

    @Test
    fun signTransaction_legacyP2pkhReferenceLog_sendsLoggedApdusAndReturnsSignature() = runTest {
        val transport = ReplayTransport(
            """
            => e042000009000000010100000001
            <= 9000
            => e0428000254ea60aeac5252c14291d428915bd7ccd1bfc4af009f4d4dc57ae597ed0420b71010000008a
            <= 9000
            => e04280003247304402201f36a12c240dbf9e566bc04321050b1984cd6eaf6caee8f02bb0bfec08e3354b022012ee2aeadcbbfd1e92959f
            <= 9000
            => e04280003257c15c1c6debb757b798451b104665aa3010569b49014104090b15bde569386734abf2a2b99f9ca6a50656627e77de663ca7
            <= 9000
            => e04280002a325702769986cf26cc9dd7fdea0af432c8e2becc867c932e1b9dd742f2a108997c2252e2bdebffffffff
            <= 9000
            => e04280000102
            <= 9000
            => e04280002281b72e00000000001976a91472a5d75c8d2d0565b656a5232703b167d50d5a2b88ac
            <= 9000
            => e042800022a0860100000000001976a9144533f5fb9b4817f713c48f0bfe96b9f50c476c9b88ac
            <= 9000
            => e04280000400000000
            <= 32005df4c773da236484dae8f0fdba3d7e0ba1d05070d1a34fc44943e638441262a04f1001000000a086010000000000b890da969aa6f3109000
            => e0440000050100000001
            <= 9000
            => e04480003b013832005df4c773da236484dae8f0fdba3d7e0ba1d05070d1a34fc44943e638441262a04f1001000000a086010000000000b890da969aa6f31019
            <= 9000
            => e04480001d76a9144533f5fb9b4817f713c48f0bfe96b9f50c476c9b88acffffffff
            <= 9000
            => e04a80002301905f0100000000001976a91472a5d75c8d2d0565b656a5232703b167d50d5a2b88ac
            <= 00009000
            => e04800001303800000000000000000000000000000000001
            <= 3145022100ff492ad0b3a634aa7751761f7e063bf6ef4148cd44ef8930164580d5ba93a17802206fac94b32e296549e2e478ce806b58d61cfacbfed35ac4ceca26ac531f92b20a019000
            """,
        )
        val transaction = LegacyTransaction(
            version = 1,
            inputs = listOf(LegacyInput(LEGACY_PREVIOUS_TX.hexToByteArray(), 1, 0xFFFFFFFF, DerivationPath.parse("0'/0/0"))),
            outputs = listOf(TxOutput(90_000, "76a91472a5d75c8d2d0565b656a5232703b167d50d5a2b88ac".hexToByteArray())),
            lockTime = 0,
            mode = LegacySigningMode.LEGACY,
        )

        val signatures = BtchipLegacyClient(transport).signTransaction(transaction)

        assertEquals(
            listOf("3045022100ff492ad0b3a634aa7751761f7e063bf6ef4148cd44ef8930164580d5ba93a17802206fac94b32e296549e2e478ce806b58d61cfacbfed35ac4ceca26ac531f92b20a01"),
            signatures.map { it.toHexString() },
        )
        transport.assertAllReplayed()
    }

    @Test
    fun signTransaction_nativeSegwitWithChangeReferenceLog_sendsLoggedApdusAndReturnsSignature() = runTest {
        val transport = ReplayTransport(
            """
            => e042000009000000010100000002
            <= 9000
            => e042800025b91a5eb409e1243dcc02440ad5709d57047946309a515902ffdba8b98ad9e9970000000000
            <= 9000
            => e042800004ffffff00
            <= 9000
            => e042800025b91a5eb409e1243dcc02440ad5709d57047946309a515902ffdba8b98ad9e9970100000000
            <= 9000
            => e042800004ffffff00
            <= 9000
            => e04280000102
            <= 9000
            => e04280001f50c300000000000016001449df9e8ba7e25dd4830579cb42fbea938497168b
            <= 9000
            => e04280001fef8b0100000000001600142f3fa4710983519e4ae45dd80e72e70b79f25c5e
            <= 9000
            => e04280000400000000
            <= 3200bb29fc4fd606567a2146ec33fd3904d978167d8309b510071b276734768397aa4eee01000000ef8b0100000000004daa5993f8b02cde9000
            => e0440002050100000001
            <= 9000
            => e04480023b01383200bb29fc4fd606567a2146ec33fd3904d978167d8309b510071b276734768397aa4eee01000000ef8b0100000000004daa5993f8b02cde00
            <= 9000
            => e044800204ffffffff
            <= 9000
            => e04aff001505800000548000000080000000000000010000001c
            <= 9000
            => e04a00003202a086010000000000160014562b254f8890788f45c7cf7bd4c81bce789ffcd0bd0400000000000016001483efa2f7154106
            <= 009000
            => e04a80000d6369afcfc98b829886b6c76bf5
            <= 00009000
            => e0440080050100000001
            <= 9000
            => e04480803b01383200bb29fc4fd606567a2146ec33fd3904d978167d8309b510071b276734768397aa4eee01000000ef8b0100000000004daa5993f8b02cde19
            <= 9000
            => e04480801d76a9142f3fa4710983519e4ae45dd80e72e70b79f25c5e88acffffffff
            <= 9000
            => e04800001b05800000548000000080000000000000010000001b000000000001
            <= 3045022100e4acf0eb3803a62399f53825d86aa30743fe999eefb01522d5f7ecd9eeec663d022063b90c512e207c2ac47d8759e1c73c6abeff58daec31c48905193470bc87f2d3019000
            """,
        )
        val transaction = LegacyTransaction(
            version = 1,
            inputs = listOf(LegacyInput(SEGWIT_PREVIOUS_TX.hexToByteArray(), 1, 0xFFFFFFFF, DerivationPath.parse("84'/0'/0'/1/27"))),
            outputs = listOf(
                TxOutput(100_000, "0014562b254f8890788f45c7cf7bd4c81bce789ffcd0".hexToByteArray()),
                TxOutput(1_213, "001483efa2f71541066369afcfc98b829886b6c76bf5".hexToByteArray()),
            ),
            lockTime = 0,
            mode = LegacySigningMode.SEGWIT,
            changePath = DerivationPath.parse("84'/0'/0'/1/28"),
        )

        val signatures = BtchipLegacyClient(transport).signTransaction(transaction)

        assertEquals(
            listOf("3045022100e4acf0eb3803a62399f53825d86aa30743fe999eefb01522d5f7ecd9eeec663d022063b90c512e207c2ac47d8759e1c73c6abeff58daec31c48905193470bc87f2d301"),
            signatures.map { it.toHexString() },
        )
        transport.assertAllReplayed()
    }

    @Test
    fun signTransaction_bitcoinCash_startsCashAddrTransactionAndSignsWithForkId() = runTest {
        val transport = deviceSigningWith(sighashType = 0x41)

        BtchipLegacyClient(transport).signTransaction(oneInputTransaction(LegacySigningMode.BITCOIN_CASH))

        val hashStart = transport.sent.first { it.ins() == 0x44 }
        assertEquals(0x03, hashStart.p2())
        assertEquals(0x41, transport.sent.last { it.ins() == 0x48 }.apduData().last().toInt())
    }

    // ledgerjs createTransaction: a legacy pass re-sends every input, only the signed one with its script.
    @Test
    fun signTransaction_legacyTwoInputs_eachPassCarriesOnlyTheSignedInputScript() = runTest {
        val transport = deviceSigningWith(sighashType = 0x01)
        val input = oneInputTransaction(LegacySigningMode.LEGACY).inputs.single()
        val transaction = LegacyTransaction(1, listOf(input, input), oneInputTransaction(LegacySigningMode.LEGACY).outputs, 0, LegacySigningMode.LEGACY)

        BtchipLegacyClient(transport).signTransaction(transaction)

        val headers = transport.sent.filter { it.ins() == 0x44 && it.apduData().size == 59 }
        assertEquals(listOf(0x19, 0x00, 0x00, 0x19), headers.map { it.apduData().last().toInt() and 0xFF })
        assertEquals(listOf(0x00, 0x80), transport.sent.filter { it.ins() == 0x44 && it[2] == 0.toByte() }.map { it.p2() })
    }

    // ledgerjs signTransaction writes the lock time big-endian, unlike the transaction itself.
    @Test
    fun signTransaction_lockTime_sendsItBigEndianToHashSign() = runTest {
        val transport = deviceSigningWith(sighashType = 0x01)

        BtchipLegacyClient(transport).signTransaction(oneInputTransaction(LegacySigningMode.LEGACY, lockTime = 0x01020304))

        assertEquals("000102030401", transport.sent.last { it.ins() == 0x48 }.apduData().takeLast(6).toByteArray().toHexString())
    }

    @Test
    fun signTransaction_signatureWithOtherSighash_throwsMalformedResponse() = runTest {
        val transport = deviceSigningWith(sighashType = 0x41)

        assertFailsWith<LedgerException.MalformedResponse> {
            BtchipLegacyClient(transport).signTransaction(oneInputTransaction(LegacySigningMode.LEGACY))
        }
    }

    // The outer length and sighash fit, but both elements are OCTET STRINGs (04), not INTEGERs (02).
    @Test
    fun signTransaction_hashSignNotStrictDer_throwsMalformedResponse() = runTest {
        val transport = RecordingTransport { apdu -> if (apdu.ins() == 0x48) "3006040101040101019000".hexToByteArray() else trustedInputOrOk(apdu) }

        assertFailsWith<LedgerException.MalformedResponse> {
            BtchipLegacyClient(transport).signTransaction(oneInputTransaction(LegacySigningMode.LEGACY))
        }
    }

    // The txid of an MWEB peg-in excludes its witness and MWEB data.
    @Test
    fun signTransaction_litecoinMwebPegInParent_streamsTheBytesOfItsTxid() = runTest {
        val streamed = trustedInputStream(LegacyParentFixtures.MWEB_PEG_IN_TX, outputIndex = 1)

        assertEquals(LegacyParentFixtures.MWEB_PEG_IN_TXID, sha256(sha256(streamed)).reversedArray().toHexString())
    }

    // The app hashes the DIP-2 payload after the lock time without the extra length in front of it.
    @Test
    fun signTransaction_dashSpecialTransactionParent_streamsPayloadAfterLockTimeBehindItsLength() = runTest {
        val raw = LegacyParentFixtures.DASH_CBTX.hexToByteArray()
        val extra = raw.copyOfRange(LegacyParentFixtures.DASH_CBTX_LOCK_TIME_END, raw.size)

        val streamed = trustedInputStream(LegacyParentFixtures.DASH_CBTX, outputIndex = 0)

        assertEquals(LegacyParentFixtures.DASH_CBTX_TXID, sha256(sha256(raw)).reversedArray().toHexString())
        assertContentEquals(raw.copyOfRange(0, LegacyParentFixtures.DASH_CBTX_LOCK_TIME_END) + compactSize(extra.size) + extra, streamed)
    }

    /** Everything GET_TRUSTED_INPUT sent after the output index; the device stops the flow at the first hash pass. */
    private suspend fun trustedInputStream(previous: String, outputIndex: Int): ByteArray {
        val transport = RecordingTransport { apdu -> if (apdu.ins() == 0x42) ByteArray(56) + OK else "6a80".hexToByteArray() }
        val input = LegacyInput(previous.hexToByteArray(), outputIndex, 0xFFFFFFFF, DerivationPath.parse("44'/2'/0'/0/0"))
        val transaction = LegacyTransaction(2, listOf(input), oneInputTransaction(LegacySigningMode.LEGACY).outputs, 0, LegacySigningMode.LEGACY)

        assertFailsWith<LedgerException.InvalidRequest> { BtchipLegacyClient(transport).signTransaction(transaction) }

        return transport.sent.filter { it.ins() == 0x42 }.map { it.apduData() }.reduce(ByteArray::plus).let { it.copyOfRange(4, it.size) }
    }

    @Test
    fun signTransaction_segwitInputWithoutWitnessProgram_throws() = runTest {
        val transport = deviceSigningWith(sighashType = 0x01)

        // The legacy previous transaction pays a P2PKH script, which segwit mode cannot spend.
        assertFailsWith<IllegalArgumentException> {
            BtchipLegacyClient(transport).signTransaction(oneInputTransaction(LegacySigningMode.SEGWIT))
        }
    }

    @Test
    fun signTransaction_appRefusesData_throwsInvalidRequest() = runTest {
        val transport = RecordingTransport { "6a80".hexToByteArray() }

        val error = assertFailsWith<LedgerException.InvalidRequest> {
            BtchipLegacyClient(transport).signTransaction(oneInputTransaction(LegacySigningMode.LEGACY))
        }
        assertEquals(0x6A80, error.statusWord)
    }

    @Test
    fun signTransaction_userRejects_throwsUserRejected() = runTest {
        val transport = RecordingTransport { apdu -> if (apdu.ins() == 0x48) "6985".hexToByteArray() else trustedInputOrOk(apdu) }

        assertFailsWith<LedgerException.UserRejected> {
            BtchipLegacyClient(transport).signTransaction(oneInputTransaction(LegacySigningMode.LEGACY))
        }
    }

    private fun oneInputTransaction(mode: LegacySigningMode, lockTime: Long = 0) = LegacyTransaction(
        version = 2,
        inputs = listOf(LegacyInput(LEGACY_PREVIOUS_TX.hexToByteArray(), 1, 0xFFFFFFFF, DerivationPath.parse("44'/145'/0'/0/0"))),
        outputs = listOf(TxOutput(90_000, "76a91472a5d75c8d2d0565b656a5232703b167d50d5a2b88ac".hexToByteArray())),
        lockTime = lockTime,
        mode = mode,
    )

    private fun deviceSigningWith(sighashType: Int) = RecordingTransport { apdu ->
        if (apdu.ins() == 0x48) "3045022100ff492ad0b3a634aa7751761f7e063bf6ef4148cd44ef8930164580d5ba93a17802206fac94b32e296549e2e478ce806b58d61cfacbfed35ac4ceca26ac531f92b20a".hexToByteArray() + sighashType.toByte() + OK
        else trustedInputOrOk(apdu)
    }

    // The last GET_TRUSTED_INPUT piece is the 4-byte lock time; the device answers it with the trusted input.
    private fun trustedInputOrOk(apdu: ByteArray): ByteArray =
        if (apdu.ins() == 0x42 && apdu.apduData().size == 4 && apdu[2] == 0x80.toByte()) ByteArray(56) + OK else OK

    private companion object {
        val OK = "9000".hexToByteArray()

        const val LEGACY_PREVIOUS_TX =
            "01000000014ea60aeac5252c14291d428915bd7ccd1bfc4af009f4d4dc57ae597ed0420b71010000008a47304402201f36a12c240dbf9e566bc04321050b1984cd6eaf6caee8f02bb0bfec08e3354b022012ee2aeadcbbfd1e92959f57c15c1c6debb757b798451b104665aa3010569b49014104090b15bde569386734abf2a2b99f9ca6a50656627e77de663ca7325702769986cf26cc9dd7fdea0af432c8e2becc867c932e1b9dd742f2a108997c2252e2bdebffffffff0281b72e00000000001976a91472a5d75c8d2d0565b656a5232703b167d50d5a2b88aca0860100000000001976a9144533f5fb9b4817f713c48f0bfe96b9f50c476c9b88ac00000000"

        const val SEGWIT_PREVIOUS_TX =
            "01000000000102b91a5eb409e1243dcc02440ad5709d57047946309a515902ffdba8b98ad9e9970000000000ffffff00b91a5eb409e1243dcc02440ad5709d57047946309a515902ffdba8b98ad9e9970100000000ffffff000250c300000000000016001449df9e8ba7e25dd4830579cb42fbea938497168bef8b0100000000001600142f3fa4710983519e4ae45dd80e72e70b79f25c5e0248304502210080ed332c269ae7d86fac26a143afcec0a634e1098fd1ee5ca43cbe0c66de861802204c804eceb4cc9ca397156fa683f46274d22bb5d95f8c8293dc595934899f7927012103cc39edf09d462b4de30cc9bf96b163f18dcee742e0a1ea6fad0274ae0b9d60330247304402203e277d48d19a01c33b45b8f102479eb10811d20991bbf060cab4ba79f0972e61022041e13ed7da2b266d20c36b01694f2d16cf144c1ff66863f26d7c332dc220bc1301210369f216ec068fb7ef17a46ad3ad4d7f0e04e8a3a16ae2da852d6e4b57c3bb972f00000000"
    }
}
