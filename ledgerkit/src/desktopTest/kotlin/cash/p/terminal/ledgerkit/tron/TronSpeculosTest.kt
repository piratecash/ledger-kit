package cash.p.terminal.ledgerkit.tron

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
 * The "Tron" app on speculos. Expected keys and signatures were derived from the speculos seed with embit 0.8.0
 * (BIP-32) and eth-keys 0.8.0 (RFC 6979) over the SHA-256 of `raw_data`. Transactions use a made-up reference block.
 */
class TronSpeculosTest {
    private val transport get() = Speculos.transport("Tron")
    private val client get() = TronAppClient(transport)

    @Test
    fun getAddress_firstAccount_matchesIndependentDerivation() = runBlocking {
        val address = client.getAddress(PATH)

        assertEquals("TMVkf58Mk6JiVHXFqScrFCCfy5cgMoKhN1", address.address)
        assertEquals(
            "0468607df0f5bd24773280155303c61818140d8836cfbe0e34e6ed8ce7ddd14ae1ba772c18c87e9a89cbe1cdf0c6049579f025259f595e70c3160a32b515f6d9be",
            address.publicKey.toHexString(),
        )
    }

    @Test
    fun signTransaction_trxTransfer_matchesIndependentSignature() = runBlocking {
        val signature = Speculos.approving { client.signTransaction(PATH, TRX_TRANSFER.hexToByteArray()) }

        assertEquals(TRX_TRANSFER_SIGNATURE, signature.toHexString())
    }

    // A USDT transfer signs in clear: no app setting is needed.
    @Test
    fun signTransaction_trc20TransferDataNotAllowed_matchesIndependentSignature() = runBlocking {
        setDataAllowed(false)

        val signature = Speculos.approving { client.signTransaction(PATH, TRC20_TRANSFER.hexToByteArray()) }

        assertEquals(TRC20_TRANSFER_SIGNATURE, signature.toHexString())
    }

    @Test
    fun signTransaction_memoDataNotAllowed_throwsBlindSigningRequired() = runBlocking {
        setDataAllowed(false)

        assertFailsWith<LedgerException.BlindSigningRequired> { client.signTransaction(PATH, TRX_TRANSFER_WITH_MEMO.hexToByteArray()) }
        Speculos.dismissToHome()
    }

    // The 154-byte memo puts the transaction over two chunks.
    @Test
    fun signTransaction_memoOverTwoChunksDataAllowed_matchesIndependentSignature() = runBlocking {
        setDataAllowed(true)

        val signature = Speculos.approving { client.signTransaction(PATH, TRX_TRANSFER_WITH_MEMO.hexToByteArray()) }

        assertEquals(TRX_TRANSFER_WITH_MEMO_SIGNATURE, signature.toHexString())
    }

    private suspend fun setDataAllowed(allowed: Boolean) {
        val flags = transport.exchange(Apdu(CLA, GET_APP_CONFIGURATION, 0, 0)).data[0].toInt()
        if ((flags and DATA_ALLOWED_FLAG != 0) != allowed) Speculos.toggleFirstSetting()
    }

    private companion object {
        val PATH = DerivationPath.parse("m/44'/195'/0'/0/0")
        const val CLA = 0xE0
        const val GET_APP_CONFIGURATION = 0x06
        const val DATA_ALLOWED_FLAG = 0x01
        val TRX_TRANSFER =
            "0a023dce220895da42177db0050740e0d4b6c19c335a67080112630a2d747970652e676f6f676c65617069732e636f6d2f70" +
            "726f746f636f6c2e5472616e73666572436f6e747261637412320a15417e70497d5c6c768aab8fa892da766aba58b389a612" +
            "1541e2ae49db6a70b9b4757d2137a43b69b24a44578018e0c65b708080b3c19c33"
        val TRX_TRANSFER_SIGNATURE =
            "323ec5bf7d2e62a3bb67170f4a3beed07063bdb70805a277aa204fe4f93f3fad78b6c5079e72b77786182716b5ecde959945" +
            "ec116db579f2e39c08bf927b65f300"
        val TRC20_TRANSFER =
            "0a023dce220895da42177db0050740e0d4b6c19c335aae01081f12a9010a31747970652e676f6f676c65617069732e636f6d" +
            "2f70726f746f636f6c2e54726967676572536d617274436f6e747261637412740a15417e70497d5c6c768aab8fa892da766a" +
            "ba58b389a6121541a614f803b6fd780986a42c78ec9c7f77e6ded13c2244a9059cbb000000000000000000000000e2ae49db" +
            "6a70b9b4757d2137a43b69b24a44578000000000000000000000000000000000000000000000000000000000002625a07080" +
            "80b3c19c3390018087a70e"
        val TRC20_TRANSFER_SIGNATURE =
            "0d3d02ee738e3baa026466673cc05f55b650c2f16777468b58fb7d42c108ffc7731a15cd96d1889e433ce4fb745051a8e667" +
            "12f85fbe9a23fab4c4ca599dd35100"
        val TRX_TRANSFER_WITH_MEMO =
            "0a023dce220895da42177db0050740e0d4b6c19c33529a01696e766f69636520343220696e766f69636520343220696e766f" +
            "69636520343220696e766f69636520343220696e766f69636520343220696e766f69636520343220696e766f696365203432" +
            "20696e766f69636520343220696e766f69636520343220696e766f69636520343220696e766f69636520343220696e766f69" +
            "636520343220696e766f69636520343220696e766f696365203432205a67080112630a2d747970652e676f6f676c65617069" +
            "732e636f6d2f70726f746f636f6c2e5472616e73666572436f6e747261637412320a15417e70497d5c6c768aab8fa892da76" +
            "6aba58b389a6121541e2ae49db6a70b9b4757d2137a43b69b24a44578018e0c65b708080b3c19c33"
        val TRX_TRANSFER_WITH_MEMO_SIGNATURE =
            "9e500ae299c98618b8d628ea816c535a6307907dcea4bf2ef631a5e7e4a69d17314823cdde8c2c31e32e3b1ea09f52b41d89" +
            "63c14823925c3f54e85dfbe71eb401"
    }
}
