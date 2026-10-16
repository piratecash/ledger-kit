package cash.p.terminal.ledgerkit.bitcoin

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.Speculos
import cash.p.terminal.ledgerkit.ins
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The "Bitcoin Test" app on speculos against LedgerHQ/app-bitcoin-new `test_vectors/`
 * (get_extended_pubkey.toml, get_wallet_address.toml, sign_psbt.toml).
 */
class BitcoinTestnetSpeculosTest {
    private val client get() = BitcoinAppClient(Speculos.transport("Bitcoin Test"))

    @Test
    fun getMasterFingerprint_speculosSeed_returnsReferenceFingerprint() = runBlocking {
        assertEquals(SignPsbtVectors.MASTER_FINGERPRINT, client.getMasterFingerprint().toHexString())
    }

    @Test
    fun getExtendedPubkey_standardPaths_returnReferenceXpubs() = runBlocking {
        val expected = mapOf(
            "m/44'/1'/0'" to "tpubDCwYjpDhUdPGP5rS3wgNg13mTrrjBuG8V9VpWbyptX6TRPbNoZVXsoVUSkCjmQ8jJycjuDKBb9eataSymXakTTaGifxR6kmVsfFehH1ZgJT",
            "m/49'/1'/1'/1/3" to "tpubDGnetmJDCL18TyaaoyRAYbkSE9wbHktSdTS4mfsR6inC8c2r6TjdBt3wkqEQhHYPtXpa46xpxDaCXU2PRNUGVvDzAHPG6hHRavYbwAGfnFr",
            "m/84'/1'/2'/0/10" to "tpubDG9YpSUwScWJBBSrhnAT47NcT4NZGLcY18cpkaiWHnkUCi19EtCh8Heeox268NaFF6o56nVeSXuTyK6jpzTvV1h68Kr3edA8AZp27MiLUNt",
            "m/86'/1'/4'/1/12" to "tpubDHTZ815MvTaRmo6Qg1rnU6TEU4ZkWyA56jA1UgpmMcBGomnSsyo34EZLoctzZY9MTJ6j7bhccceUeXZZLxZj5vgkVMYfcZ7DNPsyRdFpS3f",
        )

        expected.forEach { (path, xpub) -> assertEquals(xpub, client.getExtendedPubkey(DerivationPath.parse(path)), path) }
    }

    @Test
    fun getWalletAddress_defaultPolicies_returnReferenceAddresses() = runBlocking {
        val expected = listOf(
            Triple(SignPsbtVectors.pkh1to1, false to 0, "mz5vLWdM1wHVGSmXUkhKVvZbJ2g4epMXSm"),
            Triple(SignPsbtVectors.pkh1to1, true to 15, "myFCUBRCKFjV7292HnZtiHqMzzHrApobpT"),
            Triple(SignPsbtVectors.shWpkh1to2, false to 0, "2MyHkbusvLomaarGYMqyq7q9pSBYJRwWcsw"),
            Triple(SignPsbtVectors.wpkh1to2, false to 0, "tb1qzdr7s2sr0dwmkwx033r4nujzk86u0cy6fmzfjk"),
            Triple(SignPsbtVectors.wpkh1to2, true to 15, "tb1qlrvzyx8jcjfj2xuy69du9trtxnsvjuped7e289"),
            Triple(SignPsbtVectors.tr1to2, false to 0, "tb1pws8wvnj99ca6acf8kq7pjk7vyxknah0d9mexckh5s0vu2ccy68js9am6u7"),
            Triple(SignPsbtVectors.tr1to2, true to 9, "tb1p98d6s9jkf0la8ras4nnm72zme5r03fexn29e3pgz4qksdy84ndpqgjak72"),
        )

        expected.forEach { (case, location, address) ->
            val (change, index) = location
            assertEquals(address, client.getWalletAddress(case.policy, change, index), "${case.type} $change/$index")
        }
    }

    @Test
    fun signPsbt_ecdsaReferencePsbts_returnReferenceSignatures() = runBlocking {
        SignPsbtVectors.ecdsaCases.forEach { case ->
            val signatures = Speculos.approving { client.signPsbt(PsbtV0Fixture(case.psbt).toPsbt(), case.policy) }

            assertEquals(
                case.signatures.mapIndexed { index, (key, signature) -> Triple(index, key, signature) },
                signatures.map { Triple(it.inputIndex, it.publicKey.toHexString(), it.signature.toHexString()) },
                case.type.name,
            )
        }
    }

    // The app asks for the preimage of a policy id the client never registered; the next command must still run.
    @Test
    fun getWalletAddress_requestClientCannotAnswer_leavesAppReadyForNextCommand() = runBlocking {
        val device = Speculos.transport("Bitcoin Test")
        val unknownPolicyId = object : LedgerTransport by device {
            override suspend fun exchange(apdu: ByteArray): ByteArray =
                device.exchange(if (apdu.ins() == GET_WALLET_ADDRESS) apdu.copyOf().also { it.fill(0x11, POLICY_ID_OFFSET, POLICY_ID_OFFSET + 32) } else apdu)
        }

        assertFailsWith<IllegalStateException> {
            BitcoinAppClient(unknownPolicyId).getWalletAddress(SignPsbtVectors.wpkh1to2.policy, change = false, addressIndex = 0)
        }

        assertEquals(SignPsbtVectors.MASTER_FINGERPRINT, client.getMasterFingerprint().toHexString())
    }

    // BIP-340 signatures are randomized: the key and length are checked here, the signature against the digest offline.
    @Test
    fun signPsbt_taprootReferencePsbt_signsWithOutputKey() = runBlocking {
        val case = SignPsbtVectors.tr1to2

        val signatures = Speculos.approving { client.signPsbt(PsbtV0Fixture(case.psbt).toPsbt(), case.policy) }

        val signature = signatures.single()
        assertEquals(case.signatures.single().first, signature.publicKey.toHexString())
        assertEquals(64, signature.signature.size)
    }

    private companion object {
        const val GET_WALLET_ADDRESS = 0x03
        const val POLICY_ID_OFFSET = 6
    }
}
