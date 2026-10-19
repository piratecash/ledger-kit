package cash.p.terminal.ledgerkit

import cash.p.terminal.ledgerkit.apdu.Apdu
import cash.p.terminal.ledgerkit.apdu.ApduResponse
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.apdu.send
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.ledgerkit.transport.LedgerTransportProvider
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** One Ledger device: serializes operations and switches the open app when an operation needs another one. */
@OptIn(ExperimentalAtomicApi::class)
public class LedgerSession(private val transportProvider: LedgerTransportProvider) {
    private val mutex = Mutex()

    // close() runs outside the mutex; taking the handle atomically closes each one exactly once.
    private val transport = AtomicReference<LedgerTransport?>(null)

    /**
     * Runs [block] with [appName] open in at least [minVersion]. Every exchange of [block] belongs to
     * this operation alone; an interrupted exchange drops the transport so the next operation reopens it.
     */
    public suspend fun <T> withApp(
        appName: String,
        minVersion: String,
        block: suspend (LedgerTransport) -> T,
    ): T = withApp(appName, minVersion, admit = {}, block)

    /**
     * [withApp] that calls [admit] once it holds the device and before any exchange: an operation queued
     * behind another one is admitted by the conditions of the moment it would reach the device.
     * [admit] refuses by throwing; the connection is kept.
     */
    public suspend fun <T> withApp(
        appName: String,
        minVersion: String,
        admit: () -> Unit,
        block: suspend (LedgerTransport) -> T,
    ): T = withApp(appName, minVersion, allowSwitch = true, admit, block)

    /** [withApp] that never switches apps: throws [LedgerException.WrongAppOpen] unless [appName] is already open. */
    public suspend fun <T> withOpenApp(
        appName: String,
        minVersion: String,
        admit: () -> Unit,
        block: suspend (LedgerTransport) -> T,
    ): T = withApp(appName, minVersion, allowSwitch = false, admit, block)

    private suspend fun <T> withApp(
        appName: String,
        minVersion: String,
        allowSwitch: Boolean,
        admit: () -> Unit,
        block: suspend (LedgerTransport) -> T,
    ): T = mutex.withLock {
        admit()
        val transport = TrackedTransport(guarded { ensureApp(appName, minVersion, allowSwitch) })
        try {
            block(transport)
        } catch (error: Throwable) {
            // Only an answer cut short desyncs the channel; a failure between whole answers keeps it.
            if (!transport.inSync) {
                if (error !is CancellationException) {
                    logger.w(error) { "Exchange cut short in withApp($appName); dropping the connection" }
                }
                dropTransport()
            }
            throw error
        }
    }

    /**
     * Name of the open app, asked without a prompt and without switching apps. Null while no connection is
     * held (a background check never opens one) or the device declines to answer, e.g. while locked;
     * throws when the connection is lost. [admit] works as in [withApp].
     */
    public suspend fun openAppName(admit: () -> Unit): String? = mutex.withLock {
        admit()
        guarded {
            val current = transport.load() ?: return@guarded null
            try {
                queryHeld(current)?.name
            } catch (error: LedgerException) {
                if (error.isDeviceAnswer()) null else throw error
            }
        }
    }

    public fun close() {
        dropTransport()
    }

    private suspend fun <T> guarded(operation: suspend () -> T): T = try {
        operation()
    } catch (error: Throwable) {
        if (!error.isDeviceAnswer()) {
            if (error !is CancellationException) {
                logger.w(error) { "Transport error; dropping the connection" }
            }
            dropTransport()
        }
        throw error
    }

    // Returns the handle the app was checked on: a replacement opened later was never queried.
    private suspend fun ensureApp(appName: String, minVersion: String, allowSwitch: Boolean): LedgerTransport {
        var app = currentApp()
        if (app.info.name != appName) {
            if (!allowSwitch) {
                logger.d { "$appName not open (${app.info.name}); not switching" }
                throw LedgerException.WrongAppOpen()
            }
            logger.i { "Switching app ${app.info.name} -> $appName" }
            if (!app.info.isDashboard) app = switchApp(app.transport, DashboardCommands.quitApp)
            app = switchApp(app.transport, DashboardCommands.openApp(appName))
            if (app.info.name != appName) {
                logger.w { "Expected $appName after switching, found ${app.info.name}" }
                throw LedgerException.WrongAppOpen()
            }
        }
        if (!isVersionAtLeast(app.info.version, minVersion)) {
            logger.w { "$appName ${app.info.version} is older than the required $minVersion" }
            throw LedgerException.AppVersionTooOld(appName, app.info.version, minVersion)
        }
        return app.transport
    }

    // A held handle outlives an unplug noticed by no one; the idempotent first query tells, and a fresh one replaces it.
    private suspend fun currentApp(): CheckedApp {
        transport.load()?.let { held ->
            try {
                queryHeld(held)?.let { return CheckedApp(held, it) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (error.isDeviceAnswer()) throw error
                logger.w(error) { "Held connection failed; dropping it" }
                dropTransport()
            }
        }
        val opened = transportProvider.open().also(transport::store)
        return CheckedApp(opened, opened.getAppAndVersion()).also {
            logger.i { "Opened a connection; app ${it.info.name} ${it.info.version}" }
        }
    }

    // A held handle can stop answering while the device stays enumerated; one that does not answer in time is dropped.
    private suspend fun queryHeld(held: LedgerTransport): LedgerAppInfo? {
        val info = withTimeoutOrNull(HELD_QUERY_TIMEOUT) { held.getAppAndVersion() }
        if (info == null) {
            logger.w { "Held connection gave no answer within $HELD_QUERY_TIMEOUT; dropping it" }
            dropTransport()
        }
        return info
    }

    // The device re-enumerates on USB after an app switch, so the old handle is dead.
    private suspend fun switchApp(current: LedgerTransport, command: Apdu): CheckedApp {
        current.send(command)
        dropTransport()
        return reconnect()
    }

    private suspend fun reconnect(): CheckedApp {
        var lastError: Throwable? = null
        repeat(RECONNECT_ATTEMPTS) {
            delay(RECONNECT_DELAY)
            try {
                withTimeoutOrNull(RECONNECT_ATTEMPT_TIMEOUT) { currentApp() }?.let { return it }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (error.isDeviceAnswer()) throw error
                logger.d(error) { "Reconnect attempt failed" }
                lastError = error
            }
            dropTransport()
        }
        logger.w(lastError) { "Giving up reconnecting after $RECONNECT_ATTEMPTS attempts" }
        throw LedgerException.TransportClosed(lastError)
    }

    private fun dropTransport() {
        transport.exchange(null)?.close()
    }

    private class CheckedApp(val transport: LedgerTransport, val info: LedgerAppInfo)

    /** Counts exchanges without a whole answer — one ending in a status word: in flight, failed or malformed. */
    private class TrackedTransport(private val inner: LedgerTransport) : LedgerTransport {
        private val unsettled = AtomicInt(0)

        val inSync: Boolean get() = unsettled.load() == 0

        override suspend fun exchange(apdu: ByteArray): ByteArray {
            unsettled.addAndFetch(1)
            return inner.exchange(apdu).also {
                if (it.size >= ApduResponse.STATUS_WORD_SIZE) unsettled.addAndFetch(-1)
            }
        }

        override fun close() = inner.close()
    }

    private companion object {
        val logger = Logger.withTag("LedgerSession")
        const val RECONNECT_ATTEMPTS = 20
        val RECONNECT_DELAY = 500.milliseconds
        val RECONNECT_ATTEMPT_TIMEOUT = 5.seconds
        val HELD_QUERY_TIMEOUT = 3.seconds
    }
}

/** A complete status-word answer leaves the channel in sync; anything else may not. */
private fun Throwable.isDeviceAnswer(): Boolean =
    this is LedgerException && this !is LedgerException.TransportClosed
