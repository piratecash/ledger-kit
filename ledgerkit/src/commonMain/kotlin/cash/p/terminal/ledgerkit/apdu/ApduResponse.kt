package cash.p.terminal.ledgerkit.apdu

import cash.p.terminal.ledgerkit.readUInt16
import cash.p.terminal.ledgerkit.transport.LedgerTransport

public class ApduResponse(public val data: ByteArray, public val statusWord: Int) {
    /** Returns the data, or throws the error the status word stands for. */
    public fun requireSuccess(): ByteArray {
        if (statusWord != StatusWord.OK) throw LedgerException.fromStatusWord(statusWord)
        return data
    }

    public companion object {
        public fun parse(raw: ByteArray): ApduResponse {
            check(raw.size >= STATUS_WORD_SIZE) { "Response too short: ${raw.size}" }
            val dataSize = raw.size - STATUS_WORD_SIZE
            return ApduResponse(raw.copyOfRange(0, dataSize), raw.readUInt16(dataSize))
        }

        private const val STATUS_WORD_SIZE = 2
    }
}

public suspend fun LedgerTransport.exchange(apdu: Apdu): ApduResponse = ApduResponse.parse(exchange(apdu.toBytes()))

/** Returns the response data, or throws the [LedgerException] for a non-OK status word. */
public suspend fun LedgerTransport.send(apdu: Apdu): ByteArray = exchange(apdu).requireSuccess()
