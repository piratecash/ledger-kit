package cash.p.terminal.ledgerkit.bitcoin

import cash.p.terminal.ledgerkit.compactSize
import cash.p.terminal.ledgerkit.sha256
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// Request and response layouts: LedgerHQ/app-bitcoin-new doc/bitcoin.md, "Client commands".
class ClientCommandInterpreterTest {
    @Test
    fun execute_getPreimageLongerThanOneResponse_sendsHeadThenRestThroughGetMoreElements() {
        val preimage = ByteArray(300) { it.toByte() }
        val interpreter = ClientCommandInterpreter().apply { addKnownPreimage(preimage) }

        val first = interpreter.execute(byteArrayOf(0x40, 0x00) + sha256(preimage))
        val rest = interpreter.execute(byteArrayOf(0xA0.toByte()))

        assertEquals("fd2c01fb", first.copyOfRange(0, 4).toHexString())
        assertContentEquals(preimage.copyOfRange(0, 251), first.copyOfRange(4, first.size))
        assertEquals("3101", rest.copyOfRange(0, 2).toHexString())
        assertContentEquals(preimage.copyOfRange(251, 300), rest.copyOfRange(2, rest.size))
    }

    @Test
    fun execute_getMerkleLeafProofDeeperThanSixLevels_queuesTheRemainingHashes() {
        val interpreter = ClientCommandInterpreter()
        val elements = List(128) { byteArrayOf(it.toByte()) }
        val root = interpreter.addKnownList(elements)
        val tree = MerkleTree(elements.map(::merkleLeafHash))

        val response = interpreter.execute(byteArrayOf(0x41) + root + compactSize(128) + compactSize(5))
        val more = interpreter.execute(byteArrayOf(0xA0.toByte()))

        val proof = tree.proof(5)
        assertEquals(merkleLeafHash(elements[5]).toHexString() + "0706", response.copyOfRange(0, 34).toHexString())
        assertContentEquals(proof.take(6).reduce(ByteArray::plus), response.copyOfRange(34, response.size))
        assertContentEquals(byteArrayOf(1, 32) + proof[6], more)
    }

    @Test
    fun execute_getMerkleLeafIndex_returnsFoundFlagAndIndex() {
        val interpreter = ClientCommandInterpreter()
        val elements = List(3) { byteArrayOf(it.toByte()) }
        val root = interpreter.addKnownList(elements)

        val response = interpreter.execute(byteArrayOf(0x42) + root + merkleLeafHash(elements[2]))

        assertEquals("0102", response.toHexString())
    }

    // The app probes maps for optional keys; bitcoin_client/ledger_bitcoin/client_command.py answers "not found".
    @Test
    fun execute_getMerkleLeafIndexOfMissingLeaf_returnsNotFound() {
        val interpreter = ClientCommandInterpreter()
        val root = interpreter.addKnownList(List(3) { byteArrayOf(it.toByte()) })

        val response = interpreter.execute(byteArrayOf(0x42) + root + merkleLeafHash(byteArrayOf(9)))

        assertEquals("0000", response.toHexString())
    }

    @Test
    fun execute_getPreimageOfLeaf_returnsPrefixedElement() {
        val interpreter = ClientCommandInterpreter()
        interpreter.addKnownList(listOf("abcd".hexToByteArray()))

        val response = interpreter.execute(byteArrayOf(0x40, 0x00) + merkleLeafHash("abcd".hexToByteArray()))

        assertEquals("030300abcd", response.toHexString())
    }

    @Test
    fun execute_yield_collectsValueAndAnswersEmpty() {
        val interpreter = ClientCommandInterpreter(yieldLimit = 1)

        val response = interpreter.execute("10aabb".hexToByteArray())

        assertEquals(0, response.size)
        assertEquals(listOf("aabb"), interpreter.yielded.map { it.toHexString() })
    }

    @Test
    fun execute_emptyYield_throws() {
        assertFailsWith<IllegalStateException> { ClientCommandInterpreter(yieldLimit = 1).execute(byteArrayOf(0x10)) }
    }

    @Test
    fun execute_yieldBeyondLimit_throws() {
        val interpreter = ClientCommandInterpreter(yieldLimit = 1).apply { execute("10aa".hexToByteArray()) }

        assertFailsWith<IllegalStateException> { interpreter.execute("10bb".hexToByteArray()) }
    }

    @Test
    fun execute_getPreimageWithUndeliveredElements_throws() {
        val preimage = ByteArray(300)
        val interpreter = ClientCommandInterpreter().apply { addKnownPreimage(preimage) }
        interpreter.execute(byteArrayOf(0x40, 0x00) + sha256(preimage))

        assertFailsWith<IllegalStateException> { interpreter.execute(byteArrayOf(0x40, 0x00) + sha256(preimage)) }
    }

    @Test
    fun execute_unknownPreimage_throws() {
        assertFailsWith<IllegalStateException> {
            ClientCommandInterpreter().execute(byteArrayOf(0x40, 0x00) + ByteArray(32))
        }
    }

    @Test
    fun execute_proofForWrongTreeSize_throws() {
        val interpreter = ClientCommandInterpreter()
        val root = interpreter.addKnownList(List(3) { byteArrayOf(it.toByte()) })

        assertFailsWith<IllegalStateException> {
            interpreter.execute(byteArrayOf(0x41) + root + compactSize(4) + compactSize(0))
        }
    }

    @Test
    fun execute_unknownCommand_throws() {
        assertFailsWith<IllegalStateException> { ClientCommandInterpreter().execute(byteArrayOf(0x55)) }
    }

    @Test
    fun addKnownMap_unsortedKeys_commitsToKeysInUnsignedByteOrder() {
        val low = "02".hexToByteArray() to "aa".hexToByteArray()
        val high = "fb".hexToByteArray() to "bb".hexToByteArray()

        val commitment = ClientCommandInterpreter().addKnownMap(listOf(high, low))

        val expected = compactSize(2) + merkleRootOf(listOf(low.first, high.first)) + merkleRootOf(listOf(low.second, high.second))
        assertContentEquals(expected, commitment)
    }
}
