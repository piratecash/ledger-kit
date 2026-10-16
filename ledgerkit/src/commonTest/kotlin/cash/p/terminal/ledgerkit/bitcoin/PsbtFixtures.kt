package cash.p.terminal.ledgerkit.bitcoin

import cash.p.terminal.ledgerkit.ByteReader
import cash.p.terminal.ledgerkit.DerivationPath
import kotlin.io.encoding.Base64

/** A PSBTv0 test fixture split into its raw maps, plus the [Psbt] model a caller would build from it. */
class PsbtV0Fixture(base64: String) {
    val global: List<Pair<ByteArray, ByteArray>>
    val inputs: List<List<Pair<ByteArray, ByteArray>>>
    val outputs: List<List<Pair<ByteArray, ByteArray>>>
    val unsignedTx: UnsignedTx

    init {
        val reader = ByteReader(Base64.decode(base64))
        check(reader.readBytes(5).toHexString() == "70736274ff") { "Not a PSBT" }
        global = readMap(reader)
        unsignedTx = UnsignedTx(global.single { it.first.contentEquals(byteArrayOf(0)) }.second)
        inputs = List(unsignedTx.inputs.size) { readMap(reader) }
        outputs = List(unsignedTx.outputs.size) { readMap(reader) }
        reader.requireEnd()
    }

    fun toPsbt(): Psbt = Psbt(
        txVersion = unsignedTx.version.leInt().toInt(),
        lockTime = unsignedTx.lockTime.leInt(),
        inputs = unsignedTx.inputs.zip(inputs) { txIn, map ->
            PsbtInput(
                previousTxid = txIn.txid,
                outputIndex = txIn.vout.leInt(),
                sequence = txIn.sequence.leInt(),
                nonWitnessUtxo = map.value(0x00),
                witnessUtxo = map.value(0x01)?.let { ByteReader(it).let { r -> TxOutput(r.readInt64Le(), r.readBytes(r.readLength())) } },
                redeemScript = map.value(0x04),
                bip32Derivation = map.derivation(0x06),
                tapBip32Derivation = map.derivation(0x16),
            )
        },
        outputs = unsignedTx.outputs.zip(outputs) { txOut, map ->
            PsbtOutput(
                amount = txOut.amount.leInt(),
                script = txOut.script,
                redeemScript = map.value(0x00),
                bip32Derivation = map.derivation(0x02),
                tapBip32Derivation = map.derivation(0x07),
            )
        },
    )

    class UnsignedTx(raw: ByteArray) {
        class In(val txid: ByteArray, val vout: ByteArray, val sequence: ByteArray)
        class Out(val amount: ByteArray, val script: ByteArray)

        val version: ByteArray
        val inputs: List<In>
        val outputs: List<Out>
        val lockTime: ByteArray

        init {
            val reader = ByteReader(raw)
            version = reader.readBytes(4)
            inputs = List(reader.readLength()) {
                In(reader.readBytes(32), reader.readBytes(4), reader.readBytes(reader.readLength()).let { reader.readBytes(4) })
            }
            outputs = List(reader.readLength()) { Out(reader.readBytes(8), reader.readBytes(reader.readLength())) }
            lockTime = reader.readBytes(4)
            reader.requireEnd()
        }
    }

    private fun readMap(reader: ByteReader): List<Pair<ByteArray, ByteArray>> = buildList {
        while (true) {
            val keyLength = reader.readLength()
            if (keyLength == 0) break
            add(reader.readBytes(keyLength) to reader.readBytes(reader.readLength()))
        }
    }

    private fun List<Pair<ByteArray, ByteArray>>.value(type: Int): ByteArray? =
        singleOrNull { it.first.size == 1 && it.first[0] == type.toByte() }?.second

    // Value: fingerprint and little-endian path; a taproot one is preceded by its (empty) leaf-hash list.
    private fun List<Pair<ByteArray, ByteArray>>.derivation(type: Int): KeyDerivation? {
        val (key, value) = singleOrNull { it.first.size > 1 && it.first[0] == type.toByte() } ?: return null
        val reader = ByteReader(value)
        if (type == 0x16 || type == 0x07) check(reader.readCompactSize() == 0L) { "Leaf hashes are not supported" }
        val fingerprint = reader.readBytes(4)
        val path = List(reader.remaining / 4) { reader.readBytes(4).leInt().toUInt() }
        return KeyDerivation(key.copyOfRange(1, key.size), fingerprint, DerivationPath(path))
    }
}

fun ByteArray.leInt(): Long = foldIndexed(0L) { index, value, byte -> value or ((byte.toLong() and 0xFF) shl (index * 8)) }

/**
 * Single-key PSBTs and their expected signatures from LedgerHQ/app-bitcoin-new
 * (`tests/psbt/singlesig`, `test_vectors/sign_psbt.toml`), testnet, speculos seed.
 */
object SignPsbtVectors {
    const val MASTER_FINGERPRINT = "f5acc2fd"

    class Case(
        val psbt: String,
        val type: BitcoinAddressType,
        val accountPath: String,
        val accountXpub: String,
        val signatures: List<Pair<String, String>>,
    ) {
        val policy: WalletPolicy
            get() = WalletPolicy.singleSig(type, MASTER_FINGERPRINT.hexToByteArray(), DerivationPath.parse(accountPath), accountXpub)
    }

    private const val PKH_XPUB =
        "tpubDCwYjpDhUdPGP5rS3wgNg13mTrrjBuG8V9VpWbyptX6TRPbNoZVXsoVUSkCjmQ8jJycjuDKBb9eataSymXakTTaGifxR6kmVsfFehH1ZgJT"
    private const val SH_WPKH_XPUB =
        "tpubDC871vGLAiKPcwAw22EjhKVLk5L98UGXBEcGR8gpcigLQVDDfgcYW24QBEyTHTSFEjgJgbaHU8CdRi9vmG4cPm1kPLmZhJEP17FMBdNheh3"
    private const val WPKH_XPUB =
        "tpubDCtKfsNyRhULjZ9XMS4VKKtVcPdVDi8MKUbcSD9MJDyjRu1A2ND5MiipozyyspBT9bg8upEp7a8EAgFxNxXn1d7QkdbL52Ty5jiSLcxPt1P"
    private const val TR_XPUB =
        "tpubDDKYE6BREvDsSWMazgHoyQWiJwYaDDYPbCFjYxN3HFXJP5fokeiK4hwK5tTLBNEDBwrDXn8cQ4v9b2xdW62Xr5yxoQdMu1v6c7UDXYVH27U"

    val pkh1to1 = Case(
        psbt = "cHNidP8BAFUCAAAAAVEiws3mgj5VdUF1uSycV6Co4ayDw44Xh/06H/M0jpUTAQAAAAD9////AXhBDwAAAAAAGXapFBPX1YFmlGw+wCKTQGbYwNER0btBiKwaBB0AAAEA+QIAAAAAAQHsIw5TCVJWBSokKCcO7ASYlEsQ9vHFePQxwj0AmLSuWgEAAAAXFgAUKBU5gg4t6XOuQbpgBLQxySHE2G3+////AnJydQAAAAAAF6kUyLkGrymMcOYDoow+/C+uGearKA+HQEIPAAAAAAAZdqkUy65bUM+Tnm9TG4prer14j+FLApeIrAJHMEQCIDfstCSDYar9T4wR5wXw+npfvc1ZUXL81WQ/OxG+/11AAiACDG0yb2w31jzsra9OszX67ffETgX17x0raBQLAjvRPQEhA9rIL8Cs/Pw2NI1KSKRvAc6nfyuezj+MO0yZ0LCy+ZXShPIcACIGAu6GCCB+IQKEJvaedkR9fj1eB3BJ9eaDwxNsIxR2KkcYGPWswv0sAACAAQAAgAAAAIAAAAAAAAAAAAAA",
        type = BitcoinAddressType.LEGACY,
        accountPath = "44'/1'/0'",
        accountXpub = PKH_XPUB,
        signatures = listOf(
            "02ee8608207e21028426f69e76447d7e3d5e077049f5e683c3136c2314762a4718" to
                "3045022100e55b3ca788721aae8def2eadff710e524ffe8c9dec1764fdaa89584f9726e196022012a30fbcf9e1a24df31a1010356b794ab8de438b4250684757ed5772402540f401",
        ),
    )

    val shWpkh1to2 = Case(
        psbt = "cHNidP8BAHICAAAAAXT0yaTajRSLu1boaayjaQ3aDOOsvPgWCcyUbRtvFkrOAQAAAAD9////AlDUEgAAAAAAFgAUMxjgT65sEq/LAJxpzVflslBK5rT1cQgAAAAAABepFG1IUtrzpUCfdyFtu46j1ZIxLX7phwAAAAAAAQCMAgAAAAHQ47WR3EhO23HqtmoOmUcxAH/rfQgqUMdC8CPqCQFNHgEAAAAXFgAU4xDQRPiNqxtCdp5KhMrwg2P57MH9////AmDqAAAAAAAAGXapFEWIHtDTWHVQ95SEe3yLn6A+3Qo8iKx/ZhsAAAAAABepFPBGTZ+g6kLYDk1fFFeIOYLiO47shwAAAAABASB/ZhsAAAAAABepFPBGTZ+g6kLYDk1fFFeIOYLiO47shwEEFgAUyweAh+/0haqiJg6UpT19bRxd0VEiBgJLo7d9kz3p+j+VgzSMQPPKry7/rVtuJE7Oirv8xyRPZxj1rML9MQAAgAEAAIAAAACAAQAAAAAAAAAAAAEAFgAUTLRHxTu3NSNPKxOQ1F2dhksVdtMiAgOKsR70a0i1XwDFPv3fOM3f+dYzW8r1L6n5k4R/LM0vVxj1rML9MQAAgAEAAIAAAACAAQAAAAIAAAAA",
        type = BitcoinAddressType.NESTED_SEGWIT,
        accountPath = "49'/1'/0'",
        accountXpub = SH_WPKH_XPUB,
        signatures = listOf(
            "024ba3b77d933de9fa3f9583348c40f3caaf2effad5b6e244ece8abbfcc7244f67" to
                "30440220720722b08489c2a50d10edea8e21880086c8e8f22889a16815e306daeea4665b02203fcf453fa490b76cf4f929714065fc90a519b7b97ab18914f9451b5a4b45241201",
        ),
    )

    val wpkh1to2 = Case(
        psbt = "cHNidP8BAHQCAAAAAXoqmXlWwJ+Op/0oGcGph7sU4iv5rc2vIKiXY3Is7uJkAQAAAAD9////AqC7DQAAAAAAGXapFDRKD0jKFQ7CuQOBdmC5tosTpnAmiKx0OCMAAAAAABYAFOs4+puBKPgfJule2wxf+uqDaQ/kAAAAAAABAH0CAAAAAa+/rgZZD3Qf8a9ZtqxGESYzakxKgttVPfb++rc3rDPzAQAAAAD9////AnARAQAAAAAAIgAg/e5EHFblsG0N+CwSTHBwFKXKGWWL4LmFa8oW8e0yWfel9DAAAAAAABYAFDr4QprVlUql7oozyYP9ih6GeZJLAAAAAAEBH6X0MAAAAAAAFgAUOvhCmtWVSqXuijPJg/2KHoZ5kksiBgPuLD2Y6x+TwKGqjlpACbcOt7ROrRXxZm8TawEq1Y0waBj1rML9VAAAgAEAAIAAAACAAQAAAAgAAAAAACICAinsR3JxMe0liKIMRu2pq7fapvSf1Quv5wucWqaWHE7MGPWswv1UAACAAQAAgAAAAIABAAAACgAAAAA=",
        type = BitcoinAddressType.NATIVE_SEGWIT,
        accountPath = "84'/1'/0'",
        accountXpub = WPKH_XPUB,
        signatures = listOf(
            "03ee2c3d98eb1f93c0a1aa8e5a4009b70eb7b44ead15f1666f136b012ad58d3068" to
                "3045022100ab44f34dd7e87c9054591297a101e8500a0641d1d591878d0d23cf8096fa79e802205d12d1062d925e27b57bdcf994ecf332ad0a8e67b8fe407bab2101255da632aa01",
        ),
    )

    val wpkh2to2 = Case(
        psbt = "cHNidP8BAKYCAAAAAp4s/ifwrYe3iiN9XXQF1KMGZso2HVhaRnsN/kImK020AQAAAAD9////r7+uBlkPdB/xr1m2rEYRJjNqTEqC21U99v76tzesM/MAAAAAAP3///8CqDoGAAAAAAAWABTrOPqbgSj4HybpXtsMX/rqg2kP5OCTBAAAAAAAIgAgP6lmyd3Nwv2W5KXhvHZbn69s6LPrTxEEqta993Mk5b4AAAAAAAEAcQIAAAABk2qy4BBy95PP5Ml3VN4bYf4D59tlNsiy8h3QtXQsSEUBAAAAAP7///8C3uHHAAAAAAAWABTreNfEC/EGOw4/zinDVltonIVZqxAnAAAAAAAAFgAUIxjWb4T+9cSHX5M7A43GODH42hP5lx4AAQEfECcAAAAAAAAWABQjGNZvhP71xIdfkzsDjcY4MfjaEyIGA0Ve587cl7C6Q1uABm/JLJY6NMYAMXmB0TUzDE7kOsejGPWswv1UAACAAQAAgAAAAIAAAAAAAQAAAAABAHEBAAAAAQ5HHvTpLBrLUe/IZg+NP2mTbqnJsr/3L/m8gcUe/PRkAQAAAAAAAAAAAmCuCgAAAAAAFgAUNcbg3W08hLFrqIXcpzrIY9C1k+yvBjIAAAAAABYAFNwobgzS5r03zr6ew0n7XwiQVnL8AAAAAAEBH2CuCgAAAAAAFgAUNcbg3W08hLFrqIXcpzrIY9C1k+wiBgJxtbd5rYcIOFh3l7z28MeuxavnanCdck9I0uJs+HTwoBj1rML9VAAAgAEAAIAAAACAAQAAAAAAAAAAIgICKexHcnEx7SWIogxG7amrt9qm9J/VC6/nC5xappYcTswY9azC/VQAAIABAACAAAAAgAEAAAAKAAAAAAA=",
        type = BitcoinAddressType.NATIVE_SEGWIT,
        accountPath = "84'/1'/0'",
        accountXpub = WPKH_XPUB,
        signatures = listOf(
            "03455ee7cedc97b0ba435b80066fc92c963a34c600317981d135330c4ee43ac7a3" to
                "304402206b3e877655f08c6e7b1b74d6d893a82cdf799f68a5ae7cecae63a71b0339e5ce022019b94aa3fb6635956e109f3d89c996b1bfbbaf3c619134b5a302badfaf52180e01",
            "0271b5b779ad870838587797bcf6f0c7aec5abe76a709d724f48d2e26cf874f0a0" to
                "3045022100e2e98e4f8c70274f10145c89a5d86e216d0376bdf9f42f829e4315ea67d79d210220743589fd4f55e540540a976a5af58acd610fa5e188a5096dfe7d36baf3afb94001",
        ),
    )

    // Schnorr signatures are not deterministic; the vector gives the key and the BIP-341 sighash instead.
    val tr1to2 = Case(
        psbt = "cHNidP8BAH0CAAAAAeFoYcDSl0n1LNLt3hDLzE9ZEhBxD2QOXY4UQM6F2W3GAQAAAAD9////Ao00lwAAAAAAIlEgC450hrwwagrvt6fACvBAVULbGs1z7syoJ3HM9f5etg+ghgEAAAAAABYAFBOZuKCYR6A5sDUvWNISwYC6sX93AAAAAAABASvfu5gAAAAAACJRIImQSmNI1/+aRNSduLaoB8Yi6Gg2TFR9pCbzC1piExhqIRbpxpsJXtBLVir8jUFpGTa6Vz629om8I2YAvk+jkm9kEhkA9azC/VYAAIABAACAAAAAgAEAAAADAAAAARcg6cabCV7QS1Yq/I1BaRk2ulc+tvaJvCNmAL5Po5JvZBIAAQUgApCB7OVhaqHLmTGfxIdO/uR/CM66X2AEY2yMQ0CaXwohBwKQgezlYWqhy5kxn8SHTv7kfwjOul9gBGNsjENAml8KGQD1rML9VgAAgAEAAIAAAACAAQAAAAIAAAAAAA==",
        type = BitcoinAddressType.TAPROOT,
        accountPath = "86'/1'/0'",
        accountXpub = TR_XPUB,
        signatures = listOf(
            "89904a6348d7ff9a44d49db8b6a807c622e868364c547da426f30b5a6213186a" to
                "75c96fb06a12db4cd011d8c95a5995db758a4f2837a22f30f0f579619a4466f3",
        ),
    )

    val ecdsaCases: List<Case> = listOf(pkh1to1, shWpkh1to2, wpkh1to2, wpkh2to2)
    val allCases: List<Case> = ecdsaCases + tr1to2
}
