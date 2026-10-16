package cash.p.terminal.ledgerkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EcdsaSignatureTest {
    // thorchain/ledger-thorchain-js tests/unit/basic.spec.ts, "extractSignatureFromTLV".
    @Test
    fun compactLowSSignature_referenceSignature_returnsRAndS() {
        val compact = compactLowSSignature(
            "3045022100a1cfd97f97be1762d9ba6c526613de14ab06f086c3fb62bef6e4f3d75fa679a5022045bc35d51809bf5af415d592f06d9cddf73f833496fdc799844c5bef1cfe4450".hexToByteArray(),
        )

        assertEquals("$R$S", compact?.toHexString())
    }

    @Test
    fun compactLowSSignature_shortR_leftPadsTo32Bytes() {
        val compact = compactLowSSignature("3025020101 0220$S".replace(" ", "").hexToByteArray())

        assertEquals("00".repeat(31) + "01" + S, compact?.toHexString())
    }

    @Test
    fun compactLowSSignature_sAtHalfOrder_accepted() {
        assertEquals("$R$HALF_ORDER", compactLowSSignature("3045022100${R}0220$HALF_ORDER".hexToByteArray())?.toHexString())
    }

    @Test
    fun compactLowSSignature_sAboveHalfOrder_rejected() {
        val aboveHalf = HALF_ORDER.dropLast(1) + "1"
        assertNull(compactLowSSignature("3045022100${R}0220$aboveHalf".hexToByteArray()))
        // The reference signature with s replaced by n - s.
        assertNull(
            compactLowSSignature(
                "3046022100${R}022100ba43ca2ae7f640a50bea2a6d0f926320c36f59b2184ad8a23b86029db337fcf1".hexToByteArray(),
            ),
        )
    }

    @Test
    fun compactLowSSignature_integerOver32Bytes_rejected() {
        assertNull(compactLowSSignature("30450221 01$R 0220$S".replace(" ", "").hexToByteArray()))
    }

    @Test
    fun compactLowSSignature_notStrictDer_rejected() {
        val broken = mapOf(
            "S padded without need" to "3046022100${R}022100$S",
            "trailing sighash byte" to "3045022100${R}0220${S}01",
            "outer length off by one" to "3046022100${R}0220$S",
            "S tagged OCTET STRING" to "3045022100${R}0420$S",
            "negative S" to "3045022100${R}0220ff${S.substring(2)}",
            "truncated" to "3045022100${R}0220${S.dropLast(2)}",
        )

        broken.forEach { (case, hex) -> assertNull(compactLowSSignature(hex.hexToByteArray()), case) }
    }

    private companion object {
        const val R = "a1cfd97f97be1762d9ba6c526613de14ab06f086c3fb62bef6e4f3d75fa679a5"
        const val S = "45bc35d51809bf5af415d592f06d9cddf73f833496fdc799844c5bef1cfe4450"
        const val HALF_ORDER = "7fffffffffffffffffffffffffffffff5d576e7357a4501ddfe92f46681b20a0"
    }
}
