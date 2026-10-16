package cash.p.terminal.ledgerkit

import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LedgerSessionCloseTest {
    @Test
    fun close_pausedWhileOperationFailsAndReopens_closesEachHandleOnceAndKeepsNewest() = runBlocking {
        val ledger = FakeLedger(currentApp = "Bitcoin")
        val session = LedgerSession(ledger.provider)
        session.withApp("Bitcoin", "2.0.0") {}
        val first = ledger.transports.single()
        val closeStarted = CountDownLatch(1)
        val closeMayFinish = CountDownLatch(1)
        first.beforeClose = {
            if (first.closeCalls == 1) {
                closeStarted.countDown()
                closeMayFinish.await()
            }
        }
        val closer = thread { session.close() }
        closeStarted.await()

        assertFails { session.withApp("Bitcoin", "2.0.0") { error("operation failed") } }
        session.withApp("Bitcoin", "2.0.0") {}
        closeMayFinish.countDown()
        closer.join()
        val handlesBeforeLastOperation = ledger.transports.size
        session.withApp("Bitcoin", "2.0.0") {}

        assertEquals(handlesBeforeLastOperation, ledger.transports.size)
        assertTrue(ledger.transports.all { it.closeCalls <= 1 })
        assertFalse(ledger.transports.last().closed)
    }
}
