package cash.p.terminal.ledgerkit.solana

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.apdu.Apdu
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.apdu.appError
import cash.p.terminal.ledgerkit.apdu.chunkedForApdu
import cash.p.terminal.ledgerkit.apdu.requireAnswerSize
import cash.p.terminal.ledgerkit.apdu.send
import cash.p.terminal.ledgerkit.apdu.sendChunks
import cash.p.terminal.ledgerkit.loadPkiCertificate
import cash.p.terminal.ledgerkit.transport.LedgerTransport

public class SolanaAppConfiguration(public val blindSigningEnabled: Boolean, public val version: String)

/**
 * The Solana app (CLA E0). Commands and status words follow LedgerHQ/app-solana `doc/api.md` and `src/apdu.h`;
 * chunking follows ledgerjs `hw-app-solana`. Keys are Ed25519, so every path index must be hardened.
 */
public class SolanaAppClient(private val transport: LedgerTransport) {

    public suspend fun getAppConfiguration(): SolanaAppConfiguration {
        val answer = send(GET_APP_CONFIGURATION, P1_NON_CONFIRM, ByteArray(0))
            .requireAnswerSize(CONFIGURATION_SIZE, "app configuration")
        val blindSigning = answer[0].toInt()
        if (blindSigning !in 0..1) throw LedgerException.MalformedResponse("blind signing flag $blindSigning")
        val (major, minor, patch) = answer.drop(2).map { it.toInt() and 0xFF }
        return SolanaAppConfiguration(blindSigning == 1, "$major.$minor.$patch")
    }

    /** The 32-byte public key, which is the account address; [display] asks the user to confirm it. */
    public suspend fun getPublicKey(path: DerivationPath, display: Boolean = false): ByteArray =
        send(GET_PUBKEY, if (display) P1_CONFIRM else P1_NON_CONFIRM, path.toHardenedApduBytes())
            .requireAnswerSize(PUBLIC_KEY_SIZE, "public key")

    /**
     * Signs a serialized legacy or v0 message (what a transaction's signatures cover) and returns the 64-byte
     * signature. A message the app cannot show in clear, such as a token transfer without trusted information
     * about its accounts, needs blind signing enabled in the app, otherwise [LedgerException.BlindSigningRequired].
     */
    public suspend fun signTransaction(path: DerivationPath, message: ByteArray): ByteArray =
        sign(SIGN_MESSAGE, path, message)

    /** Signs a serialized off-chain message, its `ÿsolana offchain` header included. */
    public suspend fun signOffchainMessage(path: DerivationPath, message: ByteArray): ByteArray =
        sign(SIGN_OFFCHAIN_MESSAGE, path, message)

    /** The challenge a trusted name from Ledger's trust service must be issued for. */
    public suspend fun getChallenge(): ByteArray =
        send(GET_CHALLENGE, P1_NON_CONFIRM, ByteArray(0)).requireAnswerSize(CHALLENGE_SIZE, "challenge")

    /**
     * Passes a signed trusted name TLV (a token account and its owner) through as given, so the app can show a
     * token transfer in clear. The `TRUSTED_NAME` certificate must be loaded first ([loadPkiCertificate]).
     */
    public suspend fun provideTrustedName(descriptor: ByteArray) {
        val answer = transport.send(Apdu(CLA, PROVIDE_TRUSTED_NAME, P1_NON_CONFIRM, 0, descriptor), ::solanaAppError)
        answer.requireAnswerSize(0, "trusted name answer")
    }

    /**
     * Passes a CAL token descriptor and its signature through as given, so the app can name the token and its
     * decimals. The `COIN_META` certificate must be loaded first ([loadPkiCertificate]).
     */
    public suspend fun provideTrustedDynamicDescriptor(data: ByteArray, signature: ByteArray) {
        require(signature.size <= MAX_TLV_LENGTH) { "Descriptor signature too long: ${signature.size}" }
        val payload = data + byteArrayOf(SIGNATURE_TAG, signature.size.toByte()) + signature
        send(PROVIDE_TRUSTED_DYNAMIC_DESCRIPTOR, P1_NON_CONFIRM, payload).requireAnswerSize(0, "descriptor answer")
    }

    private suspend fun sign(ins: Int, path: DerivationPath, message: ByteArray): ByteArray =
        send(ins, P1_CONFIRM, byteArrayOf(SIGNER_COUNT) + path.toHardenedApduBytes() + message)
            .requireAnswerSize(SIGNATURE_SIZE, "signature")

    private suspend fun send(ins: Int, p1: Int, payload: ByteArray): ByteArray {
        val chunks = payload.chunkedForApdu()
        return transport.sendChunks(chunks, ::solanaAppError) { index, chunk ->
            val extend = if (index > 0) P2_EXTEND else 0
            val more = if (index < chunks.lastIndex) P2_MORE else 0
            Apdu(CLA, ins, p1, extend or more, chunk)
        }
    }

    private companion object {
        const val CLA = 0xE0
        const val GET_APP_CONFIGURATION = 0x04
        const val GET_PUBKEY = 0x05
        const val SIGN_MESSAGE = 0x06
        const val SIGN_OFFCHAIN_MESSAGE = 0x07
        const val GET_CHALLENGE = 0x20
        const val PROVIDE_TRUSTED_NAME = 0x21
        const val PROVIDE_TRUSTED_DYNAMIC_DESCRIPTOR = 0x22

        const val P1_NON_CONFIRM = 0x00
        const val P1_CONFIRM = 0x01
        const val P2_EXTEND = 0x01
        const val P2_MORE = 0x02

        // The app signs with one key per request.
        const val SIGNER_COUNT: Byte = 1
        const val SIGNATURE_TAG: Byte = 0x08
        const val MAX_TLV_LENGTH = 0xFF

        const val CONFIGURATION_SIZE = 5
        const val PUBLIC_KEY_SIZE = 32
        const val SIGNATURE_SIZE = 64
        const val CHALLENGE_SIZE = 4
    }
}

private const val BLIND_SIGNING_REQUIRED = 0x6808
private const val MESSAGE_TOO_LARGE = 0x6A83

// 0x6807 is an SDK exception here, not "app not installed"; 0x6A84 is an invalid path, not "data too large".
private val INVALID_REQUEST_STATUS_WORDS = setOf(0x6807, 0x6A80, 0x6A81, 0x6A82, 0x6A84, 0x6B00, 0x6C00, 0x6CA0)

private fun solanaAppError(statusWord: Int): LedgerException = when (statusWord) {
    BLIND_SIGNING_REQUIRED -> LedgerException.BlindSigningRequired()
    MESSAGE_TOO_LARGE -> LedgerException.DataTooLarge()
    else -> appError(statusWord, INVALID_REQUEST_STATUS_WORDS)
}
