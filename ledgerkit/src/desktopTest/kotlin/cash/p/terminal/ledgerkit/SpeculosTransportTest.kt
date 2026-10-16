package cash.p.terminal.ledgerkit

import cash.p.terminal.ledgerkit.apdu.Apdu
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.apdu.send
import cash.p.terminal.ledgerkit.transport.SpeculosTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import org.junit.Assume.assumeTrue
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SpeculosTransportTest {
    @Test
    fun exchange_speculosFraming_sendsLengthPrefixedApduAndJoinsDataWithStatusWord() = runBlocking {
        loopbackServer().use { server ->
            var received = ""
            val emulator = thread {
                server.accept().use { socket ->
                    received = DataInputStream(socket.getInputStream()).readApdu().toHexString()
                    DataOutputStream(socket.getOutputStream()).apply {
                        writeInt(2)
                        write("abcd9000".hexToByteArray())
                        flush()
                    }
                }
            }
            val transport = SpeculosTransport.connect("127.0.0.1", server.localPort)

            val response = transport.exchange("b001000000".hexToByteArray())
            transport.close()
            emulator.joinOrFail()

            assertEquals("b001000000", received)
            assertEquals("abcd9000", response.toHexString())
        }
    }

    @Test
    fun exchange_peerAnnouncesHugeResponse_throwsTransportClosed() = runBlocking {
        loopbackServer().use { server ->
            val emulator = thread {
                server.accept().use { socket ->
                    DataInputStream(socket.getInputStream()).readApdu()
                    socket.getOutputStream().apply {
                        write("7fffffff".hexToByteArray())
                        flush()
                    }
                    socket.getInputStream().read()
                }
            }
            val transport = SpeculosTransport.connect("127.0.0.1", server.localPort)

            assertFailsWith<LedgerException.TransportClosed> { transport.exchange("b001000000".hexToByteArray()) }
            transport.close()
            emulator.joinOrFail()
        }
    }

    @Test
    fun exchange_emulatorClosedConnection_throwsTransportClosed() = runBlocking {
        loopbackServer().use { server ->
            val emulator = thread { server.accept().close() }
            val transport = SpeculosTransport.connect("127.0.0.1", server.localPort)
            emulator.joinOrFail()

            assertFailsWith<LedgerException.TransportClosed> { transport.exchange("b001000000".hexToByteArray()) }
            transport.close()
        }
    }

    @Test
    fun connect_nothingListening_throwsTransportClosed(): Unit = runBlocking {
        val port = loopbackServer().use { it.localPort }

        assertFailsWith<LedgerException.TransportClosed> { SpeculosTransport.connect("127.0.0.1", port) }
    }

    @Test
    fun connect_cancelledWhilePeerNeverAccepts_returnsBeforeConnectTimeout() = runBlocking {
        loopbackServer(backlog = 1).use { server ->
            val fillers = fillBacklog(server)
            try {
                val connecting = CoroutineScope(Dispatchers.Default).launch {
                    SpeculosTransport.connect("127.0.0.1", server.localPort)
                }
                delay(200)

                connecting.cancel()

                withTimeout(1_000) { connecting.join() }
            } finally {
                fillers.forEach(Socket::close)
            }
        }
    }

    @Test
    fun withApp_peerSilentThenCancelled_closesSocketAndNextOperationRuns() = runBlocking {
        SilentAfterAppInfoPeer().use { peer ->
            val session = LedgerSession { SpeculosTransport.connect("127.0.0.1", peer.port) }
            // Outside runBlocking's scope: a regression must fail the test, not hang it.
            val stuck = CoroutineScope(Dispatchers.Default).launch {
                session.withApp("Bitcoin", "2.0.0") { it.send(Apdu(cla = 0xE1, ins = 0x00, p1 = 0, p2 = 0)) }
            }
            peer.awaitSilentRequest()

            stuck.cancel()
            withTimeout(TIMEOUT_MILLIS) { stuck.join() }

            assertTrue(peer.awaitDisconnect())
            assertEquals("ran", withTimeout(TIMEOUT_MILLIS) { session.withApp("Bitcoin", "2.0.0") { "ran" } })
        }
    }

    /** Answers GET_APP_AND_VERSION as Bitcoin 2.1.0 and never answers anything else. */
    private class SilentAfterAppInfoPeer : AutoCloseable {
        private val server = loopbackServer()
        private val silentRequests = Semaphore(0)
        private val disconnects = Semaphore(0)
        val port: Int = server.localPort

        init {
            thread(isDaemon = true) {
                while (true) {
                    val socket = try {
                        server.accept()
                    } catch (_: IOException) {
                        break
                    }
                    thread(isDaemon = true) { serve(socket) }
                }
            }
        }

        fun awaitSilentRequest() = check(silentRequests.tryAcquire(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))

        fun awaitDisconnect(): Boolean = disconnects.tryAcquire(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)

        override fun close() {
            server.close()
        }

        private fun serve(socket: Socket) = socket.use {
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())
            try {
                while (true) {
                    if (input.readApdu()[1] == 0x01.toByte()) {
                        output.writeInt(APP_INFO.size - 2)
                        output.write(APP_INFO)
                        output.flush()
                    } else {
                        silentRequests.release()
                    }
                }
            } catch (_: IOException) {
                disconnects.release()
            }
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        val APP_INFO = ("01" + "07" + "Bitcoin".encodeToByteArray().toHexString() +
            "05" + "2.1.0".encodeToByteArray().toHexString() + "9000").hexToByteArray()

        // The client dials 127.0.0.1; a wildcard bind can share its port with another loopback listener.
        fun loopbackServer(backlog: Int = 50) = ServerSocket(0, backlog, InetAddress.getLoopbackAddress())

        fun Thread.joinOrFail() {
            join(TIMEOUT_MILLIS)
            assertFalse(isAlive, "Emulator thread did not finish")
        }
    }
}

private fun DataInputStream.readApdu(): ByteArray = ByteArray(readInt()).also(::readFully)

/** Fills the listen backlog until a connect stalls; skips where the OS refuses instead of stalling. */
private fun fillBacklog(server: ServerSocket): List<Socket> {
    val fillers = mutableListOf<Socket>()
    repeat(50) {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(server.inetAddress, server.localPort), 300)
            fillers += socket
        } catch (_: SocketTimeoutException) {
            socket.close()
            return fillers
        } catch (_: IOException) {
            socket.close()
            fillers.forEach(Socket::close)
            assumeTrue("Full backlog is refused here, not stalled", false)
        }
    }
    fillers.forEach(Socket::close)
    assumeTrue("Backlog never stalled", false)
    return fillers
}
