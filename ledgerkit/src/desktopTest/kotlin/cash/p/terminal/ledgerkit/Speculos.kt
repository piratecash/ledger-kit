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
                    isHome(screen) -> Unit
                    else -> press("right")
                }
            }
        }
        val result = try {
            block()
        } finally {
            walker.cancel()
        }
        // The Stellar app drops a request that arrives while its "signed" screen is still up.
        awaitHome()
        result
    }

    /**
     * Flips the first switch of the app's settings menu, where the tested apps keep their blind signing (Tron: its
     * transactions data) setting.
     */
    fun toggleFirstSetting() {
        dismissToHome()
        press("right")
        press("both")
        press("both")
        pressUntil("right") { "Back" in it }
        press("both")
        pressUntilHome()
    }

    /**
     * Picks entry [index] of the first setting's choice list (TON: "Allow blind sign", No / Yes), which opens on the
     * current value and returns to the settings menu on a pick.
     */
    fun chooseInFirstSetting(index: Int) {
        dismissToHome()
        press("right")
        press("both")
        press("both")
        repeat(CHOICES) { press("left") }
        repeat(index) { press("right") }
        press("both")
        // The settings menu shows every entry at once, so "Back" is reached by position.
        repeat(SETTINGS_ENTRIES - 1) { press("right") }
        press("both")
        pressUntilHome()
    }

    private suspend fun awaitHome() {
        repeat(HOME_POLLS) {
            if (isHome(currentScreen())) return
            delay(STEP)
        }
        error("The app did not return to its home screen")
    }

    /** Leaves an error or info screen and walks back to the app's home screen. */
    fun dismissToHome() {
        if (!isHome(currentScreen())) press("both")
        pressUntilHome()
    }

    private fun pressUntilHome() = pressUntil("left", ::isHome)

    private fun pressUntil(button: String, reached: (String) -> Boolean) {
        repeat(MAX_PRESSES) {
            if (reached(currentScreen())) return
            press(button)
        }
        error("The expected screen did not come up")
    }

    // Zondax apps (THORChain) show their name over a "Ready" line instead of "<app> is ready".
    private fun isHome(screen: String): Boolean = READY_SCREEN in screen || ZONDAX_READY_TEXT in screen

    fun currentScreen(): String {
        val request = HttpRequest.newBuilder(URI("$api/events?currentscreenonly=true")).GET().build()
        return http.send(request, HttpResponse.BodyHandlers.ofString()).body()
    }

    fun press(button: String) {
        val request = HttpRequest.newBuilder(URI("$api/button/$button"))
            .POST(HttpRequest.BodyPublishers.ofString("""{"action":"press-and-release"}"""))
            .header("Content-Type", "application/json")
            .build()
        http.send(request, HttpResponse.BodyHandlers.discarding())
    }

    private val STEP = 300.milliseconds
    private const val READY_SCREEN = "is ready"
    private const val ZONDAX_READY_TEXT = "\"text\": \"Ready\""
    private const val MAX_PRESSES = 10
    private const val HOME_POLLS = 30
    private const val CHOICES = 2
    private const val SETTINGS_ENTRIES = 3

    // Tron asks to "Continue" past its extra data warning; Zondax apps (THORChain) write "APPROVE".
    private val APPROVE_SCREENS = listOf(
        "Approve", "APPROVE", "Accept", "accept risk", "Sign transaction", "Sign Transaction", "Sign message", "Continue",
    )
}
