package cash.p.terminal.ledgerkit.ton

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.RecordingTransport
import cash.p.terminal.ledgerkit.ReplayTransport
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.apduData
import cash.p.terminal.ledgerkit.ins
import cash.p.terminal.ledgerkit.p2
import cash.p.terminal.ledgerkit.readUInt16
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// Requests: LedgerHQ/app-ton tests/application_client/ton_transaction.py (Transaction.to_request_bytes), body cell
// refs from its tonsdk 1.0.13 cells; ZERO_FORWARD_JETTON follows ton-ledger-ts convertPayload and writeVarUInt(0n).
class TonAppClientTest {
    @Test
    fun getPublicKey_firstAccount_sendsPathAndReturnsKey() = runTest {
        val transport = ReplayTransport(
            """
            => e005000019068000002c8000025f80000000800000008000000080000000
            <= ${PUBLIC_KEY}9000
            """,
        )

        val publicKey = TonAppClient(transport).getPublicKey(PATH)

        assertEquals(PUBLIC_KEY, publicKey.toHexString())
        transport.assertAllReplayed()
    }

    @Test
    fun getPublicKey_display_sendsConfirmFlag() = runTest {
        val transport = ReplayTransport(
            """
            => e005010019068000002c8000025f80000000800000008000000080000000
            <= ${PUBLIC_KEY}9000
            """,
        )

        TonAppClient(transport).getPublicKey(PATH, display = true)

        transport.assertAllReplayed()
    }

    @Test
    fun getPublicKey_unhardenedIndex_sendsNothing() = runTest {
        val transport = RecordingTransport { error("nothing may be sent") }

        assertFailsWith<IllegalArgumentException> {
            TonAppClient(transport).getPublicKey(DerivationPath.parse("44'/607'/0'/0'/0'/0"))
        }
    }

    @Test
    fun getPublicKey_shortKey_throwsMalformedResponse() = runTest {
        assertFailsWith<LedgerException.MalformedResponse> { client("11".repeat(31)).getPublicKey(PATH) }
    }

    @Test
    fun signTransaction_comment_sendsReferenceRequest() = runTest {
        val transport = ReplayTransport(
            """
            $SIGN_PATH_APDU
            => e00600006b000000000770dbd8800459682f00002f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f000300010000f190dc3807908d8304a67bff2e908e44cb2a5afab6aa48088aecd5e86d552e560100000000000f6c65646765722d6b69742074657374
            <= 40${COMMENT_SIGNATURE}20${COMMENT_HASH}9000
            """,
        )

        val signature = TonAppClient(transport).signTransaction(PATH, COMMENT_TRANSFER)

        assertEquals(COMMENT_SIGNATURE, signature.signature.toHexString())
        assertEquals(COMMENT_HASH, signature.hash.toHexString())
        transport.assertAllReplayed()
    }

    @Test
    fun signTransaction_jettonTransfer_sendsReferenceRequest() = runTest {
        val transport = ReplayTransport(
            """
            $SIGN_PATH_APDU
            => e0060000d1000000000870dbd8800402faf080003c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c010300010001380bafceb4dc42696cc663703c136bdd73f87363062b4c97e812bc556d637c8601000000010075010000000000003039030f4240002f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f00d67b2fc518f85dde98667ab7555e2fb3508ba37f23b8fb6f72fd54f5c83d26790001010100002c67ed9a6d2f1bb0901dd9d0c8a0412b4012d1992ae58a89d27eb1800bfdc680
            <= 40${COMMENT_SIGNATURE}20${COMMENT_HASH}9000
            """,
        )

        val transfer = TonTransfer(
            seqno = 8u,
            timeout = TIMEOUT,
            destination = JETTON_WALLET,
            value = TonCoins(50_000_000u),
            bounce = true,
            sendMode = 3,
            body = TonBody(
                tonCell("0001380bafceb4dc42696cc663703c136bdd73f87363062b4c97e812bc556d637c86"),
                TonBodyHint.JettonTransfer(
                    queryId = 12345u,
                    amount = TonCoins(1_000_000u),
                    destination = RECIPIENT,
                    responseDestination = OWN_WALLET,
                    customPayload = null,
                    forwardAmount = TonCoins(1u),
                    forwardPayload = FORWARD_PAYLOAD,
                ),
            ),
        )
        TonAppClient(transport).signTransaction(PATH, transfer)

        transport.assertAllReplayed()
    }

    @Test
    fun signTransaction_zeroForwardAmountNoPayloads_sendsEmptyCoins() = runTest {
        val transport = ReplayTransport(
            """
            $SIGN_PATH_APDU
            => e0060000ae000000000a70dbd8800402faf080003c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c010300010000ead8c1381c0ee7165cd546243a79bdb008a9d1c59225d21cbe5e879d8e50008101000000010052010000000000000000034c4b40002f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f00d67b2fc518f85dde98667ab7555e2fb3508ba37f23b8fb6f72fd54f5c83d2679000000
            <= 40${COMMENT_SIGNATURE}20${COMMENT_HASH}9000
            """,
        )

        TonAppClient(transport).signTransaction(PATH, ZERO_FORWARD_JETTON)

        transport.assertAllReplayed()
    }

    @Test
    fun signTransaction_stateInitAndMaxAmountsOverTwoChunks_sendsReferenceChunks() = runTest {
        val transport = ReplayTransport(
            """
            $SIGN_PATH_APDU
            => e0060002ff000000000b70dbd8800fffffffffffffffffffffffffffffff003c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c0101010001bb3e870e015a0c658752e1e241534e8d5a04f5f041db490509fb78a99f01c63c01000178b72289352ce32fcb511d2522bac2b75c5cc7c4323faa703d3246718b1fe6e9010000000100b101ffffffffffffffff0fffffffffffffffffffffffffffffff002f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f00d67b2fc518f85dde98667ab7555e2fb3508ba37f23b8fb6f72fd54f5c83d26790100002bed51e772ea246231b1d42fc48add2d69ee9f3cb1b69de9
            <= 9000
            => e00600003be2dcaec347f2a5940fffffffffffffffffffffffffffffff0100002c67ed9a6d2f1bb0901dd9d0c8a0412b4012d1992ae58a89d27eb1800bfdc680
            <= 40${COMMENT_SIGNATURE}20${COMMENT_HASH}9000
            """,
        )

        // 2^120 - 1 as a JVM BigInteger.toByteArray() gives it, with its leading sign byte.
        val maxCoins = TonCoins(("00" + "ff".repeat(15)).hexToByteArray())
        val transfer = TonTransfer(
            seqno = 11u,
            timeout = TIMEOUT,
            destination = JETTON_WALLET,
            value = maxCoins,
            bounce = true,
            sendMode = 1,
            stateInit = tonCell("0001bb3e870e015a0c658752e1e241534e8d5a04f5f041db490509fb78a99f01c63c"),
            body = TonBody(
                tonCell("000178b72289352ce32fcb511d2522bac2b75c5cc7c4323faa703d3246718b1fe6e9"),
                TonBodyHint.JettonTransfer(
                    queryId = ULong.MAX_VALUE,
                    amount = maxCoins,
                    destination = RECIPIENT,
                    responseDestination = OWN_WALLET,
                    customPayload = tonCell("00002bed51e772ea246231b1d42fc48add2d69ee9f3cb1b69de9e2dcaec347f2a594"),
                    forwardAmount = maxCoins,
                    forwardPayload = FORWARD_PAYLOAD,
                ),
            ),
        )
        TonAppClient(transport).signTransaction(PATH, transfer)

        transport.assertAllReplayed()
    }

    @Test
    fun signTransaction_bodyWithoutHint_sendsNoHints() = runTest {
        val transport = ReplayTransport(
            """
            $SIGN_PATH_APDU
            => e006000055000000000970dbd88003989680002f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f010100010000563c2bfcc0ba5873c290cc2296ba7e355d8e687a6480ce792e1fc7cb1fd6984f00
            <= 40${COMMENT_SIGNATURE}20${COMMENT_HASH}9000
            """,
        )

        TonAppClient(transport).signTransaction(PATH, UNSAFE_TRANSFER)

        transport.assertAllReplayed()
    }

    @Test
    fun signTransaction_noBody_sendsReferenceRequest() = runTest {
        val transport = ReplayTransport(
            """
            $SIGN_PATH_APDU
            => e006000034000000000070dbd8800405f5e100002f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f0101000000
            <= 40${COMMENT_SIGNATURE}20${COMMENT_HASH}9000
            """,
        )

        val transfer = TonTransfer(0u, TIMEOUT, RECIPIENT, TonCoins(100_000_000u), bounce = true, sendMode = 1)
        TonAppClient(transport).signTransaction(PATH, transfer)

        transport.assertAllReplayed()
    }

    @Test
    fun signTransaction_masterchainDestination_sendsChainByteFf() = runTest {
        val transport = RecordingTransport { apdu ->
            if (apdu.p2() == 0) "40${COMMENT_SIGNATURE}20${COMMENT_HASH}9000".hexToByteArray() else "9000".hexToByteArray()
        }

        val transfer = TonTransfer(0u, TIMEOUT, TonAddress(-1, ByteArray(32)), TonCoins(1u), bounce = true, sendMode = 1)
        TonAppClient(transport).signTransaction(PATH, transfer)

        // Tag, seqno, timeout and the two-byte value come before the destination.
        assertEquals("ff" + "00".repeat(32), transport.sent.last().apduData().copyOfRange(11, 44).toHexString())
    }

    @Test
    fun comment_notPrintableAscii_throwsIllegalArgument() {
        listOf("café", "tab\t", "del\u007f").forEach { text ->
            assertFailsWith<IllegalArgumentException>(text) { TonBodyHint.Comment(text) }
        }
    }

    @Test
    fun transfer_sendModeOverOneByte_throwsIllegalArgument() {
        assertFailsWith<IllegalArgumentException> {
            TonTransfer(0u, TIMEOUT, RECIPIENT, TonCoins(1u), bounce = true, sendMode = 0x100)
        }
    }

    @Test
    fun address_otherWorkchain_throwsIllegalArgument() {
        assertFailsWith<IllegalArgumentException> { TonAddress(1, ByteArray(32)) }
    }

    @Test
    fun cellRef_depthOver16Bits_throwsIllegalArgument() {
        TonCellRef(ByteArray(32), 0xFFFF)
        assertFailsWith<IllegalArgumentException> { TonCellRef(ByteArray(32), 0x10000) }
    }

    @Test
    fun comment_over120Characters_throwsIllegalArgument() {
        TonBodyHint.Comment("a".repeat(120))
        assertFailsWith<IllegalArgumentException> { TonBodyHint.Comment("a".repeat(121)) }
    }

    @Test
    fun coins_over120Bits_throwsIllegalArgument() {
        assertFailsWith<IllegalArgumentException> { TonCoins(("01" + "00".repeat(15)).hexToByteArray()) }
    }

    @Test
    fun signTransaction_blindSigningDisabled_throwsBlindSigningRequired() = runTest {
        assertFailsWith<LedgerException.BlindSigningRequired> { client(status = "bd00").signTransaction(PATH, UNSAFE_TRANSFER) }
    }

    @Test
    fun signTransaction_rejectedOnDevice_throwsUserRejected() = runTest {
        assertFailsWith<LedgerException.UserRejected> { client(status = "6985").signTransaction(PATH, COMMENT_TRANSFER) }
    }

    @Test
    fun signTransaction_hintDoesNotMatchBody_throwsInvalidRequest() = runTest {
        val error = assertFailsWith<LedgerException.InvalidRequest> {
            client(status = "b010").signTransaction(PATH, COMMENT_TRANSFER)
        }
        assertEquals(0xB010, error.statusWord)
    }

    @Test
    fun signTransaction_requestTooLong_throwsDataTooLarge() = runTest {
        assertFailsWith<LedgerException.DataTooLarge> { client(status = "b004").signTransaction(PATH, COMMENT_TRANSFER) }
        assertFailsWith<LedgerException.DataTooLarge> { client(status = "b00b").signTransaction(PATH, COMMENT_TRANSFER) }
    }

    @Test
    fun signTransaction_wrongSignatureLength_throwsMalformedResponse() = runTest {
        assertFailsWith<LedgerException.MalformedResponse> {
            client("3f${COMMENT_SIGNATURE.dropLast(2)}20$COMMENT_HASH").signTransaction(PATH, COMMENT_TRANSFER)
        }
    }

    @Test
    fun signTransaction_wrongHashLength_throwsMalformedResponse() = runTest {
        assertFailsWith<LedgerException.MalformedResponse> {
            client("40${COMMENT_SIGNATURE}21${COMMENT_HASH}00").signTransaction(PATH, COMMENT_TRANSFER)
        }
    }

    @Test
    fun signTransaction_trailingBytes_throwsMalformedResponse() = runTest {
        assertFailsWith<LedgerException.MalformedResponse> {
            client("40${COMMENT_SIGNATURE}20${COMMENT_HASH}00").signTransaction(PATH, COMMENT_TRANSFER)
        }
    }

    // The app accepts the path and data chunks with an empty 9000; [answer] and [status] go to the last APDU.
    private fun client(answer: String = "", status: String = "9000") = TonAppClient(
        RecordingTransport { apdu ->
            if (apdu.ins() == SIGN_TX && apdu.p2() != 0) "9000".hexToByteArray() else (answer + status).hexToByteArray()
        },
    )

    private companion object {
        val PATH = DerivationPath.parse("m/44'/607'/0'/0'/0'/0'")
        const val PUBLIC_KEY = "9aff66dcc01b79787f6038c8070b8f7b9f27e381297c846a59f743bb075ed61c"
        const val SIGN_PATH_APDU = "=> e006000319068000002c8000025f80000000800000008000000080000000\n<= 9000"
        const val TIMEOUT = 1893456000u
        const val SIGN_TX = 0x06
        const val COMMENT_SIGNATURE =
            "e7dc285a2ea99219dc03d987bd19dc829dc6974ccb1ba4238fb7529b8fead98931bd9f7d28adc962210a11d91d8b9cc9eaa492d8d197d65abfa24ee8b1294703"
        const val COMMENT_HASH = "e8bf086eb8c8c828a91d74212f557d9382ea856a8f1185c7f2ff2046c76139d1"

        val RECIPIENT = TonAddress(0, "2f".repeat(32).hexToByteArray())
        val JETTON_WALLET = TonAddress(0, "3c".repeat(32).hexToByteArray())
        val OWN_WALLET = TonAddress(0, "d67b2fc518f85dde98667ab7555e2fb3508ba37f23b8fb6f72fd54f5c83d2679".hexToByteArray())
        val FORWARD_PAYLOAD = tonCell("00002c67ed9a6d2f1bb0901dd9d0c8a0412b4012d1992ae58a89d27eb1800bfdc680")

        val COMMENT_TRANSFER = TonTransfer(
            seqno = 7u,
            timeout = TIMEOUT,
            destination = RECIPIENT,
            value = TonCoins(1_500_000_000u),
            bounce = false,
            sendMode = 3,
            body = TonBody(
                tonCell("0000f190dc3807908d8304a67bff2e908e44cb2a5afab6aa48088aecd5e86d552e56"),
                TonBodyHint.Comment("ledger-kit test"),
            ),
        )
        val ZERO_FORWARD_JETTON = TonTransfer(
            seqno = 10u,
            timeout = TIMEOUT,
            destination = JETTON_WALLET,
            value = TonCoins(50_000_000u),
            bounce = true,
            sendMode = 3,
            body = TonBody(
                tonCell("0000ead8c1381c0ee7165cd546243a79bdb008a9d1c59225d21cbe5e879d8e500081"),
                TonBodyHint.JettonTransfer(
                    queryId = 0u,
                    amount = TonCoins(5_000_000u),
                    destination = RECIPIENT,
                    responseDestination = OWN_WALLET,
                    customPayload = null,
                    forwardAmount = TonCoins(0u),
                    forwardPayload = null,
                ),
            ),
        )
        val UNSAFE_TRANSFER = TonTransfer(
            seqno = 9u,
            timeout = TIMEOUT,
            destination = RECIPIENT,
            value = TonCoins(10_000_000u),
            bounce = true,
            sendMode = 1,
            body = TonBody(tonCell("0000563c2bfcc0ba5873c290cc2296ba7e355d8e687a6480ce792e1fc7cb1fd6984f")),
        )
    }
}

/** A cell given as `depth (2 bytes) || hash`, the form app-ton's ton_utils.write_cell prints. */
fun tonCell(depthAndHash: String): TonCellRef {
    val bytes = depthAndHash.hexToByteArray()
    return TonCellRef(bytes.copyOfRange(2, 34), bytes.readUInt16(0))
}
