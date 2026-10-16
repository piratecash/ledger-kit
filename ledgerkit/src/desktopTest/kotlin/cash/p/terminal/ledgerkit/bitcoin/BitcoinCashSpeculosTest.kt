package cash.p.terminal.ledgerkit.bitcoin

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.Speculos
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The legacy "Bitcoin Cash" app on speculos. Keys come from the speculos seed via embit 0.8.0; the
 * `SIGHASH_FORKID` digest was computed by a standalone script (BIP-143 layout, checked against embit on
 * a segwit spend) and signed with plain RFC 6979 nonces as the app does.
 */
class BitcoinCashSpeculosTest {
    private val client get() = BtchipLegacyClient(Speculos.transport("Bitcoin Cash"))

    @Test
    fun getWalletPublicKey_legacyFirstReceive_matchesIndependentDerivation() = runBlocking {
        val key = client.getWalletPublicKey(PATH_0)

        assertEquals(PUBKEY_0, key.publicKey.toHexString())
        assertEquals("17Y6gka7hSD3zLk3YUhQrbh2EDcsiWCWiv", key.address)
    }

    @Test
    fun getWalletPublicKey_cashAddrFirstReceive_matchesIndependentEncoding() = runBlocking {
        val key = client.getWalletPublicKey(PATH_0, LegacyAddressFormat.CASHADDR)

        assertEquals(PUBKEY_0, key.publicKey.toHexString())
        assertEquals("qprmyef6dfj04h2prr9tjtsuyf4ed3q4sym63lgrmm", key.address)
    }

    @Test
    fun signTransaction_oneP2pkhInput_signsIndependentForkIdDigest() = runBlocking {
        val transaction = LegacyTransaction(
            version = 2,
            inputs = listOf(LegacyInput(PREVIOUS.hexToByteArray(), 0, 0xFFFFFFFF, PATH_0)),
            outputs = listOf(TxOutput(190_000, "76a914${"66".repeat(20)}88ac".hexToByteArray())),
            lockTime = 0,
            mode = LegacySigningMode.BITCOIN_CASH,
        )

        val signatures = Speculos.approving { client.signTransaction(transaction) }

        assertEquals(
            listOf("30440220417e5d717baef34f81018c8dab87bdf80b1ea600cf0b5894e83a34f9e184682102204054385b83743e0e6255fc18de64f304322789b197623a3a401dea05f8e3549541"),
            signatures.map { it.toHexString() },
        )
    }

    private companion object {
        val PATH_0 = DerivationPath.parse("m/44'/145'/0'/0/0")
        const val PUBKEY_0 = "02756adf786c21c5410d356db694d1591086c4ee5ece83f9148ecd71c5a6a09c24"

        const val PREVIOUS =
            "020000000161616161616161616161616161616161616161616161616161616161616161610000000000feffffff01400d0300000000001976a91447b2653a6a64fadd4118cab92e1c226b96c4158188ac00000000"
    }
}
