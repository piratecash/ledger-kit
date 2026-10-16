package cash.p.terminal.ledgerkit.solana

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.Speculos
import cash.p.terminal.ledgerkit.apdu.LedgerException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The "Solana" app on speculos. Expected keys and signatures were derived from the speculos seed with SLIP-10 and
 * pycryptodome 3.24.1 Ed25519 (RFC 8032, deterministic). Messages use made-up accounts and blockhash.
 */
class SolanaSpeculosTest {
    private val client get() = SolanaAppClient(Speculos.transport("Solana"))

    @Test
    fun getPublicKey_firstAccount_matchesIndependentDerivation() = runBlocking {
        // Base58: 2SjBBhnRiESRWYPqSti1mM2u47aBa6vBg2YHVqcEmTKH
        assertEquals("1572e52f2c5e7e66bc19ac6cd30b6d696ea799cfe6d784542ab7d8b7fe315b5c", client.getPublicKey(PATH).toHexString())
    }

    @Test
    fun getAppConfiguration_pinnedApp_reportsVersion() = runBlocking {
        assertEquals("1.15.2", client.getAppConfiguration().version)
    }

    @Test
    fun signTransaction_solTransfer_matchesIndependentSignature() = runBlocking {
        val signature = Speculos.approving { client.signTransaction(PATH, SOL_TRANSFER.hexToByteArray()) }

        assertEquals(SOL_TRANSFER_SIGNATURE, signature.toHexString())
    }

    // Without trusted information about its token accounts the app cannot show an SPL transfer in clear.
    @Test
    fun signTransaction_splTransferBlindSigningDisabled_throwsBlindSigningRequired() = runBlocking {
        setBlindSigning(false)

        assertFailsWith<LedgerException.BlindSigningRequired> { client.signTransaction(PATH, SPL_TRANSFER.hexToByteArray()) }
        Speculos.dismissToHome()
    }

    @Test
    fun signTransaction_splTransferBlindSigningEnabled_matchesIndependentSignature() = runBlocking {
        setBlindSigning(true)

        val signature = Speculos.approving { client.signTransaction(PATH, SPL_TRANSFER.hexToByteArray()) }

        assertEquals(SPL_TRANSFER_SIGNATURE, signature.toHexString())
    }

    // Four transfers in a v0 message: two chunks, and more than the app shows in clear.
    @Test
    fun signTransaction_v0MessageOverTwoChunksBlindSigningEnabled_matchesIndependentSignature() = runBlocking {
        setBlindSigning(true)

        val signature = Speculos.approving { client.signTransaction(PATH, V0_FOUR_TRANSFERS.hexToByteArray()) }

        assertEquals(V0_FOUR_TRANSFERS_SIGNATURE, signature.toHexString())
    }

    private suspend fun setBlindSigning(enabled: Boolean) {
        if (client.getAppConfiguration().blindSigningEnabled != enabled) Speculos.toggleFirstSetting()
    }

    private companion object {
        val PATH = DerivationPath.parse("m/44'/501'/0'")
        val SOL_TRANSFER =
            "010001031572e52f2c5e7e66bc19ac6cd30b6d696ea799cfe6d784542ab7d8b7fe315b5c2121212121212121212121212121" +
            "2121212121212121212121212121212121210000000000000000000000000000000000000000000000000000000000000000" +
            "424242424242424242424242424242424242424242424242424242424242424201020200010c0200000040420f0000000000"
        val SOL_TRANSFER_SIGNATURE =
            "9ac63d8b3402c3f759e34f88dfc47327615c85f0c42d34f116419afb1f2ee725a39a68a0d95e5266338ee11762001b5ef16c" +
            "9881f79c3ed2aa8efd3e3f6e170a"
        val SPL_TRANSFER =
            "010002051572e52f2c5e7e66bc19ac6cd30b6d696ea799cfe6d784542ab7d8b7fe315b5c2727272727272727272727272727" +
            "2727272727272727272727272727272727272929292929292929292929292929292929292929292929292929292929292929" +
            "282828282828282828282828282828282828282828282828282828282828282806ddf6e1d765a193d9cbe146ceeb79ac1cb4" +
            "85ed5f5b37913a8cf5857eff00a9424242424242424242424242424242424242424242424242424242424242424201040401" +
            "0302000a0c60e316000000000006"
        val SPL_TRANSFER_SIGNATURE =
            "1eb2c4b00fbf2bc468facaa291b4231903a076d940f695ed666d15fe3075ba3721cca3c35f2f0c5b2a2d525bdfc09f3364bd" +
            "77389f6589087f19654bbc433108"
        val V0_FOUR_TRANSFERS =
            "80010001061572e52f2c5e7e66bc19ac6cd30b6d696ea799cfe6d784542ab7d8b7fe315b5c21212121212121212121212121" +
            "2121212121212121212121212121212121212122222222222222222222222222222222222222222222222222222222222222" +
            "2223232323232323232323232323232323232323232323232323232323232323232424242424242424242424242424242424" +
            "2424242424242424242424242424240000000000000000000000000000000000000000000000000000000000000000424242" +
            "424242424242424242424242424242424242424242424242424242424204050200010c02000000e803000000000000050200" +
            "020c02000000d007000000000000050200030c02000000b80b000000000000050200040c02000000a00f00000000000000"
        val V0_FOUR_TRANSFERS_SIGNATURE =
            "0fb39a7cf63c15e8d49e6d5f77c17bb08fa0724d114a021d0a68dbdad0a0ad93dcac99f0eeca4cf4db1607810e467742b7a9" +
            "97e71afe589af3c910968d6f1a00"
    }
}
