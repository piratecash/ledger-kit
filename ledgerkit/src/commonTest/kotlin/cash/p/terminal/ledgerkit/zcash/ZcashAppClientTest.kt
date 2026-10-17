package cash.p.terminal.ledgerkit.zcash

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.ReplayTransport
import cash.p.terminal.ledgerkit.apdu.LedgerException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// Keys and addresses: LedgerHQ/app-zcash tests/standalone/test_pubkey_cmd.py (speculos seed). PCZT requests and
// their packets: LedgerHQ/device-sdk-ts signer-zcash task/__fixtures__/pcztFixtures.ts and pcztSerializer.test.ts;
// P1/P2 framing: app-zcash docs/PCZT_APDU.md and tests/application_client/zcash_command_sender.py.
class ZcashAppClientTest {

    @Test
    fun getUnifiedFullViewingKey_account0_collectsTheContinuation() = runTest {
        val answer = lengthPrefixed(UFVK_ACC_0)
        val transport = ReplayTransport(
            """
            => e05000001a03800000208000008580000000038000002c8000008580000000
            <= ${answer.take(255 * 2)}9000
            => e050800000
            <= ${answer.drop(255 * 2)}9000
            """,
        )

        assertEquals(UFVK_ACC_0, ZcashAppClient(transport).getUnifiedFullViewingKey(account = 0))
        transport.assertAllReplayed()
    }

    @Test
    fun getUnifiedFullViewingKey_account1_sendsAccountPaths() = runTest {
        val answer = lengthPrefixed(UFVK_ACC_1)
        val transport = ReplayTransport(
            """
            => e05000001a03800000208000008580000001038000002c8000008580000001
            <= ${answer.take(255 * 2)}9000
            => e050800000
            <= ${answer.drop(255 * 2)}9000
            """,
        )

        assertEquals(UFVK_ACC_1, ZcashAppClient(transport).getUnifiedFullViewingKey(account = 1))
        transport.assertAllReplayed()
    }

    @Test
    fun getUnifiedFullViewingKey_emptyContinuation_throwsMalformedResponse() = runTest {
        val transport = ReplayTransport(
            """
            => e05000001a03800000208000008580000000038000002c8000008580000000
            <= ${lengthPrefixed(UFVK_ACC_0).take(255 * 2)}9000
            => e050800000
            <= 9000
            """,
        )

        assertFailsWith<LedgerException.MalformedResponse> { ZcashAppClient(transport).getUnifiedFullViewingKey(0) }
    }

    @Test
    fun getUnifiedFullViewingKey_continuationPastTheAnnouncedLength_throwsMalformedResponse() = runTest {
        val answer = lengthPrefixed(UFVK_ACC_0)
        val transport = ReplayTransport(
            """
            => e05000001a03800000208000008580000000038000002c8000008580000000
            <= ${answer.take(255 * 2)}9000
            => e050800000
            <= ${answer.drop(255 * 2)}009000
            """,
        )

        assertFailsWith<LedgerException.MalformedResponse> { ZcashAppClient(transport).getUnifiedFullViewingKey(0) }
    }

    @Test
    fun getUnifiedFullViewingKey_addressInsteadOfKey_throwsMalformedResponse() = runTest {
        val transport = ReplayTransport(
            """
            => e05000001a03800000208000008580000000038000002c8000008580000000
            <= ${lengthPrefixed(ADDRESS_ACC_0)}9000
            """,
        )

        assertFailsWith<LedgerException.MalformedResponse> { ZcashAppClient(transport).getUnifiedFullViewingKey(0) }
    }

    @Test
    fun getUnifiedFullViewingKey_noChecksum_throwsMalformedResponse() = runTest {
        val transport = ReplayTransport(
            """
            => e05000001a03800000208000008580000000038000002c8000008580000000
            <= ${lengthPrefixed("uview1q")}9000
            """,
        )

        assertFailsWith<LedgerException.MalformedResponse> { ZcashAppClient(transport).getUnifiedFullViewingKey(0) }
    }

    @Test
    fun getUnifiedFullViewingKey_oneCharacterChanged_throwsMalformedResponse() = runTest {
        val answer = lengthPrefixed(oneCharacterChanged(UFVK_ACC_0))
        val transport = ReplayTransport(
            """
            => e05000001a03800000208000008580000000038000002c8000008580000000
            <= ${answer.take(255 * 2)}9000
            => e050800000
            <= ${answer.drop(255 * 2)}9000
            """,
        )

        assertFailsWith<LedgerException.MalformedResponse> { ZcashAppClient(transport).getUnifiedFullViewingKey(0) }
    }

    @Test
    fun getUnifiedFullViewingKey_rejectedOnDevice_throwsUserRejected() = runTest {
        val transport = ReplayTransport(
            """
            => e05000001a03800000208000008580000000038000002c8000008580000000
            <= 6985
            """,
        )

        assertFailsWith<LedgerException.UserRejected> { ZcashAppClient(transport).getUnifiedFullViewingKey(0) }
    }

    @Test
    fun getUnifiedFullViewingKey_derivationCapReached_throwsKeyDerivationLimit() = runTest {
        val transport = ReplayTransport(
            """
            => e05000001a03800000208000008580000000038000002c8000008580000000
            <= 6a84
            """,
        )

        assertFailsWith<LedgerException.KeyDerivationLimit> { ZcashAppClient(transport).getUnifiedFullViewingKey(0) }
    }

    @Test
    fun getUnifiedAddress_account0_returnsOrchardAddress() = runTest {
        val transport = ReplayTransport(
            """
            => e05100002203800000208000008580000000058000002c80000085800000000000000000000000
            <= ${lengthPrefixed(ADDRESS_ACC_0)}9000
            """,
        )

        assertEquals(ADDRESS_ACC_0, ZcashAppClient(transport).getUnifiedAddress(account = 0))
        transport.assertAllReplayed()
    }

    @Test
    fun getUnifiedAddress_displayAccount1_sendsConfirmFlag() = runTest {
        val transport = ReplayTransport(
            """
            => e05101002203800000208000008580000001058000002c80000085800000010000000000000000
            <= ${lengthPrefixed(ADDRESS_ACC_1)}9000
            """,
        )

        assertEquals(ADDRESS_ACC_1, ZcashAppClient(transport).getUnifiedAddress(account = 1, display = true))
        transport.assertAllReplayed()
    }

    @Test
    fun getUnifiedAddress_trailingByte_throwsMalformedResponse() = runTest {
        val transport = ReplayTransport(
            """
            => e05100002203800000208000008580000000058000002c80000085800000000000000000000000
            <= ${lengthPrefixed(ADDRESS_ACC_0)}719000
            """,
        )

        // 0x71 is "q", so only the announced length tells the extra character apart from the address.
        assertFailsWith<LedgerException.MalformedResponse> { ZcashAppClient(transport).getUnifiedAddress(0) }
    }

    @Test
    fun getUnifiedAddress_noChecksum_throwsMalformedResponse() = runTest {
        val transport = ReplayTransport(
            """
            => e05100002203800000208000008580000000058000002c80000085800000000000000000000000
            <= ${lengthPrefixed("u1q")}9000
            """,
        )

        assertFailsWith<LedgerException.MalformedResponse> { ZcashAppClient(transport).getUnifiedAddress(0) }
    }

    @Test
    fun getUnifiedAddress_oneCharacterChanged_throwsMalformedResponse() = runTest {
        val transport = ReplayTransport(
            """
            => e05100002203800000208000008580000000058000002c80000085800000000000000000000000
            <= ${lengthPrefixed(oneCharacterChanged(ADDRESS_ACC_0))}9000
            """,
        )

        assertFailsWith<LedgerException.MalformedResponse> { ZcashAppClient(transport).getUnifiedAddress(0) }
    }

    @Test
    fun getUnifiedAddress_derivationCapReached_throwsKeyDerivationLimit() = runTest {
        val transport = ReplayTransport(
            """
            => e05100002203800000208000008580000000058000002c80000085800000000000000000000000
            <= 6a84
            """,
        )

        assertFailsWith<LedgerException.KeyDerivationLimit> { ZcashAppClient(transport).getUnifiedAddress(0) }
    }

    @Test
    fun signTransaction_v5TransparentAndOrchard_streamsReferencePacketsAndCollectsSignatures() = runTest {
        val transport = ReplayTransport(
            exchange(PCZT_HEADER, 0x00, 0x00, HEADER_V5) +
                transparentInputExchanges() +
                transparentOutputExchanges() +
                orchardExchanges(PCZT_ORCHARD_ACTION, ORCHARD_FILL, finished = true) +
                exchange(SIGN_ORCHARD, 0x00, 0x00, "", SPEND_AUTH_SIG + "9000") +
                exchange(SIGN_TRANSPARENT, 0x00, 0x00, "", DER_SIGNATURE + "019000"),
        )

        val signatures = ZcashAppClient(transport).signTransaction(
            transaction(V5, orchard = bundle(ORCHARD_FILL), inputs = listOf(transparentInput()), outputs = listOf(transparentOutput())),
        )

        transport.assertAllReplayed()
        assertEquals(SPEND_AUTH_SIG, signatures.orchard.getValue(0).toHexString())
        assertEquals(DER_SIGNATURE + "01", signatures.transparent.getValue(0).toHexString())
        assertEquals(emptyMap(), signatures.ironwood)
    }

    @Test
    fun signTransaction_v6BothBundles_movesFinishedToIronwoodAndSendsNoteVersion() = runTest {
        val transport = ReplayTransport(
            exchange(PCZT_HEADER, 0x00, 0x00, HEADER_V6) +
                exchange(PCZT_TRANSPARENT_INPUT, 0x00, 0x00, "00") +
                exchange(PCZT_TRANSPARENT_OUTPUT, 0x00, 0x00, "00") +
                orchardExchanges(PCZT_ORCHARD_ACTION, ORCHARD_FILL, finished = false) +
                orchardExchanges(PCZT_IRONWOOD_ACTION, IRONWOOD_FILL, finished = true) +
                exchange(SIGN_ORCHARD, 0x00, 0x00, "", SPEND_AUTH_SIG + "9000") +
                exchange(SIGN_IRONWOOD, 0x00, 0x00, "", IRONWOOD_SIG + "9000"),
        )

        val signatures = ZcashAppClient(transport).signTransaction(
            transaction(V6, orchard = bundle(ORCHARD_FILL), ironwood = bundle(IRONWOOD_FILL)),
        )

        transport.assertAllReplayed()
        assertEquals(SPEND_AUTH_SIG, signatures.orchard.getValue(0).toHexString())
        assertEquals(IRONWOOD_SIG, signatures.ironwood.getValue(0).toHexString())
    }

    @Test
    fun signTransaction_v6WithoutIronwood_finishesOnAnEmptyIronwoodBundle() = runTest {
        val transport = ReplayTransport(
            exchange(PCZT_HEADER, 0x00, 0x00, HEADER_V6) +
                exchange(PCZT_TRANSPARENT_INPUT, 0x00, 0x00, "00") +
                exchange(PCZT_TRANSPARENT_OUTPUT, 0x00, 0x00, "00") +
                orchardExchanges(PCZT_ORCHARD_ACTION, ORCHARD_FILL, finished = false) +
                exchange(PCZT_IRONWOOD_ACTION, 0x00, 0x01, "00") +
                exchange(SIGN_ORCHARD, 0x00, 0x00, "", SPEND_AUTH_SIG + "9000"),
        )

        ZcashAppClient(transport).signTransaction(transaction(V6, orchard = bundle(ORCHARD_FILL)))

        transport.assertAllReplayed()
    }

    @Test
    fun signTransaction_v5TransparentOnly_finishesOnAnEmptyOrchardBundle() = runTest {
        val transport = ReplayTransport(
            exchange(PCZT_HEADER, 0x00, 0x00, HEADER_V5) +
                transparentInputExchanges() +
                exchange(PCZT_TRANSPARENT_OUTPUT, 0x00, 0x00, "00") +
                exchange(PCZT_ORCHARD_ACTION, 0x00, 0x01, "00") +
                exchange(SIGN_TRANSPARENT, 0x00, 0x00, "", DER_SIGNATURE + "019000"),
        )

        ZcashAppClient(transport).signTransaction(transaction(V5, inputs = listOf(transparentInput())))

        transport.assertAllReplayed()
    }

    @Test
    fun signTransaction_absentLockTimeAndSequence_sendNoneTags() = runTest {
        val transport = ReplayTransport(
            exchange(PCZT_HEADER, 0x00, 0x00, HEADER_V5.replace("b4d0d6c20100000000", "b4d0d6c200")) +
                framed(
                    PCZT_TRANSPARENT_INPUT,
                    listOf(
                        "01",
                        "11".repeat(32) + "00000000" + "00" + "e803000000000000",
                        "19" + INPUT_SCRIPT,
                        "01" + "01" + "02".repeat(33) + "00".repeat(32) + "05" + "8000002c" + "80000085" + "80000000" +
                            "00000000" + "00000000",
                    ),
                    finished = false,
                ) +
                exchange(PCZT_TRANSPARENT_OUTPUT, 0x00, 0x00, "00") +
                exchange(PCZT_ORCHARD_ACTION, 0x00, 0x01, "00") +
                exchange(SIGN_TRANSPARENT, 0x00, 0x00, "", DER_SIGNATURE + "019000"),
        )
        val input = transparentInput().run {
            ZcashTransparentInput(prevoutTxid, prevoutIndex, null, value, scriptPubkey, sighashType, derivation)
        }
        val transaction = transaction(V5, inputs = listOf(input)).run {
            ZcashTransaction(
                txVersion, versionGroupId, consensusBranchId, null, expiryHeight, coinType, txModifiable,
                transparentInputs, transparentOutputs, orchard, ironwood,
            )
        }

        ZcashAppClient(transport).signTransaction(transaction)

        transport.assertAllReplayed()
    }

    @Test
    fun signTransaction_dummyActions_asksOnlyForTheSignIndices() = runTest {
        val packets = orchardActionPackets(ORCHARD_FILL)
        val twoActions = listOf("02") + packets.drop(1).dropLast(1) + packets.drop(1).dropLast(1) + packets.last()
        val transport = ReplayTransport(
            exchange(PCZT_HEADER, 0x00, 0x00, HEADER_V5) +
                exchange(PCZT_TRANSPARENT_INPUT, 0x00, 0x00, "00") +
                exchange(PCZT_TRANSPARENT_OUTPUT, 0x00, 0x00, "00") +
                framed(PCZT_ORCHARD_ACTION, twoActions, finished = true) +
                exchange(SIGN_ORCHARD, 0x00, 0x01, "", SPEND_AUTH_SIG + "9000"),
        )
        val bundle = ZcashActionBundle(List(2) { action(ORCHARD_FILL) }, 2, 0, bytes(32, 0x0d), signIndices = listOf(1))

        val signatures = ZcashAppClient(transport).signTransaction(transaction(V5, orchard = bundle))

        transport.assertAllReplayed()
        assertEquals(setOf(1), signatures.orchard.keys)
    }

    @Test
    fun signTransaction_negativeValueBalance_setsTheSignFlag() = runTest {
        val packets = orchardActionPackets(ORCHARD_FILL)
        val trailer = "02" + "0700000000000000" + "01" + "0d".repeat(32)
        val transport = ReplayTransport(
            exchange(PCZT_HEADER, 0x00, 0x00, HEADER_V5) +
                exchange(PCZT_TRANSPARENT_INPUT, 0x00, 0x00, "00") +
                exchange(PCZT_TRANSPARENT_OUTPUT, 0x00, 0x00, "00") +
                framed(PCZT_ORCHARD_ACTION, packets.dropLast(1) + trailer, finished = true) +
                exchange(SIGN_ORCHARD, 0x00, 0x00, "", SPEND_AUTH_SIG + "9000"),
        )
        val bundle = ZcashActionBundle(listOf(action(ORCHARD_FILL)), 2, -7, bytes(32, 0x0d), signIndices = listOf(0))

        ZcashAppClient(transport).signTransaction(transaction(V5, orchard = bundle))

        transport.assertAllReplayed()
    }

    @Test
    fun signTransaction_rejectedOnReview_throwsUserRejected() = runTest {
        val transport = ReplayTransport(
            exchange(PCZT_HEADER, 0x00, 0x00, HEADER_V5) +
                exchange(PCZT_TRANSPARENT_INPUT, 0x00, 0x00, "00") +
                exchange(PCZT_TRANSPARENT_OUTPUT, 0x00, 0x00, "00") +
                orchardExchanges(PCZT_ORCHARD_ACTION, ORCHARD_FILL, finished = true, lastAnswer = "6985"),
        )

        assertFailsWith<LedgerException.UserRejected> {
            ZcashAppClient(transport).signTransaction(transaction(V5, orchard = bundle(ORCHARD_FILL)))
        }
    }

    @Test
    fun signTransaction_derivationCapReached_throwsKeyDerivationLimit() = runTest {
        val transport = ReplayTransport(
            exchange(PCZT_HEADER, 0x00, 0x00, HEADER_V5) +
                exchange(PCZT_TRANSPARENT_INPUT, 0x00, 0x00, "00") +
                exchange(PCZT_TRANSPARENT_OUTPUT, 0x00, 0x00, "00") +
                orchardExchanges(PCZT_ORCHARD_ACTION, ORCHARD_FILL, finished = true, lastAnswer = "6a84"),
        )

        assertFailsWith<LedgerException.KeyDerivationLimit> {
            ZcashAppClient(transport).signTransaction(transaction(V5, orchard = bundle(ORCHARD_FILL)))
        }
    }

    @Test
    fun signTransaction_changeToAnotherAccount_throwsInvalidRequest() = runTest {
        val transport = ReplayTransport(
            exchange(PCZT_HEADER, 0x00, 0x00, HEADER_V5) +
                exchange(PCZT_TRANSPARENT_INPUT, 0x00, 0x00, "00") +
                exchange(PCZT_TRANSPARENT_OUTPUT, 0x00, 0x00, "00") +
                orchardExchanges(PCZT_ORCHARD_ACTION, ORCHARD_FILL, finished = true) +
                exchange(SIGN_ORCHARD, 0x00, 0x00, "", "6986"),
        )

        val error = assertFailsWith<LedgerException.InvalidRequest> {
            ZcashAppClient(transport).signTransaction(transaction(V5, orchard = bundle(ORCHARD_FILL)))
        }
        assertEquals(0x6986, error.statusWord)
    }

    @Test
    fun signTransaction_answerToABundlePacket_throwsMalformedResponse() = runTest {
        val transport = ReplayTransport(
            exchange(PCZT_HEADER, 0x00, 0x00, HEADER_V5, "009000"),
        )

        assertFailsWith<LedgerException.MalformedResponse> {
            ZcashAppClient(transport).signTransaction(transaction(V5, orchard = bundle(ORCHARD_FILL)))
        }
    }

    @Test
    fun signTransaction_shortSpendAuthSignature_throwsMalformedResponse() = runTest {
        val transport = ReplayTransport(
            exchange(PCZT_HEADER, 0x00, 0x00, HEADER_V5) +
                exchange(PCZT_TRANSPARENT_INPUT, 0x00, 0x00, "00") +
                exchange(PCZT_TRANSPARENT_OUTPUT, 0x00, 0x00, "00") +
                orchardExchanges(PCZT_ORCHARD_ACTION, ORCHARD_FILL, finished = true) +
                exchange(SIGN_ORCHARD, 0x00, 0x00, "", SPEND_AUTH_SIG.drop(2) + "9000"),
        )

        assertFailsWith<LedgerException.MalformedResponse> {
            ZcashAppClient(transport).signTransaction(transaction(V5, orchard = bundle(ORCHARD_FILL)))
        }
    }

    @Test
    fun signTransaction_transparentSignatureWithoutSighashByte_throwsMalformedResponse() = runTest {
        val transport = ReplayTransport(
            exchange(PCZT_HEADER, 0x00, 0x00, HEADER_V5) +
                transparentInputExchanges() +
                exchange(PCZT_TRANSPARENT_OUTPUT, 0x00, 0x00, "00") +
                exchange(PCZT_ORCHARD_ACTION, 0x00, 0x01, "00") +
                exchange(SIGN_TRANSPARENT, 0x00, 0x00, "", DER_SIGNATURE + "9000"),
        )

        assertFailsWith<LedgerException.MalformedResponse> {
            ZcashAppClient(transport).signTransaction(transaction(V5, inputs = listOf(transparentInput())))
        }
    }

    /** Same length, still in the bech32 alphabet: only the checksum can tell. */
    private fun oneCharacterChanged(text: String): String {
        val middle = text.length / 2
        return text.replaceRange(middle, middle + 1, if (text[middle] == 'q') "p" else "q")
    }

    private fun lengthPrefixed(text: String): String =
        text.length.toString(16).padStart(4, '0') + text.encodeToByteArray().toHexString()

    private fun exchange(ins: Int, p1: Int, p2: Int, data: String, answer: String = "9000"): String {
        val header = listOf(0xE0, ins, p1, p2, data.length / 2).joinToString("") { it.toString(16).padStart(2, '0') }
        return "=> $header$data\n<= $answer\n"
    }

    /** P1: first 0x00, next 0x80, last 0x01, a lone packet 0x00; P2 0x01 marks the last packet of the PCZT. */
    private fun framed(ins: Int, packets: List<String>, finished: Boolean, lastAnswer: String = "9000"): String =
        packets.mapIndexed { index, packet ->
            val last = index == packets.lastIndex
            val p1 = when {
                index == 0 -> 0x00
                last -> 0x01
                else -> 0x80
            }
            exchange(ins, p1, if (finished && last) 0x01 else 0x00, packet, if (last) lastAnswer else "9000")
        }.joinToString("")

    private fun transparentInputExchanges(): String = framed(
        PCZT_TRANSPARENT_INPUT,
        listOf(
            "01",
            "11".repeat(32) + "00000000" + "01ffffffff" + "e803000000000000",
            "19" + INPUT_SCRIPT,
            "01" + "01" + "02".repeat(33) + "00".repeat(32) + "05" + "8000002c" + "80000085" + "80000000" + "00000000" + "00000000",
        ),
        finished = false,
    )

    private fun transparentOutputExchanges(): String = framed(
        PCZT_TRANSPARENT_OUTPUT,
        listOf("01", "8403000000000000", "19" + OUTPUT_SCRIPT, "00"),
        finished = false,
    )

    private fun orchardExchanges(ins: Int, fill: Fill, finished: Boolean, lastAnswer: String = "9000"): String =
        framed(ins, orchardActionPackets(fill, ironwood = ins == PCZT_IRONWOOD_ACTION), finished, lastAnswer)

    /** pcztSerializer.test.ts: count, spend, zip32, cmx+epk, enc (583 bytes over three), out, metadata, trailer. */
    private fun orchardActionPackets(fill: Fill, ironwood: Boolean = false): List<String> {
        val enc = "fd4402" + hex(580, fill.enc)
        return listOf(
            "01",
            hex(32, fill.base) + hex(32, fill + 1) + hex(32, fill + 2) + hex(43, fill + 3) + "0500000000000000" +
                hex(32, fill + 4) + hex(32, fill + 5) + hex(32, fill + 6),
            "00".repeat(32) + "03" + "8000002c" + "80000085" + "80000000",
            hex(32, fill + 7) + hex(32, fill + 8),
            enc.take(510),
            enc.drop(510).take(510),
            enc.drop(1020),
            "50" + hex(80, fill.out),
            hex(43, fill + 9) + "0500000000000000" + hex(32, fill + 10) + hex(32, fill + 11) + if (ironwood) "03" else "",
            "02" + "0000000000000000" + "00" + hex(32, fill + 12),
        )
    }

    private fun hex(size: Int, fill: Int): String = (fill and 0xFF).toString(16).padStart(2, '0').repeat(size)

    private fun bytes(size: Int, fill: Int): ByteArray = ByteArray(size) { fill.toByte() }

    private fun transaction(
        version: Int,
        inputs: List<ZcashTransparentInput> = emptyList(),
        outputs: List<ZcashTransparentOutput> = emptyList(),
        orchard: ZcashActionBundle? = null,
        ironwood: ZcashActionBundle? = null,
    ) = ZcashTransaction(version, 0x26A7270A, 0xC2D6D0B4, 0, 0, 133, 0, inputs, outputs, orchard, ironwood)

    private fun transparentInput() = ZcashTransparentInput(
        prevoutTxid = bytes(32, 0x11),
        prevoutIndex = 0,
        sequence = 0xFFFFFFFF,
        value = 1000,
        scriptPubkey = INPUT_SCRIPT.hexToByteArray(),
        sighashType = 0x01,
        derivation = ZcashKeyOrigin(bytes(33, 0x02), bytes(32, 0x00), DerivationPath.parse("44'/133'/0'/0/0")),
    )

    private fun transparentOutput() = ZcashTransparentOutput(900, OUTPUT_SCRIPT.hexToByteArray(), derivation = null)

    /** sampleOrchardAction (fill 0x01) and sampleIronwoodAction (fill 0x11) of pcztFixtures.ts. */
    private fun action(fill: Fill) = ZcashAction(
        cvNet = bytes(32, fill.base),
        nullifier = bytes(32, fill + 1),
        rk = bytes(32, fill + 2),
        spendRecipient = bytes(43, fill + 3),
        spendValue = 5,
        spendRho = bytes(32, fill + 4),
        spendRseed = bytes(32, fill + 5),
        alpha = bytes(32, fill + 6),
        seedFingerprint = bytes(32, 0x00),
        path = DerivationPath.parse("44'/133'/0'"),
        cmx = bytes(32, fill + 7),
        ephemeralKey = bytes(32, fill + 8),
        encCiphertext = bytes(580, fill.enc),
        outCiphertext = bytes(80, fill.out),
        recipient = bytes(43, fill + 9),
        value = 5,
        rseed = bytes(32, fill + 10),
        rcv = bytes(32, fill + 11),
    )

    private fun bundle(fill: Fill) = ZcashActionBundle(listOf(action(fill)), 2, 0, bytes(32, fill + 12), signIndices = listOf(0))

    /** Field fill bytes of one fixture action: [base] for the first field, counting up in field order. */
    private class Fill(val base: Int, val enc: Int, val out: Int) {
        operator fun plus(offset: Int): Int = base + offset
    }

    private companion object {
        const val V5 = 5
        const val V6 = 6

        const val PCZT_HEADER = 0x52
        const val PCZT_TRANSPARENT_INPUT = 0x53
        const val PCZT_TRANSPARENT_OUTPUT = 0x54
        const val SIGN_TRANSPARENT = 0x55
        const val PCZT_ORCHARD_ACTION = 0x56
        const val SIGN_ORCHARD = 0x57
        const val PCZT_IRONWOOD_ACTION = 0x58
        const val SIGN_IRONWOOD = 0x59

        // The fixtures fill Orchard fields from 0x01 and Ironwood ones from 0x11; ciphertexts 0xaa/0xbb, 0xcc/0xdd.
        val ORCHARD_FILL = Fill(base = 0x01, enc = 0xAA, out = 0xBB)
        val IRONWOOD_FILL = Fill(base = 0x11, enc = 0xCC, out = 0xDD)

        const val HEADER_V5 = "50435a54" + "01000000" + "05000000" + "0a27a726" + "b4d0d6c2" + "0100000000" + "00000000" +
            "85000000" + "00"
        const val HEADER_V6 = "50435a54" + "02000000" + "06000000" + "0a27a726" + "b4d0d6c2" + "0100000000" + "00000000" +
            "85000000" + "00"
        const val INPUT_SCRIPT = "76a914000000000000000000000000000000000000000088ac"
        const val OUTPUT_SCRIPT = "76a914111111111111111111111111111111111111111188ac"

        // Any 64 bytes: the client forwards spend authorization signatures; the SDK verifies them.
        val SPEND_AUTH_SIG = "a5".repeat(64)
        val IRONWOOD_SIG = "5a".repeat(64)

        // Strict DER signature from EcdsaSignatureTest.
        const val DER_SIGNATURE =
            "3045022100a1cfd97f97be1762d9ba6c526613de14ab06f086c3fb62bef6e4f3d75fa679a5022045bc35d51809bf5af415d592f06d9cddf73f833496fdc799844c5bef1cfe4450"

        const val UFVK_ACC_0 =
            "uview1zkk7f8hp2m5v09kq7h29vkgngwhhvgy2ey32cy5j0kp69g7ju2vqjvnue03u99z382rtkgvj3f8vtqdtxfxvgjytezgt39dqc0lyt2" +
                "sj084jdq4md69snc3wxdcl8uah8sxw3rrt9pnxnfl3r4xnczapts7gr4l0cuell7dcjv36gkdcsl4axps827xt6fgmfl78zlhddec72t" +
                "n2p0eqnpkuy7a08puhj97v0ahxuqlyzmyqtldqnc0p3696d9ww8x6mpd56mz6w32twryevru2rx34lf8dtqsp50gar"
        const val UFVK_ACC_1 =
            "uview15lcx60j8zufp6qe5xveppqjjw3ukg5n90ln8uhgdxukp60tejk626763gffftfw4a2mjkxy4s9mpjdd6ckfkecz846jdvth57djc" +
                "hnpq7699v09g7eu9xnyyfeqtvm5jxhvpn6dxkzqq3726xwhxmn458a8hd2agvl30r2kz9cde8d8nd3e7akdkufuzp3hyule9v0w3a6qx" +
                "5p5fx8qa3wvjcj9qg9ypnr56m672rsv9y8fqn20usqzhxmrnmm2jf7gnh8kdk68dyvej9jlsm522w24jvce0lcqpn3mf"
        const val ADDRESS_ACC_0 =
            "u1u2h4ce7e2cn3z4nzur95muq2dl4da9x8h8kdp2l80gm9nl9raj8zzpx79ycjnfvar4v5exea5pqr5y9qsnlp0cdunwf9yjjx5c4q7ar9"
        const val ADDRESS_ACC_1 =
            "u1n4d94z4l9zs0kxhhytwyktg3rsmr9u0eagt3kn78j9m3lmnuzswuwn63az5jzfwqmvrfn0g8s3rvvg0wr0pklnkejm6d69hv8u5g6w9e"
    }
}
