package cash.p.terminal.ledgerkit.apdu

import cash.p.terminal.ledgerkit.ByteReader
import cash.p.terminal.ledgerkit.transport.LedgerTransport

/** Runs [read] over a device answer; a truncated or inconsistent answer becomes [LedgerException.MalformedResponse]. */
internal inline fun <T> readAnswer(what: String, read: () -> T): T =
    try {
        read()
    } catch (_: IllegalStateException) {
        throw LedgerException.MalformedResponse(what)
    }

internal fun ByteArray.requireAnswerSize(size: Int, what: String): ByteArray {
    if (this.size != size) throw LedgerException.MalformedResponse("$what of ${this.size} bytes")
    return this
}

/**
 * Sends [chunks] in order as [apduFor] frames them and returns the answer to the last one. An answer before the
 * last chunk means the app acted on part of the payload.
 */
internal suspend fun LedgerTransport.sendChunks(
    chunks: List<ByteArray>,
    errorFor: (Int) -> LedgerException,
    apduFor: (index: Int, chunk: ByteArray) -> Apdu,
): ByteArray {
    chunks.dropLast(1).forEachIndexed { index, chunk ->
        val answer = send(apduFor(index, chunk), errorFor)
        if (answer.isNotEmpty()) throw LedgerException.MalformedResponse("answer before the last chunk")
    }
    return send(apduFor(chunks.lastIndex, chunks.last()), errorFor)
}

/** `length, uncompressed secp256k1 public key, length, ASCII address`: the address answer of the Ethereum and Tron apps. */
internal fun readKeyAndAddress(answer: ByteArray, address: Regex): Pair<ByteArray, String> = readAnswer("address") {
    val reader = ByteReader(answer)
    val publicKey = reader.readBytes(reader.readByte())
    val text = reader.readBytes(reader.readByte()).decodeToString()
    reader.requireEnd()
    check(publicKey.size == UNCOMPRESSED_KEY_SIZE && publicKey[0] == UNCOMPRESSED_KEY_PREFIX) { "public key" }
    check(address.matches(text)) { "address" }
    publicKey to text
}

private const val UNCOMPRESSED_KEY_SIZE = 65
private const val UNCOMPRESSED_KEY_PREFIX: Byte = 0x04
