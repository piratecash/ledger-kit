package cash.p.terminal.ledgerkit

internal fun Int.toUInt16Bytes(): ByteArray = byteArrayOf((this shr 8).toByte(), toByte())

internal fun ByteArray.readUInt16(offset: Int): Int =
    (this[offset].toInt() and 0xFF shl 8) or (this[offset + 1].toInt() and 0xFF)

internal fun UInt.toUInt32Be(): ByteArray = ByteArray(4) { (this shr (24 - it * 8)).toByte() }

internal fun Long.toUInt32Le(): ByteArray {
    require(this in 0..0xFFFF_FFFFL) { "Not an unsigned 32-bit value: $this" }
    return ByteArray(4) { (this shr (it * 8)).toByte() }
}

internal fun Long.toUInt64Le(): ByteArray = ByteArray(8) { (this shr (it * 8)).toByte() }

/** Bitcoin CompactSize. */
internal fun compactSize(value: Long): ByteArray = when {
    value < 0 -> throw IllegalArgumentException("Negative CompactSize: $value")
    value < 0xFD -> byteArrayOf(value.toByte())
    value <= 0xFFFF -> byteArrayOf(0xFD.toByte(), value.toByte(), (value shr 8).toByte())
    value <= 0xFFFF_FFFFL -> byteArrayOf(0xFE.toByte()) + value.toUInt32Le()
    else -> byteArrayOf(0xFF.toByte()) + value.toUInt64Le()
}

internal fun compactSize(value: Int): ByteArray = compactSize(value.toLong())

internal expect fun sha256(data: ByteArray): ByteArray

/** Sequential reader over a device answer or a serialized transaction; running past the end throws. */
internal class ByteReader(private val bytes: ByteArray) {
    private var offset = 0

    val remaining: Int get() = bytes.size - offset

    fun readByte(): Int = readBytes(1)[0].toInt() and 0xFF

    fun readBytes(count: Int): ByteArray {
        check(count in 0..remaining) { "Truncated data: need $count bytes, have $remaining" }
        return bytes.copyOfRange(offset, offset + count).also { offset += count }
    }

    fun readInt64Le(): Long = readLe(8)

    fun readCompactSize(): Long = when (val first = readByte()) {
        0xFD -> readLe(2)
        0xFE -> readLe(4)
        0xFF -> readLe(8).also { check(it >= 0) { "CompactSize out of range" } }
        else -> first.toLong()
    }

    /** A CompactSize length that must fit in what is left of the data. */
    fun readLength(): Int {
        val value = readCompactSize()
        check(value <= remaining) { "Length $value exceeds the remaining $remaining bytes" }
        return value.toInt()
    }

    fun requireEnd() {
        check(remaining == 0) { "Unexpected $remaining trailing bytes" }
    }

    private fun readLe(size: Int): Long =
        readBytes(size).foldIndexed(0L) { index, value, byte -> value or ((byte.toLong() and 0xFF) shl (index * 8)) }
}
