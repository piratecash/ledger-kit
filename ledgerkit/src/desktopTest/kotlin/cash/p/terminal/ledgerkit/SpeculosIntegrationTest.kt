package cash.p.terminal.ledgerkit

import cash.p.terminal.ledgerkit.transport.SpeculosTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

/** Runs only against a live emulator: `SPECULOS_APDU_PORT=9999`. */
class SpeculosIntegrationTest {
    @Test
    fun getAppAndVersion_speculosApp_returnsNameAndVersion() = runBlocking {
        val port = System.getenv("SPECULOS_APDU_PORT")?.toIntOrNull()
        assumeTrue("SPECULOS_APDU_PORT is not set", port != null)
        val transport = SpeculosTransport.connect("127.0.0.1", requireNotNull(port))

        val app = try {
            transport.getAppAndVersion()
        } finally {
            transport.close()
        }

        assertTrue(app.name.isNotEmpty() && app.version.isNotEmpty(), "$app")
    }
}
