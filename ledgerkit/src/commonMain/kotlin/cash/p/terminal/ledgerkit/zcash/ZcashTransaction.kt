package cash.p.terminal.ledgerkit.zcash

import cash.p.terminal.ledgerkit.DerivationPath

/**
 * A proved PCZT as the Zcash app takes it (app-zcash `docs/PCZT_APDU.md`): the global fields, the transparent
 * inputs and outputs, and the Orchard and Ironwood bundles. A V6 transaction without [ironwood] still sends an
 * empty Ironwood bundle, which is where its review runs.
 */
public class ZcashTransaction(
    public val txVersion: Int,
    public val versionGroupId: Long,
    public val consensusBranchId: Long,
    public val fallbackLockTime: Long?,
    public val expiryHeight: Long,
    public val coinType: Long,
    public val txModifiable: Int,
    public val transparentInputs: List<ZcashTransparentInput>,
    public val transparentOutputs: List<ZcashTransparentOutput>,
    public val orchard: ZcashActionBundle?,
    public val ironwood: ZcashActionBundle?,
) {
    init {
        require(txVersion == V5 || txVersion == V6) { "Unsupported transaction version $txVersion" }
        require(ironwood == null || txVersion == V6) { "An Ironwood bundle needs a V6 transaction" }
        require(txModifiable in 0..0xFF) { "tx_modifiable is a byte" }
    }

    internal val isV6: Boolean get() = txVersion == V6

    internal companion object {
        const val V5 = 5
        const val V6 = 6
    }
}

/** The key behind a transparent script: compressed [pubkey], ZIP-32 seed fingerprint and full BIP-44 path. */
public class ZcashKeyOrigin(
    public val pubkey: ByteArray,
    public val seedFingerprint: ByteArray,
    public val path: DerivationPath,
) {
    init {
        require(pubkey.size == COMPRESSED_KEY_SIZE) { "Compressed public key expected" }
        require(seedFingerprint.size == FIELD_SIZE) { "Seed fingerprint must be $FIELD_SIZE bytes" }
    }
}

public class ZcashTransparentInput(
    public val prevoutTxid: ByteArray,
    public val prevoutIndex: Long,
    public val sequence: Long?,
    public val value: Long,
    public val scriptPubkey: ByteArray,
    public val sighashType: Int,
    public val derivation: ZcashKeyOrigin,
) {
    init {
        require(prevoutTxid.size == FIELD_SIZE) { "Txid must be $FIELD_SIZE bytes" }
        require(value >= 0) { "Negative input value" }
        require(sighashType in 0..0xFF) { "Sighash type is a byte" }
    }
}

public class ZcashTransparentOutput(
    public val value: Long,
    public val scriptPubkey: ByteArray,
    public val derivation: ZcashKeyOrigin?,
) {
    init {
        require(value >= 0) { "Negative output value" }
    }
}

/**
 * An Orchard or Ironwood bundle. [valueBalance] is the signed `value_sum`; [signIndices] are the actions the
 * device signs: real spends, the host has already signed the dummy ones.
 */
public class ZcashActionBundle(
    public val actions: List<ZcashAction>,
    public val flags: Int,
    public val valueBalance: Long,
    public val anchor: ByteArray,
    public val signIndices: List<Int>,
) {
    init {
        require(flags in 0..0xFF) { "Flags are a byte" }
        require(anchor.size == FIELD_SIZE) { "Anchor must be $FIELD_SIZE bytes" }
        require(signIndices.all { it in actions.indices } && signIndices.toSet().size == signIndices.size) {
            "Sign indices must be distinct action indices"
        }
    }
}

/** One action with the spend and output fields the app checks; [path] is the ZIP-32 account path `32'/133'/n'`. */
public class ZcashAction(
    public val cvNet: ByteArray,
    public val nullifier: ByteArray,
    public val rk: ByteArray,
    public val spendRecipient: ByteArray,
    public val spendValue: Long,
    public val spendRho: ByteArray,
    public val spendRseed: ByteArray,
    public val alpha: ByteArray,
    public val seedFingerprint: ByteArray,
    public val path: DerivationPath,
    public val cmx: ByteArray,
    public val ephemeralKey: ByteArray,
    public val encCiphertext: ByteArray,
    public val outCiphertext: ByteArray,
    public val recipient: ByteArray,
    public val value: Long,
    public val rseed: ByteArray,
    public val rcv: ByteArray,
) {
    init {
        val fields = listOf(cvNet, nullifier, rk, spendRho, spendRseed, alpha, seedFingerprint, cmx, ephemeralKey, rseed, rcv)
        require(fields.all { it.size == FIELD_SIZE }) { "Action fields must be $FIELD_SIZE bytes" }
        require(spendRecipient.size == ADDRESS_SIZE && recipient.size == ADDRESS_SIZE) { "Raw address must be $ADDRESS_SIZE bytes" }
        require(spendValue >= 0 && value >= 0) { "Negative note value" }
    }
}

/**
 * What the device signed, by index: transparent signatures are DER followed by the `SIGHASH_ALL` byte, Orchard
 * and Ironwood ones are 64-byte spend authorization signatures.
 */
public class ZcashSignatures(
    public val transparent: Map<Int, ByteArray>,
    public val orchard: Map<Int, ByteArray>,
    public val ironwood: Map<Int, ByteArray>,
)

private const val FIELD_SIZE = 32
private const val ADDRESS_SIZE = 43
private const val COMPRESSED_KEY_SIZE = 33
