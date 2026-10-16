package cash.p.terminal.ledgerkit

import cash.p.terminal.ledgerkit.device.LedgerModel
import kotlin.test.Test
import kotlin.test.assertEquals

class LedgerModelTest {
    @Test
    fun fromProductId_highByte_namesModel() {
        val expected = mapOf(
            0x1011 to LedgerModel.NanoS,
            0x4011 to LedgerModel.NanoX,
            0x5011 to LedgerModel.NanoSPlus,
            0x6011 to LedgerModel.Stax,
            0x7011 to LedgerModel.Flex,
            0x8011 to LedgerModel.NanoGen5,
            0x0001 to LedgerModel.Unknown,
        )

        expected.forEach { (productId, model) -> assertEquals(model, LedgerModel.fromProductId(productId)) }
    }
}
