package cash.p.terminal.ledgerkit.bitcoin

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.Speculos
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The "Bitcoin" app on speculos. Expected values were derived from the speculos seed with embit 0.8.0,
 * signing with plain RFC 6979 nonces (`grind=False`) as the app does.
 */
class BitcoinMainnetSpeculosTest {
    private val client get() = BitcoinAppClient(Speculos.transport("Bitcoin"))

    @Test
    fun getExtendedPubkey_nativeSegwitAccount_matchesIndependentDerivation() = runBlocking {
        assertEquals(ACCOUNT_XPUB, client.getExtendedPubkey(ACCOUNT_PATH))
    }

    @Test
    fun getWalletAddress_nativeSegwitFirstReceive_matchesIndependentDerivation() = runBlocking {
        assertEquals("bc1qqtl9jlrwcr3fsfcjj2du7pu6fcgaxl5dsw2vyg", client.getWalletAddress(policy(), change = false, addressIndex = 0))
    }

    @Test
    fun signPsbt_oneInputOneOutputNativeSegwit_matchesIndependentSignature() = runBlocking {
        val prevTx = "020000000111111111111111111111111111111111111111111111111111111111111111110000000000feffffff01a08601000000000016001402fe597c6ec0e2982712929bcf079a4e11d37e8d00000000"
        val psbt = Psbt(
            txVersion = 2,
            lockTime = 0,
            inputs = listOf(
                PsbtInput(
                    previousTxid = "b31030d5b83310d648cf8eb941f50ed848e5739ba1263c84ca6f61f3585018ef".hexToByteArray(),
                    outputIndex = 0,
                    sequence = 0xFFFFFFFD,
                    nonWitnessUtxo = prevTx.hexToByteArray(),
                    witnessUtxo = TxOutput(100_000, "001402fe597c6ec0e2982712929bcf079a4e11d37e8d".hexToByteArray()),
                    bip32Derivation = KeyDerivation(PUBKEY_0.hexToByteArray(), FINGERPRINT, DerivationPath.parse("84'/0'/0'/0/0")),
                ),
            ),
            outputs = listOf(PsbtOutput(90_000, "0014${"22".repeat(20)}".hexToByteArray())),
        )

        val signatures = Speculos.approving { client.signPsbt(psbt, policy()) }

        assertEquals(
            listOf(
                Triple(
                    0,
                    PUBKEY_0,
                    "3045022100f296a3d10b28dd7b1ad41ee38c06cf97195b273bd9e1cb2b1acea27afcf9c20f0220319554ff1af9f1958a318f6c5a3433fa491ced6ac1233a2632686bcb9fc0346d01",
                ),
            ),
            signatures.map { Triple(it.inputIndex, it.publicKey.toHexString(), it.signature.toHexString()) },
        )
    }

    // No independent signer here: the app recovers the path's key from these signatures (LedgerBtcSignerTest).
    @Test
    fun signMessage_helloWorldOnEachScriptPath_returnsRecordedSignature() = runBlocking {
        val expected = mapOf(
            "m/44'/0'/0'/0/0" to "20063dcbfd423c3c79671fa2dd083abd03736f98da76dd85d7ae03658717c16f020741b48308d9e062fc6931a61ef9c1a1f4c197c755611dc15e50ff401e48e63e",
            "m/49'/0'/0'/0/0" to "1fd51d8cbab1555a832a9a346e01422d37d4fda98d18c5918aebcf8d670d06c54f53a3e239079641ab0fd1415354195b477c6ac742c84d591d31fadaa9e9b6b632",
            "m/84'/0'/0'/0/0" to "1f0bac04efba27daf3b04c19b35f7820aaab911b63f3d489f521dc379a1026dbe86df21553395037c04f42c1445139eefab4dd8da7ab96fab807c15eab6dbc324d",
        )

        val signatures = expected.keys.associateWith { path ->
            Speculos.approving { client.signMessage(DerivationPath.parse(path), "Hello world!".encodeToByteArray()) }.toHexString()
        }

        assertEquals(expected, signatures)
    }

    private fun policy() = WalletPolicy.singleSig(BitcoinAddressType.NATIVE_SEGWIT, FINGERPRINT, ACCOUNT_PATH, ACCOUNT_XPUB)

    companion object {
        private val ACCOUNT_PATH = DerivationPath.parse("m/84'/0'/0'")
        private const val ACCOUNT_XPUB =
            "xpub6DUYn4moKgHkK2d7bXX3mHTPb6XQwRVFRMdZ6ZwLS5u3nonGVpJiFeZiQkHutwdFqxKP75jex8gvVm7ed4euYeDtMnoiF1Cz1z4CeBJYWin"
        private const val PUBKEY_0 = "031869567d5e88d988ff7baf6827983f89530ddd79dbaeadaa6ec538a8f03dea8b"
        private val FINGERPRINT = "f5acc2fd".hexToByteArray()
    }
}
