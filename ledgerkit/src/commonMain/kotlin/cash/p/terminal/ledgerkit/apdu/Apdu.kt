package cash.p.terminal.ledgerkit.apdu

public class Apdu(
    public val cla: Int,
    public val ins: Int,
    public val p1: Int,
    public val p2: Int,
    public val data: ByteArray = ByteArray(0),
) {
    init {
        require(listOf(cla, ins, p1, p2).all { it in 0..0xFF }) { "APDU header bytes must fit in a byte" }
        require(data.size <= MAX_DATA_LENGTH) { "APDU data too long: ${data.size}" }
    }

    public fun toBytes(): ByteArray =
        byteArrayOf(cla.toByte(), ins.toByte(), p1.toByte(), p2.toByte(), data.size.toByte()) + data

    public companion object {
        public const val MAX_DATA_LENGTH: Int = 255
    }
}

/** Splits a payload into APDU-sized pieces; an empty payload still yields one empty piece. */
public fun ByteArray.chunkedForApdu(): List<ByteArray> =
    if (isEmpty()) listOf(this) else asList().chunked(Apdu.MAX_DATA_LENGTH).map { it.toByteArray() }
