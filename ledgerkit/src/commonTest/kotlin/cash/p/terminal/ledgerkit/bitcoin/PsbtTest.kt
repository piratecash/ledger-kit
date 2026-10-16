package cash.p.terminal.ledgerkit.bitcoin

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.compactSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PsbtTest {
    @Test
    fun maps_referencePsbts_matchFixtureMapsWithV2Fields() {
        SignPsbtVectors.allCases.forEach { case ->
            val fixture = PsbtV0Fixture(case.psbt)
            val psbt = fixture.toPsbt()

            assertEquals(expectedGlobal(fixture), psbt.globalMap().toHexSet(), case.type.name)
            psbt.inputs.forEachIndexed { index, input ->
                val txIn = fixture.unsignedTx.inputs[index]
                val v2 = listOf(entry(0x0E, txIn.txid), entry(0x0F, txIn.vout), entry(0x10, txIn.sequence))
                assertEquals((fixture.inputs[index] + v2).toHexSet(), input.toMap().toHexSet(), "${case.type} input $index")
            }
            psbt.outputs.forEachIndexed { index, output ->
                val txOut = fixture.unsignedTx.outputs[index]
                val v2 = listOf(entry(0x03, txOut.amount), entry(0x04, txOut.script))
                assertEquals((fixture.outputs[index] + v2).toHexSet(), output.toMap().toHexSet(), "${case.type} output $index")
            }
        }
    }

    @Test
    fun toMap_derivationWithUncompressedKey_throws() {
        val derivation = KeyDerivation(ByteArray(65), ByteArray(4), DerivationPath.parse("84'/0'/0'/0/0"))
        val input = PsbtInput(ByteArray(32), 0, 0, bip32Derivation = derivation)

        assertFailsWith<IllegalArgumentException> { input.toMap() }
    }

    @Test
    fun init_shortPreviousTxid_throws() {
        assertFailsWith<IllegalArgumentException> { PsbtInput(ByteArray(31), 0, 0) }
    }

    private fun expectedGlobal(fixture: PsbtV0Fixture): Set<String> {
        val tx = fixture.unsignedTx
        return listOf(
            entry(0x02, tx.version),
            entry(0x03, tx.lockTime),
            entry(0x04, compactSize(tx.inputs.size)),
            entry(0x05, compactSize(tx.outputs.size)),
            entry(0xFB, "02000000".hexToByteArray()),
        ).toHexSet() + fixture.global.filterNot { it.first.contentEquals(byteArrayOf(0)) }.toHexSet()
    }

    private fun entry(type: Int, value: ByteArray) = byteArrayOf(type.toByte()) to value

    private fun List<Pair<ByteArray, ByteArray>>.toHexSet(): Set<String> =
        map { "${it.first.toHexString()}=${it.second.toHexString()}" }.toSet().also { check(it.size == size) { "Duplicate keys" } }
}
