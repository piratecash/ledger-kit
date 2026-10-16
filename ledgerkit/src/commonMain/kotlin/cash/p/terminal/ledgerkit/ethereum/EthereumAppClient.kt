package cash.p.terminal.ledgerkit.ethereum

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.apdu.Apdu
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.apdu.appError
import cash.p.terminal.ledgerkit.apdu.readKeyAndAddress
import cash.p.terminal.ledgerkit.apdu.requireAnswerSize
import cash.p.terminal.ledgerkit.apdu.send
import cash.p.terminal.ledgerkit.apdu.sendChunks
import cash.p.terminal.ledgerkit.toUInt32Be
import cash.p.terminal.ledgerkit.toUInt64Be
import cash.p.terminal.ledgerkit.transport.LedgerTransport

/** [address] is `0x` followed by the 40 hex digits in the EIP-55 case the app reports. */
public class EthereumAddress(public val publicKey: ByteArray, public val address: String)

/**
 * A signature exactly as the app returns it, plus its recovery id [yParity] (0 or 1), checked against [v].
 *
 * - Typed transaction: `v` is the y parity itself.
 * - Legacy EIP-155 transaction: `v` is only the low byte of `chainId * 2 + 35 + yParity` (the app cuts the
 *   chain id to its 4 high bytes), so the signed transaction must carry `chainId * 2 + 35 + yParity`.
 * - Personal message and EIP-712: `v` is `27 + yParity`.
 */
public class EthereumSignature(public val v: Int, public val r: ByteArray, public val s: ByteArray, public val yParity: Int)

/**
 * The Ethereum app 1.x (CLA E0). Commands and chunking follow LedgerHQ/app-ethereum `doc/apdu.md` and its
 * Python client; legacy transaction chunking follows ledgerjs `hw-app-eth` `safeChunkTransaction`.
 */
public class EthereumAppClient(private val transport: LedgerTransport) {

    /** [display] asks the user to confirm the address; [chainId] lets the app name the network while doing so. */
    public suspend fun getAddress(path: DerivationPath, display: Boolean = false, chainId: Long? = null): EthereumAddress {
        require(chainId == null || chainId >= 0) { "Negative chain id" }
        val data = path.toApduBytes() + (chainId?.toUInt64Be() ?: ByteArray(0))
        val answer = send(GET_ADDRESS, if (display) 1 else 0, 0, data)
        val (publicKey, address) = readKeyAndAddress(answer, ADDRESS_HEX)
        return EthereumAddress(publicKey, "0x$address")
    }

    /**
     * Signs an unsigned EIP-155 legacy transaction (`rlp([nonce, gasPrice, gas, to, value, data, chainId, 0, 0])`)
     * or an EIP-2930 / EIP-1559 one (`type || rlp([...])`). Contract data the app cannot clear-sign needs blind
     * signing enabled in the app, otherwise [LedgerException.BlindSigningRequired].
     */
    public suspend fun signTransaction(path: DerivationPath, rawTransaction: ByteArray): EthereumSignature {
        val transaction = UnsignedTransaction.parse(rawTransaction)
        val payload = path.toApduBytes() + rawTransaction
        val answer = sendChunks(SIGN_TRANSACTION, payload, transaction.chunkSize(payload.size)) { statusWord ->
            if (statusWord == INCORRECT_DATA && transaction.hasData) LedgerException.BlindSigningRequired()
            else ethereumAppError(statusWord)
        }
        return signature(answer, transaction::yParityOf)
    }

    /** `personal_sign`: the app hashes `"\u0019Ethereum Signed Message:\n" + length + message`. */
    public suspend fun signPersonalMessage(path: DerivationPath, message: ByteArray): EthereumSignature {
        val payload = path.toApduBytes() + message.size.toUInt().toUInt32Be() + message
        return signature(sendChunks(SIGN_PERSONAL_MESSAGE, payload, Apdu.MAX_DATA_LENGTH, ::ethereumAppError), ::messageYParity)
    }

    /**
     * Signs EIP-712 typed data from its JSON (`types`, `primaryType`, `domain`, `message`) by sending every struct
     * definition and value, so the app hashes and shows them itself. Without clear-signing filters the app asks
     * for blind signing (or its raw-messages setting), otherwise [LedgerException.BlindSigningRequired].
     */
    public suspend fun signTypedData(path: DerivationPath, json: String): EthereumSignature {
        val commands = Eip712Message.parse(json).commands()
        commands.forEach { command ->
            val answer = transport.send(command, ::ethereumAppError)
            if (answer.isNotEmpty()) throw LedgerException.MalformedResponse("answer to an EIP-712 struct command")
        }
        val answer = send(SIGN_EIP712, 0, P2_EIP712_FULL, path.toApduBytes()) { statusWord ->
            if (statusWord == INCORRECT_DATA) LedgerException.BlindSigningRequired() else ethereumAppError(statusWord)
        }
        return signature(answer, ::messageYParity)
    }

    /**
     * Lets the app show a token transfer in clear: [descriptor] is the CAL-signed ERC-20 descriptor
     * (`ticker length, ticker, address, decimals, chain id, signature`), passed through as is.
     */
    public suspend fun provideErc20TokenInformation(descriptor: ByteArray) {
        send(PROVIDE_ERC20_TOKEN_INFORMATION, 0, 0, descriptor).requireAnswerSize(1, "ERC-20 asset index")
    }

    private fun UnsignedTransaction.chunkSize(payloadSize: Int): Int {
        var size = Apdu.MAX_DATA_LENGTH
        // Older apps took a legacy transaction whose chunk ended right before chainId as complete and pre-EIP-155.
        if (!isTyped) while (payloadSize % size in 1..legacyTailLength) size--
        return size
    }

    private suspend fun sendChunks(
        ins: Int,
        payload: ByteArray,
        chunkSize: Int,
        errorFor: (Int) -> LedgerException,
    ): ByteArray {
        val chunks = payload.asList().chunked(chunkSize).map { it.toByteArray() }
        return transport.sendChunks(chunks, errorFor) { index, chunk ->
            Apdu(CLA, ins, if (index == 0) P1_FIRST else P1_MORE, 0, chunk)
        }
    }

    private suspend fun send(
        ins: Int,
        p1: Int,
        p2: Int,
        data: ByteArray,
        errorFor: (Int) -> LedgerException = ::ethereumAppError,
    ): ByteArray = transport.send(Apdu(CLA, ins, p1, p2, data), errorFor)

    private companion object {
        const val CLA = 0xE0
        const val GET_ADDRESS = 0x02
        const val SIGN_TRANSACTION = 0x04
        const val SIGN_PERSONAL_MESSAGE = 0x08
        const val PROVIDE_ERC20_TOKEN_INFORMATION = 0x0A
        const val SIGN_EIP712 = 0x0C
        const val P2_EIP712_FULL = 0x01

        const val P1_FIRST = 0x00
        const val P1_MORE = 0x80

        val ADDRESS_HEX = Regex("[0-9a-fA-F]{40}")
    }
}

internal const val INCORRECT_DATA = 0x6A80

// SDK status_words.h codes the app answers with; 0x6807 is its overflow exception, not "app not installed".
private val INVALID_REQUEST_STATUS_WORDS = setOf(
    0x6700, 0x6800, 0x6807, 0x6980, 0x6A00, INCORRECT_DATA, 0x6A86, 0x6A87, 0x6A88, 0x6B00,
)

internal fun ethereumAppError(statusWord: Int): LedgerException = appError(statusWord, INVALID_REQUEST_STATUS_WORDS)

private const val SIGNATURE_SIZE = 65
private const val SCALAR_SIZE = 32
private const val MESSAGE_V_BASE = 27

internal fun signature(answer: ByteArray, yParityOf: (Int) -> Int?): EthereumSignature {
    answer.requireAnswerSize(SIGNATURE_SIZE, "signature")
    val v = answer[0].toInt() and 0xFF
    val yParity = yParityOf(v) ?: throw LedgerException.MalformedResponse("signature v $v")
    return EthereumSignature(v, answer.copyOfRange(1, 1 + SCALAR_SIZE), answer.copyOfRange(1 + SCALAR_SIZE, SIGNATURE_SIZE), yParity)
}

internal fun messageYParity(v: Int): Int? = (v - MESSAGE_V_BASE).takeIf { it in 0..1 }
