package cash.p.terminal.ledgerkit.bitcoin

import cash.p.terminal.ledgerkit.ByteReader
import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.apdu.Apdu
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.apdu.send
import cash.p.terminal.ledgerkit.compactSize
import cash.p.terminal.ledgerkit.toUInt32Be
import cash.p.terminal.ledgerkit.toUInt32Le
import cash.p.terminal.ledgerkit.transport.LedgerTransport

/** Address encoding [BtchipLegacyClient.getWalletPublicKey] returns. */
public enum class LegacyAddressFormat(internal val p2: Int) {
    LEGACY(0x00),
    P2SH_SEGWIT(0x01),
    BECH32(0x02),
    CASHADDR(0x03),
}

/** How the inputs are hashed: legacy sighash, BIP-143 segwit, or BIP-143 with Bitcoin Cash `SIGHASH_FORKID`. */
public enum class LegacySigningMode(internal val newTransactionP2: Int, internal val sighashType: Int) {
    LEGACY(0x00, SIGHASH_ALL),
    SEGWIT(0x02, SIGHASH_ALL),
    BITCOIN_CASH(0x03, SIGHASH_ALL or SIGHASH_FORKID),
    ;

    internal val usesBip143: Boolean get() = this != LEGACY
}

public class LegacyWalletPublicKey(public val publicKey: ByteArray, public val address: String, public val chainCode: ByteArray)

/**
 * One input: the full previous transaction it spends and the key path that owns it. [redeemScript]
 * is the P2SH redeem script (the `0014…` witness program for P2SH-P2WPKH).
 */
public class LegacyInput(
    public val previousTransaction: ByteArray,
    public val outputIndex: Int,
    public val sequence: Long,
    public val path: DerivationPath,
    public val redeemScript: ByteArray? = null,
)

public class LegacyTransaction(
    public val version: Int,
    public val inputs: List<LegacyInput>,
    public val outputs: List<TxOutput>,
    public val lockTime: Long,
    public val mode: LegacySigningMode,
    public val changePath: DerivationPath? = null,
) {
    init {
        require(inputs.isNotEmpty() && outputs.isNotEmpty()) { "A transaction needs inputs and outputs" }
    }
}

/**
 * The legacy btchip protocol (CLA E0) of the Litecoin, Dogecoin, Dash and Bitcoin Cash apps.
 * Flow and chunking follow ledgerjs `hw-app-btc` (createTransaction, getTrustedInput, finalizeInput).
 */
public class BtchipLegacyClient(private val transport: LedgerTransport) {

    /** The compressed public key at [path] with its address; [display] asks the user to confirm the address. */
    public suspend fun getWalletPublicKey(
        path: DerivationPath,
        format: LegacyAddressFormat = LegacyAddressFormat.LEGACY,
        display: Boolean = false,
    ): LegacyWalletPublicKey {
        val reader = ByteReader(send(GET_WALLET_PUBLIC_KEY, if (display) 1 else 0, format.p2, path.toApduBytes()))
        val publicKey = compressed(reader.readBytes(reader.readByte()))
        val address = reader.readBytes(reader.readByte()).decodeToString()
        return LegacyWalletPublicKey(publicKey, address, reader.readBytes(CHAIN_CODE_SIZE))
    }

    /** Returns one DER signature (with its sighash byte) per input, in input order. */
    public suspend fun signTransaction(transaction: LegacyTransaction): List<ByteArray> {
        val spent = transaction.inputs.map { input ->
            val previous = SplitTransaction.parse(input.previousTransaction)
            require(input.outputIndex in previous.outputs.indices) {
                "Previous transaction has no transparent output ${input.outputIndex}; MWEB outputs cannot be spent here"
            }
            SpentInput(input, getTrustedInput(previous, input.outputIndex), scriptCode(input, previous, transaction.mode))
        }
        return if (transaction.mode.usesBip143) signBip143(transaction, spent) else signLegacy(transaction, spent)
    }

    // BIP-143: every input and the outputs are hashed once, then each input is signed on its own.
    private suspend fun signBip143(transaction: LegacyTransaction, spent: List<SpentInput>): List<ByteArray> {
        hashInputs(transaction, spent.map { it to ByteArray(0) }, newTransaction = true)
        hashOutputs(transaction)
        return spent.map { input ->
            hashInputs(transaction, listOf(input to input.scriptCode), newTransaction = false)
            sign(transaction, input.input.path)
        }
    }

    // Legacy sighash: the whole transaction is re-hashed per input, only that input carrying its script.
    private suspend fun signLegacy(transaction: LegacyTransaction, spent: List<SpentInput>): List<ByteArray> =
        spent.mapIndexed { index, signed ->
            val scripts = spent.map { it to if (it === signed) it.scriptCode else ByteArray(0) }
            hashInputs(transaction, scripts, newTransaction = index == 0)
            hashOutputs(transaction)
            sign(transaction, signed.input.path)
        }

    private suspend fun getTrustedInput(previous: SplitTransaction, outputIndex: Int): ByteArray {
        send(GET_TRUSTED_INPUT, P1_FIRST, 0, outputIndex.toUInt().toUInt32Be() + previous.version + compactSize(previous.inputs.size))
        previous.inputs.forEach { input ->
            send(GET_TRUSTED_INPUT, P1_NEXT, 0, input.outpoint + compactSize(input.script.size))
            scriptBlocks(input.script, input.sequence).forEach { send(GET_TRUSTED_INPUT, P1_NEXT, 0, it) }
        }
        send(GET_TRUSTED_INPUT, P1_NEXT, 0, compactSize(previous.outputs.size))
        // Only scripts may be split across APDUs; the output header always fits in the first piece.
        previous.outputs.forEach { output ->
            output.serialize().asList().chunked(Apdu.MAX_DATA_LENGTH).forEach {
                send(GET_TRUSTED_INPUT, P1_NEXT, 0, it.toByteArray())
            }
        }
        // The app reads the extra length unhashed, then hashes that many bytes (ledgerjs getTrustedInput).
        val extra = previous.extraPayload.let { if (it.isEmpty()) it else compactSize(it.size) + it }
        val trustedInput = scriptBlocks(previous.lockTime + extra, ByteArray(0)).map { send(GET_TRUSTED_INPUT, P1_NEXT, 0, it) }.last()
        check(trustedInput.size == TRUSTED_INPUT_SIZE) { "Unexpected trusted input length ${trustedInput.size}" }
        return trustedInput
    }

    private suspend fun hashInputs(
        transaction: LegacyTransaction,
        inputs: List<Pair<SpentInput, ByteArray>>,
        newTransaction: Boolean,
    ) {
        val p2 = if (newTransaction) transaction.mode.newTransactionP2 else P2_CONTINUE
        val header = transaction.version.toUInt().toLong().toUInt32Le() + compactSize(inputs.size)
        send(HASH_INPUT_START, P1_FIRST, p2, header)
        inputs.forEach { (input, script) ->
            val trusted = byteArrayOf(TRUSTED_INPUT_FLAG, input.trustedInput.size.toByte()) + input.trustedInput
            send(HASH_INPUT_START, P1_NEXT, p2, trusted + compactSize(script.size))
            scriptBlocks(script, input.input.sequence.toUInt32Le()).forEach { send(HASH_INPUT_START, P1_NEXT, p2, it) }
        }
    }

    private suspend fun hashOutputs(transaction: LegacyTransaction) {
        transaction.changePath?.let { send(HASH_INPUT_FINALIZE_FULL, P1_CHANGE_PATH, 0, it.toApduBytes()) }
        val outputs = transaction.outputs.fold(compactSize(transaction.outputs.size)) { bytes, output -> bytes + output.serialize() }
        val blocks = outputs.asList().chunked(MAX_SCRIPT_BLOCK)
        blocks.forEachIndexed { index, block ->
            send(HASH_INPUT_FINALIZE_FULL, if (index == blocks.lastIndex) P1_LAST else P1_MORE, 0, block.toByteArray())
        }
    }

    private suspend fun sign(transaction: LegacyTransaction, path: DerivationPath): ByteArray {
        val sighashType = transaction.mode.sighashType
        val data = path.toApduBytes() + NO_PIN + transaction.lockTime.toUInt32Le().reversedArray() + sighashType.toByte()
        // The app stores the R parity in the first byte; DER needs the plain SEQUENCE tag.
        val signature = send(HASH_SIGN, 0, 0, data).copyOf().apply {
            if (isNotEmpty() && this[0].toInt() and 0xFE == DER_SEQUENCE.toInt()) this[0] = DER_SEQUENCE
        }
        if (!isStrictDerSignature(signature, sighashType)) throw LedgerException.MalformedResponse("signature")
        return signature
    }

    private suspend fun send(ins: Int, p1: Int, p2: Int, data: ByteArray): ByteArray =
        transport.send(Apdu(CLA, ins, p1, p2, data), ::bitcoinAppError)

    private class SpentInput(val input: LegacyInput, val trustedInput: ByteArray, val scriptCode: ByteArray)

    private companion object {
        const val CLA = 0xE0
        const val GET_WALLET_PUBLIC_KEY = 0x40
        const val GET_TRUSTED_INPUT = 0x42
        const val HASH_INPUT_START = 0x44
        const val HASH_SIGN = 0x48
        const val HASH_INPUT_FINALIZE_FULL = 0x4A

        const val P1_FIRST = 0x00
        const val P1_NEXT = 0x80
        const val P1_MORE = 0x00
        const val P1_LAST = 0x80
        const val P1_CHANGE_PATH = 0xFF
        const val P2_CONTINUE = 0x80

        const val TRUSTED_INPUT_FLAG: Byte = 0x01
        const val TRUSTED_INPUT_SIZE = 56
        const val CHAIN_CODE_SIZE = 32
        const val DER_SEQUENCE: Byte = 0x30
        val NO_PIN = byteArrayOf(0x00)
    }
}

/** ledgerjs `MAX_SCRIPT_BLOCK`: the app checks outputs in a 100-byte buffer. */
private const val MAX_SCRIPT_BLOCK = 50

private const val SIGHASH_FORKID = 0x40

/** Script chunks with [trailer] (the sequence) appended to the last one, or sent alone for an empty script. */
private fun scriptBlocks(script: ByteArray, trailer: ByteArray): List<ByteArray> {
    if (script.isEmpty()) return listOf(trailer)
    val blocks = script.asList().chunked(MAX_SCRIPT_BLOCK).map { it.toByteArray() }
    return blocks.dropLast(1) + (blocks.last() + trailer)
}

/** The script the signed input commits to; BIP-143 segwit replaces a P2WPKH program with its P2PKH form. */
private fun scriptCode(input: LegacyInput, previous: SplitTransaction, mode: LegacySigningMode): ByteArray {
    val script = input.redeemScript ?: previous.outputs[input.outputIndex].script
    if (mode != LegacySigningMode.SEGWIT) return script
    require(script.size == P2WPKH_PROGRAM_SIZE && script[0] == 0x00.toByte() && script[1] == 0x14.toByte()) {
        "Only P2WPKH and P2SH-P2WPKH segwit inputs are supported"
    }
    return byteArrayOf(OP_DUP, OP_HASH160, 0x14) + script.copyOfRange(2, P2WPKH_PROGRAM_SIZE) + byteArrayOf(OP_EQUALVERIFY, OP_CHECKSIG)
}

private const val P2WPKH_PROGRAM_SIZE = 22
private const val OP_DUP: Byte = 0x76
private const val OP_HASH160: Byte = 0xA9.toByte()
private const val OP_EQUALVERIFY: Byte = 0x88.toByte()
private const val OP_CHECKSIG: Byte = 0xAC.toByte()

/** The app answers with an uncompressed-length key whose first byte may already be the compressed prefix. */
private fun compressed(publicKey: ByteArray): ByteArray {
    if (publicKey.size == COMPRESSED_KEY_SIZE) return publicKey
    check(publicKey.size == UNCOMPRESSED_KEY_SIZE) { "Unexpected public key length ${publicKey.size}" }
    val prefix: Byte = if (publicKey.last().toInt() and 1 == 1) 0x03 else 0x02
    return byteArrayOf(prefix) + publicKey.copyOfRange(1, COMPRESSED_KEY_SIZE)
}

private const val COMPRESSED_KEY_SIZE = 33
private const val UNCOMPRESSED_KEY_SIZE = 65
