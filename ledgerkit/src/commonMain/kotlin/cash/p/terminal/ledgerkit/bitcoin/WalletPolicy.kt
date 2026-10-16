package cash.p.terminal.ledgerkit.bitcoin

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.compactSize
import cash.p.terminal.ledgerkit.sha256

/** The single-key policies the Bitcoin app accepts without registration. */
public enum class BitcoinAddressType(internal val descriptorTemplate: String) {
    LEGACY("pkh(@0/**)"),
    NESTED_SEGWIT("sh(wpkh(@0/**))"),
    NATIVE_SEGWIT("wpkh(@0/**)"),
    TAPROOT("tr(@0/**)"),
}

/** A wallet policy (version 2) as the Bitcoin app 2.1+ identifies it. */
public class WalletPolicy internal constructor(
    internal val name: String,
    internal val descriptorTemplate: String,
    internal val keys: List<String>,
) {
    init {
        require(name.encodeToByteArray().size <= MAX_NAME_LENGTH) { "Wallet policy name is too long" }
    }

    internal fun serialize(): ByteArray {
        val nameBytes = name.encodeToByteArray()
        val template = descriptorTemplate.encodeToByteArray()
        return byteArrayOf(VERSION_2, nameBytes.size.toByte()) + nameBytes +
            compactSize(template.size) + sha256(template) +
            compactSize(keys.size) + merkleRootOf(keys.map { it.encodeToByteArray() })
    }

    internal val id: ByteArray get() = sha256(serialize())

    public companion object {
        /** The app's default policy for one account: `[fingerprint/accountPath]accountXpub`. */
        public fun singleSig(
            type: BitcoinAddressType,
            masterFingerprint: ByteArray,
            accountPath: DerivationPath,
            accountXpub: String,
        ): WalletPolicy {
            require(masterFingerprint.size == FINGERPRINT_SIZE) { "Master fingerprint must be 4 bytes" }
            val keyOrigin = "[${masterFingerprint.toHexString()}/$accountPath]"
            return WalletPolicy(name = "", descriptorTemplate = type.descriptorTemplate, keys = listOf(keyOrigin + accountXpub))
        }

        private const val VERSION_2: Byte = 0x02
        private const val MAX_NAME_LENGTH = 64
        internal const val FINGERPRINT_SIZE = 4
    }
}
