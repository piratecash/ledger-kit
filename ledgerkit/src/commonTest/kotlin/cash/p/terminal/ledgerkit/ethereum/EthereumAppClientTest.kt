package cash.p.terminal.ledgerkit.ethereum

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.RecordingTransport
import cash.p.terminal.ledgerkit.ReplayTransport
import cash.p.terminal.ledgerkit.apdu.LedgerException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// APDU logs: LedgerHQ/ledger-live libs/ledgerjs/packages/hw-app-eth/tests/Eth.unit.test.ts unless noted.
class EthereumAppClientTest {
    @Test
    fun getAddress_referenceLog_parsesKeyAndAddress() = runTest {
        val transport = ReplayTransport(
            """
            => e002000015058000002c8000003c800000008000000000000000
            <= 4104df00ad3869baad7ce54f4d560ba7f268d542df8f2679a5898d78a690c3db8f9833d2973671cb14b088e91bdf7c0ab00029a576473c0e12f84d252e630bb3809b28436241393833363265313939633431453138363444303932334146393634366433413634383435319000
            """,
        )

        val address = EthereumAppClient(transport).getAddress(DerivationPath.parse("44'/60'/0'/0'/0"))

        assertEquals("0xCbA98362e199c41E1864D0923AF9646d3A648451", address.address)
        assertEquals(
            "04df00ad3869baad7ce54f4d560ba7f268d542df8f2679a5898d78a690c3db8f9833d2973671cb14b088e91bdf7c0ab00029a576473c0e12f84d252e630bb3809b",
            address.publicKey.toHexString(),
        )
        transport.assertAllReplayed()
    }

    @Test
    fun getAddress_displayWithChainId_sendsChainIdAsEightBytes() = runTest {
        val transport = ReplayTransport(
            """
            => e00201001d058000002c8000003c8000000000000000000000000000000000000089
            <= 41047d8d3c470d1cfd8525d9537efdb92319a13a9bc9e336b6621fa5a664d2591b60fcd4f7882b0ff07d5ea0697050c7d23428daa5beaf6268cbac1369c278c6d8ea28366342434437334344386538613432383434363632663041306537364437463739416664393333649000
            """,
        )

        val address = EthereumAppClient(transport).getAddress(PATH, display = true, chainId = 137)

        assertEquals("0x6cBCD73CD8e8a42844662f0A0e76D7F79Afd933d", address.address)
        transport.assertAllReplayed()
    }

    @Test
    fun getAddress_compressedKey_throwsMalformedResponse() = runTest {
        val answer = "21" + "02" + "11".repeat(32) + "28" + "61".repeat(40)

        assertFailsWith<LedgerException.MalformedResponse> { client(answer).getAddress(PATH) }
    }

    @Test
    fun getAddress_nonHexAddress_throwsMalformedResponse() = runTest {
        val answer = "41" + "04" + "11".repeat(64) + "28" + "7a".repeat(40)

        assertFailsWith<LedgerException.MalformedResponse> { client(answer).getAddress(PATH) }
    }

    @Test
    fun getAddress_truncatedAnswer_throwsMalformedResponse() = runTest {
        val answer = "41" + "04" + "11".repeat(64) + "28" + "61".repeat(39)

        assertFailsWith<LedgerException.MalformedResponse> { client(answer).getAddress(PATH) }
    }

    @Test
    fun getAddress_trailingBytes_throwsMalformedResponse() = runTest {
        val answer = "41" + "04" + "11".repeat(64) + "28" + "61".repeat(40) + "00"

        assertFailsWith<LedgerException.MalformedResponse> { client(answer).getAddress(PATH) }
    }

    @Test
    fun signTransaction_eip1559_returnsParityAsV() = runTest {
        val transport = ReplayTransport(
            """
            => e004000046058000002c8000003c80000000000000000000000002ef0306843b9aca008504a817c80082520894b2bb2b958afa2e96dab3f3ce7162b87daea39017872386f26fc1000080c0
            <= 003ccff815955e82c416dcf2ace0bf0aa5f479acd47f5152f0c1753cf68fb723746774b8bff4f776f2788bb19c87c22ca8a2933ea4415eed8d25a9ea0500c81ce19000
            """,
        )

        val signature = EthereumAppClient(transport).signTransaction(PATH, EIP1559_TRANSFER.hexToByteArray())

        assertEquals(0, signature.v)
        assertEquals(0, signature.yParity)
        assertEquals("3ccff815955e82c416dcf2ace0bf0aa5f479acd47f5152f0c1753cf68fb72374", signature.r.toHexString())
        assertEquals("6774b8bff4f776f2788bb19c87c22ca8a2933ea4415eed8d25a9ea0500c81ce1", signature.s.toHexString())
        transport.assertAllReplayed()
    }

    @Test
    fun signTransaction_eip2930_returnsParityAsV() = runTest {
        val transport = ReplayTransport(
            """
            => e00400009e058000002c8000003c80000000000000000000000001f886030685012a05f20082520894b2bb2b958afa2e96dab3f3ce7162b87daea39017872386f26fc1000080f85bf85994de0b295669a9fd93d5f28d9ec85e40f4cb697baef842a00000000000000000000000000000000000000000000000000000000000000003a00000000000000000000000000000000000000000000000000000000000000007
            <= 01a74d82400f49d1f9d85f734c22a1648d4ab74bb6367bef54c6abb0936be3d8b77a84a09673394c3c1bd76be05620ee17a2d0ff32837607625efa433cc017854e9000
            """,
        )
        val raw = "01f886030685012a05f20082520894b2bb2b958afa2e96dab3f3ce7162b87daea39017872386f26fc1000080f85bf85994de0b295669a9fd93d5f28d9ec85e40f4cb697baef842a00000000000000000000000000000000000000000000000000000000000000003a00000000000000000000000000000000000000000000000000000000000000007"

        val signature = EthereumAppClient(transport).signTransaction(PATH, raw.hexToByteArray())

        assertEquals(1, signature.yParity)
        transport.assertAllReplayed()
    }

    // ledgerjs: v "134d2ff100edb3" = chainId * 2 + 35 + 0; the app keeps the low byte of its 4-high-byte variant.
    @Test
    fun signTransaction_legacySevenByteChainId_recoversParityFromTruncatedV() = runTest {
        val transport = ReplayTransport(
            """
            => e004000048058000002c8000003c800000008000000000000000f2018504e3b292008252089428ee52a8f3d6e5d15f8b131996950d7f296c7952872bd72a24874000808709a697f88076c88080
            <= 132ceca22fd700a75d8c18588cc4f5f8ba49ccbe583ddfb48f6b30186977f030312fb2c430662265c56fb09c0fcf0cfebde0e58d1a977d9c012b55c6c6cf66d99e9000
            """,
        )
        val raw = "f2018504e3b292008252089428ee52a8f3d6e5d15f8b131996950d7f296c7952872bd72a24874000808709a697f88076c88080"

        val signature = EthereumAppClient(transport).signTransaction(DerivationPath.parse("44'/60'/0'/0'/0"), raw.hexToByteArray())

        assertEquals(0x13, signature.v)
        assertEquals(0, signature.yParity)
        transport.assertAllReplayed()
    }

    // ledgerjs: v "0542b8613e" = chainId * 2 + 35 + 1.
    @Test
    fun signTransaction_legacyFiveByteChainId_recoversOddParity() = runTest {
        val transport = ReplayTransport(
            """
            => e00400004c058000002c8000003c800000008000000000000000f8358332d79f85072e9ad0f0830186a094810a9082d51802b2281d23e43e77dd846e51b8ee880194be7d2ebf07b3808502a15c308d8080
            <= 84d86809a5e283da8b967c38a1a6351f8c2bde177858449c26348f8d9ce876681672b0ae2862a68f35edc7a5b84d4a39299b98a4973452acf2c6571e9d4cf702d39000
            """,
        )
        val raw = "f8358332d79f85072e9ad0f0830186a094810a9082d51802b2281d23e43e77dd846e51b8ee880194be7d2ebf07b3808502a15c308d8080"

        val signature = EthereumAppClient(transport).signTransaction(DerivationPath.parse("44'/60'/0'/0'/0"), raw.hexToByteArray())

        assertEquals(1, signature.yParity)
        transport.assertAllReplayed()
    }

    @Test
    fun signTransaction_legacyOverTwoChunks_sendsReferenceChunks() = runTest {
        val raw = "f901ad8205448505c205da808310c8e19402b3f51ac9202aa19be63d61a8c681579d6e3a5180b90184293491160000000000000000000000000000000000000000000000000000000005ee832e0000000000000000000000000000000000000000000000000000000005eeb9ac000000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000000001000000000000000000000000505846a0a89dd26fa5cd0677fd5406039c218620000000000000000000000000000000000000000000000000000000001f969fc88a4d19062c1525d6ca664dee285aa573c06e0f8bdd4971032d2b63be6183d05300000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000041c4c3f1f8711741f2180d850a09a2933bb21dff1c79caf8c45ecda957836ec7e60d78661c28ad96713e5f22a458376422599bd3776d9aeafc02e319ed0c1b41e51c00000000000000000000000000000000000000000000000000000000000000018080"
        val transport = ReplayTransport(
            """
            => e0040000ff058000002c8000003c800000000000000000000000f901ad8205448505c205da808310c8e19402b3f51ac9202aa19be63d61a8c681579d6e3a5180b90184293491160000000000000000000000000000000000000000000000000000000005ee832e0000000000000000000000000000000000000000000000000000000005eeb9ac000000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000000001000000000000000000000000505846a0a89dd26fa5cd0677fd5406039c218620000000000000000000000000000000000000000000000000000000001f
            <= 9000
            => e0048000c6969fc88a4d19062c1525d6ca664dee285aa573c06e0f8bdd4971032d2b63be6183d05300000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000041c4c3f1f8711741f2180d850a09a2933bb21dff1c79caf8c45ecda957836ec7e60d78661c28ad96713e5f22a458376422599bd3776d9aeafc02e319ed0c1b41e51c00000000000000000000000000000000000000000000000000000000000000018080
            <= 26dc6ad1d9d847defdffde2f3b70004c89a1a8a6c614fec484891ae8f1ebc46f9966159ca542f5cf36d64278218bfcce24ba96d7495dec25b10a7609346ca063ec9000
            """,
        )

        val signature = EthereumAppClient(transport).signTransaction(PATH, raw.hexToByteArray())

        assertEquals(1, signature.yParity)
        transport.assertAllReplayed()
    }

    // Expected chunks recorded from ledgerjs hw-app-eth 6.47.1 `Eth.signTransaction`: 257 bytes would leave 2 in the last chunk.
    @Test
    fun signTransaction_legacyTailWouldBeSplit_shrinksChunks() = runTest {
        val header = "f8ea010182520894${"11".repeat(20)}80b8ca"
        val raw = header + "ab".repeat(202) + "018080"
        val transport = ReplayTransport(
            """
            => e0040000fd058000002c8000003c800000000000000000000000$header${"ab".repeat(201)}
            <= 9000
            => e004800004ab018080
            <= 26${"11".repeat(32)}${"22".repeat(32)}9000
            """,
        )

        EthereumAppClient(transport).signTransaction(PATH, raw.hexToByteArray())

        transport.assertAllReplayed()
    }

    @Test
    fun signTransaction_erc20AfterTokenInformation_sendsReferenceLog() = runTest {
        val transport = ReplayTransport(
            """
            => e00a000066035a5258e41d2489571d322189246dafa5ebde1f4699f4980000001200000001304402200ae8634c22762a8ba41d2acb1e068dcce947337c6dd984f13b820d396176952302203306a49d8a6c35b11a61088e1570b3928ca3a0db6bd36f577b5ef87628561ff7
            <= 009000
            => e004000085058000002c8000003c80000000000000000000000002f86d0106843b9aca008504a817c80082520894e41d2489571d322189246dafa5ebde1f4699f49800b844095ea7b3000000000000000000000000221657776846890989a759ba2973e427dff5c9bb0000000000000000000000000000000000000000000000004563918244f40000c0
            <= 00d6814aa5db69de910824b14462af006fde864224c616ab93e30f646e7309a93f0312ac6e580e918ce6e39e5f910cb95ba7b68167f4d71e581dec2495a198ecc09000
            """,
        )
        val client = EthereumAppClient(transport)

        client.provideErc20TokenInformation(ZRX_DESCRIPTOR.hexToByteArray())
        val signature = client.signTransaction(PATH, ERC20_APPROVE.hexToByteArray())

        assertEquals("d6814aa5db69de910824b14462af006fde864224c616ab93e30f646e7309a93f", signature.r.toHexString())
        transport.assertAllReplayed()
    }

    @Test
    fun signTransaction_incorrectDataWithContractData_throwsBlindSigningRequired() = runTest {
        assertFailsWith<LedgerException.BlindSigningRequired> {
            client(status = "6a80").signTransaction(PATH, ERC20_APPROVE.hexToByteArray())
        }
    }

    @Test
    fun signTransaction_incorrectDataWithLegacyContractData_throwsBlindSigningRequired() = runTest {
        assertFailsWith<LedgerException.BlindSigningRequired> {
            client(status = "6a80").signTransaction(PATH, LEGACY_WITH_DATA.hexToByteArray())
        }
    }

    @Test
    fun signTransaction_incorrectDataWithoutContractData_throwsInvalidRequest() = runTest {
        val error = assertFailsWith<LedgerException.InvalidRequest> {
            client(status = "6a80").signTransaction(PATH, EIP1559_TRANSFER.hexToByteArray())
        }

        assertEquals(0x6A80, error.statusWord)
    }

    @Test
    fun signTransaction_userRejects_throwsUserRejected() = runTest {
        assertFailsWith<LedgerException.UserRejected> {
            client(status = "6985").signTransaction(PATH, EIP1559_TRANSFER.hexToByteArray())
        }
    }

    @Test
    fun signTransaction_preEip155Legacy_sendsNothing() = runTest {
        val transport = ReplayTransport("")

        assertFailsWith<IllegalArgumentException> {
            EthereumAppClient(transport).signTransaction(PATH, "e8018504e3b292008252089428ee52a8f3d6e5d15f8b131996950d7f296c7952872bd72a2487400080".hexToByteArray())
        }
    }

    @Test
    fun signTransaction_unsupportedType_sendsNothing() = runTest {
        assertFailsWith<IllegalArgumentException> {
            EthereumAppClient(ReplayTransport("")).signTransaction(PATH, ("03" + EIP1559_TRANSFER.drop(2)).hexToByteArray())
        }
    }

    @Test
    fun signTransaction_trailingBytes_sendsNothing() = runTest {
        assertFailsWith<IllegalArgumentException> {
            EthereumAppClient(ReplayTransport("")).signTransaction(PATH, (EIP1559_TRANSFER + "00").hexToByteArray())
        }
    }

    @Test
    fun signTransaction_typedVAboveOne_throwsMalformedResponse() = runTest {
        assertFailsWith<LedgerException.MalformedResponse> {
            client("02" + "11".repeat(64)).signTransaction(PATH, EIP1559_TRANSFER.hexToByteArray())
        }
    }

    @Test
    fun signTransaction_legacyVOfAnotherChain_throwsMalformedResponse() = runTest {
        val raw = "f2018504e3b292008252089428ee52a8f3d6e5d15f8b131996950d7f296c7952872bd72a24874000808709a697f88076c88080"

        assertFailsWith<LedgerException.MalformedResponse> {
            client("26" + "11".repeat(64)).signTransaction(PATH, raw.hexToByteArray())
        }
    }

    @Test
    fun signTransaction_shortSignature_throwsMalformedResponse() = runTest {
        assertFailsWith<LedgerException.MalformedResponse> {
            client("00" + "11".repeat(63)).signTransaction(PATH, EIP1559_TRANSFER.hexToByteArray())
        }
    }

    @Test
    fun signTransaction_answerBeforeLastChunk_throwsMalformedResponse() = runTest {
        val transport = RecordingTransport { "26${"11".repeat(64)}9000".hexToByteArray() }

        assertFailsWith<LedgerException.MalformedResponse> {
            EthereumAppClient(transport).signTransaction(PATH, LEGACY_WITH_DATA.hexToByteArray())
        }
        assertEquals(1, transport.sent.size)
    }

    @Test
    fun signPersonalMessage_referenceLog_returnsVAsTwentySevenPlusParity() = runTest {
        val transport = ReplayTransport(
            """
            => e00800001d058000002c8000003c8000000080000000000000000000000474657374
            <= 1b8beafdd56521af1213d6d668a2aed262cc840e7174b642215aec013a1c88b2bd3a407b9125f1bfc015df6983ae8b87a34d54be367b4275834c3039622a73ee009000
            """,
        )

        val signature = EthereumAppClient(transport).signPersonalMessage(DerivationPath.parse("44'/60'/0'/0'/0"), "test".encodeToByteArray())

        assertEquals(27, signature.v)
        assertEquals(0, signature.yParity)
        assertEquals("8beafdd56521af1213d6d668a2aed262cc840e7174b642215aec013a1c88b2bd", signature.r.toHexString())
        transport.assertAllReplayed()
    }

    // Chunking of LedgerHQ/app-ethereum client command_builder.py `personal_sign`: the payload cut every 255 bytes.
    @Test
    fun signPersonalMessage_longMessage_chunksPayloadAt255Bytes() = runTest {
        val transport = RecordingTransport { apdu ->
            if (apdu[2].toInt() and 0xFF == 0x80 && apdu[4].toInt() and 0xFF < 255) "1c${"11".repeat(64)}9000".hexToByteArray()
            else "9000".hexToByteArray()
        }
        val message = ByteArray(600) { it.toByte() }

        val signature = EthereumAppClient(transport).signPersonalMessage(PATH, message)

        val payload = "058000002c8000003c80000000000000000000000000000258".hexToByteArray() + message
        assertEquals(listOf("e0080000", "e0088000", "e0088000"), transport.sent.map { it.copyOfRange(0, 4).toHexString() })
        assertEquals(payload.toHexString(), transport.sent.joinToString("") { it.copyOfRange(5, it.size).toHexString() })
        assertEquals(listOf(255, 255, payload.size - 510), transport.sent.map { it[4].toInt() and 0xFF })
        assertEquals(1, signature.yParity)
    }

    @Test
    fun signPersonalMessage_vBelowTwentySeven_throwsMalformedResponse() = runTest {
        assertFailsWith<LedgerException.MalformedResponse> {
            client("01" + "11".repeat(64)).signPersonalMessage(PATH, "test".encodeToByteArray())
        }
    }

    @Test
    fun provideErc20TokenInformation_badSignature_throwsInvalidRequest() = runTest {
        val error = assertFailsWith<LedgerException.InvalidRequest> {
            client(status = "6a80").provideErc20TokenInformation(ZRX_DESCRIPTOR.hexToByteArray())
        }

        assertEquals(0x6A80, error.statusWord)
    }

    @Test
    fun provideErc20TokenInformation_answerNotOneByte_throwsMalformedResponse() = runTest {
        listOf("", "deadbeef").forEach { answer ->
            assertFailsWith<LedgerException.MalformedResponse>(answer) { client(answer).provideErc20TokenInformation(ZRX_DESCRIPTOR.hexToByteArray()) }
        }
    }

    private fun client(answer: String = "", status: String = "9000") =
        EthereumAppClient(RecordingTransport { (answer + status).hexToByteArray() })

    private companion object {
        val PATH = DerivationPath.parse("44'/60'/0'/0/0")

        const val EIP1559_TRANSFER = "02ef0306843b9aca008504a817c80082520894b2bb2b958afa2e96dab3f3ce7162b87daea39017872386f26fc1000080c0"
        const val ERC20_APPROVE = "02f86d0106843b9aca008504a817c80082520894e41d2489571d322189246dafa5ebde1f4699f49800b844095ea7b3000000000000000000000000221657776846890989a759ba2973e427dff5c9bb0000000000000000000000000000000000000000000000004563918244f40000c0"
        val LEGACY_WITH_DATA = "f8ea010182520894${"11".repeat(20)}80b8ca${"ab".repeat(202)}018080"
        const val ZRX_DESCRIPTOR = "035a5258e41d2489571d322189246dafa5ebde1f4699f4980000001200000001304402200ae8634c22762a8ba41d2acb1e068dcce947337c6dd984f13b820d396176952302203306a49d8a6c35b11a61088e1570b3928ca3a0db6bd36f577b5ef87628561ff7"
    }
}
