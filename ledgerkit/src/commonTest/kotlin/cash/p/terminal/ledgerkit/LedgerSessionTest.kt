package cash.p.terminal.ledgerkit

import cash.p.terminal.ledgerkit.apdu.Apdu
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.apdu.send
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.AbstractLongTimeSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

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
    fun withOpenApp_otherAppOpen_throwsWrongAppOpenWithoutSwitching() = runTest {
        val ledger = FakeLedger(currentApp = "Ethereum")
        var blockRan = false

        assertFailsWith<LedgerException.WrongAppOpen> {
            LedgerSession(ledger.provider).withOpenApp("Bitcoin", "2.0.0", admit = {}) { blockRan = true }
        }

        assertEquals(listOf("GET"), ledger.commands)
        assertFalse(blockRan)
        assertFalse(ledger.transports.single().closed)
    }

    @Test
    fun withOpenApp_onDashboard_throwsWrongAppOpenWithoutOpeningApp() = runTest {
        val ledger = FakeLedger()

        assertFailsWith<LedgerException.WrongAppOpen> {
            LedgerSession(ledger.provider).withOpenApp("Bitcoin", "2.0.0", admit = {}) { it.send(appCommand) }
        }

        assertEquals(listOf("GET"), ledger.commands)
        assertEquals("BOLOS", ledger.currentApp)
    }

    @Test
    fun withOpenApp_appOpen_runsBlock() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin")

        LedgerSession(ledger.provider).withOpenApp("Bitcoin", "2.0.0", admit = {}) { it.send(appCommand) }

        assertEquals(listOf("GET", "APP"), ledger.commands)
        assertEquals(1, ledger.transports.size)
    }

    @Test
    fun withOpenApp_heldHandleStoppedAnsweringAndOtherAppOpen_reopensThenThrowsWrongAppOpen() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin")
        val session = LedgerSession(ledger.provider)
        session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) }
        ledger.transports.single().silent = true
        ledger.currentApp = "Ethereum"

        assertFailsWith<LedgerException.WrongAppOpen> {
            session.withOpenApp("Bitcoin", "2.0.0", admit = {}) { it.send(appCommand) }
        }

        assertEquals(listOf("GET", "APP", "GET"), ledger.commands)
        assertEquals(2, ledger.transports.size)
        assertTrue(ledger.transports[0].closed)
        assertFalse(ledger.transports[1].closed)
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

    @Test
    fun connectionId_firstOperation_changesFromInitial() = runTest {
        val session = LedgerSession(FakeLedger(currentApp = "Bitcoin").provider)
        val initial = session.connectionId

        session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) }

        assertNotEquals(initial, session.connectionId)
    }

    @Test
    fun connectionId_appSwitchQuitThenOpen_keepsId() = runTest {
        val ledger = FakeLedger(currentApp = "Ethereum")
        val session = LedgerSession(ledger.provider, testScheduler.timeSource)
        session.withApp("Ethereum", "1.0.0") { it.send(appCommand) }
        val connected = session.connectionId

        session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) }

        assertEquals(listOf("GET", "APP", "GET", "QUIT", "GET", "OPEN Bitcoin", "GET", "APP"), ledger.commands)
        assertEquals(connected, session.connectionId)
    }

    @Test
    fun connectionId_reconnectReopensAfterFailuresWithinWindow_keepsId() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin")
        val session = LedgerSession(ledger.provider, testScheduler.timeSource)
        session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) }
        val connected = session.connectionId
        ledger.failingReopens = 3

        session.withApp("Ethereum", "1.0.0") { it.send(appCommand) }

        assertEquals(connected, session.connectionId)
    }

    @Test
    fun connectionId_reconnectReopensLaterThanWindow_changesId() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin")
        val session = LedgerSession(ledger.provider, testScheduler.timeSource)
        session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) }
        val connected = session.connectionId
        ledger.failingReopens = 10

        session.withApp("Ethereum", "1.0.0") { it.send(appCommand) }

        assertNotEquals(connected, session.connectionId)
    }

    @Test
    fun connectionId_oldHandleCloseOutlastsWindow_changesId() = runTest {
        var closeTime = Duration.ZERO
        // The scheduler's clock cannot move inside a non-suspending close, so close time is added by hand.
        val timeSource = object : AbstractLongTimeSource(DurationUnit.MILLISECONDS) {
            override fun read() = testScheduler.currentTime + closeTime.inWholeMilliseconds
        }
        val ledger = FakeLedger(currentApp = "Bitcoin")
        val session = LedgerSession(ledger.provider, timeSource)
        session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) }
        val connected = session.connectionId
        ledger.transports.single().beforeClose = { closeTime += 5.seconds }

        session.withApp("Ethereum", "1.0.0") { it.send(appCommand) }

        assertNotEquals(connected, session.connectionId)
    }

    @Test
    fun connectionId_heldHandleDiedSinceLastOperation_changesId() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin")
        val session = LedgerSession(ledger.provider, testScheduler.timeSource)
        session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) }
        val connected = session.connectionId
        ledger.transports.single().close()

        session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) }

        assertNotEquals(connected, session.connectionId)
    }

    @Test
    fun connectionId_reconnectCancelledAfterSwitchAnswer_nextOperationChangesId() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin")
        val session = LedgerSession(ledger.provider, testScheduler.timeSource)
        session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) }
        val connected = session.connectionId
        ledger.failingReopens = Int.MAX_VALUE
        val job = launch { session.withApp("Ethereum", "1.0.0") { it.send(appCommand) } }
        advanceTimeBy(1.seconds)
        job.cancel()
        job.join()
        ledger.failingReopens = 0

        session.withApp("Ethereum", "1.0.0") { it.send(appCommand) }

        assertNotEquals(connected, session.connectionId)
    }

    @Test
    fun connectionId_deviceLockedInOperation_changesId() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin")
        val session = LedgerSession(ledger.provider)
        session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) }
        val connected = session.connectionId

        assertFailsWith<LedgerException.DeviceLocked> {
            session.withApp("Bitcoin", "2.0.0") { throw LedgerException.DeviceLocked() }
        }

        assertNotEquals(connected, session.connectionId)
    }

    @Test
    fun connectionId_deviceLockedOnAppCheck_changesId() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin")
        val session = LedgerSession(ledger.provider)
        session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) }
        val connected = session.connectionId
        ledger.locked = true

        assertFailsWith<LedgerException.DeviceLocked> {
            session.withOpenApp("Bitcoin", "2.0.0", admit = {}) { it.send(appCommand) }
        }

        assertEquals(1, ledger.transports.size)
        assertNotEquals(connected, session.connectionId)
    }

    @Test
    fun connectionId_deviceLockedOnOpenAppName_changesId() = runTest {
        val ledger = FakeLedger(currentApp = "Bitcoin")
        val session = LedgerSession(ledger.provider)
        session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) }
        val connected = session.connectionId
        ledger.locked = true

        assertNull(session.openAppName(admit = {}))
        assertNotEquals(connected, session.connectionId)
    }

    @Test
    fun openingApp_appSwitchQuitThenOpen_isAppNameOnlyWhileOpenInFlight() = runTest {
        val ledger = FakeLedger(currentApp = "Ethereum")
        val session = LedgerSession(ledger.provider)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            session.openingApp.collect { ledger.commands += "openingApp=$it" }
        }

        session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) }

        assertEquals(
            listOf(
                "openingApp=null", "GET", "QUIT", "GET",
                "openingApp=Bitcoin", "OPEN Bitcoin", "openingApp=null", "GET", "APP",
            ),
            ledger.commands,
        )
    }

    @Test
    fun openingApp_appNotInstalled_clearsAfterAnswer() = runTest {
        val ledger = FakeLedger()
        val session = LedgerSession(ledger.provider)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            session.openingApp.collect { ledger.commands += "openingApp=$it" }
        }

        assertFailsWith<LedgerException.AppNotInstalled> {
            session.withApp("Monero", "2.0.0") { it.send(appCommand) }
        }

        assertEquals(
            listOf("openingApp=null", "GET", "openingApp=Monero", "OPEN Monero", "openingApp=null"),
            ledger.commands,
        )
    }

    @Test
    fun openingApp_cancelledWhileDeviceAsks_clears() = runTest {
        val ledger = FakeLedger().apply { answersOpenApp = false }
        val session = LedgerSession(ledger.provider)
        val job = launch { session.withApp("Bitcoin", "2.0.0") { it.send(appCommand) } }
        advanceUntilIdle()
        assertEquals("Bitcoin", session.openingApp.value)

        job.cancel()
        job.join()

        assertNull(session.openingApp.value)
    }
}
