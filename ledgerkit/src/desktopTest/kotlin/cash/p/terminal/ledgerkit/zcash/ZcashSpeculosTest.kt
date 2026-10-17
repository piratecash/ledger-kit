package cash.p.terminal.ledgerkit.zcash

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.Speculos
import cash.p.terminal.ledgerkit.bitcoin.BtchipLegacyClient
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The "Zcash" app on speculos. The viewing key and unified address are the references of LedgerHQ/app-zcash
 * tests/standalone/test_pubkey_cmd.py; the transparent address was derived from the speculos seed with embit 0.8.0
 * (BIP-32 `m/44'/133'/0'/0/0`, Base58Check with the `t1` version bytes 1cb8).
 */
class ZcashSpeculosTest {
    private val transport get() = Speculos.transport("Zcash")

    @Test
    fun getUnifiedFullViewingKey_account0_matchesAppReference() = runBlocking {
        val key = Speculos.approving { ZcashAppClient(transport).getUnifiedFullViewingKey(account = 0) }

        assertEquals(UFVK_ACC_0, key)
    }

    @Test
    fun getUnifiedAddress_account0_matchesAppReference() = runBlocking {
        assertEquals(ADDRESS_ACC_0, ZcashAppClient(transport).getUnifiedAddress(account = 0))
    }

    @Test
    fun getUnifiedAddress_displayed_returnsTheConfirmedAddress() = runBlocking {
        val address = Speculos.approving { ZcashAppClient(transport).getUnifiedAddress(account = 0, display = true) }

        assertEquals(ADDRESS_ACC_0, address)
    }

    @Test
    fun getWalletPublicKey_firstTransparentAddress_matchesIndependentDerivation() = runBlocking {
        val key = Speculos.approving { BtchipLegacyClient(transport).getWalletPublicKey(TRANSPARENT_PATH, display = true) }

        assertEquals(TRANSPARENT_ADDRESS, key.address)
        assertEquals(TRANSPARENT_PUBLIC_KEY, key.publicKey.toHexString())
    }

    private companion object {
        val TRANSPARENT_PATH = DerivationPath.parse("m/44'/133'/0'/0/0")
        const val TRANSPARENT_ADDRESS = "t1LBsxhHpmugntmxBVBNh6MSvq2CmUE6g9X"
        const val TRANSPARENT_PUBLIC_KEY = "02749c3f99dd136601daa824ecf40ae144c1a7de432bf22dbb23c81c7b6077d431"
        const val UFVK_ACC_0 =
            "uview1zkk7f8hp2m5v09kq7h29vkgngwhhvgy2ey32cy5j0kp69g7ju2vqjvnue03u99z382rtkgvj3f8vtqdtxfxvgjytezgt39dqc0lyt2" +
                "sj084jdq4md69snc3wxdcl8uah8sxw3rrt9pnxnfl3r4xnczapts7gr4l0cuell7dcjv36gkdcsl4axps827xt6fgmfl78zlhddec72t" +
                "n2p0eqnpkuy7a08puhj97v0ahxuqlyzmyqtldqnc0p3696d9ww8x6mpd56mz6w32twryevru2rx34lf8dtqsp50gar"
        const val ADDRESS_ACC_0 =
            "u1u2h4ce7e2cn3z4nzur95muq2dl4da9x8h8kdp2l80gm9nl9raj8zzpx79ycjnfvar4v5exea5pqr5y9qsnlp0cdunwf9yjjx5c4q7ar9"
    }
}
