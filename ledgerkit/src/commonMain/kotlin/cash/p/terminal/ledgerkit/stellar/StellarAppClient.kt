package cash.p.terminal.ledgerkit.stellar

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.apdu.Apdu
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.apdu.appError
import cash.p.terminal.ledgerkit.apdu.chunkedForApdu
import cash.p.terminal.ledgerkit.apdu.requireAnswerSize
import cash.p.terminal.ledgerkit.apdu.send
import cash.p.terminal.ledgerkit.apdu.sendChunks
import cash.p.terminal.ledgerkit.transport.LedgerTransport

/**
 * The Stellar app (CLA E0). Commands and status words follow LedgerHQ/app-stellar `docs/COMMANDS.md`; chunking
 * follows ledgerjs `hw-app-str`. Keys are Ed25519, so every path index must be hardened.
 */
public class StellarAppClient(private val transport: LedgerTransport) {

    /** The raw 32-byte public key behind the `G…` account id; [display] asks the user to confirm it. */
    public suspend fun getPublicKey(path: DerivationPath, display: Boolean = false): ByteArray {
        val apdu = Apdu(CLA, GET_PUBLIC_KEY, 0, if (display) 1 else 0, path.toHardenedApduBytes())
        return transport.send(apdu, ::stellarAppError).requireAnswerSize(PUBLIC_KEY_SIZE, "public key")
    }

    /**
     * Signs a transaction by its signature base (network id hash, envelope type, transaction XDR), the bytes
     * whose SHA-256 is the transaction hash, so the app parses and shows the operations. Returns the 64-byte
     * signature of that hash. Soroban transactions need blind signing enabled in the app, otherwise
     * [LedgerException.BlindSigningRequired].
     */
    public suspend fun signTransaction(path: DerivationPath, signatureBase: ByteArray): ByteArray {
        val chunks = (path.toHardenedApduBytes() + signatureBase).chunkedForApdu()
        val answer = transport.sendChunks(chunks, ::stellarAppError) { index, chunk ->
            val p1 = if (index == 0) P1_FIRST else P1_MORE
            val p2 = if (index == chunks.lastIndex) P2_LAST else P2_MORE
            Apdu(CLA, SIGN_TX, p1, p2, chunk)
        }
        return answer.requireAnswerSize(SIGNATURE_SIZE, "signature")
    }

    private companion object {
        const val CLA = 0xE0
        const val GET_PUBLIC_KEY = 0x02
        const val SIGN_TX = 0x04

        const val P1_FIRST = 0x00
        const val P1_MORE = 0x80
        const val P2_LAST = 0x00
        const val P2_MORE = 0x80

        const val PUBLIC_KEY_SIZE = 32
        const val SIGNATURE_SIZE = 64
    }
}

private const val BLIND_SIGNING_REQUIRED = 0x6C66
private const val REQUEST_DATA_TOO_LARGE = 0xB004
private val INVALID_REQUEST_STATUS_WORDS = setOf(0x6125, 0x6A87, 0x6B00, 0xB001, 0xB005, 0xB009)

private fun stellarAppError(statusWord: Int): LedgerException = when (statusWord) {
    BLIND_SIGNING_REQUIRED -> LedgerException.BlindSigningRequired()
    REQUEST_DATA_TOO_LARGE -> LedgerException.DataTooLarge()
    else -> appError(statusWord, INVALID_REQUEST_STATUS_WORDS)
}
