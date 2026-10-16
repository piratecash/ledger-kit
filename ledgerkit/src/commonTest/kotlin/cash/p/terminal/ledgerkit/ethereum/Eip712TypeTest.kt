package cash.p.terminal.ledgerkit.ethereum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// Expected bytes: ledgerjs hw-app-eth tests/fixtures/apdus/{0,5,13,14}.apdus (APDU data after the header).
class Eip712TypeTest {
    @Test
    fun definition_referenceFields_matchReferenceBytes() {
        val fields = listOf(
            Triple("string", "name", "05046e616d65"),
            Triple("uint256", "chainId", "422007636861696e4964"),
            Triple("address", "verifyingContract", "0311766572696679696e67436f6e7472616374"),
            Triple("Person", "from", "0006506572736f6e0466726f6d"),
            Triple("address[]", "wallets", "8301000777616c6c657473"),
            Triple("string[3][]", "document", "850201030008646f63756d656e74"),
            Triple("string[][4]", "proof", "85020001040570726f6f66"),
            Triple("uint8[][][][]", "depthy", "c201040000000006646570746879"),
            Triple("int256", "neg256", "4120066e6567323536"),
            Triple("int8", "neg8", "4101046e656738"),
        )

        assertEquals(fields.map { it.third }, fields.map { (type, name) -> Eip712Type.parse(type).definition(name).toHexString() })
    }

    @Test
    fun encode_signedAndUnsignedLimits_matchReferenceBytes() {
        val values = listOf(
            Triple("uint256", "115792089237316195423570985008687907853269984665640564039457584007913129639935", "ff".repeat(32)),
            Triple("int256", "-57896044618658097711785492504343953926634992332820282019728792003956564819968", "80" + "00".repeat(31)),
            Triple("int256", "57896044618658097711785492504343953926634992332820282019728792003956564819967", "7f" + "ff".repeat(31)),
            Triple("int128", "-170141183460469231731687303715884105728", "80" + "00".repeat(15)),
            Triple("int64", "9223372036854775807", "7f" + "ff".repeat(7)),
            Triple("int16", "-32768", "8000"),
            Triple("int8", "-128", "80"),
            Triple("int8", "127", "7f"),
            Triple("uint256", "5", "05"),
            Triple("uint256", "0x3", "03"),
            Triple("uint256", "0x03", "03"),
        )

        assertEquals(values.map { it.third }, values.map { (type, value) -> Eip712Type.parse(type).encode(value).toHexString() })
    }

    @Test
    fun encode_otherPrimitives_matchReferenceBytes() {
        assertEquals("31", Eip712Type.parse("string").encode("1").toHexString())
        assertEquals("cd2a3d9f938e13cd947ec05abc7fe734df8dd826", Eip712Type.parse("address").encode("0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826").toHexString())
    }

    @Test
    fun encode_smallNegativeAndZero_usesFullWidthAndOneByte() {
        assertEquals("ffff", Eip712Type.parse("int16").encode("-1").toHexString())
        assertEquals("00", Eip712Type.parse("uint8").encode("0").toHexString())
        assertEquals("00", Eip712Type.parse("int8").encode("-0").toHexString())
        assertEquals("01", Eip712Type.parse("bool").encode("true").toHexString())
        assertEquals("", Eip712Type.parse("bytes").encode("0x").toHexString())
        assertEquals("deadbeef", Eip712Type.parse("bytes4").encode("0xdeadbeef").toHexString())
    }

    // ledgerjs EIP712_TYPE_ENCODERS.INT sends hex as written; app-ethereum encode_int sign-extends only a full-width value.
    @Test
    fun encode_hexIntegers_sendBytesAsWritten() {
        val values = listOf(
            Triple("int8", "0x7f", "7f"),
            Triple("int8", "0xff", "ff"),
            Triple("int16", "0xff", "ff"),
            Triple("int8", "0x007f", "007f"),
            Triple("int256", "0x8" + "0".repeat(63), "80" + "00".repeat(31)),
            Triple("uint8", "0x00ff", "00ff"),
            Triple("uint8", "0x0000", "0000"),
            Triple("uint256", "0x3", "03"),
        )

        assertEquals(values.map { it.third }, values.map { (type, value) -> Eip712Type.parse(type).encode(value).toHexString() })
    }

    @Test
    fun encode_valueOutOfRange_throws() {
        val invalid = listOf(
            "uint8" to "256", "uint8" to "0x100", "int8" to "128", "int8" to "-129", "uint8" to "-1",
            "int8" to "0x00ff", "int8" to "0x0080", "uint8" to "0x0100", "uint256" to "0x00" + "ff".repeat(32), "int8" to "0x",
            "uint8" to "0xzz", "uint8" to "1e2", "uint8" to "", "bool" to "1", "address" to "0x1234",
            "bytes4" to "0xdead", "bytes" to "0xabc", "string[]" to "x",
        )

        invalid.forEach { (type, value) ->
            assertFailsWith<IllegalArgumentException>("$type $value") { Eip712Type.parse(type).encode(value) }
        }
    }

    @Test
    fun parse_invalidTypes_throws() {
        val invalid = listOf(
            "uint7", "uint264", "bytes0", "bytes33", "uint8[0]", "uint8[256]", "uint8[1000]", "uint8[", "uint8[]]", "uint8[[]",
            "uint8[x]", "uint8[+5]", "uint8[]x", "1abc", "", "A".repeat(256), "uint8" + "[]".repeat(256), "uint8" + "[]".repeat(10000),
        )

        invalid.forEach { type ->
            assertFailsWith<IllegalArgumentException>(type) { Eip712Type.parse(type) }
        }
    }

    @Test
    fun parse_maximalNameAndLevels_parses() {
        assertEquals(255, Eip712Type.parse("uint8" + "[]".repeat(255)).arrayLevels.size)
        assertEquals(listOf(255, null, 1), Eip712Type.parse("A".repeat(255) + "[255][][1]").arrayLevels)
    }
}
