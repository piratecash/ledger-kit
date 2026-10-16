package cash.p.terminal.ledgerkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DerivationPathTest {
    // APDU path bytes: ledgerjs hw-app-btc getWalletPublicKey log for 44'/0'/0'/0.
    @Test
    fun toApduBytes_bip44Path_depthThenBigEndianIndices() {
        assertEquals("048000002c800000008000000000000000", DerivationPath.parse("44'/0'/0'/0").toApduBytes().toHexString())
    }

    @Test
    fun parse_prefixAndHardenedMarkers_sameIndices() {
        assertEquals(DerivationPath.parse("84'/0'/0'/1/5"), DerivationPath.parse("m/84h/0h/0h/1/5"))
    }

    @Test
    fun toString_hardenedPath_keyOriginForm() {
        assertEquals("84'/1'/0'/1/5", DerivationPath.parse("m/84'/1'/0'/1/5").toString())
    }

    @Test
    fun parse_indexOutOfRange_throws() {
        assertFailsWith<IllegalArgumentException> { DerivationPath.parse("m/2147483648") }
    }

    @Test
    fun parse_notANumber_throws() {
        assertFailsWith<IllegalArgumentException> { DerivationPath.parse("m/44'/x") }
    }
}
