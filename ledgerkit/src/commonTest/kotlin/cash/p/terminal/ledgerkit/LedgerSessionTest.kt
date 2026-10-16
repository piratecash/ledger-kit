package cash.p.terminal.ledgerkit

import cash.p.terminal.ledgerkit.apdu.Apdu
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.apdu.send
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerSessionTest {
    private val appCommand = Apdu(cla = 0xE1, ins = 0x00, p1 = 0, p2 = 0)

    @Test
    fun withApp_appAlreadyOpen_runsBlockWithoutSwitching() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin")

        LedgerSession(ledger.provider).withApp("Bitcoin", "2.0.0") { it.send(appCommand) }

        assertEquals(listOf("GET", "APP"), ledger.commands)
        assertEquals(1, ledger.transports.size)
    }

    @Test
    fun withApp_onDashboard_opensAppAndReconnects() = runTest {
        val ledger = FakeLedger()

        LedgerSession(ledger.provider).withApp("Bitcoin", "2.0.0") { it.send(appCommand) }

        assertEquals(listOf("GET", "OPEN Bitcoin", "GET", "APP"), ledger.commands)
        assertEquals(2, ledger.transports.size)
        assertTrue(ledger.transports[0].closed)
    }

    @Test
    fun withApp_otherAppOpen_quitsThenOpens() = runTest {
        val ledger = FakeLedger(currentApp = "Ethereum")

        LedgerSession(ledger.provider).withApp("Bitcoin", "2.0.0") { it.send(appCommand) }

        assertEquals(listOf("GET", "QUIT", "GET", "OPEN Bitcoin", "GET", "APP"), ledger.commands)
        assertEquals(3, ledger.transports.size)
    }

    @Test
    fun withApp_appNotInstalled_throwsAppNotInstalledAndKeepsTransport() = runTest {
        val ledger = FakeLedger()

        assertFailsWith<LedgerException.AppNotInstalled> {
            LedgerSession(ledger.provider).withApp("Monero", "2.0.0") { it.send(appCommand) }
        }

        assertFalse(ledger.transports.single().closed)
    }

    @Test
    fun withApp_appOlderThanRequired_throwsAppVersionTooOld() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin")

        val error = assertFailsWith<LedgerException.AppVersionTooOld> {
            LedgerSession(ledger.provider).withApp("Bitcoin", "2.2.0") { it.send(appCommand) }
        }

        assertEquals("2.1.0", error.actual)
        assertEquals(listOf("GET"), ledger.commands)
    }

    @Test
    fun withApp_deviceSlowToReappear_retriesUntilItDoes() = runTest {
        val ledger = FakeLedger().apply { failingReopens = 3 }

        LedgerSession(ledger.provider).withApp("Bitcoin", "2.0.0") { it.send(appCommand) }

        assertEquals(listOf("GET", "OPEN Bitcoin", "GET", "APP"), ledger.commands)
    }

    @Test
    fun withApp_deviceNeverReappears_throwsTransportClosed() = runTest {
        val ledger = FakeLedger().apply { failingReopens = Int.MAX_VALUE }

        assertFailsWith<LedgerException.TransportClosed> {
            LedgerSession(ledger.provider).withApp("Bitcoin", "2.0.0") { it.send(appCommand) }
        }
    }

    @Test
    fun withApp_reopenedDeviceNeverAnswers_throwsTransportClosedAndClosesSilentHandles() = runTest {
        val ledger = FakeLedger().apply { silentReopens = true }

        assertFailsWith<LedgerException.TransportClosed> {
            LedgerSession(ledger.provider).withApp("Bitcoin", "2.0.0") { it.send(appCommand) }
        }

        assertEquals(21, ledger.transports.size)
        assertTrue(ledger.transports.all { it.closeCalls == 1 })
    }

    @Test
    fun withApp_deviceLockedAfterAppSwitch_throwsDeviceLockedWithoutRetrying() = runTest {
        val ledger = FakeLedger().apply { locksOnAppSwitch = true }

        assertFailsWith<LedgerException.DeviceLocked> {
            LedgerSession(ledger.provider).withApp("Bitcoin", "2.0.0") { it.send(appCommand) }
        }

        assertEquals(listOf("GET", "OPEN Bitcoin", "GET"), ledger.commands)
    }

    @Test
    fun withApp_closedAfterAppCheckWhileReplacementRunsOtherApp_neverRunsBlockOnReplacement() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin")
        val session = LedgerSession(ledger.provider)
        ledger.afterAppInfo = {
            ledger.afterAppInfo = {}
            session.close()
            ledger.currentApp = "Ethereum"
        }

        assertFailsWith<LedgerException.TransportClosed> {
            session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) }
        }

        assertEquals(listOf("GET"), ledger.commands)
        assertEquals(1, ledger.transports.size)
    }

    @Test
    fun withApp_cancelledDuringBlock_closesTransport() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin")
        val session = LedgerSession(ledger.provider)
        val job = launch { session.withApp("Bitcoin", "2.0.0") { awaitCancellation() } }
        advanceUntilIdle()

        job.cancel()
        job.join()

        assertTrue(ledger.transports.single().closed)
    }

    @Test
    fun withApp_consecutiveOperations_reuseTransport() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin")
        val session = LedgerSession(ledger.provider)

        repeat(2) { session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) } }

        assertEquals(1, ledger.transports.size)
    }

    @Test
    fun withApp_concurrentOperations_runOneAtATime() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin")
        val session = LedgerSession(ledger.provider)
        val firstMayFinish = CompletableDeferred<Unit>()
        var secondStarted = false
        launch { session.withApp("Bitcoin", "2.0.0") { firstMayFinish.await() } }
        launch { session.withApp("Bitcoin", "2.0.0") { secondStarted = true } }
        advanceUntilIdle()

        assertFalse(secondStarted)
        firstMayFinish.complete(Unit)
        advanceUntilIdle()

        assertTrue(secondStarted)
    }
}
