package cash.p.terminal.ledgerkit

import cash.p.terminal.ledgerkit.apdu.Apdu
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.apdu.send
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

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
    fun withApp_cancelledDuringExchange_closesTransport() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin").apply { answersAppCommands = false }
        val session = LedgerSession(ledger.provider)
        val job = launch { session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) } }
        advanceUntilIdle()

        job.cancel()
        job.join()

        assertTrue(ledger.transports.single().closed)
    }

    @Test
    fun withApp_cancelledBetweenExchanges_keepsTransport() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin")
        val session = LedgerSession(ledger.provider)
        val job = launch { session.withApp("Bitcoin", "2.0.0") { awaitCancellation() } }
        advanceUntilIdle()

        job.cancel()
        job.join()

        assertFalse(ledger.transports.single().closed)
    }

    @Test
    fun withApp_blockFailsAfterWholeAnswers_keepsTransport() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin")
        val session = LedgerSession(ledger.provider)

        assertFailsWith<IllegalStateException> {
            session.withApp("Bitcoin", "2.0.0") {
                it.send(appCommand)
                error("refused, as the caller reads the answer")
            }
        }

        assertFalse(ledger.transports.single().closed)
        assertEquals("Bitcoin", session.openAppName(admit = {}))
    }

    @Test
    fun withApp_answerWithoutStatusWord_closesTransport() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin").apply { truncatesAppAnswers = true }
        val session = LedgerSession(ledger.provider)

        assertFailsWith<IllegalStateException> {
            session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) }
        }

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

    @Test
    fun withApp_heldHandleDiedSinceLastOperation_reopensAndRuns() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin")
        val session = LedgerSession(ledger.provider)
        session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) }
        ledger.transports.single().close()

        session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) }

        assertEquals(listOf("GET", "APP", "GET", "APP"), ledger.commands)
        assertEquals(2, ledger.transports.size)
    }

    @Test
    fun withApp_heldHandleStoppedAnswering_reopensAndRuns() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin")
        val session = LedgerSession(ledger.provider)
        session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) }
        ledger.transports.single().silent = true

        session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) }

        assertEquals(listOf("GET", "APP", "GET", "APP"), ledger.commands)
        assertEquals(2, ledger.transports.size)
        assertTrue(ledger.transports[0].closed)
    }

    @Test
    fun withApp_appCommandAwaitsUserLongerThanAppQueryDeadline_isNotCutOff() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin")
        val session = LedgerSession(ledger.provider)
        session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) }
        ledger.answersAppCommands = false
        val job = launch { session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) } }

        advanceTimeBy(10.minutes)

        assertTrue(job.isActive)
        assertFalse(ledger.transports.single().closed)
        job.cancel()
    }

    @Test
    fun withApp_admitRefuses_exchangesNothingAndKeepsConnection() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin")
        val session = LedgerSession(ledger.provider)
        session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) }

        assertFailsWith<IllegalStateException> {
            session.withApp("Bitcoin", "2.0.0", admit = { error("refused") }) { it.send(appCommand) }
        }

        assertEquals(listOf("GET", "APP"), ledger.commands)
        assertFalse(ledger.transports.single().closed)
    }

    @Test
    fun withApp_conditionChangesWhileQueued_admitsByConditionAtAcquisition() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin")
        val session = LedgerSession(ledger.provider)
        val firstMayFinish = CompletableDeferred<Unit>()
        var allowed = true
        launch { session.withApp("Bitcoin", "2.0.0") { firstMayFinish.await() } }
        val queued = async {
            runCatching { session.withApp("Bitcoin", "2.0.0", admit = { check(allowed) }) { it.send(appCommand) } }
        }
        advanceUntilIdle()

        allowed = false
        firstMayFinish.complete(Unit)

        assertTrue(queued.await().isFailure)
        assertEquals(listOf("GET"), ledger.commands)
    }

    @Test
    fun openAppName_otherAppOpenedAfterOperation_answersOverKeptConnectionWithoutSwitching() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin")
        val session = LedgerSession(ledger.provider)
        session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) }
        ledger.currentApp = "Ethereum"

        assertEquals("Ethereum", session.openAppName(admit = {}))
        assertEquals(listOf("GET", "APP", "GET"), ledger.commands)
        assertEquals(1, ledger.transports.size)
    }

    @Test
    fun openAppName_admitRefuses_exchangesNothing() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin")
        val session = LedgerSession(ledger.provider)
        session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) }

        assertFailsWith<IllegalStateException> { session.openAppName(admit = { error("background") }) }

        assertEquals(listOf("GET", "APP"), ledger.commands)
    }

    @Test
    fun openAppName_noConnectionHeld_returnsNullWithoutOpeningOne() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin")

        assertNull(LedgerSession(ledger.provider).openAppName(admit = {}))
        assertTrue(ledger.transports.isEmpty())
    }

    @Test
    fun openAppName_deviceLocked_returnsNullAndKeepsConnection() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin")
        val session = LedgerSession(ledger.provider)
        session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) }
        ledger.locked = true

        assertNull(session.openAppName(admit = {}))
        assertFalse(ledger.transports.single().closed)
    }

    @Test
    fun openAppName_deviceUnplugged_throwsAndDropsConnection() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin")
        val session = LedgerSession(ledger.provider)
        session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) }
        ledger.unplugged = true

        assertFailsWith<LedgerException.TransportClosed> { session.openAppName(admit = {}) }
        assertTrue(ledger.transports.single().closed)
        assertNull(session.openAppName(admit = {}))
    }

    @Test
    fun openAppName_heldHandleStoppedAnswering_returnsNullAndNextOperationReopens() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin")
        val session = LedgerSession(ledger.provider)
        session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) }
        ledger.transports.single().silent = true

        assertNull(session.openAppName(admit = {}))
        assertTrue(ledger.transports.single().closed)

        session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) }
        assertEquals(2, ledger.transports.size)
    }
}
