package cash.p.terminal.ledgerkit.apdu

/** Maps an app's own refusals to [LedgerException.InvalidRequest], everything else as shared status words. */
internal fun appError(statusWord: Int, invalidRequests: Set<Int>): LedgerException =
    if (statusWord in invalidRequests) LedgerException.InvalidRequest(statusWord)
    else LedgerException.fromStatusWord(statusWord)
