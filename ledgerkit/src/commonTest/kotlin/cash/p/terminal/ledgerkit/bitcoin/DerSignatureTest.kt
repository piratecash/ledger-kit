package cash.p.terminal.ledgerkit.bitcoin

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Cases follow the BIP-66 IsValidSignatureEncoding rules, each breaking one of them.
class DerSignatureTest {
    @Test
    fun isStrictDerSignature_minimalValidSignature_accepted() {
        assertTrue(isStrictDerSignature("300602010102010101".hexToByteArray(), SIGHASH_ALL))
    }

    @Test
    fun isStrictDerSignature_highBitValuesPaddedWithZero_accepted() {
        assertTrue(isStrictDerSignature("30080202008102020081 01".replace(" ", "").hexToByteArray(), SIGHASH_ALL))
    }

    @Test
    fun isStrictDerSignature_brokenRule_rejected() {
        val broken = mapOf(
            "R tagged OCTET STRING" to "300604010102010101",
            "S tagged OCTET STRING" to "300602010104010101",
            "negative R" to "300602018102010101",
            "negative S" to "300602010102018101",
            "R padded without need" to "30070202000102010101",
            "S padded without need" to "30070201010202000101",
            "empty R" to "300602000202010101",
            "outer length off by one" to "300702010102010101",
            "trailing byte inside" to "30070201010201010001",
            "other sighash" to "300602010102010183",
            "too short" to "3006020101020101",
        )

        broken.forEach { (case, hex) -> assertFalse(isStrictDerSignature(hex.hexToByteArray(), SIGHASH_ALL), case) }
    }
}
