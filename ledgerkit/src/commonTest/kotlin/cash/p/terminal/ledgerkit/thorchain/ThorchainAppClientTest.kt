package cash.p.terminal.ledgerkit.thorchain

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.RecordingTransport
import cash.p.terminal.ledgerkit.ReplayTransport
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.ins
import cash.p.terminal.ledgerkit.p1
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// APDUs follow thorchain/ledger-thorchain-js src/helper.ts (serializePath, signSendChunk) and src/app.ts
// (getAddressAndPubKey, signGetChunks); answers are the speculos app's for the default seed.
class ThorchainAppClientTest {
    @Test
    fun getAddress_firstAccount_sendsHrpAndLittleEndianPath() = runTest {
        val transport = ReplayTransport(
            """
            => 55040000190474686f722c000080a3030080000000800000000000000000
            <= ${PUBLIC_KEY}${ADDRESS.encodeToByteArray().toHexString()}9000
            """,
        )

        val address = ThorchainAppClient(transport).getAddress(PATH)

        assertEquals(PUBLIC_KEY, address.publicKey.toHexString())
        assertEquals(ADDRESS, address.address)
        transport.assertAllReplayed()
    }

    @Test
    fun getAddress_display_sendsConfirmFlag() = runTest {
        val transport = ReplayTransport(
            """
            => 55040100190474686f722c000080a3030080000000800000000000000000
            <= ${PUBLIC_KEY}${ADDRESS.encodeToByteArray().toHexString()}9000
            """,
        )

        ThorchainAppClient(transport).getAddress(PATH, display = true)

        transport.assertAllReplayed()
    }

    // ledger-thorchain-js tests/unit/basic.spec.ts, "check address conversion".
    @Test
    fun getAddress_referenceKeyAndAddress_parsed() = runTest {
        val address = client(
            "0264bff60f78aae3326b4fc3e16a7c48d0158bfe175fa4a9361190a42565662ae6" +
                "thor1mwyrp6lj85swy5e5g4hjlaacm33g6rw3p4qmq4".encodeToByteArray().toHexString(),
        ).getAddress(PATH)

        assertEquals("thor1mwyrp6lj85swy5e5g4hjlaacm33g6rw3p4qmq4", address.address)
    }

    @Test
    fun getAddress_malformedAnswer_throwsMalformedResponse() = runTest {
        val addressHex = ADDRESS.encodeToByteArray().toHexString()
        val malformed = mapOf(
            "uncompressed key prefix" to "04" + PUBLIC_KEY.substring(2) + addressHex,
            "other HRP" to PUBLIC_KEY + "cosmos19hzdfmktztey2vuqpa3tv7dqe0kw3u24km6yca".encodeToByteArray().toHexString(),
            "truncated address" to PUBLIC_KEY + addressHex.dropLast(2),
            "truncated key" to PUBLIC_KEY.dropLast(2),
        )

        malformed.forEach { (case, answer) ->
            assertFailsWith<LedgerException.MalformedResponse>(case) { client(answer).getAddress(PATH) }
        }
    }

    @Test
    fun getAddress_fourLevelPath_sendsNothing() = runTest {
        val transport = RecordingTransport { error("nothing may be sent") }

        assertFailsWith<IllegalArgumentException> {
            ThorchainAppClient(transport).getAddress(DerivationPath.parse("m/44'/931'/0'/0"))
        }
    }

    @Test
    fun signTransaction_msgSendOverTwoChunks_sendsReferenceChunksAndReturnsCompactSignature() = runTest {
        val transport = ReplayTransport(
            """
            $SIGN_INIT
            => 55020100fa7b226163636f756e745f6e756d626572223a22313233222c22636861696e5f6964223a2274686f72636861696e2d31222c22666565223a7b22616d6f756e74223a5b5d2c22676173223a223530303030303030227d2c226d656d6f223a226c65646765722d6b69742074657374222c226d736773223a5b7b2274797065223a2274686f72636861696e2f4d736753656e64222c2276616c7565223a7b22616d6f756e74223a5b7b22616d6f756e74223a22313530303030303030222c2264656e6f6d223a2272756e65227d5d2c2266726f6d5f61646472657373223a2274686f723139687a64666d6b747a746579327675717061337476376471
            <= 9000
            => 550202005f65306b77337532346b6d36796361222c22746f5f61646472657373223a2274686f72316d77797270366c6a38357377793565356734686a6c6161636d333367367277337034716d7134227d7d5d2c2273657175656e6365223a22343536227d
            <= 304402202f25b7c73bcdf0aa48a064352b7b4b19452a99d1cf508acdfca52e58c4548f2c022064831512675ac3cf3e9bf0c724dfdc0ec924c11da493b02fc20857f0a46a1d019000
            """,
        )

        val signature = ThorchainAppClient(transport).signTransaction(PATH, SEND_DOC.encodeToByteArray())

        assertEquals(
            "2f25b7c73bcdf0aa48a064352b7b4b19452a99d1cf508acdfca52e58c4548f2c64831512675ac3cf3e9bf0c724dfdc0ec924c11da493b02fc20857f0a46a1d01",
            signature.toHexString(),
        )
        transport.assertAllReplayed()
    }

    @Test
    fun signTransaction_oneChunk_sendsInitThenLast() = runTest {
        val transport = ReplayTransport(
            """
            $SIGN_INIT
            => 55020200027b7d
            <= ${DER_SIGNATURE}9000
            """,
        )

        ThorchainAppClient(transport).signTransaction(PATH, "{}".encodeToByteArray())

        transport.assertAllReplayed()
    }

    @Test
    fun signTransaction_emptyDoc_throwsIllegalArgument() = runTest {
        assertFailsWith<IllegalArgumentException> { client(DER_SIGNATURE).signTransaction(PATH, ByteArray(0)) }
    }

    @Test
    fun signTransaction_rejectedOnDevice_throwsUserRejected() = runTest {
        assertFailsWith<LedgerException.UserRejected> { client(status = "6986").signTransaction(PATH, DOC) }
    }

    @Test
    fun signTransaction_invalidDocOrPath_throwsInvalidRequest() = runTest {
        // 0x6984: the doc does not parse or the path is not 44'/931'/x'/0/x; 0x6985: an index above 100.
        listOf(0x6984, 0x6985).forEach { statusWord ->
            val error = assertFailsWith<LedgerException.InvalidRequest> {
                client(status = statusWord.toString(16)).signTransaction(PATH, DOC)
            }
            assertEquals(statusWord, error.statusWord)
        }
    }

    @Test
    fun signTransaction_docOverflowsAppBuffer_throwsDataTooLarge() = runTest {
        assertFailsWith<LedgerException.DataTooLarge> { client(status = "6983").signTransaction(PATH, DOC) }
    }

    @Test
    fun signTransaction_highS_throwsMalformedResponse() = runTest {
        val highS =
            "3046022100a1cfd97f97be1762d9ba6c526613de14ab06f086c3fb62bef6e4f3d75fa679a5022100ba43ca2ae7f640a50bea2a6d0f926320c36f59b2184ad8a23b86029db337fcf1"

        assertFailsWith<LedgerException.MalformedResponse> { client(highS).signTransaction(PATH, DOC) }
    }

    @Test
    fun signTransaction_compactSignatureAnswer_throwsMalformedResponse() = runTest {
        assertFailsWith<LedgerException.MalformedResponse> { client("ab".repeat(64)).signTransaction(PATH, DOC) }
    }

    // The app accepts the init and add chunks with an empty 9000; [answer] and [status] go to the last APDU.
    private fun client(answer: String = "", status: String = "9000") = ThorchainAppClient(
        RecordingTransport { apdu ->
            if (apdu.ins() == SIGN && apdu.p1() != P1_LAST) "9000".hexToByteArray() else (answer + status).hexToByteArray()
        },
    )

    private companion object {
        val PATH = DerivationPath.parse("m/44'/931'/0'/0/0")
        const val PUBLIC_KEY = "0335f35211b872a0a6a5e0cb54885e82e8d71c7c945111d6f782db1254ba2a2d8e"
        const val ADDRESS = "thor19hzdfmktztey2vuqpa3tv7dqe0kw3u24km6yca"
        const val SIGN_INIT = "=> 55020000142c000080a3030080000000800000000000000000\n<= 9000"
        const val SEND_DOC =
            """{"account_number":"123","chain_id":"thorchain-1","fee":{"amount":[],"gas":"50000000"},"memo":"ledger-kit test","msgs":[{"type":"thorchain/MsgSend","value":{"amount":[{"amount":"150000000","denom":"rune"}],"from_address":"thor19hzdfmktztey2vuqpa3tv7dqe0kw3u24km6yca","to_address":"thor1mwyrp6lj85swy5e5g4hjlaacm33g6rw3p4qmq4"}}],"sequence":"456"}"""
        const val DER_SIGNATURE =
            "3045022100a1cfd97f97be1762d9ba6c526613de14ab06f086c3fb62bef6e4f3d75fa679a5022045bc35d51809bf5af415d592f06d9cddf73f833496fdc799844c5bef1cfe4450"
        val DOC = "{}".encodeToByteArray()
        const val SIGN = 0x02
        const val P1_LAST = 2
    }
}
