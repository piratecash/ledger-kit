package cash.p.terminal.ledgerkit

import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.ledgerkit.transport.SpeculosTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Duration.Companion.milliseconds

/**
 * A running speculos emulator: `SPECULOS_APDU_PORT` for APDUs, `SPECULOS_API_URL` (default
 * `http://127.0.0.1:5000`) for its REST API. Tests skip unless the port is set and [app] is open.
 */
object Speculos {
    private val port = System.getenv("SPECULOS_APDU_PORT")?.toIntOrNull()
    private val api = System.getenv("SPECULOS_API_URL") ?: "http://127.0.0.1:5000"
    private val http = HttpClient.newHttpClient()

    // Speculos serves one APDU client at a time, so every test shares one connection.
    private val shared: LedgerTransport by lazy {
        runBlocking { SpeculosTransport.connect("127.0.0.1", requireNotNull(port)) }
    }

    fun transport(): LedgerTransport {
        assumeTrue("SPECULOS_APDU_PORT is not set", port != null)
        return shared
    }

    /** The shared transport, or a skipped test when speculos runs another app than [app]. */
    fun transport(app: String): LedgerTransport {
        val running = runBlocking { transport().getAppAndVersion().name }
        assumeTrue("speculos runs '$running', not '$app'", running == app)
        return shared
    }

    /** Runs [block] while pressing through the review screens and approving on the first approval screen. */
    suspend fun <T> approving(block: suspend () -> T): T = coroutineScope {
        val walker = launch(Dispatchers.IO) {
            while (true) {
                delay(STEP)
                val screen = currentScreen()
                when {
                    APPROVE_SCREENS.any { it in screen } && "Reject" !in screen -> press("both")
                    READY_SCREEN in screen -> Unit
                    else -> press("right")
                }
            }
        }
        try {
            block()
        } finally {
            walker.cancel()
        }
    }

    private fun currentScreen(): String {
        val request = HttpRequest.newBuilder(URI("$api/events?currentscreenonly=true")).GET().build()
        return http.send(request, HttpResponse.BodyHandlers.ofString()).body()
    }

    private fun press(button: String) {
        val request = HttpRequest.newBuilder(URI("$api/button/$button"))
            .POST(HttpRequest.BodyPublishers.ofString("""{"action":"press-and-release"}"""))
            .header("Content-Type", "application/json")
            .build()
        http.send(request, HttpResponse.BodyHandlers.discarding())
    }

    private val STEP = 300.milliseconds
    private const val READY_SCREEN = "is ready"
    private val APPROVE_SCREENS = listOf("Approve", "Accept", "Sign transaction")
}
