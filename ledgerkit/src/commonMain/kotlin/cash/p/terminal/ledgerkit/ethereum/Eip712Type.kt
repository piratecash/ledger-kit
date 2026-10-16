package cash.p.terminal.ledgerkit.ethereum

/**
 * An EIP-712 field type such as `uint8[2][]` or `Person[]`, coded for the app's struct definition and
 * implementation commands (app-ethereum `doc/eip712.md`; ledgerjs `hw-app-eth` `modules/EIP712/utils.ts`).
 * [arrayLevels] runs left to right as written; null is a dynamic level.
 */
internal class Eip712Type private constructor(
    val name: String,
    private val kind: Kind,
    private val size: Int?,
    val arrayLevels: List<Int?>,
) {
    enum class Kind(val code: Int) { STRUCT(0), INT(1), UINT(2), ADDRESS(3), BOOL(4), STRING(5), FIXED_BYTES(6), BYTES(7) }

    val isStruct: Boolean get() = kind == Kind.STRUCT

    fun elementType(): Eip712Type = Eip712Type(name, kind, size, arrayLevels.drop(1))

    /** The field description sent with INS 0x1A, P2 0xFF. */
    fun definition(fieldName: String): ByteArray {
        val arrayBit = if (arrayLevels.isEmpty()) 0 else TYPE_ARRAY
        val sizeBit = if (size == null) 0 else TYPE_SIZED
        val structName = if (isStruct) lengthPrefixed(name) else ByteArray(0)
        val sizeByte = size?.let { byteArrayOf(it.toByte()) } ?: ByteArray(0)
        val levels = if (arrayLevels.isEmpty()) ByteArray(0) else byteArrayOf(arrayLevels.size.toByte()) + arrayLevels.fold(ByteArray(0)) { bytes, level ->
            bytes + (level?.let { byteArrayOf(ARRAY_FIXED, it.toByte()) } ?: byteArrayOf(ARRAY_DYNAMIC))
        }
        return byteArrayOf((arrayBit or sizeBit or kind.code).toByte()) + structName + sizeByte + levels + lengthPrefixed(fieldName)
    }

    /** The value of a primitive field; [value] is the JSON literal text (strings unquoted). */
    fun encode(value: String): ByteArray {
        require(arrayLevels.isEmpty()) { "Array value expected for $name" }
        return encodePrimitive(value)
    }

    private fun encodePrimitive(value: String): ByteArray = when (kind) {
        Kind.UINT, Kind.INT -> if (value.startsWith(HEX_PREFIX)) hexInteger(value) else integer(value)
        Kind.BOOL -> when (value) {
            "true" -> byteArrayOf(1)
            "false" -> byteArrayOf(0)
            else -> throw IllegalArgumentException("Not a bool: $value")
        }
        Kind.ADDRESS -> hex(value).also { require(it.size == ADDRESS_SIZE) { "Not an address: $value" } }
        Kind.STRING -> value.encodeToByteArray()
        Kind.FIXED_BYTES -> hex(value).also { require(it.size == size) { "Not $name: $value" } }
        Kind.BYTES -> hex(value)
        Kind.STRUCT -> throw IllegalArgumentException("$name is a struct")
    }

    // Hex goes as written, like ledgerjs; the app takes an int as negative only when it fills the type width.
    private fun hexInteger(value: String): ByteArray {
        val digits = value.removePrefix(HEX_PREFIX)
        val bytes = hex(HEX_PREFIX + digits.padStart(digits.length + digits.length % 2, '0'))
        val bits = requireNotNull(size) * 8
        val fits = when {
            bytes.isEmpty() || bytes.size > MAX_FIXED_BYTES -> false
            kind == Kind.UINT -> bitLength(bytes) <= bits
            else -> bytes.size == size || bitLength(bytes) < bits
        }
        require(fits) { "$value does not fit $name" }
        return bytes
    }

    // Minimal big-endian magnitude; a negative int goes as two's complement over the full type width, as ledgerjs sends it.
    private fun integer(value: String): ByteArray {
        val negative = value.startsWith('-')
        val digits = value.removePrefix("-")
        require(digits.isNotEmpty() && digits.all { it in '0'..'9' } && (kind == Kind.INT || !negative)) { "Not a $name: $value" }
        val magnitude = decimalToBytes(digits)
        val width = requireNotNull(size)
        val bits = width * 8
        val limitBits = if (kind == Kind.INT) bits - 1 else bits
        val fits = bitLength(magnitude) <= limitBits || (negative && isPowerOfTwo(magnitude, limitBits))
        require(fits) { "$value does not fit $name" }
        if (!negative || magnitude.all { it == 0.toByte() }) return magnitude
        return twosComplement(magnitude, width)
    }

    companion object {
        fun parse(type: String): Eip712Type {
            val nameEnd = type.indexOf('[').takeIf { it >= 0 } ?: type.length
            val name = type.substring(0, nameEnd)
            require(name.length <= MAX_BYTE && NAME_PATTERN.matches(name)) { "Bad EIP-712 type name" }
            val levels = arrayLevels(type.substring(nameEnd))
            val (kind, size) = kindOf(name)
            return Eip712Type(name, kind, size, levels)
        }

        // Parsed by hand: a backtracking regex over a long "[][]..." suffix overflows the stack.
        private fun arrayLevels(suffix: String): List<Int?> {
            if (suffix.isEmpty()) return emptyList()
            require(suffix.first() == '[' && suffix.last() == ']') { "Bad EIP-712 array suffix" }
            val levels = suffix.substring(1, suffix.length - 1).split("][")
            require(levels.size <= MAX_BYTE) { "Too many array levels: ${levels.size}" }
            return levels.map { level ->
                if (level.isEmpty()) return@map null
                require(level.all { it in '0'..'9' }) { "Bad array size: $level" }
                level.toInt().also { require(it in 1..MAX_BYTE) { "Bad array size: $level" } }
            }
        }

        private fun kindOf(name: String): Pair<Kind, Int?> {
            val sized = SIZED_PATTERN.matchEntire(name)
            if (sized != null) {
                val (base, digits) = sized.destructured
                val number = digits.toInt()
                return if (base == "bytes") {
                    require(number in 1..MAX_FIXED_BYTES) { "Bad type $name" }
                    Kind.FIXED_BYTES to number
                } else {
                    require(number in 8..MAX_INT_BITS && number % 8 == 0) { "Bad type $name" }
                    (if (base == "int") Kind.INT else Kind.UINT) to number / 8
                }
            }
            return when (name) {
                "address" -> Kind.ADDRESS to null
                "bool" -> Kind.BOOL to null
                "string" -> Kind.STRING to null
                "bytes" -> Kind.BYTES to null
                else -> Kind.STRUCT to null
            }
        }

        private val NAME_PATTERN = Regex("""[A-Za-z_$][A-Za-z0-9_$]*""")
        private val SIZED_PATTERN = Regex("""(int|uint|bytes)(\d{1,3})""")
        private const val TYPE_ARRAY = 0x80
        private const val TYPE_SIZED = 0x40
        private const val ARRAY_DYNAMIC: Byte = 0
        private const val ARRAY_FIXED: Byte = 1
        private const val MAX_BYTE = 0xFF
        private const val MAX_FIXED_BYTES = 32
        private const val MAX_INT_BITS = 256
        private const val ADDRESS_SIZE = 20
        private const val HEX_PREFIX = "0x"
    }
}

internal fun lengthPrefixed(text: String): ByteArray {
    val bytes = text.encodeToByteArray()
    require(bytes.size <= 0xFF) { "Name too long: $text" }
    return byteArrayOf(bytes.size.toByte()) + bytes
}

private fun hex(value: String): ByteArray {
    require(value.startsWith("0x") && value.length % 2 == 0) { "Not a hex string: $value" }
    return value.substring(2).hexToByteArray()
}

/** Minimal big-endian bytes of a decimal number; zero is one zero byte. */
private fun decimalToBytes(digits: String): ByteArray {
    var bytes = IntArray(1)
    for (digit in digits) {
        var carry = digit - '0'
        for (i in bytes.indices.reversed()) {
            val value = bytes[i] * 10 + carry
            bytes[i] = value and 0xFF
            carry = value shr 8
        }
        if (carry > 0) bytes = intArrayOf(carry) + bytes
    }
    return ByteArray(bytes.size) { bytes[it].toByte() }
}

private fun bitLength(bytes: ByteArray): Int {
    val first = bytes.indexOfFirst { it != 0.toByte() }
    if (first < 0) return 0
    return (bytes.size - first - 1) * 8 + (32 - (bytes[first].toInt() and 0xFF).countLeadingZeroBits())
}

private fun isPowerOfTwo(bytes: ByteArray, exponent: Int): Boolean =
    bitLength(bytes) == exponent + 1 && bytes.count { it != 0.toByte() } == 1 && (bytes.first { it != 0.toByte() }.toInt() and 0xFF).countOneBits() == 1

private fun twosComplement(magnitude: ByteArray, width: Int): ByteArray {
    val padded = ByteArray(width - magnitude.size) + magnitude
    var carry = 1
    for (i in padded.indices.reversed()) {
        val value = (padded[i].toInt().inv() and 0xFF) + carry
        padded[i] = value.toByte()
        carry = value shr 8
    }
    return padded
}
