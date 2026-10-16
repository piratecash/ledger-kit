package cash.p.terminal.ledgerkit.ethereum

import cash.p.terminal.ledgerkit.apdu.Apdu
import cash.p.terminal.ledgerkit.toUInt16Bytes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * The struct definition and implementation APDUs of an EIP-712 message, built in full before anything is
 * sent so a malformed message never leaves the app half-loaded. Order follows ledgerjs `signEIP712Message`:
 * every type sorted by name, then the `EIP712Domain` values, then the `primaryType` values.
 */
internal class Eip712Message private constructor(
    private val types: Map<String, List<Field>>,
    private val primaryType: String,
    private val domain: JsonElement?,
    private val message: JsonElement?,
) {
    private class Field(val name: String, val type: Eip712Type)

    fun commands(): List<Apdu> = buildList {
        types.keys.sorted().forEach { name ->
            add(Apdu(CLA, STRUCT_DEFINITION, 0, P2_NAME, name.encodeToByteArray()))
            types.getValue(name).forEach { add(Apdu(CLA, STRUCT_DEFINITION, 0, P2_FIELD, it.type.definition(it.name))) }
        }
        addRoot(DOMAIN, domain)
        addRoot(primaryType, message)
    }

    private fun MutableList<Apdu>.addRoot(name: String, value: JsonElement?) {
        add(Apdu(CLA, STRUCT_IMPLEMENTATION, 0, P2_NAME, name.encodeToByteArray()))
        addStruct(name, value)
    }

    private fun MutableList<Apdu>.addStruct(name: String, value: JsonElement?) {
        val values = value as? JsonObject ?: throw IllegalArgumentException("$name value is not an object")
        types.getValue(name).forEach { field -> addValue(field.type, values[field.name] ?: throw IllegalArgumentException("Missing ${field.name} of $name")) }
    }

    private fun MutableList<Apdu>.addValue(type: Eip712Type, value: JsonElement) {
        when {
            type.arrayLevels.isNotEmpty() -> {
                val items = value as? JsonArray ?: throw IllegalArgumentException("${type.name} array value is not an array")
                require(items.size <= MAX_ARRAY_SIZE) { "Array of ${items.size} items" }
                add(Apdu(CLA, STRUCT_IMPLEMENTATION, 0, P2_ARRAY, byteArrayOf(items.size.toByte())))
                val element = type.elementType()
                items.forEach { addValue(element, it) }
            }
            type.isStruct -> addStruct(type.name, value)
            else -> addField(type.encode(literal(value)))
        }
    }

    // The app takes a field as its 2-byte length and value, cut into partial (P1 01) and final (P1 00) chunks.
    private fun MutableList<Apdu>.addField(encoded: ByteArray) {
        require(encoded.size <= MAX_FIELD_SIZE) { "Field of ${encoded.size} bytes" }
        val chunks = (encoded.size.toUInt16Bytes() + encoded).asList().chunked(Apdu.MAX_DATA_LENGTH)
        chunks.forEachIndexed { index, chunk ->
            add(Apdu(CLA, STRUCT_IMPLEMENTATION, if (index == chunks.lastIndex) 0 else P1_PARTIAL, P2_FIELD, chunk.toByteArray()))
        }
    }

    private fun literal(value: JsonElement): String =
        (value as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content ?: throw IllegalArgumentException("Not a primitive value: $value")

    companion object {
        fun parse(json: String): Eip712Message {
            requireShallow(json)
            val root = Json.parseToJsonElement(json).jsonObject
            val types = (root["types"] ?: throw IllegalArgumentException("Missing types")).jsonObject.mapValues { (_, fields) ->
                fields.jsonArray.map { field ->
                    val entry = field.jsonObject
                    Field(literal(entry, "name"), Eip712Type.parse(literal(entry, "type")))
                }
            }
            val primaryType = literal(root, "primaryType")
            require(DOMAIN in types && primaryType in types) { "Missing $DOMAIN or $primaryType definition" }
            types.values.flatten().filter { it.type.isStruct }.forEach { require(it.type.name in types) { "Undefined type ${it.type.name}" } }
            return Eip712Message(types, primaryType, root["domain"], root["message"])
        }

        // The JSON parser and the value walk both recurse per nesting level, so deep input would overflow the stack.
        private fun requireShallow(json: String) {
            var depth = 0
            var inString = false
            var escaped = false
            for (char in json) {
                when {
                    escaped -> escaped = false
                    inString -> if (char == '\\') escaped = true else if (char == '"') inString = false
                    char == '"' -> inString = true
                    char == '{' || char == '[' -> require(++depth <= MAX_JSON_DEPTH) { "JSON nested deeper than $MAX_JSON_DEPTH" }
                    char == '}' || char == ']' -> depth--
                }
            }
        }

        private fun literal(entry: JsonObject, key: String): String =
            (entry[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw IllegalArgumentException("Missing string $key")

        private const val DOMAIN = "EIP712Domain"
        private const val CLA = 0xE0
        private const val STRUCT_DEFINITION = 0x1A
        private const val STRUCT_IMPLEMENTATION = 0x1C
        private const val P1_PARTIAL = 0x01
        private const val P2_NAME = 0x00
        private const val P2_ARRAY = 0x0F
        private const val P2_FIELD = 0xFF
        private const val MAX_ARRAY_SIZE = 0xFF
        private const val MAX_FIELD_SIZE = 0xFFFF
        private const val MAX_JSON_DEPTH = 64
    }
}
