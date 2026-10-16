package cash.p.terminal.ledgerkit.bitcoin

import cash.p.terminal.ledgerkit.ByteReader
import cash.p.terminal.ledgerkit.compactSize

/**
 * A serialized transaction split into the pieces GET_TRUSTED_INPUT streams. Witnesses and Litecoin MWEB
 * data are dropped because the txid does not cover them. Fixed-size fields stay raw bytes.
 */
internal class SplitTransaction(
    val version: ByteArray,
    val inputs: List<Input>,
    val outputs: List<TxOutput>,
    val lockTime: ByteArray,
    /** Dash DIP-2 special transaction payload with its length; the txid covers it after the lock time. */
    val extraPayload: ByteArray,
) {
    class Input(val outpoint: ByteArray, val script: ByteArray, val sequence: ByteArray)

    companion object {
        fun parse(raw: ByteArray): SplitTransaction {
            val reader = ByteReader(raw)
            val version = reader.readBytes(4)
            var inputCount = reader.readLength()
            val flagged = inputCount == 0
            if (flagged) {
                val flag = reader.readByte()
                require(flag != 0 && flag and KNOWN_FLAGS.inv() == 0) { "Unsupported transaction serialization flag $flag" }
                inputCount = reader.readLength()
            }
            val inputs = List(inputCount) { Input(reader.readBytes(36), reader.readBytes(reader.readLength()), reader.readBytes(4)) }
            val outputs = List(reader.readLength()) { TxOutput(reader.readInt64Le(), reader.readBytes(reader.readLength())) }
            // As ledgerjs splitTransaction: whatever follows the outputs of a flagged transaction, up to its lock time.
            if (flagged) reader.readBytes(reader.remaining - LOCK_TIME_SIZE)
            val lockTime = reader.readBytes(LOCK_TIME_SIZE)
            val extraPayload = if (isDashSpecial(version)) reader.readBytes(reader.readLength()).let { compactSize(it.size) + it } else ByteArray(0)
            reader.requireEnd()
            return SplitTransaction(version, inputs, outputs, lockTime, extraPayload)
        }

        // DIP-2: version 3 or above with a non-zero type in the upper 16 bits.
        private fun isDashSpecial(version: ByteArray): Boolean {
            val number = (version[0].toInt() and 0xFF) or ((version[1].toInt() and 0xFF) shl 8)
            return number >= 3 && (version[2].toInt() != 0 || version[3].toInt() != 0)
        }

        private const val WITNESS_FLAG = 0x01
        private const val MWEB_FLAG = 0x08
        private const val KNOWN_FLAGS = WITNESS_FLAG or MWEB_FLAG
        private const val LOCK_TIME_SIZE = 4
    }
}
