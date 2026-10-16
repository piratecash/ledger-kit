package cash.p.terminal.ledgerkit.bitcoin

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.Speculos
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The legacy "Dogecoin" app on speculos. Expected values were derived from the speculos seed with
 * embit 0.8.0, signing with plain RFC 6979 nonces (`grind=False`) as the app does.
 */
class DogecoinSpeculosTest {
    private val client get() = BtchipLegacyClient(Speculos.transport("Dogecoin"))

    @Test
    fun getWalletPublicKey_firstReceive_matchesIndependentDerivation() = runBlocking {
        val key = client.getWalletPublicKey(PATH_0)

        assertEquals("0322b34e13d09ba4b2164d731e8548015b3cc56b0bb6831895a31e08c99dff4e37", key.publicKey.toHexString())
        assertEquals("DGKP5HY3Rk1CAiB9we92JPpjQXDb9RCPzo", key.address)
    }

    @Test
    fun signTransaction_oneP2pkhInput_matchesIndependentSignature() = runBlocking {
        val transaction = LegacyTransaction(
            version = 1,
            inputs = listOf(LegacyInput(PREVIOUS.hexToByteArray(), 0, 0xFFFFFFFF, PATH_0)),
            outputs = listOf(TxOutput(400_000_000, "76a914${"55".repeat(20)}88ac".hexToByteArray())),
            lockTime = 0,
            mode = LegacySigningMode.LEGACY,
        )

        val signatures = Speculos.approving { client.signTransaction(transaction) }

        assertEquals(
            listOf("304402202d14f27259d7980ea61baf5590e0b949611d644f9d41b0d533d8ea9cbf80afa4022033db8e2a0df335e64d9168480fd279ced9492efccb030b39370e9dcac0d3623801"),
            signatures.map { it.toHexString() },
        )
    }

    private companion object {
        val PATH_0 = DerivationPath.parse("m/44'/3'/0'/0/0")

        const val PREVIOUS =
            "010000000151515151515151515151515151515151515151515151515151515151515151510000000000feffffff010065cd1d000000001976a9147a9b588e995d3134c13672b6ad09a74b2aa7e94988ac00000000"
    }
}
