package cash.p.terminal.ledgerkit.zcash

import cash.p.terminal.ledgerkit.apdu.chunkedForApdu
import cash.p.terminal.ledgerkit.compactSize
import cash.p.terminal.ledgerkit.toUInt32Le
import cash.p.terminal.ledgerkit.toUInt64Le

// The packets of each PCZT command, one APDU each, as app-zcash docs/PCZT_APDU.md lays them out and
// device-sdk-ts signer-zcash pcztSerializer.ts sends them. Integers are little-endian, paths big-endian.

internal fun ZcashTransaction.headerPacket(): ByteArray =
    PCZT_MAGIC + (if (isV6) PCZT_VERSION_V6 else PCZT_VERSION_V5).toUInt32Le() +
        txVersion.toLong().toUInt32Le() + versionGroupId.toUInt32Le() + consensusBranchId.toUInt32Le() +
        optionalUInt32(fallbackLockTime) + expiryHeight.toUInt32Le() + coinType.toUInt32Le() +
        byteArrayOf(txModifiable.toByte())

internal fun transparentInputPackets(inputs: List<ZcashTransparentInput>): List<ByteArray> =
    listOf(compactSize(inputs.size)) + inputs.flatMap { input ->
        listOf(
            input.prevoutTxid + input.prevoutIndex.toUInt32Le() + optionalUInt32(input.sequence) + input.value.toUInt64Le(),
        ) + fieldPackets(input.scriptPubkey) +
            (byteArrayOf(input.sighashType.toByte()) + keyOriginPacket(input.derivation))
    }

internal fun transparentOutputPackets(outputs: List<ZcashTransparentOutput>): List<ByteArray> =
    listOf(compactSize(outputs.size)) + outputs.flatMap { output ->
        listOf(output.value.toUInt64Le()) + fieldPackets(output.scriptPubkey) + keyOriginPacket(output.derivation)
    }

/** An empty bundle is its count alone: the app finalizes on a count of 0 and reads no trailer. */
internal fun actionPackets(bundle: ZcashActionBundle?, ironwood: Boolean): List<ByteArray> {
    val actions = bundle?.actions.orEmpty()
    if (bundle == null || actions.isEmpty()) return listOf(compactSize(0))
    return listOf(compactSize(actions.size)) + actions.flatMap { actionPackets(it, ironwood) } + trailerPacket(bundle)
}

private fun actionPackets(action: ZcashAction, ironwood: Boolean): List<ByteArray> = with(action) {
    listOf(
        cvNet + nullifier + rk + spendRecipient + spendValue.toUInt64Le() + spendRho + spendRseed + alpha,
        seedFingerprint + path.toApduBytes(),
        cmx + ephemeralKey,
    ) + fieldPackets(encCiphertext) + fieldPackets(outCiphertext) +
        (recipient + value.toUInt64Le() + rseed + rcv + if (ironwood) IRONWOOD_NOTE_VERSION else ByteArray(0))
}

private fun trailerPacket(bundle: ZcashActionBundle): ByteArray {
    val negative = bundle.valueBalance < 0
    val magnitude = if (negative) -bundle.valueBalance else bundle.valueBalance
    return byteArrayOf(bundle.flags.toByte()) + magnitude.toUInt64Le() + byteArrayOf(if (negative) 1 else 0) + bundle.anchor
}

/** CompactSize count 0, or 1 followed by the key, seed fingerprint and path. */
private fun keyOriginPacket(origin: ZcashKeyOrigin?): ByteArray =
    if (origin == null) compactSize(0)
    else compactSize(1) + origin.pubkey + origin.seedFingerprint + origin.path.toApduBytes()

/** A `Vec<u8>` field: its CompactSize length and bytes, continued over as many packets as it takes. */
private fun fieldPackets(field: ByteArray): List<ByteArray> = (compactSize(field.size) + field).chunkedForApdu()

private fun optionalUInt32(value: Long?): ByteArray =
    if (value == null) byteArrayOf(0) else byteArrayOf(1) + value.toUInt32Le()

private val PCZT_MAGIC = "PCZT".encodeToByteArray()
private const val PCZT_VERSION_V5 = 1L
private const val PCZT_VERSION_V6 = 2L

// The Ironwood pool holds V3 note plaintexts only.
private val IRONWOOD_NOTE_VERSION = byteArrayOf(0x03)
