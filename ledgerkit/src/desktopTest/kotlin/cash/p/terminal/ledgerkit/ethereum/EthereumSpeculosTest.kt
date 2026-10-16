package cash.p.terminal.ledgerkit.ethereum

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.Speculos
import cash.p.terminal.ledgerkit.apdu.LedgerException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The "Ethereum" app on speculos. Expected keys and signatures were derived from the speculos seed with
 * eth-account 0.13.7 / eth-keys 0.8.0 (RFC 6979 nonces, as the app uses).
 */
class EthereumSpeculosTest {
    private val transport get() = Speculos.transport("Ethereum")
    private val client get() = EthereumAppClient(transport)

    @Test
    fun getAddress_firstAccount_matchesIndependentDerivation() = runBlocking {
        val address = client.getAddress(PATH)

        assertEquals("0xDad77910DbDFdE764fC21FCD4E74D71bBACA6D8D", address.address)
        assertEquals(
            "04ef5b152e3f15eb0c50c9916161c2309e54bd87b9adce722d69716bcdef85f547678e15ab40a78919c7284e67a17ee9a96e8b9886b60f767d93023bac8dbc16e4",
            address.publicKey.toHexString(),
        )
    }

    @Test
    fun signTransaction_eip1559Transfer_matchesIndependentSignature() = runBlocking {
        val raw = "02ee0180843b9aca008477359400825208941111111111111111111111111111111111111111872386f26fc1000080c0"

        val signature = Speculos.approving { client.signTransaction(PATH, raw.hexToByteArray()) }

        assertSignature(
            1,
            "fa030182f88e254f6202bebdb474d3c51771d7056b5bcf8bf2cedd9285b92ae9",
            "04fa5be18567c22f105c12b6ff480fde7a06995b3d51fbc3c5b0c61d4bd4ee02",
            signature,
        )
        assertEquals(1, signature.v)
    }

    // Chain id 56: the signed transaction carries v = 56 * 2 + 35 + yParity = 148.
    @Test
    fun signTransaction_legacyEip155Transfer_matchesIndependentSignature() = runBlocking {
        val raw = "ec0785012a05f20082520894111111111111111111111111111111111111111188016345785d8a000080388080"

        val signature = Speculos.approving { client.signTransaction(PATH, raw.hexToByteArray()) }

        assertSignature(
            1,
            "beac9d26bc4446dd7eec79ac18a2eda102094a3d4747d48600bd09341202dcdc",
            "588a84407419e2564405e95d6ca1b2c2cfbd896eaca4dfa1e2f909141dc827f2",
            signature,
        )
        assertEquals(148, signature.v)
    }

    @Test
    fun signTransaction_contractCallBlindSigningDisabled_throwsBlindSigningRequired() = runBlocking {
        setBlindSigning(false)

        assertFailsWith<LedgerException.BlindSigningRequired> { client.signTransaction(PATH, CONTRACT_CALL.hexToByteArray()) }
        dismissToHome()
    }

    @Test
    fun signTransaction_contractCallBlindSigningEnabled_matchesIndependentSignature() = runBlocking {
        setBlindSigning(true)

        val signature = Speculos.approving { client.signTransaction(PATH, CONTRACT_CALL.hexToByteArray()) }

        assertSignature(
            0,
            "1836975f172da156a0278ea0d93d6e89a9ed1d428f530ddaaf08c6ecd189f054",
            "6fd23ca5b62a8bc5bdad33f6cf4eed28197bf68d682c4b8def8d66ddfe49e83c",
            signature,
        )
    }

    @Test
    fun signPersonalMessage_twoChunks_matchesIndependentSignature() = runBlocking {
        val message = "Ledger personal_sign ".repeat(20).encodeToByteArray()

        val signature = Speculos.approving { client.signPersonalMessage(PATH, message) }

        assertSignature(
            1,
            "cb341156111bc26eacb3bc0be5ae28c8672f1b9ac82d2912f4eb824767ab52d1",
            "6a2f1fbecfca94b00cb022c601585c6e6c8b6bb64d1c518cead52a3029ac5f85",
            signature,
        )
        assertEquals(28, signature.v)
    }

    @Test
    fun signTypedData_mailBlindSigningDisabled_throwsBlindSigningRequired() = runBlocking {
        setBlindSigning(false)

        assertFailsWith<LedgerException.BlindSigningRequired> { client.signTypedData(PATH, MAIL_JSON) }
        dismissToHome()
    }

    // Without clear-signing filters the app shows typed data only with blind signing enabled.
    @Test
    fun signTypedData_mail_matchesIndependentSignature() = runBlocking {
        setBlindSigning(true)

        val signature = Speculos.approving { client.signTypedData(PATH, MAIL_JSON) }

        assertSignature(
            1,
            "b1a7312afb06c47b56d25475bdfdf37d907c84e6c1143825f85136062e941ff7",
            "759c2298e0dba1792facd2e579eda48109e4640f4227575bf60db9b06f6e78c8",
            signature,
        )
        assertEquals(28, signature.v)
    }

    private fun assertSignature(yParity: Int, r: String, s: String, signature: EthereumSignature) {
        assertEquals(listOf(yParity.toString(), r, s), listOf(signature.yParity.toString(), signature.r.toHexString(), signature.s.toHexString()))
    }

    /** Flips the app's blind signing setting through its menu when it differs from [enabled]. */
    private suspend fun setBlindSigning(enabled: Boolean) {
        val flags = transport.exchange(GET_APP_CONFIGURATION.hexToByteArray())[0].toInt()
        if ((flags and BLIND_SIGNING_FLAG != 0) == enabled) return
        dismissToHome()
        Speculos.press("right")
        Speculos.press("both")
        Speculos.press("both")
        pressUntil("Back", "right")
        Speculos.press("both")
        pressUntil(HOME, "left")
    }

    private fun dismissToHome() {
        if (HOME !in Speculos.currentScreen()) Speculos.press("both")
        pressUntil(HOME, "left")
    }

    private fun pressUntil(screen: String, button: String) {
        repeat(MAX_PRESSES) {
            if (screen in Speculos.currentScreen()) return
            Speculos.press(button)
        }
        error("No '$screen' screen")
    }

    private companion object {
        val PATH = DerivationPath.parse("m/44'/60'/0'/0/0")
        const val CONTRACT_CALL =
            "02f84c0101843b9aca008477359400830186a094111111111111111111111111111111111111111180a4deadbeef0000000000000000000000000000000000000000000000000000000000000000c0"
        val MAIL_JSON = EIP712_FIXTURES.first { it.name == "0" }.json
        const val GET_APP_CONFIGURATION = "e006000000"
        const val BLIND_SIGNING_FLAG = 0x01
        const val HOME = "is ready"
        const val MAX_PRESSES = 10
    }
}
