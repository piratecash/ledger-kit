package cash.p.terminal.ledgerkit.apdu

/** Status words shared by the dashboard and every app; app-specific ones are mapped by app clients. */
public object StatusWord {
    public const val OK: Int = 0x9000
    public const val USER_REJECTED: Int = 0x6985
    public const val APP_NOT_INSTALLED: Int = 0x6807
    public const val DEVICE_LOCKED: Int = 0x5515

    /** What a locked device answers to commands it refuses to run. */
    public const val SECURITY_STATUS_NOT_SATISFIED: Int = 0x6982
    public const val CLA_NOT_SUPPORTED: Int = 0x6E00
    public const val INS_NOT_SUPPORTED: Int = 0x6D00

    /** What the dashboard answers to an app command. */
    public const val APP_NOT_OPEN: Int = 0x6511
    public const val DATA_TOO_LARGE: Int = 0x6A84
}
