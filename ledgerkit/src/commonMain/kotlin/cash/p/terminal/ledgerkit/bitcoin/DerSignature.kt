package cash.p.terminal.ledgerkit.bitcoin

internal const val SIGHASH_ALL = 0x01

/** BIP-66 strict DER ECDSA signature followed by its sighash byte, which must be [sighashType]. */
internal fun isStrictDerSignature(signature: ByteArray, sighashType: Int): Boolean {
    val size = signature.size
    if (size !in MIN_SIZE..MAX_SIZE || signature.last().toInt() and 0xFF != sighashType) return false
    val rLength = signature.unsigned(3)
    if (signature.unsigned(0) != SEQUENCE || signature.unsigned(1) != size - 3 || 5 + rLength >= size) return false
    val sLength = signature.unsigned(5 + rLength)
    return rLength + sLength + 7 == size &&
        isStrictInteger(signature, tagIndex = 2, length = rLength) &&
        isStrictInteger(signature, tagIndex = 4 + rLength, length = sLength)
}

// A non-empty, non-negative INTEGER without a redundant leading zero.
private fun isStrictInteger(signature: ByteArray, tagIndex: Int, length: Int): Boolean {
    val first = tagIndex + 2
    return signature.unsigned(tagIndex) == INTEGER && length > 0 && signature[first] >= 0 &&
        !(length > 1 && signature[first].toInt() == 0 && signature[first + 1] >= 0)
}

private fun ByteArray.unsigned(index: Int): Int = this[index].toInt() and 0xFF

private const val MIN_SIZE = 9
private const val MAX_SIZE = 73
private const val SEQUENCE = 0x30
private const val INTEGER = 0x02
