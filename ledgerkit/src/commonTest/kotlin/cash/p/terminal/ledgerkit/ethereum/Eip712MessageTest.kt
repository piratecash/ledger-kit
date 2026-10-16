package cash.p.terminal.ledgerkit.ethereum

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.RecordingTransport
import cash.p.terminal.ledgerkit.ReplayTransport
import cash.p.terminal.ledgerkit.apdu.LedgerException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Eip712MessageTest {
    @Test
    fun signTypedData_referenceFixtures_sendReferenceApdus() = runTest {
        val failures = EIP712_FIXTURES.mapNotNull { fixture ->
            val transport = ReplayTransport(fixture.apdus)
            try {
                EthereumAppClient(transport).signTypedData(PATH, fixture.json)
                transport.assertAllReplayed()
                null
            } catch (e: Throwable) {
                "${fixture.name}: ${e.message}"
            }
        }

        assertEquals(emptyList(), failures)
    }

    // ledgerjs noFilter-signMessage.unit.test.ts, "0.json sample message".
    @Test
    fun signTypedData_mail_returnsReferenceSignature() = runTest {
        val fixture = EIP712_FIXTURES.first { it.name == "0" }

        val signature = EthereumAppClient(ReplayTransport(fixture.apdus)).signTypedData(PATH, fixture.json)

        assertEquals(28, signature.v)
        assertEquals(1, signature.yParity)
        assertEquals("8a540510e13b0f2b11a451275716d29e08caad07e89a1c84964782fb5e1ad788", signature.r.toHexString())
        assertEquals("64a0de235b270fbe81e8e40688f4a9f9ad9d283d690552c9331d7773ceafa513", signature.s.toHexString())
    }

    @Test
    fun signTypedData_incorrectDataOnSign_throwsBlindSigningRequired() = runTest {
        val transport = RecordingTransport { apdu -> (if (apdu[1].toInt() == 0x0C) "6a80" else "9000").hexToByteArray() }

        assertFailsWith<LedgerException.BlindSigningRequired> { EthereumAppClient(transport).signTypedData(PATH, MAIL_JSON) }
    }

    @Test
    fun signTypedData_incorrectDataOnStruct_throwsInvalidRequest() = runTest {
        val error = assertFailsWith<LedgerException.InvalidRequest> { EthereumAppClient(answering("6a80")).signTypedData(PATH, MAIL_JSON) }

        assertEquals(0x6A80, error.statusWord)
    }

    @Test
    fun signTypedData_malformedMessages_sendNothing() = runTest {
        val malformed = listOf(
            "not json",
            MAIL_JSON.replace("\"primaryType\": \"Mail\"", "\"primaryType\": \"Letter\""),
            MAIL_JSON.replace("\"type\": \"Person\"", "\"type\": \"Human\""),
            MAIL_JSON.replace("\"contents\": \"Hello, Bob!\"", "\"body\": \"Hello, Bob!\""),
            MAIL_JSON.replace("\"chainId\": 5", "\"chainId\": -5"),
            MAIL_JSON.replace("\"name\": \"Cow\",", "\"name\": null,"),
            MAIL_JSON.replace("\"wallets\": [\"0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826\"]", "\"wallets\": \"0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826\""),
        )

        malformed.forEach { json ->
            val transport = answering("9000")
            assertFailsWith<IllegalArgumentException>(json) { EthereumAppClient(transport).signTypedData(PATH, json) }
            assertEquals(0, transport.sent.size, json)
        }
    }

    @Test
    fun signTypedData_deepNesting_throwsIllegalArgumentAndSendsNothing() = runTest {
        val manyLevels = """{"types":{"EIP712Domain":[{"name":"x","type":"uint8${"[]".repeat(10000)}"}]},"primaryType":"EIP712Domain","domain":{},"message":{}}"""
        val deepValue = """{"types":{"EIP712Domain":[],"Node":[{"name":"next","type":"Node[]"}]},"primaryType":"Node","domain":{},""" +
            """"message":${"{\"next\":[".repeat(5000)}${"]}".repeat(5000)}}"""

        listOf(manyLevels, deepValue).forEach { json ->
            val transport = answering("9000")
            assertFailsWith<IllegalArgumentException> { EthereumAppClient(transport).signTypedData(PATH, json) }
            assertEquals(0, transport.sent.size)
        }
    }

    @Test
    fun signTypedData_manyBracketsAtLowDepth_signs() = runTest {
        val bracketsInString = MAIL_JSON.replace("\"contents\": \"Hello, Bob!\"", "\"contents\": \"\\\"${"[{".repeat(100)}\"")
        val siblings = List(100) { """{"next":[]}""" }.joinToString(",")
        val wide = """{"types":{"EIP712Domain":[],"Node":[{"name":"next","type":"Node[]"}]},"primaryType":"Node","domain":{},"message":{"next":[$siblings]}}"""

        listOf(bracketsInString, wide).forEach { json ->
            assertEquals(0, EthereumAppClient(signing("9000")).signTypedData(PATH, json).yParity)
        }
    }

    @Test
    fun signTypedData_answerToStructCommand_throwsMalformedResponse() = runTest {
        assertFailsWith<LedgerException.MalformedResponse> { EthereumAppClient(signing("deadbeef9000")).signTypedData(PATH, MAIL_JSON) }
    }

    private fun signing(structAnswer: String) =
        RecordingTransport { apdu -> (if (apdu[1].toInt() == 0x0C) "1b" + "11".repeat(64) + "9000" else structAnswer).hexToByteArray() }

    private fun answering(status: String) = RecordingTransport { status.hexToByteArray() }

    private companion object {
        val PATH = DerivationPath.parse("44'/60'/0'/0/0")

        val MAIL_JSON = """
            {
              "types": {
                "EIP712Domain": [{"name": "name", "type": "string"}, {"name": "chainId", "type": "uint256"}],
                "Mail": [{"name": "from", "type": "Person"}, {"name": "contents", "type": "string"}],
                "Person": [{"name": "name", "type": "string"}, {"name": "wallets", "type": "address[]"}]
              },
              "primaryType": "Mail",
              "domain": {"name": "Ether Mail", "chainId": 5},
              "message": {
                "from": {"name": "Cow", "wallets": ["0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826"]},
                "contents": "Hello, Bob!"
              }
            }
        """.trimIndent()
    }
}
