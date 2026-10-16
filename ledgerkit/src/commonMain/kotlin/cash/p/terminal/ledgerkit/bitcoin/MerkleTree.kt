package cash.p.terminal.ledgerkit.bitcoin

import cash.p.terminal.ledgerkit.sha256

/**
 * The Bitcoin app's Merkle tree over leaf hashes: the left subtree holds the largest power of two
 * strictly below the leaf count. Adapted from app-bitcoin-new `bitcoin_client_rs/src/merkle.rs`.
 */
internal class MerkleTree(private val leaves: List<ByteArray>) {
    init {
        require(leaves.isNotEmpty()) { "A Merkle tree needs at least one leaf" }
    }

    val size: Int get() = leaves.size

    val root: ByteArray = rootOf(0, leaves.size)

    fun leaf(index: Int): ByteArray = leaves[index]

    fun indexOf(leafHash: ByteArray): Int = leaves.indexOfFirst { it.contentEquals(leafHash) }

    /** Sibling hashes from the leaf up to the root. */
    fun proof(index: Int): List<ByteArray> {
        require(index in leaves.indices) { "Leaf index $index out of ${leaves.size}" }
        return proofOf(0, leaves.size, index)
    }

    private fun rootOf(start: Int, count: Int): ByteArray {
        if (count == 1) return leaves[start]
        val left = leftSize(count)
        return nodeHash(rootOf(start, left), rootOf(start + left, count - left))
    }

    private fun proofOf(start: Int, count: Int, index: Int): List<ByteArray> {
        if (count == 1) return emptyList()
        val left = leftSize(count)
        return if (index < left) {
            proofOf(start, left, index) + rootOf(start + left, count - left)
        } else {
            proofOf(start + left, count - left, index - left) + rootOf(start, left)
        }
    }

    private fun leftSize(count: Int): Int = (count - 1).takeHighestOneBit()

    private fun nodeHash(left: ByteArray, right: ByteArray): ByteArray = sha256(byteArrayOf(0x01) + left + right)
}

internal fun merkleLeafHash(element: ByteArray): ByteArray = sha256(byteArrayOf(0x00) + element)

internal fun merkleRootOf(elements: List<ByteArray>): ByteArray = MerkleTree(elements.map(::merkleLeafHash)).root
