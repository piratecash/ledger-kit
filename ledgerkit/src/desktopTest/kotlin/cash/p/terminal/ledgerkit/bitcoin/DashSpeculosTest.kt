package cash.p.terminal.ledgerkit.bitcoin

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.Speculos
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The legacy "Dash" app on speculos. Expected values were derived from the speculos seed with embit 0.8.0,
 * signing with plain RFC 6979 nonces (`grind=False`) over the published txid of the parent.
 */
class DashSpeculosTest {
    private val client get() = BtchipLegacyClient(Speculos.transport("Dash"))

    @Test
    fun getWalletPublicKey_firstReceive_matchesIndependentDerivation() = runBlocking {
        val key = client.getWalletPublicKey(PATH_0)

        assertEquals("02baa79d442ecc3b2a4e829061daa8264f6be5186978ec3a634865b85c69ae0027", key.publicKey.toHexString())
        assertEquals("XjtH43LhcLXa9F7A7WKei1QkQtqMFfsJCY", key.address)
    }

    // The signature commits to the parent txid the device computed, so a mis-streamed payload would change it.
    @Test
    fun signTransaction_specialTransactionParent_matchesIndependentSignature() = runBlocking {
        val transaction = LegacyTransaction(
            version = 2,
            inputs = listOf(LegacyInput(LegacyParentFixtures.DASH_CBTX.hexToByteArray(), 0, 0xFFFFFFFF, PATH_0)),
            outputs = listOf(TxOutput(51_000_000, "76a914${"77".repeat(20)}88ac".hexToByteArray())),
            lockTime = 0,
            mode = LegacySigningMode.LEGACY,
        )

        val signatures = Speculos.approving { client.signTransaction(transaction) }

        assertEquals(
            listOf("3045022100dd2d4dcfe71efd9b6cf921d23d3e9d5569b2d68ad5c56b4789e5343003894265022059b3a18bb05e2a001f0450cac0bc15c49353ab442ecf4368534fda1ce4ac81d001"),
            signatures.map { it.toHexString() },
        )
    }

    private companion object {
        val PATH_0 = DerivationPath.parse("m/44'/5'/0'/0/0")
    }
}
