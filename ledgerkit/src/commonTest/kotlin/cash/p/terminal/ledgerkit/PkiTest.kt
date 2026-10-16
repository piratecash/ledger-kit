package cash.p.terminal.ledgerkit

import cash.p.terminal.ledgerkit.apdu.LedgerException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PkiTest {
    // LedgerHQ/ledger-live libs/ledgerjs/packages/hw-bolos/src/loadPKI.test.ts, with the trusted name key usage as P1.
    @Test
    fun loadPkiCertificate_trustedName_appendsSignatureTlv() = runTest {
        val transport = RecordingTransport { "9000".hexToByteArray() }

        transport.loadPkiCertificate(PkiKeyUsage.TRUSTED_NAME, "010203".hexToByteArray(), "0a0b0c0d0e0f1a1b1c1d1e1f".hexToByteArray())

        assertEquals("b006040011" + "010203" + "15" + "0c" + "0a0b0c0d0e0f1a1b1c1d1e1f", transport.sent.single().toHexString())
    }

    @Test
    fun loadPkiCertificate_coinMeta_sendsItsKeyUsage() = runTest {
        val transport = RecordingTransport { "9000".hexToByteArray() }

        transport.loadPkiCertificate(PkiKeyUsage.COIN_META, "01".hexToByteArray(), "02".hexToByteArray())

        assertEquals("b00608000401150102", transport.sent.single().toHexString())
    }

    @Test
    fun loadPkiCertificate_signatureNotVerified_throwsInvalidRequest() = runTest {
        val transport = RecordingTransport { "5720".hexToByteArray() }

        val error = assertFailsWith<LedgerException.InvalidRequest> {
            transport.loadPkiCertificate(PkiKeyUsage.TRUSTED_NAME, "01".hexToByteArray(), "02".hexToByteArray())
        }

        assertEquals(0x5720, error.statusWord)
    }

    @Test
    fun loadPkiCertificate_answerWithData_throwsMalformedResponse() = runTest {
        val transport = RecordingTransport { "019000".hexToByteArray() }

        assertFailsWith<LedgerException.MalformedResponse> {
            transport.loadPkiCertificate(PkiKeyUsage.TRUSTED_NAME, "01".hexToByteArray(), "02".hexToByteArray())
        }
    }
}
