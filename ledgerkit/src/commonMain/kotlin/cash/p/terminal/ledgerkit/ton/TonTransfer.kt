package cash.p.terminal.ledgerkit.ton

import cash.p.terminal.ledgerkit.toUInt16Bytes
import cash.p.terminal.ledgerkit.toUInt32Be
import cash.p.terminal.ledgerkit.toUInt64Be

/** A basechain (`0`) or masterchain (`-1`) account by its 32-byte hash. */
public class TonAddress(public val workchain: Int, public val hash: ByteArray) {
    init {
        require(workchain == BASECHAIN || workchain == MASTERCHAIN) { "Unsupported workchain $workchain" }
        require(hash.size == HASH_SIZE) { "Account hash must be $HASH_SIZE bytes" }
    }

    internal fun serialize(): ByteArray = byteArrayOf(workchain.toByte()) + hash

    private companion object {
        const val BASECHAIN = 0
        const val MASTERCHAIN = -1
    }
}

/** A cell by its representation hash and depth, the `hash()` and `depth()` of the cell itself. */
public class TonCellRef(public val hash: ByteArray, public val depth: Int) {
    init {
        require(hash.size == HASH_SIZE) { "Cell hash must be $HASH_SIZE bytes" }
        require(depth in 0..MAX_DEPTH) { "Cell depth out of range: $depth" }
    }

    internal fun serialize(): ByteArray = depth.toUInt16Bytes() + hash

    private companion object {
        const val MAX_DEPTH = 0xFFFF
    }
}

/**
 * A `Coins` amount (`VarUInteger 16`, up to 120 bits) as its unsigned big-endian magnitude; leading zero bytes, such
 * as the sign byte of a JVM `BigInteger.toByteArray()`, are dropped.
 */
public class TonCoins(magnitude: ByteArray) {
    public constructor(value: ULong) : this(value.toLong().toUInt64Be())

    // The app stores the bytes as given, so only the minimal form hashes like the cell the wallet builds.
    private val bytes = magnitude.dropWhile { it == 0.toByte() }.toByteArray()

    init {
        require(bytes.size <= MAX_SIZE) { "Coins amount exceeds 120 bits" }
    }

    internal fun serialize(): ByteArray = byteArrayOf(bytes.size.toByte()) + bytes

    private companion object {
        const val MAX_SIZE = 15
    }
}

/**
 * The body of the internal message. With a [hint] the app rebuilds the body from it, shows it, and refuses the request
 * ([cash.p.terminal.ledgerkit.apdu.LedgerException.InvalidRequest] `0xB010`) unless the rebuilt cell is [cell].
 * Without one the app shows only the hash and needs blind signing.
 */
public class TonBody(public val cell: TonCellRef, public val hint: TonBodyHint? = null)

public sealed class TonBodyHint {
    internal abstract val type: UInt

    internal abstract fun serialize(): ByteArray

    /**
     * A text comment, the body `0x00000000 || text` in a single cell. The app shows printable ASCII of at most 120
     * characters only; other text has to go as a body without a hint.
     */
    public class Comment(public val text: String) : TonBodyHint() {
        init {
            require(text.length <= MAX_COMMENT_LENGTH && text.all { it in ' '..'~' }) {
                "The app shows only printable ASCII comments of up to $MAX_COMMENT_LENGTH characters"
            }
        }

        override val type: UInt = 0u

        override fun serialize(): ByteArray = text.encodeToByteArray()
    }

    /**
     * A TEP-74 `transfer#0f8a7ea5` sent to the sender's jetton wallet. The app rebuilds it as `query_id:uint64
     * amount:Coins destination:MsgAddress response_destination:MsgAddress custom_payload:(Maybe ^Cell)
     * forward_ton_amount:Coins forward_payload:(Either Cell ^Cell)` with the forward payload, when present, always in a
     * reference and an absent one as a single `0` bit, so the body must be built the same way.
     */
    public class JettonTransfer(
        public val queryId: ULong,
        public val amount: TonCoins,
        public val destination: TonAddress,
        public val responseDestination: TonAddress,
        public val customPayload: TonCellRef?,
        public val forwardAmount: TonCoins,
        public val forwardPayload: TonCellRef?,
    ) : TonBodyHint() {
        override val type: UInt = 1u

        override fun serialize(): ByteArray =
            byteArrayOf(HAS_QUERY_ID) + queryId.toLong().toUInt64Be() + amount.serialize() + destination.serialize() +
                responseDestination.serialize() + customPayload.serializeMaybe() + forwardAmount.serialize() +
                forwardPayload.serializeMaybe()

        private companion object {
            const val HAS_QUERY_ID: Byte = 0x01
        }
    }

    private companion object {
        const val MAX_COMMENT_LENGTH = 120
    }
}

/**
 * An outgoing transfer from a v4R2 wallet with the default subwallet id, as its transfer cell carries it. [sendMode] is
 * the message's send mode byte; [stateInit] the state init attached to the internal message.
 */
public class TonTransfer(
    public val seqno: UInt,
    public val timeout: UInt,
    public val destination: TonAddress,
    public val value: TonCoins,
    public val bounce: Boolean,
    public val sendMode: Int,
    public val stateInit: TonCellRef? = null,
    public val body: TonBody? = null,
) {
    init {
        require(sendMode in 0..0xFF) { "Send mode must fit in a byte: $sendMode" }
    }

    // Request tag 0: the default subwallet with the wallet op byte, as v4R2 has it.
    internal fun serialize(): ByteArray =
        byteArrayOf(0) + seqno.toUInt32Be() + timeout.toUInt32Be() + value.serialize() + destination.serialize() +
            byteArrayOf(bounce.toByte(), sendMode.toByte()) + stateInit.serializeMaybe() + body?.cell.serializeMaybe() +
            body?.hint.serializeHint()
}

private const val HASH_SIZE = 32

private fun Boolean.toByte(): Byte = if (this) 1 else 0

private fun TonCellRef?.serializeMaybe(): ByteArray = this?.let { byteArrayOf(1) + it.serialize() } ?: byteArrayOf(0)

private fun TonBodyHint?.serializeHint(): ByteArray {
    if (this == null) return byteArrayOf(0)
    val data = serialize()
    return byteArrayOf(1) + type.toUInt32Be() + data.size.toUInt16Bytes() + data
}
