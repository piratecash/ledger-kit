package cash.p.terminal.ledgerkit

/** A BIP-32 path; hardened indices carry the `0x80000000` bit. */
public class DerivationPath(public val indices: List<UInt>) {
    init {
        require(indices.size <= MAX_DEPTH) { "Derivation path is deeper than $MAX_DEPTH" }
    }

    /** Ledger apps take a path as its depth followed by big-endian indices. */
    internal fun toApduBytes(): ByteArray =
        byteArrayOf(indices.size.toByte()) + indices.fold(ByteArray(0)) { bytes, index -> bytes + index.toUInt32Be() }

    /** [toApduBytes] for apps that derive Ed25519 keys, which have no unhardened derivation. */
    internal fun toHardenedApduBytes(): ByteArray {
        require(indices.all { it >= HARDENED }) { "Ed25519 derivation needs every index hardened: $this" }
        return toApduBytes()
    }

    /** Key-origin form without the `m/` prefix, hardened indices marked with `'`: `84'/0'/0'`. */
    override fun toString(): String = indices.joinToString("/") { index ->
        if (index >= HARDENED) "${index - HARDENED}'" else index.toString()
    }

    override fun equals(other: Any?): Boolean = other is DerivationPath && other.indices == indices

    override fun hashCode(): Int = indices.hashCode()

    public companion object {
        public const val HARDENED: UInt = 0x80000000u

        // Ledger apps refuse paths deeper than 10 levels.
        private const val MAX_DEPTH = 10

        /** Parses `m/84'/0'/0'/0/5`; the `m/` prefix is optional and `h` marks hardened like `'`. */
        public fun parse(path: String): DerivationPath {
            val parts = path.removePrefix("m").removePrefix("/").split('/').filter { it.isNotEmpty() }
            return DerivationPath(parts.map(::parseIndex))
        }

        private fun parseIndex(part: String): UInt {
            val hardened = part.endsWith('\'') || part.endsWith('h')
            val index = part.trimEnd('\'', 'h').toUIntOrNull()
            require(index != null && index < HARDENED) { "Invalid derivation index: $part" }
            return if (hardened) index + HARDENED else index
        }
    }
}
