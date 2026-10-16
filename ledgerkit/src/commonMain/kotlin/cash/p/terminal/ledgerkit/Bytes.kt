package cash.p.terminal.ledgerkit

internal fun Int.toUInt16Bytes(): ByteArray = byteArrayOf((this shr 8).toByte(), toByte())

internal fun ByteArray.readUInt16(offset: Int): Int =
    (this[offset].toInt() and 0xFF shl 8) or (this[offset + 1].toInt() and 0xFF)
