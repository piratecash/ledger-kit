package cash.p.terminal.ledgerkit

import cash.p.terminal.ledgerkit.transport.LedgerTransport
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Replays a ledgerjs `RecordStore` log (`=> apdu` / `<= response` lines), failing on any other APDU. */
class ReplayTransport(record: String) : LedgerTransport {
    private val exchanges = ArrayDeque(
        record.lines().map { it.trim() }.filter { it.isNotEmpty() }.chunked(2).map { (command, response) ->
            check(command.startsWith("=> ") && response.startsWith("<= ")) { "Malformed record: $command / $response" }
            command.removePrefix("=> ") to response.removePrefix("<= ")
        },
    )

    override suspend fun exchange(apdu: ByteArray): ByteArray {
        val (expected, response) = exchanges.removeFirstOrNull() ?: error("Unexpected APDU ${apdu.toHexString()}")
        assertEquals(expected, apdu.toHexString(), "APDU #${exchanges.size} from the end")
        return response.hexToByteArray()
    }

    override fun close() = Unit

    fun assertAllReplayed() {
        assertTrue(exchanges.isEmpty(), "${exchanges.size} recorded exchanges were not sent")
    }
}
