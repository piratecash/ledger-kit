package cash.p.terminal.ledgerkit

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/** Runs only against a live emulator: `SPECULOS_APDU_PORT=9999`. */
class SpeculosIntegrationTest {
    @Test
    fun getAppAndVersion_speculosApp_returnsNameAndVersion() = runBlocking {
        val app = Speculos.transport().getAppAndVersion()

        assertTrue(app.name.isNotEmpty() && app.version.isNotEmpty(), "$app")
    }
}
