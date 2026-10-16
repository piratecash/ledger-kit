package cash.p.terminal.ledgerkit.stellar

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.Speculos
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The "Stellar" app on speculos. Expected keys and signatures were derived from the speculos seed with SLIP-10 and
 * pycryptodome 3.24.1 Ed25519 (RFC 8032) over the SHA-256 of the signature base (public network, made-up accounts).
 */
class StellarSpeculosTest {
    private val client get() = StellarAppClient(Speculos.transport("Stellar"))

    @Test
    fun getPublicKey_firstAccount_matchesIndependentDerivation() = runBlocking {
        // GCNCEJIAZ5D3APIF5XWAJ3JSSTHM4HPHE7GK3NAB6R6WWSZDB2A2BQ5B
        assertEquals("9a222500cf47b03d05edec04ed3294cece1de727ccadb401f47d6b4b230e81a0", client.getPublicKey(PATH).toHexString())
    }

    @Test
    fun signTransaction_payment_matchesIndependentSignature() = runBlocking {
        val signature = Speculos.approving { client.signTransaction(PATH, PAYMENT.hexToByteArray()) }

        assertEquals(PAYMENT_SIGNATURE, signature.toHexString())
    }

    @Test
    fun signTransaction_threePaymentsOverTwoChunks_matchesIndependentSignature() = runBlocking {
        val signature = Speculos.approving { client.signTransaction(PATH, THREE_PAYMENTS_WITH_MEMO.hexToByteArray()) }

        assertEquals(THREE_PAYMENTS_WITH_MEMO_SIGNATURE, signature.toHexString())
    }

    private companion object {
        val PATH = DerivationPath.parse("m/44'/148'/0'")
        val PAYMENT =
            "7ac33997544e3175d266bd022439b22cdb16508c01163f26e5cb2a3e1045a97900000002000000009a222500cf47b03d05ed" +
            "ec04ed3294cece1de727ccadb401f47d6b4b230e81a0000000640000001cbe991a1400000000000000000000000100000000" +
            "00000001000000003030303030303030303030303030303030303030303030303030303030303030000000000000000000bc" +
            "614e00000000"
        val PAYMENT_SIGNATURE =
            "aa31757c16d0140d46fd3abf8368a6dc2cf729d71b8c79e2486d23eb917014905c12e9dc61fb92f44f4f37c7b37c17b26f52" +
            "966f528135e87800f55ad0640007"
        val THREE_PAYMENTS_WITH_MEMO =
            "7ac33997544e3175d266bd022439b22cdb16508c01163f26e5cb2a3e1045a97900000002000000009a222500cf47b03d05ed" +
            "ec04ed3294cece1de727ccadb401f47d6b4b230e81a00000012c0000001cbe991a1400000000000000010000000f6c656467" +
            "65722d6b69742074657374000000000300000000000000010000000030303030303030303030303030303030303030303030" +
            "3030303030303030303000000000000000000098968000000000000000010000000031313131313131313131313131313131" +
            "31313131313131313131313131313131000000000000000001312d0000000000000000010000000032323232323232323232" +
            "32323232323232323232323232323232323232323232000000000000000001c9c38000000000"
        val THREE_PAYMENTS_WITH_MEMO_SIGNATURE =
            "20d3eb6daa317abaff462a4a73360e107a360bc454a2d67877db192047953a48b6680ee727603b5f83f78afab8dc01fdb5f0" +
            "cb951bfc7d13935953699fa70308"
    }
}
