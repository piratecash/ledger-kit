package cash.p.terminal.ledgerkit

import cash.p.terminal.ledgerkit.apdu.Apdu
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.apdu.appError
import cash.p.terminal.ledgerkit.apdu.requireAnswerSize
import cash.p.terminal.ledgerkit.apdu.send
import cash.p.terminal.ledgerkit.transport.LedgerTransport

/** What a certificate lets the open app verify; values from the SDK's `os_pki.h`. */
public enum class PkiKeyUsage(internal val code: Int) {
    TRUSTED_NAME(0x04),
    COIN_META(0x08),
}

/**
 * Loads a Ledger-signed certificate ([descriptor] and [signature] as the CAL serves them) into the open app, which
 * then accepts descriptors signed under it. The OS answers this command (CLA B0) whatever app is open; framing
 * follows ledgerjs `hw-bolos` `loadPKI`.
 */
public suspend fun LedgerTransport.loadPkiCertificate(keyUsage: PkiKeyUsage, descriptor: ByteArray, signature: ByteArray) {
    require(signature.size <= MAX_SIGNATURE_SIZE) { "Certificate signature too long: ${signature.size}" }
    val data = descriptor + byteArrayOf(SIGNATURE_TAG, signature.size.toByte()) + signature
    send(Apdu(CLA, LOAD_CERTIFICATE, keyUsage.code, 0, data), ::pkiError).requireAnswerSize(0, "certificate answer")
}

private const val CLA = 0xB0
private const val LOAD_CERTIFICATE = 0x06
private const val SIGNATURE_TAG: Byte = 0x15
private const val MAX_SIGNATURE_SIZE = 0xFF

// Certificate refusals listed in ledgerjs hw-bolos PKIError.ts and the DMK LoadCertificateCommand; 0x6A81 is
// firmware without PKI support.
private val PKI_ERRORS = setOf(
    0x3301, 0x4118, 0x422D, 0x422E, 0x422F, 0x4230, 0x4231, 0x4232, 0x4233, 0x4234, 0x4235, 0x4236, 0x4237,
    0x4238, 0x4239, 0x5720, 0x6A81, 0xFFFF,
)

private fun pkiError(statusWord: Int): LedgerException = appError(statusWord, PKI_ERRORS)
