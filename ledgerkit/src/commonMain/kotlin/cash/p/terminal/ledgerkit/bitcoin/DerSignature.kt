package cash.p.terminal.ledgerkit.bitcoin

import cash.p.terminal.ledgerkit.isStrictDer

internal const val SIGHASH_ALL = 0x01

/** BIP-66 strict DER ECDSA signature followed by its sighash byte, which must be [sighashType]. */
internal fun isStrictDerSignature(signature: ByteArray, sighashType: Int): Boolean =
    signature.isNotEmpty() && signature.last().toInt() and 0xFF == sighashType &&
        isStrictDer(signature.copyOf(signature.size - 1))
