package cash.p.terminal.ledgerkit.ethereum

/**
 * What the client needs to know about an unsigned transaction before sending it: whether it carries
 * contract data, and for legacy ones the EIP-155 chain id and the length of its `chainId, 0, 0` tail.
 */
internal class UnsignedTransaction private constructor(
    val hasData: Boolean,
    val legacyChainId: ByteArray?,
    val legacyTailLength: Int,
) {
    val isTyped: Boolean get() = legacyChainId == null

    /** The y parity behind the app's `v`, or null when `v` cannot belong to this transaction. */
    fun yParityOf(v: Int): Int? {
        // The app computes v from the chain id's (up to) 4 high bytes and keeps the low byte.
        val base = legacyChainId?.let { chainId ->
            val high = chainId.take(4).fold(0L) { value, byte -> value shl 8 or (byte.toLong() and 0xFF) }
            high * 2 + EIP155_V_OFFSET
        } ?: 0L
        return ((v - base) and 0xFF).toInt().takeIf { it <= 1 }
    }

    companion object {
        fun parse(raw: ByteArray): UnsignedTransaction {
            require(raw.isNotEmpty()) { "Empty transaction" }
            val type = raw[0].toInt() and 0xFF
            return if (type >= RLP_LIST) legacy(rlpList(raw, 0)) else typed(type, rlpList(raw, 1))
        }

        private fun legacy(items: List<RlpItem>): UnsignedTransaction {
            // A pre-EIP-155 transaction (6 items or chain id 0) is valid on every chain, so it is never signed.
            require(items.size == LEGACY_ITEMS) { "Not an EIP-155 transaction: ${items.size} items" }
            val chainId = items[LEGACY_CHAIN_ID].payload()
            require(chainId.isNotEmpty() && !items[LEGACY_CHAIN_ID].isList) { "Not an EIP-155 chain id" }
            return UnsignedTransaction(
                hasData = items.dataAt(LEGACY_DATA),
                legacyChainId = chainId,
                legacyTailLength = items.last().end - items[LEGACY_CHAIN_ID].offset,
            )
        }

        private fun typed(type: Int, items: List<RlpItem>): UnsignedTransaction {
            val (itemCount, dataIndex) = TYPED_LAYOUT[type] ?: throw IllegalArgumentException("Unsupported transaction type $type")
            require(items.size == itemCount) { "Type $type transaction needs $itemCount items, has ${items.size}" }
            return UnsignedTransaction(hasData = items.dataAt(dataIndex), legacyChainId = null, legacyTailLength = 0)
        }

        private fun List<RlpItem>.dataAt(index: Int): Boolean {
            require(!this[index].isList) { "Data field is a list" }
            return this[index].end > this[index].payloadOffset
        }

        private const val RLP_LIST = 0xC0
        private const val LEGACY_ITEMS = 9
        private const val LEGACY_DATA = 5
        private const val LEGACY_CHAIN_ID = 6
        private const val EIP155_V_OFFSET = 35

        // EIP-2930 and EIP-1559: item count of the unsigned list and the index of its data field.
        private val TYPED_LAYOUT = mapOf(0x01 to (8 to 6), 0x02 to (9 to 7))
    }
}

private class RlpItem(
    private val bytes: ByteArray,
    val offset: Int,
    val payloadOffset: Int,
    val end: Int,
    val isList: Boolean,
) {
    fun payload(): ByteArray = bytes.copyOfRange(payloadOffset, end)
}

/** The items of the RLP list that starts at [offset] and must run to the end of [bytes]. */
private fun rlpList(bytes: ByteArray, offset: Int): List<RlpItem> {
    val list = rlpItem(bytes, offset)
    require(list.isList && list.end == bytes.size) { "Not a single RLP list" }
    return buildList {
        var next = list.payloadOffset
        while (next < list.end) add(rlpItem(bytes, next).also { next = it.end })
        require(next == list.end) { "RLP item overruns its list" }
    }
}

private fun rlpItem(bytes: ByteArray, offset: Int): RlpItem {
    require(offset < bytes.size) { "Truncated RLP" }
    val prefix = bytes[offset].toInt() and 0xFF
    val isList = prefix >= 0xC0
    val shortBase = if (isList) 0xC0 else 0x80
    val longBase = shortBase + SHORT_LIMIT
    val (headerLength, payloadLength) = when {
        prefix < 0x80 -> 0 to 1
        prefix <= longBase -> 1 to prefix - shortBase
        else -> {
            val lengthSize = prefix - longBase
            require(lengthSize <= 4 && offset + lengthSize < bytes.size) { "Bad RLP length" }
            val length = (1..lengthSize).fold(0L) { value, i -> value shl 8 or (bytes[offset + i].toLong() and 0xFF) }
            require(length <= Int.MAX_VALUE) { "Bad RLP length" }
            1 + lengthSize to length.toInt()
        }
    }
    val end = offset.toLong() + headerLength + payloadLength
    require(end <= bytes.size) { "Truncated RLP" }
    return RlpItem(bytes, offset, offset + headerLength, end.toInt(), isList)
}

private const val SHORT_LIMIT = 55
