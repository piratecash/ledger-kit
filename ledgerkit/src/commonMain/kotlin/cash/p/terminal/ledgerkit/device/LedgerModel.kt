package cash.p.terminal.ledgerkit.device

public enum class LedgerModel {
    NanoS, NanoX, NanoSPlus, Stax, Flex, NanoGen5, Unknown;

    public companion object {
        public const val VENDOR_ID: Int = 0x2c97

        /** The high byte of the USB product id names the model; the low byte varies with the open app. */
        public fun fromProductId(productId: Int): LedgerModel = when (productId shr 8 and 0xFF) {
            0x10 -> NanoS
            0x40 -> NanoX
            0x50 -> NanoSPlus
            0x60 -> Stax
            0x70 -> Flex
            0x80 -> NanoGen5
            else -> Unknown
        }
    }
}
