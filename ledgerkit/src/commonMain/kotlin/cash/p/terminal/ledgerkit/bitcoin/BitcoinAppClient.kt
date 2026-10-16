package cash.p.terminal.ledgerkit.bitcoin

import cash.p.terminal.ledgerkit.ByteReader
import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.apdu.Apdu
import cash.p.terminal.ledgerkit.apdu.ApduResponse
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.apdu.exchange
import cash.p.terminal.ledgerkit.compactSize
import cash.p.terminal.ledgerkit.toUInt32Be
import cash.p.terminal.ledgerkit.transport.LedgerTransport

/**
 * A signature the app produced for one input: a 33-byte [publicKey] with a DER signature followed
 * by its sighash byte, or a 32-byte x-only [publicKey] with a BIP-340 signature (64 bytes, 65 with
 * an explicit sighash).
 */
public class PartialSignature(public val inputIndex: Int, public val publicKey: ByteArray, public val signature: ByteArray)

/** The Bitcoin app 2.1+ ("Bitcoin", "Bitcoin Test"); protocol from app-bitcoin-new `doc/bitcoin.md`. */
public class BitcoinAppClient(private val transport: LedgerTransport) {

    public suspend fun getMasterFingerprint(): ByteArray =
        request(GET_MASTER_FINGERPRINT, ByteArray(0)).also {
            check(it.size == WalletPolicy.FINGERPRINT_SIZE) { "Unexpected fingerprint length ${it.size}" }
        }

    /** The base58 extended public key at [path]; [display] asks the user to confirm it on screen. */
    public suspend fun getExtendedPubkey(path: DerivationPath, display: Boolean = false): String =
        request(GET_EXTENDED_PUBKEY, byteArrayOf(display.toByte()) + path.toApduBytes()).decodeToString()

    public suspend fun getWalletAddress(
        policy: WalletPolicy,
        change: Boolean,
        addressIndex: Int,
        display: Boolean = false,
    ): String {
        require(addressIndex >= 0) { "Address index must be non-hardened" }
        val data = byteArrayOf(display.toByte()) + policy.id + NO_HMAC + change.toByte() +
            addressIndex.toUInt().toUInt32Be()
        return request(GET_WALLET_ADDRESS, data, policyInterpreter(policy)).decodeToString()
    }

    /** Signs every input of [psbt] that [policy] owns; the user reviews the transaction on the device. */
    public suspend fun signPsbt(psbt: Psbt, policy: WalletPolicy): List<PartialSignature> {
        // Single-key policies sign each input at most once.
        val interpreter = policyInterpreter(policy, yieldLimit = psbt.inputs.size)
        val globalCommitment = interpreter.addKnownMap(psbt.globalMap())
        val inputsRoot = interpreter.addKnownList(psbt.inputs.map { interpreter.addKnownMap(it.toMap()) })
        val outputsRoot = interpreter.addKnownList(psbt.outputs.map { interpreter.addKnownMap(it.toMap()) })
        val data = globalCommitment +
            compactSize(psbt.inputs.size) + inputsRoot +
            compactSize(psbt.outputs.size) + outputsRoot +
            policy.id + NO_HMAC
        request(SIGN_PSBT, data, interpreter)
        return interpreter.yielded.map { parseSignature(it, psbt.inputs.size) }
    }

    private suspend fun request(
        ins: Int,
        data: ByteArray,
        interpreter: ClientCommandInterpreter = ClientCommandInterpreter(),
    ): ByteArray {
        var response = transport.exchange(Apdu(CLA_BITCOIN, ins, 0, PROTOCOL_VERSION, data))
        while (response.statusWord == SW_INTERRUPTED_EXECUTION) {
            val answer = try {
                interpreter.execute(response.data)
            } catch (error: Exception) {
                abortInterruptedCommand()
                throw error
            }
            response = continueWith(answer)
        }
        return response.requireSuccess(::bitcoinAppError)
    }

    // A reconnect does not reset the app: it would consume the next operation's first command as this reply.
    // Any APDU but CONTINUE aborts the pending command (answered 6A80, ignored here).
    private suspend fun abortInterruptedCommand() {
        transport.exchange(Apdu(CLA_BITCOIN, GET_MASTER_FINGERPRINT, 0, PROTOCOL_VERSION, ByteArray(0)))
    }

    private suspend fun continueWith(answer: ByteArray): ApduResponse =
        transport.exchange(Apdu(CLA_FRAMEWORK, CONTINUE_INTERRUPTED, 0, PROTOCOL_VERSION, answer))

    // The app reads the descriptor template by hash since protocol version 1.
    private fun policyInterpreter(policy: WalletPolicy, yieldLimit: Int = 0) = ClientCommandInterpreter(yieldLimit).apply {
        addKnownPreimage(policy.serialize())
        addKnownList(policy.keys.map { it.encodeToByteArray() })
        addKnownPreimage(policy.descriptorTemplate.encodeToByteArray())
    }

    private fun parseSignature(yielded: ByteArray, inputCount: Int): PartialSignature {
        val reader = ByteReader(yielded)
        val inputIndex = reader.readCompactSize()
        check(inputIndex < inputCount) { "Signature for unknown input $inputIndex" }
        val keySize = reader.readByte()
        check(keySize == ECDSA_KEY_SIZE || keySize == X_ONLY_KEY_SIZE) { "Unsupported signature key size $keySize" }
        val publicKey = reader.readBytes(keySize)
        val signature = reader.readBytes(reader.remaining)
        if (!isValidSignature(keySize, signature)) throw LedgerException.MalformedResponse("signature for input $inputIndex")
        return PartialSignature(inputIndex.toInt(), publicKey, signature)
    }

    // Inputs are signed with the default sighash: SIGHASH_ALL for ECDSA, implicit or explicit for BIP-340.
    private fun isValidSignature(keySize: Int, signature: ByteArray): Boolean =
        if (keySize == ECDSA_KEY_SIZE) {
            isStrictDerSignature(signature, SIGHASH_ALL)
        } else {
            signature.size == SCHNORR_SIGNATURE_SIZE ||
                (signature.size == SCHNORR_SIGNATURE_SIZE + 1 && signature.last().toInt() == SIGHASH_ALL)
        }

    private fun Boolean.toByte(): Byte = if (this) 1 else 0

    private companion object {
        const val CLA_BITCOIN = 0xE1
        const val CLA_FRAMEWORK = 0xF8
        const val CONTINUE_INTERRUPTED = 0x01
        const val PROTOCOL_VERSION = 1

        const val GET_EXTENDED_PUBKEY = 0x00
        const val GET_WALLET_ADDRESS = 0x03
        const val SIGN_PSBT = 0x04
        const val GET_MASTER_FINGERPRINT = 0x05

        const val SW_INTERRUPTED_EXECUTION = 0xE000
        const val ECDSA_KEY_SIZE = 33
        const val X_ONLY_KEY_SIZE = 32
        const val SCHNORR_SIGNATURE_SIZE = 64

        // Default policies are not registered, so they have no HMAC.
        val NO_HMAC = ByteArray(32)
    }
}
