package cash.p.terminal.ledgerkit.thorchain

import cash.p.terminal.ledgerkit.ByteReader
import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.apdu.Apdu
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.apdu.appError
import cash.p.terminal.ledgerkit.apdu.readAnswer
import cash.p.terminal.ledgerkit.apdu.send
import cash.p.terminal.ledgerkit.apdu.sendChunks
import cash.p.terminal.ledgerkit.compactLowSSignature
import cash.p.terminal.ledgerkit.toUInt32Le
import cash.p.terminal.ledgerkit.transport.LedgerTransport

/** [publicKey] is the compressed secp256k1 key, [address] the `thor1…` bech32 address the app reports. */
public class ThorchainAddress(public val publicKey: ByteArray, public val address: String)

/**
 * The THORChain app (CLA 55, Zondax). Commands and status words follow LedgerHQ/app-thorchain `docs/APDUSPEC.md`;
 * path encoding and chunking follow thorchain/ledger-thorchain-js. Paths are `44'/931'/account'/0/index`, indices up
 * to 100 unless the app's expert mode is on.
 */
public class ThorchainAppClient(private val transport: LedgerTransport) {

    /** [display] asks the user to confirm the address. */
    public suspend fun getAddress(path: DerivationPath, display: Boolean = false): ThorchainAddress {
        val data = byteArrayOf(HRP.length.toByte()) + HRP.encodeToByteArray() + path.toLittleEndianBytes()
        val answer = transport.send(Apdu(CLA, GET_ADDR_SECP256K1, if (display) 1 else 0, 0, data), ::thorchainAppError)
        return readAnswer("address") {
            val reader = ByteReader(answer)
            val publicKey = reader.readBytes(COMPRESSED_KEY_SIZE)
            val address = reader.readBytes(reader.remaining).decodeToString()
            check(publicKey[0] == EVEN_KEY_PREFIX || publicKey[0] == ODD_KEY_PREFIX) { "public key" }
            check(ADDRESS.matches(address)) { "address" }
            ThorchainAddress(publicKey, address)
        }
    }

    /**
     * Signs a legacy amino JSON sign doc ([signDoc], the exact bytes: keys sorted, no whitespace), which the app parses
     * and shows. Building it is the caller's job; thornode accepts `SIGN_MODE_LEGACY_AMINO_JSON` with the amino names
     * `thorchain/MsgSend` and `thorchain/MsgDeposit`.
     *
     * Returns the 64-byte `r || s` Cosmos SDK expects, converted from the app's DER signature over the SHA-256 of the
     * doc: each strict DER integer stripped of its sign byte and left-padded to 32 bytes. The app signs with a low `s`;
     * an answer that is not strict DER or has a high `s` is a [LedgerException.MalformedResponse].
     */
    public suspend fun signTransaction(path: DerivationPath, signDoc: ByteArray): ByteArray {
        require(signDoc.isNotEmpty()) { "Empty sign doc" }
        val chunks = listOf(path.toLittleEndianBytes()) + signDoc.asList().chunked(CHUNK_SIZE).map { it.toByteArray() }
        val answer = transport.sendChunks(chunks, ::thorchainAppError) { index, chunk ->
            val p1 = when (index) {
                0 -> P1_INIT
                chunks.lastIndex -> P1_LAST
                else -> P1_ADD
            }
            Apdu(CLA, SIGN_SECP256K1, p1, P2_JSON, chunk)
        }
        return compactLowSSignature(answer) ?: throw LedgerException.MalformedResponse("signature")
    }

    private fun DerivationPath.toLittleEndianBytes(): ByteArray {
        require(indices.size == PATH_DEPTH) { "THORChain paths have $PATH_DEPTH levels: $this" }
        return indices.fold(ByteArray(0)) { bytes, index -> bytes + index.toLong().toUInt32Le() }
    }

    private companion object {
        const val CLA = 0x55
        const val SIGN_SECP256K1 = 0x02
        const val GET_ADDR_SECP256K1 = 0x04

        const val P1_INIT = 0
        const val P1_ADD = 1
        const val P1_LAST = 2
        const val P2_JSON = 0

        // ledger-thorchain-js sends the doc in 250-byte chunks.
        const val CHUNK_SIZE = 250
        const val PATH_DEPTH = 5
        const val HRP = "thor"
        const val COMPRESSED_KEY_SIZE = 33
        const val EVEN_KEY_PREFIX: Byte = 0x02
        const val ODD_KEY_PREFIX: Byte = 0x03

        val ADDRESS = Regex("thor1[02-9ac-hj-np-z]{38}")
    }
}

// Zondax apps answer a rejection with "command not allowed"; "conditions not satisfied" is an index above 100.
private const val REJECTED = 0x6986
private const val OUTPUT_BUFFER_TOO_SMALL = 0x6983
private val INVALID_REQUEST_STATUS_WORDS = setOf(0x6700, 0x6984, 0x6985, 0x6987, 0x6A80, 0x6B00)

private fun thorchainAppError(statusWord: Int): LedgerException = when (statusWord) {
    REJECTED -> LedgerException.UserRejected()
    OUTPUT_BUFFER_TOO_SMALL -> LedgerException.DataTooLarge()
    else -> appError(statusWord, INVALID_REQUEST_STATUS_WORDS)
}
