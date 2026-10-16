package cash.p.terminal.ledgerkit.bitcoin

import cash.p.terminal.ledgerkit.sha256
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// Leaves and expected proofs: LedgerHQ/app-bitcoin-new bitcoin_client_rs/src/merkle.rs `test_merkle_tree`.
class MerkleTreeTest {
    private val leaves = listOf(
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592",
        "ef537f25c895bfa782526529a9b63d97aa631564d5d789c2b765448c8635fb6c",
    ).map { it.hexToByteArray() }

    @Test
    fun proof_threeLeafTree_matchesReferenceProofs() {
        val tree = MerkleTree(leaves)
        val firstPair = sha256(byteArrayOf(0x01) + leaves[0] + leaves[1])

        assertEquals(listOf(leaves[1], leaves[2]).hex(), tree.proof(0).hex())
        assertEquals(listOf(leaves[0], leaves[2]).hex(), tree.proof(1).hex())
        assertEquals(listOf(firstPair).hex(), tree.proof(2).hex())
    }

    @Test
    fun indexOf_knownAndUnknownLeaf_indexOrMinusOne() {
        val tree = MerkleTree(leaves)

        assertEquals(2, tree.indexOf(leaves[2]))
        assertEquals(-1, tree.indexOf(ByteArray(32)))
    }

    @Test
    fun init_noLeaves_throws() {
        assertFailsWith<IllegalArgumentException> { MerkleTree(emptyList()) }
    }

    private fun List<ByteArray>.hex() = map { it.toHexString() }
}
