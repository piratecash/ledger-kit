package cash.p.terminal.ledgerkit.bitcoin

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.compactSize
import cash.p.terminal.ledgerkit.toUInt32Le
import cash.p.terminal.ledgerkit.toUInt64Le

public class TxOutput(public val amount: Long, public val script: ByteArray) {
    init {
        require(amount >= 0) { "Negative output amount" }
    }

    internal fun serialize(): ByteArray = amount.toUInt64Le() + compactSize(script.size) + script
}

/** Where a key comes from: a 33-byte key for ECDSA, a 32-byte x-only key for taproot. */
public class KeyDerivation(
    public val publicKey: ByteArray,
    public val masterFingerprint: ByteArray,
    public val path: DerivationPath,
) {
    init {
        require(masterFingerprint.size == WalletPolicy.FINGERPRINT_SIZE) { "Master fingerprint must be 4 bytes" }
    }

    internal fun origin(): ByteArray =
        path.indices.fold(masterFingerprint) { bytes, index -> bytes + index.toLong().toUInt32Le() }
}

/**
 * One input of a PSBT. Legacy and segwit v0 inputs carry [nonWitnessUtxo] (the full previous
 * transaction), segwit inputs also carry [witnessUtxo]; the input's own key goes in [bip32Derivation],
 * or in [tapBip32Derivation] for a taproot key-path spend.
 */
public class PsbtInput(
    public val previousTxid: ByteArray,
    public val outputIndex: Long,
    public val sequence: Long,
    public val nonWitnessUtxo: ByteArray? = null,
    public val witnessUtxo: TxOutput? = null,
    public val redeemScript: ByteArray? = null,
    public val bip32Derivation: KeyDerivation? = null,
    public val tapBip32Derivation: KeyDerivation? = null,
) {
    init {
        require(previousTxid.size == HASH_SIZE) { "Previous txid must be 32 bytes in transaction byte order" }
    }

    internal fun toMap(): List<Pair<ByteArray, ByteArray>> = buildList {
        add(key(IN_PREVIOUS_TXID) to previousTxid)
        add(key(IN_OUTPUT_INDEX) to outputIndex.toUInt32Le())
        add(key(IN_SEQUENCE) to sequence.toUInt32Le())
        nonWitnessUtxo?.let { add(key(IN_NON_WITNESS_UTXO) to it) }
        witnessUtxo?.let { add(key(IN_WITNESS_UTXO) to it.serialize()) }
        redeemScript?.let { add(key(IN_REDEEM_SCRIPT) to it) }
        bip32Derivation?.let { addAll(bip32Entries(IN_BIP32_DERIVATION, it)) }
        tapBip32Derivation?.let { addAll(tapEntries(IN_TAP_BIP32_DERIVATION, IN_TAP_INTERNAL_KEY, it)) }
    }
}

/** One output; [bip32Derivation] / [tapBip32Derivation] mark it as change of the signing account. */
public class PsbtOutput(
    public val amount: Long,
    public val script: ByteArray,
    public val redeemScript: ByteArray? = null,
    public val bip32Derivation: KeyDerivation? = null,
    public val tapBip32Derivation: KeyDerivation? = null,
) {
    init {
        require(amount >= 0) { "Negative output amount" }
    }

    internal fun toMap(): List<Pair<ByteArray, ByteArray>> = buildList {
        add(key(OUT_AMOUNT) to amount.toUInt64Le())
        add(key(OUT_SCRIPT) to script)
        redeemScript?.let { add(key(OUT_REDEEM_SCRIPT) to it) }
        bip32Derivation?.let { addAll(bip32Entries(OUT_BIP32_DERIVATION, it)) }
        tapBip32Derivation?.let { addAll(tapEntries(OUT_TAP_BIP32_DERIVATION, OUT_TAP_INTERNAL_KEY, it)) }
    }
}

/** A PSBT version 2 (BIP-370), limited to what the app's default single-key policies need. */
public class Psbt(
    public val txVersion: Int,
    public val lockTime: Long,
    public val inputs: List<PsbtInput>,
    public val outputs: List<PsbtOutput>,
) {
    init {
        require(inputs.isNotEmpty() && outputs.isNotEmpty()) { "A PSBT needs inputs and outputs" }
    }

    internal fun globalMap(): List<Pair<ByteArray, ByteArray>> = listOf(
        key(GLOBAL_TX_VERSION) to txVersion.toUInt().toLong().toUInt32Le(),
        key(GLOBAL_FALLBACK_LOCKTIME) to lockTime.toUInt32Le(),
        key(GLOBAL_INPUT_COUNT) to compactSize(inputs.size),
        key(GLOBAL_OUTPUT_COUNT) to compactSize(outputs.size),
        key(GLOBAL_VERSION) to PSBT_VERSION_2.toUInt32Le(),
    )
}

private fun key(type: Int, keyData: ByteArray = ByteArray(0)): ByteArray = byteArrayOf(type.toByte()) + keyData

private fun bip32Entries(type: Int, derivation: KeyDerivation): List<Pair<ByteArray, ByteArray>> {
    require(derivation.publicKey.size == COMPRESSED_KEY_SIZE) { "A BIP-32 derivation needs a 33-byte key" }
    return listOf(key(type, derivation.publicKey) to derivation.origin())
}

// A key-path spend of tr(@0/**): no leaf hashes, and the derived key is the internal key.
private fun tapEntries(derivationType: Int, internalKeyType: Int, derivation: KeyDerivation): List<Pair<ByteArray, ByteArray>> {
    require(derivation.publicKey.size == X_ONLY_KEY_SIZE) { "A taproot derivation needs a 32-byte x-only key" }
    return listOf(
        key(derivationType, derivation.publicKey) to compactSize(0) + derivation.origin(),
        key(internalKeyType) to derivation.publicKey,
    )
}

private const val HASH_SIZE = 32
private const val COMPRESSED_KEY_SIZE = 33
private const val X_ONLY_KEY_SIZE = 32
private const val PSBT_VERSION_2 = 2L

private const val GLOBAL_TX_VERSION = 0x02
private const val GLOBAL_FALLBACK_LOCKTIME = 0x03
private const val GLOBAL_INPUT_COUNT = 0x04
private const val GLOBAL_OUTPUT_COUNT = 0x05
private const val GLOBAL_VERSION = 0xFB

private const val IN_NON_WITNESS_UTXO = 0x00
private const val IN_WITNESS_UTXO = 0x01
private const val IN_REDEEM_SCRIPT = 0x04
private const val IN_BIP32_DERIVATION = 0x06
private const val IN_PREVIOUS_TXID = 0x0E
private const val IN_OUTPUT_INDEX = 0x0F
private const val IN_SEQUENCE = 0x10
private const val IN_TAP_BIP32_DERIVATION = 0x16
private const val IN_TAP_INTERNAL_KEY = 0x17

private const val OUT_REDEEM_SCRIPT = 0x00
private const val OUT_BIP32_DERIVATION = 0x02
private const val OUT_AMOUNT = 0x03
private const val OUT_SCRIPT = 0x04
private const val OUT_TAP_INTERNAL_KEY = 0x05
private const val OUT_TAP_BIP32_DERIVATION = 0x07
