package cash.p.terminal.ledgerkit.bitcoin

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.Speculos
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The legacy "Litecoin" app on speculos. Expected values were derived from the speculos seed with
 * embit 0.8.0, signing with plain RFC 6979 nonces (`grind=False`) as the app does.
 */
class LitecoinSpeculosTest {
    private val client get() = BtchipLegacyClient(Speculos.transport("Litecoin"))

    @Test
    fun getWalletPublicKey_legacyFirstReceive_matchesIndependentDerivation() = runBlocking {
        val key = client.getWalletPublicKey(LEGACY_PATH_0)

        assertEquals("03a975f065a0f5ecfe849ba3d31d7fdadf4fdbea233ffc095057e9977f54f237d1", key.publicKey.toHexString())
        assertEquals("LbSAY7xS2BB78yDx97z7L3PmqDVgAkNnRh", key.address)
    }

    @Test
    fun getWalletPublicKey_nativeSegwitFirstReceive_matchesIndependentDerivation() = runBlocking {
        val key = client.getWalletPublicKey(SEGWIT_PATH_0, LegacyAddressFormat.BECH32)

        assertEquals("032888b52439bb10c092ce34acd4902d719a0e71d8ce2829e90bfa9af7c0489f7c", key.publicKey.toHexString())
        assertEquals("ltc1qrxrnmaczk7dfle83a5sxvap73320eh5zkuv05e", key.address)
    }

    @Test
    fun signTransaction_twoLegacyP2pkhInputs_matchesIndependentSignatures() = runBlocking {
        val transaction = LegacyTransaction(
            version = 2,
            inputs = listOf(
                LegacyInput(PREVIOUS_A.hexToByteArray(), 0, 0xFFFFFFFF, LEGACY_PATH_0),
                LegacyInput(PREVIOUS_B.hexToByteArray(), 0, 0xFFFFFFFF, DerivationPath.parse("m/44'/2'/0'/0/1")),
            ),
            outputs = listOf(TxOutput(700_000, "76a914${"33".repeat(20)}88ac".hexToByteArray())),
            lockTime = 0,
            mode = LegacySigningMode.LEGACY,
        )

        val signatures = Speculos.approving { client.signTransaction(transaction) }

        assertEquals(
            listOf(
                "30440220729ae0bf6ef5c6f3f67d758ec1404b1e4002c64cfbc3590e3509af935d3bfae702202d0679303eaf8b606863b9699e736b0aadd3e2fed310b8ada1aee8d92ca1ebd501",
                "304402200945fc565dca1a8e64dfda77905cb002ed52de002a869c31e505e3c34d3f539602204db2a79896fa07e30156a69bb06d9c1f9695a84dce52213d8165cf947a4210dd01",
            ),
            signatures.map { it.toHexString() },
        )
    }

    @Test
    fun signTransaction_nativeSegwitInput_matchesIndependentSignature() = runBlocking {
        val transaction = LegacyTransaction(
            version = 2,
            inputs = listOf(LegacyInput(PREVIOUS_SEGWIT.hexToByteArray(), 0, 0xFFFFFFFD, SEGWIT_PATH_0)),
            outputs = listOf(TxOutput(390_000, "0014${"44".repeat(20)}".hexToByteArray())),
            lockTime = 0,
            mode = LegacySigningMode.SEGWIT,
        )

        val signatures = Speculos.approving { client.signTransaction(transaction) }

        assertEquals(
            listOf("3045022100e20174570165f91a17b2e94c5f4b5a2546649c5e3a9dcef1b2ab911c9661e7e302201b75911d958a76299dd227696a9f5209c24b92603baa346e6653b8b94022457301"),
            signatures.map { it.toHexString() },
        )
    }

    // The signature commits to the parent txid and amount the device computed from the streamed peg-in.
    @Test
    fun signTransaction_mwebPegInParent_matchesIndependentSignature() = runBlocking {
        val transaction = LegacyTransaction(
            version = 2,
            inputs = listOf(LegacyInput(LegacyParentFixtures.MWEB_PEG_IN_TX.hexToByteArray(), 1, 0xFFFFFFFD, SEGWIT_PATH_0)),
            outputs = listOf(TxOutput(8_300_000, "0014${"88".repeat(20)}".hexToByteArray())),
            lockTime = 0,
            mode = LegacySigningMode.SEGWIT,
        )

        val signatures = Speculos.approving { client.signTransaction(transaction) }

        assertEquals(
            listOf("3045022100ee6a0ca43637286902112ee3d61532a03caab8ce872dfece6ff11d189d738ca402202f7f4b3204cd0393e0e2d1380c14ac7afcd28049b4e7e452f9587b70e998c7d501"),
            signatures.map { it.toHexString() },
        )
    }

    private companion object {
        val LEGACY_PATH_0 = DerivationPath.parse("m/44'/2'/0'/0/0")
        val SEGWIT_PATH_0 = DerivationPath.parse("m/84'/2'/0'/0/0")

        const val PREVIOUS_A =
            "020000000121212121212121212121212121212121212121212121212121212121212121210000000000feffffff0120a10700000000001976a914b1d140085b7ea3f5ebc46d49dabed8cb78e7c42488ac00000000"
        const val PREVIOUS_B =
            "020000000122222222222222222222222222222222222222222222222222222222222222220000000000feffffff01e0930400000000001976a914478c3db550295485d5dc92747f9c15814fda687988ac00000000"
        const val PREVIOUS_SEGWIT =
            "020000000131313131313131313131313131313131313131313131313131313131313131310000000000feffffff01801a06000000000016001419873df702b79a9fe4f1ed2066743e8c54fcde8200000000"
    }
}
