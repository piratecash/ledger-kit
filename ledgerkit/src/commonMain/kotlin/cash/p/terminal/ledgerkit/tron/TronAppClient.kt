package cash.p.terminal.ledgerkit.tron

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.apdu.Apdu
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.apdu.appError
import cash.p.terminal.ledgerkit.apdu.readKeyAndAddress
import cash.p.terminal.ledgerkit.apdu.requireAnswerSize
import cash.p.terminal.ledgerkit.apdu.send
import cash.p.terminal.ledgerkit.apdu.sendChunks
import cash.p.terminal.ledgerkit.transport.LedgerTransport

/** [address] is the Base58Check `T…` address the app reports. */
public class TronAddress(public val publicKey: ByteArray, public val address: String)

/**
 * The Tron app (CLA E0). Commands and status words follow LedgerHQ/app-tron `docs/APDU.md`; transaction chunking
 * follows ledgerjs `hw-app-trx`.
 */
public class TronAppClient(private val transport: LedgerTransport) {

    /** [display] asks the user to confirm the address. */
    public suspend fun getAddress(path: DerivationPath, display: Boolean = false): TronAddress {
        val answer = transport.send(Apdu(CLA, GET_ADDRESS, if (display) 1 else 0, 0, path.toApduBytes()), ::tronAppError)
        val (publicKey, address) = readKeyAndAddress(answer, ADDRESS)
        return TronAddress(publicKey, address)
    }

    /**
     * Signs a `Transaction.raw` protobuf ([rawData]) and returns `r || s || v`, `v` being the recovery id 0 or 1.
     * TRX and TRC-20 transfers and approvals sign in clear. Memo data needs the app's "Transactions data" setting,
     * other contract calls its "Custom contracts", other contract types its blind signing, otherwise
     * [LedgerException.BlindSigningRequired]. A top-level field over 250 bytes, such as a long memo, cannot be sent:
     * [LedgerException.DataTooLarge].
     */
    public suspend fun signTransaction(path: DerivationPath, rawData: ByteArray): ByteArray {
        val chunks = chunks(path.toApduBytes(), rawData)
        val answer = transport.sendChunks(chunks, ::tronAppError) { index, chunk ->
            val p1 = when {
                chunks.size == 1 -> P1_SINGLE
                index == 0 -> P1_FIRST
                index == chunks.lastIndex -> P1_LAST
                else -> P1_MORE
            }
            Apdu(CLA, SIGN, p1, 0, chunk)
        }
        answer.requireAnswerSize(SIGNATURE_SIZE, "signature")
        if (answer.last() !in 0..1) throw LedgerException.MalformedResponse("signature recovery id ${answer.last()}")
        return answer
    }

    // The app decodes every chunk as a protobuf of its own, so no top-level field may straddle two chunks.
    private fun chunks(path: ByteArray, rawData: ByteArray): List<ByteArray> {
        require(rawData.isNotEmpty()) { "Empty transaction" }
        val chunks = mutableListOf<ByteArray>()
        var chunk = path
        var offset = 0
        while (offset < rawData.size) {
            val field = rawData.copyOfRange(offset, fieldEnd(rawData, offset))
            if (field.size > CHUNK_SIZE) throw LedgerException.DataTooLarge()
            if (chunk.size + field.size > CHUNK_SIZE) {
                chunks += chunk
                chunk = ByteArray(0)
            }
            chunk += field
            offset += field.size
        }
        return chunks + chunk
    }

    private fun fieldEnd(rawData: ByteArray, start: Int): Int {
        var offset = start
        fun readVarint(): Long {
            var value = 0L
            for (shift in 0 until Long.SIZE_BITS step VARINT_BITS) {
                require(offset < rawData.size) { "Truncated transaction" }
                val byte = rawData[offset++].toInt()
                value = value or ((byte and VARINT_VALUE).toLong() shl shift)
                if (byte and VARINT_MORE == 0) return value
            }
            throw IllegalArgumentException("Varint too long")
        }
        val wireType = (readVarint() and WIRE_TYPE_MASK).toInt()
        val value = readVarint()
        return when (wireType) {
            WIRE_VARINT -> offset
            WIRE_LENGTH_DELIMITED -> {
                require(value in 0..(rawData.size - offset).toLong()) { "Truncated transaction field" }
                offset + value.toInt()
            }
            else -> throw IllegalArgumentException("Unsupported protobuf wire type $wireType")
        }
    }

    private companion object {
        const val CLA = 0xE0
        const val GET_ADDRESS = 0x02
        const val SIGN = 0x04

        const val P1_FIRST = 0x00
        const val P1_SINGLE = 0x10
        const val P1_MORE = 0x80
        const val P1_LAST = 0x90

        // ledgerjs keeps chunks below the 255-byte APDU limit.
        const val CHUNK_SIZE = 250
        const val SIGNATURE_SIZE = 65

        const val VARINT_BITS = 7
        const val VARINT_VALUE = 0x7F
        const val VARINT_MORE = 0x80
        const val WIRE_TYPE_MASK = 0x07L
        const val WIRE_VARINT = 0
        const val WIRE_LENGTH_DELIMITED = 2

        val ADDRESS = Regex("T[1-9A-HJ-NP-Za-km-z]{33}")
    }
}

// "Transactions data", "Sign by hash" (blind signing) and "Custom contracts" settings.
private val SETTING_REQUIRED_STATUS_WORDS = setOf(0x6A8B, 0x6A8C, 0x6A8D)
private val INVALID_REQUEST_STATUS_WORDS = setOf(0x6700, 0x6800, 0x6A80, 0x6A8A, 0x6A8E, 0x6B00)

private fun tronAppError(statusWord: Int): LedgerException =
    if (statusWord in SETTING_REQUIRED_STATUS_WORDS) LedgerException.BlindSigningRequired()
    else appError(statusWord, INVALID_REQUEST_STATUS_WORDS)
