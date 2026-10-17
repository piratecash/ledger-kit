package cash.p.terminal.ledgerkit.zcash

private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
private const val BECH32M_CONSTANT = 0x2bc830a3
private const val CHECKSUM_SIZE = 6
private val GENERATORS = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

/** BIP-350 bech32m, lowercase only and without the 90-character limit: unified encodings are longer. */
internal fun isBech32m(text: String, hrp: String): Boolean {
    val prefix = hrp + '1'
    if (!text.startsWith(prefix)) return false
    val data = text.substring(prefix.length).map(CHARSET::indexOf)
    if (data.size <= CHECKSUM_SIZE || data.any { it < 0 }) return false
    val expandedHrp = hrp.map { it.code shr 5 } + 0 + hrp.map { it.code and 31 }
    return polymod(expandedHrp + data) == BECH32M_CONSTANT
}

private fun polymod(values: List<Int>): Int = values.fold(1) { checksum, value ->
    val top = checksum ushr 25
    GENERATORS.indices.fold((checksum and 0x1ffffff) shl 5 xor value) { next, i ->
        if ((top shr i) and 1 != 0) next xor GENERATORS[i] else next
    }
}
