package cash.p.terminal.ledgerkit.ton

import cash.p.terminal.ledgerkit.ByteReader
import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.apdu.Apdu
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.apdu.appError
import cash.p.terminal.ledgerkit.apdu.chunkedForApdu
import cash.p.terminal.ledgerkit.apdu.readAnswer
import cash.p.terminal.ledgerkit.apdu.requireAnswerSize
import cash.p.terminal.ledgerkit.apdu.send
import cash.p.terminal.ledgerkit.apdu.sendChunks
import cash.p.terminal.ledgerkit.transport.LedgerTransport

/** [hash] is the hash of the wallet transfer cell the app built and signed: the signed data itself. */
public class TonSignature(public val signature: ByteArray, public val hash: ByteArray)

/**
 * The TON app (CLA E0). Commands, the request layout and status words follow LedgerHQ/app-ton `doc/COMMANDS.md` and
 * `doc/TRANSACTION.md`; field encoding follows ton-community/ton-ledger-ts. Keys are Ed25519, so every path index
 * must be hardened (`m/44'/607'/0'/0'/0'/0'` for the first account).
 */
public class TonAppClient(private val transport: LedgerTransport) {

    /** The raw 32-byte public key; [display] asks the user to confirm its mainnet basechain v4R2 address. */
    public suspend fun getPublicKey(path: DerivationPath, display: Boolean = false): ByteArray {
        val apdu = Apdu(CLA, GET_PUBLIC_KEY, if (display) 1 else 0, 0, path.toHardenedApduBytes())
        return transport.send(apdu, ::tonAppError).requireAnswerSize(PUBLIC_KEY_SIZE, "public key")
    }

    /**
     * Signs [transfer]. The app builds the wallet transfer cell from it, so the caller must check that the returned
     * hash equals the hash of the transfer cell it sends: only then does the signature belong to that message.
     * A body without a hint needs blind signing, otherwise [LedgerException.BlindSigningRequired].
     */
    public suspend fun signTransaction(path: DerivationPath, transfer: TonTransfer): TonSignature {
        val chunks = listOf(path.toHardenedApduBytes()) + transfer.serialize().chunkedForApdu()
        val answer = transport.sendChunks(chunks, ::tonAppError) { index, chunk ->
            val p2 = when (index) {
                0 -> P2_FIRST or P2_MORE
                chunks.lastIndex -> 0
                else -> P2_MORE
            }
            Apdu(CLA, SIGN_TX, 0, p2, chunk)
        }
        return readAnswer("signature") {
            val reader = ByteReader(answer)
            val signature = reader.readBytes(reader.readByte().also { check(it == SIGNATURE_SIZE) })
            val hash = reader.readBytes(reader.readByte().also { check(it == HASH_SIZE) })
            reader.requireEnd()
            TonSignature(signature, hash)
        }
    }

    private companion object {
        const val CLA = 0xE0
        const val GET_PUBLIC_KEY = 0x05
        const val SIGN_TX = 0x06

        const val P2_FIRST = 0x01
        const val P2_MORE = 0x02

        const val PUBLIC_KEY_SIZE = 32
        const val SIGNATURE_SIZE = 64
        const val HASH_SIZE = 32
    }
}

private const val BLIND_SIGNING_DISABLED = 0xBD00
private val DATA_TOO_LARGE_STATUS_WORDS = setOf(0xB004, 0xB00B)
private val INVALID_REQUEST_STATUS_WORDS =
    setOf(0x6A86, 0x6A87, 0xB000, 0xB002, 0xB003, 0xB005, 0xB007, 0xB008, 0xB010, 0xB011, 0xB0BD)

private fun tonAppError(statusWord: Int): LedgerException = when (statusWord) {
    BLIND_SIGNING_DISABLED -> LedgerException.BlindSigningRequired()
    in DATA_TOO_LARGE_STATUS_WORDS -> LedgerException.DataTooLarge()
    else -> appError(statusWord, INVALID_REQUEST_STATUS_WORDS)
}
