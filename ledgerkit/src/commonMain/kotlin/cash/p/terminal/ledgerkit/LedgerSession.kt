package cash.p.terminal.ledgerkit

import cash.p.terminal.ledgerkit.apdu.Apdu
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.apdu.send
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.ledgerkit.transport.LedgerTransportProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
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
    ): T = mutex.withLock {
        try {
            block(ensureApp(appName, minVersion))
        } catch (error: Throwable) {
            if (!error.isDeviceAnswer()) dropTransport()
            throw error
        }
    }

    public fun close() {
        dropTransport()
    }

    // Returns the handle the app was checked on: a replacement opened later was never queried.
    private suspend fun ensureApp(appName: String, minVersion: String): LedgerTransport {
        var app = currentApp()
        if (app.info.name != appName) {
            if (!app.info.isDashboard) app = switchApp(app.transport, DashboardCommands.quitApp)
            app = switchApp(app.transport, DashboardCommands.openApp(appName))
            if (app.info.name != appName) throw LedgerException.WrongAppOpen()
        }
        if (!isVersionAtLeast(app.info.version, minVersion)) {
            throw LedgerException.AppVersionTooOld(appName, app.info.version, minVersion)
        }
        return app.transport
    }

    private suspend fun currentApp(): CheckedApp {
        val current = connectedTransport()
        return CheckedApp(current, current.getAppAndVersion())
    }

    private suspend fun connectedTransport(): LedgerTransport =
        transport.load() ?: transportProvider.open().also(transport::store)

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
                lastError = error
            }
            dropTransport()
        }
        throw LedgerException.TransportClosed(lastError)
    }

    private fun dropTransport() {
        transport.exchange(null)?.close()
    }

    private class CheckedApp(val transport: LedgerTransport, val info: LedgerAppInfo)

    private companion object {
        const val RECONNECT_ATTEMPTS = 20
        val RECONNECT_DELAY = 500.milliseconds
        val RECONNECT_ATTEMPT_TIMEOUT = 5.seconds
    }
}

/** A complete status-word answer leaves the channel in sync; anything else may not. */
private fun Throwable.isDeviceAnswer(): Boolean =
    this is LedgerException && this !is LedgerException.TransportClosed
