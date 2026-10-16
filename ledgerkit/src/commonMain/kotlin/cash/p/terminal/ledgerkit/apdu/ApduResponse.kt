package cash.p.terminal.ledgerkit.apdu

import cash.p.terminal.ledgerkit.readUInt16
import cash.p.terminal.ledgerkit.transport.LedgerTransport

public class ApduResponse(public val data: ByteArray, public val statusWord: Int) {
    /** Returns the data, or throws the error [errorFor] maps the status word to. */
    public fun requireSuccess(errorFor: (Int) -> LedgerException = LedgerException::fromStatusWord): ByteArray {
        if (statusWord != StatusWord.OK) throw errorFor(statusWord)
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
public suspend fun LedgerTransport.send(
    apdu: Apdu,
    errorFor: (Int) -> LedgerException = LedgerException::fromStatusWord,
): ByteArray = exchange(apdu).requireSuccess(errorFor)
