package cash.p.terminal.ledgerkit.bitcoin

import cash.p.terminal.ledgerkit.ByteReader
import cash.p.terminal.ledgerkit.compactSize
import cash.p.terminal.ledgerkit.sha256

/**
 * Answers the Bitcoin app's requests for data it only holds commitments to. Adapted from app-bitcoin-new
 * `bitcoin_client_rs/src/interpreter.rs` and `bitcoin_client/ledger_bitcoin/client_command.py`.
 */
/** [yieldLimit]: how many YIELD values the running command may legitimately produce. */
internal class ClientCommandInterpreter(private val yieldLimit: Int = 0) {
    private val preimages = mutableMapOf<String, ByteArray>()
    private val trees = mutableListOf<MerkleTree>()
    private val queue = ArrayDeque<ByteArray>()
    private val yieldedValues = mutableListOf<ByteArray>()

    val yielded: List<ByteArray> get() = yieldedValues

    fun addKnownPreimage(element: ByteArray) {
        preimages[sha256(element).toHexString()] = element
    }

    /** Registers [elements] as a Merkle list and returns its root. */
    fun addKnownList(elements: List<ByteArray>): ByteArray {
        val leaves = elements.map { element ->
            val preimage = byteArrayOf(0x00) + element
            sha256(preimage).also { preimages[it.toHexString()] = preimage }
        }
        return MerkleTree(leaves).also(trees::add).root
    }

    /** Registers a key-value map and returns its commitment: count, root of sorted keys, root of their values. */
    fun addKnownMap(map: List<Pair<ByteArray, ByteArray>>): ByteArray {
        val sorted = map.sortedWith { left, right -> compareUnsigned(left.first, right.first) }
        return compactSize(sorted.size) + addKnownList(sorted.map { it.first }) + addKnownList(sorted.map { it.second })
    }

    fun execute(command: ByteArray): ByteArray {
        val request = ByteReader(command)
        return when (val code = request.readByte()) {
            YIELD -> yieldValue(request)
            GET_PREIMAGE -> preimage(request)
            GET_MERKLE_LEAF_PROOF -> leafProof(request)
            GET_MERKLE_LEAF_INDEX -> leafIndex(request)
            GET_MORE_ELEMENTS -> moreElements()
            else -> throw IllegalStateException("Unknown client command 0x${code.toString(16)}")
        }
    }

    // Bounded so a peer repeating YIELD cannot keep the signing loop running.
    private fun yieldValue(request: ByteReader): ByteArray {
        check(request.remaining > 0) { "Empty yield" }
        check(yieldedValues.size < yieldLimit) { "More yields than the request can produce" }
        yieldedValues += request.readBytes(request.remaining)
        return ByteArray(0)
    }

    private fun preimage(request: ByteReader): ByteArray {
        check(queue.isEmpty()) { "Preimage requested with undelivered elements" }
        check(request.readByte() == 0) { "Unsupported preimage request" }
        val hash = request.readBytes(HASH_SIZE).also { request.requireEnd() }
        val preimage = checkNotNull(preimages[hash.toHexString()]) { "Unknown preimage requested" }
        val length = compactSize(preimage.size)
        val payloadSize = minOf(preimage.size, MAX_RESPONSE - length.size - 1)
        preimage.copyOfRange(payloadSize, preimage.size).forEach { queue.addLast(byteArrayOf(it)) }
        return length + payloadSize.toByte() + preimage.copyOfRange(0, payloadSize)
    }

    private fun leafProof(request: ByteReader): ByteArray {
        check(queue.isEmpty()) { "Merkle proof requested with undelivered elements" }
        val tree = treeWithRoot(request.readBytes(HASH_SIZE))
        val size = request.readCompactSize()
        val index = request.readCompactSize().also { request.requireEnd() }
        check(size == tree.size.toLong() && index < size) { "Merkle proof request for a different tree" }
        val proof = tree.proof(index.toInt())
        proof.drop(PROOF_HASHES_PER_RESPONSE).forEach(queue::addLast)
        val inResponse = proof.take(PROOF_HASHES_PER_RESPONSE)
        return tree.leaf(index.toInt()) + proof.size.toByte() + inResponse.size.toByte() +
            inResponse.fold(ByteArray(0), ByteArray::plus)
    }

    // The app probes maps for optional keys, so a missing leaf is an answer, not an error.
    private fun leafIndex(request: ByteReader): ByteArray {
        val tree = treeWithRoot(request.readBytes(HASH_SIZE))
        val index = tree.indexOf(request.readBytes(HASH_SIZE).also { request.requireEnd() })
        return if (index >= 0) byteArrayOf(FOUND) + compactSize(index) else byteArrayOf(NOT_FOUND) + compactSize(0)
    }

    private fun moreElements(): ByteArray {
        check(queue.isNotEmpty()) { "No elements left to deliver" }
        val elementSize = queue.first().size
        check(queue.all { it.size == elementSize }) { "Queued elements differ in size" }
        val count = minOf(queue.size, MAX_ELEMENTS_BYTES / elementSize)
        val elements = List(count) { queue.removeFirst() }
        return byteArrayOf(count.toByte(), elementSize.toByte()) + elements.fold(ByteArray(0), ByteArray::plus)
    }

    private fun treeWithRoot(root: ByteArray): MerkleTree =
        checkNotNull(trees.firstOrNull { it.root.contentEquals(root) }) { "Unknown Merkle root requested" }

    private companion object {
        const val YIELD = 0x10
        const val GET_PREIMAGE = 0x40
        const val GET_MERKLE_LEAF_PROOF = 0x41
        const val GET_MERKLE_LEAF_INDEX = 0x42
        const val GET_MORE_ELEMENTS = 0xA0

        const val FOUND: Byte = 0x01
        const val NOT_FOUND: Byte = 0x00
        const val HASH_SIZE = 32
        const val MAX_RESPONSE = 255
        const val MAX_ELEMENTS_BYTES = 253
        const val PROOF_HASHES_PER_RESPONSE = 6
    }
}

private fun compareUnsigned(left: ByteArray, right: ByteArray): Int {
    for (i in 0 until minOf(left.size, right.size)) {
        val diff = (left[i].toInt() and 0xFF) - (right[i].toInt() and 0xFF)
        if (diff != 0) return diff
    }
    return left.size - right.size
}
