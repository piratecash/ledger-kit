package cash.p.terminal.ledgerkit

/** A strict DER `SEQUENCE { INTEGER r, INTEGER s }`: the BIP-66 rules without the trailing sighash byte. */
internal fun isStrictDer(der: ByteArray): Boolean {
    val size = der.size
    if (size !in MIN_SIZE..MAX_SIZE) return false
    val rLength = der.unsigned(3)
    if (der.unsigned(0) != SEQUENCE || der.unsigned(1) != size - 2 || 5 + rLength >= size) return false
    val sLength = der.unsigned(5 + rLength)
    return rLength + sLength + 6 == size &&
        isStrictInteger(der, tagIndex = 2, length = rLength) &&
        isStrictInteger(der, tagIndex = 4 + rLength, length = sLength)
}

/**
 * `r || s`, 32 big-endian bytes each, of a strict DER secp256k1 signature, or null when it is not one or its `s` is
 * above half the curve order: Cosmos SDK chains refuse such a signature as malleable.
 */
internal fun compactLowSSignature(der: ByteArray): ByteArray? {
    if (!isStrictDer(der)) return null
    val rLength = der.unsigned(3)
    val r = der.integer(4, rLength) ?: return null
    val s = der.integer(6 + rLength, der.unsigned(5 + rLength)) ?: return null
    return if (s.isAbove(SECP256K1_HALF_ORDER)) null else r + s
}

// A non-empty, non-negative INTEGER without a redundant leading zero.
private fun isStrictInteger(der: ByteArray, tagIndex: Int, length: Int): Boolean {
    val first = tagIndex + 2
    return der.unsigned(tagIndex) == INTEGER && length > 0 && der[first] >= 0 &&
        !(length > 1 && der[first].toInt() == 0 && der[first + 1] >= 0)
}

// The strict INTEGER at [start] left-padded to 32 bytes, or null when it is longer.
private fun ByteArray.integer(start: Int, length: Int): ByteArray? {
    val value = copyOfRange(start, start + length).dropWhile { it == 0.toByte() }.toByteArray()
    return if (value.size > SCALAR_SIZE) null else ByteArray(SCALAR_SIZE - value.size) + value
}

// Unsigned comparison of two equally long big-endian numbers.
private fun ByteArray.isAbove(other: ByteArray): Boolean {
    val index = indices.firstOrNull { this[it] != other[it] } ?: return false
    return unsigned(index) > other.unsigned(index)
}

private fun ByteArray.unsigned(index: Int): Int = this[index].toInt() and 0xFF

private const val MIN_SIZE = 8
private const val MAX_SIZE = 72
private const val SEQUENCE = 0x30
private const val INTEGER = 0x02
private const val SCALAR_SIZE = 32
private val SECP256K1_HALF_ORDER = "7fffffffffffffffffffffffffffffff5d576e7357a4501ddfe92f46681b20a0".hexToByteArray()
