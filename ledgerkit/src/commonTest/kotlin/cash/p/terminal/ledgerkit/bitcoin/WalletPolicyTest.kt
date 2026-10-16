package cash.p.terminal.ledgerkit.bitcoin

import cash.p.terminal.ledgerkit.DerivationPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// Expected ids: LedgerHQ/app-bitcoin-new test_vectors/register_wallet.toml.
class WalletPolicyTest {
    @Test
    fun id_singleKeyNamedPolicy_matchesReferenceId() {
        val policy = WalletPolicy(
            name = "Unusual native_segwit",
            descriptorTemplate = "wpkh(@0/**)",
            keys = listOf(
                "[f5acc2fd/44'/1'/3']tpubDCwYjpDhUdPGW815u9VExvLRXDAHehcnkNfjqiwSvJp7NizpAGsX3Qfq9A173rES9vF17HgeqXBUvodRTyeTHCeAvg7gVgDE19mudggheN1",
            ),
        )

        assertEquals("d5de0973b368d69f67166391d66b8ead31478a6e7dda5c9acbeff185523e1f64", policy.id.toHexString())
    }

    @Test
    fun id_twoKeyPolicy_matchesReferenceId() {
        val policy = WalletPolicy(
            name = "Cold storage",
            descriptorTemplate = "sh(sortedmulti(2,@0/**,@1/**))",
            keys = listOf(
                "[5c9e228d/48'/1'/0'/0']tpubDEGquuorgFNb8bjh5kNZQMPtABJzoWwNm78FUmeoPkfRtoPF7JLrtoZeT3J3ybq1HmC3Rn1Q8wFQ8J5usanzups5rj7PJoQLNyvq8QbJruW",
                "[f5acc2fd/48'/1'/0'/0']tpubDFAqEGNyad35WQAZMmPD4vgBXnjH16RGciLdWekPe4f4d5JzoHVu1PS86Sy4Tm63vDf8rfV3UjifhrRuSUDfiZj5KPffTPyZ4ZXBKvjD8jm",
            ),
        )

        assertEquals("1d150ed425a871a5ca7e2c55db1b3295c1fd97147fd0a7b188d4327f4ba7402a", policy.id.toHexString())
    }

    @Test
    fun id_threeKeyPolicy_matchesReferenceId() {
        val policy = WalletPolicy(
            name = "Taproot single-key or multisig 2-of-2",
            descriptorTemplate = "tr(@0/**,sortedmulti_a(2,@1/**,@2/**))",
            keys = listOf(
                "[f5acc2fd/48'/1'/0'/1']tpubDFAqEGNyad35YgH8zxvxFZqNUoPtr5mDojs7wzbXQBHTZ4xHeVXG6w2HvsKvjBpaRpTmjYDjdPg5w2c6Wvu8QBkyMDrmBWdCyqkDM7reSsY",
                "[76223a6e/48'/1'/0'/2']tpubDE7NQymr4AFtewpAsWtnreyq9ghkzQBXpCZjWLFVRAvnbf7vya2eMTvT2fPapNqL8SuVvLQdbUbMfWLVDCZKnsEBqp6UK93QEzL8Ck23AwF",
                "[f5acc2fd/48'/1'/0'/2']tpubDFAqEGNyad35aBCKUAXbQGDjdVhNueno5ZZVEn3sQbW5ci457gLR7HyTmHBg93oourBssgUxuWz1jX5uhc1qaqFo9VsybY1J5FuedLfm4dK",
            ),
        )

        assertEquals("46c498a049e164ec7feaf167c3ae0f4282217645a338f66f5377bfe67f6a8aa0", policy.id.toHexString())
    }

    // Key info as LedgerHQ/app-bitcoin-new test_vectors/get_wallet_address.toml spells it.
    @Test
    fun singleSig_nativeSegwitAccount_buildsReferenceKeyInfoAndTemplate() {
        val xpub = "tpubDCtKfsNyRhULjZ9XMS4VKKtVcPdVDi8MKUbcSD9MJDyjRu1A2ND5MiipozyyspBT9bg8upEp7a8EAgFxNxXn1d7QkdbL52Ty5jiSLcxPt1P"

        val policy = WalletPolicy.singleSig(
            BitcoinAddressType.NATIVE_SEGWIT,
            "f5acc2fd".hexToByteArray(),
            DerivationPath.parse("m/84'/1'/0'"),
            xpub,
        )

        assertEquals(listOf("[f5acc2fd/84'/1'/0']$xpub"), policy.keys)
        assertEquals("wpkh(@0/**)", policy.descriptorTemplate)
        assertEquals("", policy.name)
    }

    @Test
    fun descriptorTemplate_defaultPolicies_matchReferenceTemplates() {
        assertEquals(
            listOf("pkh(@0/**)", "sh(wpkh(@0/**))", "wpkh(@0/**)", "tr(@0/**)"),
            BitcoinAddressType.entries.map { it.descriptorTemplate },
        )
    }

    @Test
    fun singleSig_shortFingerprint_throws() {
        assertFailsWith<IllegalArgumentException> {
            WalletPolicy.singleSig(BitcoinAddressType.TAPROOT, ByteArray(3), DerivationPath.parse("86'/0'/0'"), "xpub")
        }
    }
}
