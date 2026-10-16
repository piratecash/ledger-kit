package cash.p.terminal.ledgerkit.bitcoin

import cash.p.terminal.ledgerkit.apdu.LedgerException

// Shared by the Bitcoin app 2.x (src/boilerplate/sw.h) and the legacy btchip apps (apdu_constants.h).
private val INVALID_REQUEST_STATUS_WORDS = setOf(
    0x6700, // wrong length (legacy)
    0x6A80, // incorrect data
    0x6A82, // not supported
    0x6A86, // wrong P1/P2
    0x6A87, // wrong data length
    0x6B00, // wrong P1/P2 (legacy), swap failure (2.x)
    0xB007, // bad state
    0xB008, // signature failure
)

internal fun bitcoinAppError(statusWord: Int): LedgerException =
    if (statusWord in INVALID_REQUEST_STATUS_WORDS) LedgerException.InvalidRequest(statusWord)
    else LedgerException.fromStatusWord(statusWord)
