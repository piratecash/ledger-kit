package cash.p.terminal.ledgerkit.apdu

public sealed class LedgerException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    public class UserRejected : LedgerException("Rejected on the device")
    public class AppNotInstalled : LedgerException("App is not installed on the device")
    public class DeviceLocked : LedgerException("Device is locked")
    public class WrongAppOpen : LedgerException("Another app is open on the device")
    public class DataTooLarge : LedgerException("Data is too large for the device")

    /** Raised by app clients for their own status word. */
    public class BlindSigningRequired : LedgerException("Blind signing is disabled in the device app")

    public class AppVersionTooOld(
        public val appName: String,
        public val actual: String,
        public val required: String,
    ) : LedgerException("$appName $actual is older than $required")

    public class TransportClosed(cause: Throwable? = null) : LedgerException("Connection to the device is closed", cause)

    public class Unknown(public val statusWord: Int) :
        LedgerException("Unexpected status word 0x${statusWord.toString(16).padStart(4, '0')}")

    public companion object {
        public fun fromStatusWord(statusWord: Int): LedgerException = when (statusWord) {
            StatusWord.USER_REJECTED -> UserRejected()
            StatusWord.APP_NOT_INSTALLED -> AppNotInstalled()
            StatusWord.DEVICE_LOCKED, StatusWord.SECURITY_STATUS_NOT_SATISFIED -> DeviceLocked()
            StatusWord.CLA_NOT_SUPPORTED, StatusWord.INS_NOT_SUPPORTED, StatusWord.APP_NOT_OPEN -> WrongAppOpen()
            StatusWord.DATA_TOO_LARGE -> DataTooLarge()
            else -> Unknown(statusWord)
        }
    }
}
