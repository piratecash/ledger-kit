package cash.p.terminal.ledgerkit.ton

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.Speculos
import cash.p.terminal.ledgerkit.apdu.Apdu
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.apdu.exchange
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The "TON" app on speculos. Expected keys come from the speculos seed with SLIP-10; expected hashes are the v4R2
 * transfer cells of LedgerHQ/app-ton tests/application_client/ton_transaction.py (tonsdk 1.0.13), signed with
 * pycryptodome 3.24.1 Ed25519 (RFC 8032). Accounts are made up apart from the sender's own wallet.
 */
class TonSpeculosTest {
    private val transport get() = Speculos.transport("TON")
    private val client get() = TonAppClient(transport)

    @Test
    fun getPublicKey_firstAccount_matchesIndependentDerivation() = runBlocking {
        assertEquals("9aff66dcc01b79787f6038c8070b8f7b9f27e381297c846a59f743bb075ed61c", client.getPublicKey(PATH).toHexString())
    }

    @Test
    fun signTransaction_comment_matchesIndependentSignature() = runBlocking {
        val signature = Speculos.approving { client.signTransaction(PATH, COMMENT_TRANSFER) }

        assertSigned(
            "e8bf086eb8c8c828a91d74212f557d9382ea856a8f1185c7f2ff2046c76139d1",
            "e7dc285a2ea99219dc03d987bd19dc829dc6974ccb1ba4238fb7529b8fead98931bd9f7d28adc962210a11d91d8b9cc9eaa492d8d197d65abfa24ee8b1294703",
            signature,
        )
    }

    @Test
    fun signTransaction_jettonTransferWithForwardPayload_matchesIndependentSignature() = runBlocking {
        val transfer = jettonTransfer(
            seqno = 8u,
            body = "0001380bafceb4dc42696cc663703c136bdd73f87363062b4c97e812bc556d637c86",
            hint = TonBodyHint.JettonTransfer(
                queryId = 12345u,
                amount = TonCoins(1_000_000u),
                destination = RECIPIENT,
                responseDestination = OWN_WALLET,
                customPayload = null,
                forwardAmount = TonCoins(1u),
                forwardPayload = tonCell("00002c67ed9a6d2f1bb0901dd9d0c8a0412b4012d1992ae58a89d27eb1800bfdc680"),
            ),
        )

        val signature = Speculos.approving { client.signTransaction(PATH, transfer) }

        assertSigned(
            "3e464d63823def109a8ee42259394be0193f50330b3377411412dee821ef632d",
            "e8f013b9be0dd0ee1bec4fccea71e825465122e4b736c6e8e84ce30541334cfa6bbb35c12821da45ad5ed8e9dc27974ddb18382dc0b1863a6772a85499359e0e",
            signature,
        )
    }

    // The zero forward amount goes as an empty VarUInteger: a 0x00 byte would make the app hash another cell.
    @Test
    fun signTransaction_jettonTransferZeroForwardAmount_matchesIndependentSignature() = runBlocking {
        val transfer = jettonTransfer(
            seqno = 10u,
            body = "0000ead8c1381c0ee7165cd546243a79bdb008a9d1c59225d21cbe5e879d8e500081",
            hint = TonBodyHint.JettonTransfer(
                queryId = 0u,
                amount = TonCoins(5_000_000u),
                destination = RECIPIENT,
                responseDestination = OWN_WALLET,
                customPayload = null,
                forwardAmount = TonCoins(0u),
                forwardPayload = null,
            ),
        )

        val signature = Speculos.approving { client.signTransaction(PATH, transfer) }

        assertSigned(
            "062624af3adcd1177e6ab5217cb43d1413fb5624ca6039c518f913ca9db883ee",
            "2bade3df163c1d5d104d21ec9eec2e35c588bfac928657a1ea6c0bc612e43fdfb2ae479b07f4f4b46c41894ea99786763c694b89b2087af385f22d079bdcad0d",
            signature,
        )
    }

    @Test
    fun signTransaction_hintDoesNotMatchBody_throwsInvalidRequest() = runBlocking {
        val transfer = TonTransfer(
            seqno = 7u,
            timeout = TIMEOUT,
            destination = RECIPIENT,
            value = TonCoins(1_500_000_000u),
            bounce = false,
            sendMode = 3,
            body = TonBody(COMMENT_BODY, TonBodyHint.Comment("another comment")),
        )

        val client = client
        val error = assertFailsWith<LedgerException.InvalidRequest> { client.signTransaction(PATH, transfer) }

        assertEquals(0xB010, error.statusWord)
    }

    @Test
    fun signTransaction_bodyWithoutHint_signsOnlyWithBlindSigning() = runBlocking {
        setBlindSigning(false)
        assertFailsWith<LedgerException.BlindSigningRequired> { client.signTransaction(PATH, UNSAFE_TRANSFER) }

        setBlindSigning(true)
        val signature = try {
            Speculos.approving { client.signTransaction(PATH, UNSAFE_TRANSFER) }
        } finally {
            setBlindSigning(false)
        }

        assertSigned(
            "69aa5034d09bf146faea169d2e937e90e1e3714db04bcb8dd894f96d4afba5ad",
            "53298690ebf7a0153fb88e91fd9d549bfe86260a36d721d09283d4f34ea0de658fa0563e2ccb416b606ee635ba9118ebd57cf8e2e005849c8bd0862e84629d08",
            signature,
        )
    }

    private suspend fun setBlindSigning(enabled: Boolean) {
        val flags = transport.exchange(Apdu(CLA, GET_APP_SETTINGS, 0, 0)).data[0].toInt()
        if ((flags and BLIND_SIGNING_FLAG != 0) != enabled) Speculos.chooseInFirstSetting(if (enabled) 1 else 0)
        // The refusal leaves an error screen up; the menu walk leaves the home screen.
        Speculos.dismissToHome()
    }

    private fun assertSigned(hash: String, signature: String, actual: TonSignature) {
        assertEquals(hash, actual.hash.toHexString())
        assertEquals(signature, actual.signature.toHexString())
    }

    private fun jettonTransfer(seqno: UInt, body: String, hint: TonBodyHint) = TonTransfer(
        seqno = seqno,
        timeout = TIMEOUT,
        destination = TonAddress(0, "3c".repeat(32).hexToByteArray()),
        value = TonCoins(50_000_000u),
        bounce = true,
        sendMode = 3,
        body = TonBody(tonCell(body), hint),
    )

    private companion object {
        val PATH = DerivationPath.parse("m/44'/607'/0'/0'/0'/0'")
        const val CLA = 0xE0
        const val GET_APP_SETTINGS = 0x0A
        const val BLIND_SIGNING_FLAG = 0x01
        const val TIMEOUT = 1893456000u

        val RECIPIENT = TonAddress(0, "2f".repeat(32).hexToByteArray())
        val OWN_WALLET = TonAddress(0, "d67b2fc518f85dde98667ab7555e2fb3508ba37f23b8fb6f72fd54f5c83d2679".hexToByteArray())
        val COMMENT_BODY = tonCell("0000f190dc3807908d8304a67bff2e908e44cb2a5afab6aa48088aecd5e86d552e56")

        val COMMENT_TRANSFER = TonTransfer(
            seqno = 7u,
            timeout = TIMEOUT,
            destination = RECIPIENT,
            value = TonCoins(1_500_000_000u),
            bounce = false,
            sendMode = 3,
            body = TonBody(COMMENT_BODY, TonBodyHint.Comment("ledger-kit test")),
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
