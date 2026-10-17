package cash.p.terminal.ledgerkit.zcash

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.DerivationPath.Companion.HARDENED
import cash.p.terminal.ledgerkit.apdu.Apdu
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.apdu.StatusWord
import cash.p.terminal.ledgerkit.apdu.appError
import cash.p.terminal.ledgerkit.apdu.requireAnswerSize
import cash.p.terminal.ledgerkit.apdu.send
import cash.p.terminal.ledgerkit.apdu.sendChunks
import cash.p.terminal.ledgerkit.bitcoin.SIGHASH_ALL
import cash.p.terminal.ledgerkit.bitcoin.isStrictDerSignature
import cash.p.terminal.ledgerkit.readUInt16
import cash.p.terminal.ledgerkit.transport.LedgerTransport

/**
 * The Zcash app (CLA E0), 3.9 or later: unified viewing keys and addresses of transparent + Orchard accounts and
 * PCZT signing. Commands and status words follow LedgerHQ/app-zcash `docs/APDU.md` and `docs/PCZT_APDU.md`; the
 * signing flow follows device-sdk-ts signer-zcash `SignPcztTransactionTask.ts`. Transparent addresses come from
 * the legacy `GET_WALLET_PUBLIC_KEY`, which [cash.p.terminal.ledgerkit.bitcoin.BtchipLegacyClient] speaks.
 */
public class ZcashAppClient(private val transport: LedgerTransport) {

    /** The `uview1…` key of [account]'s transparent and Orchard pools; the device always asks the user first. */
    public suspend fun getUnifiedFullViewingKey(account: Int): String {
        val first = Apdu(CLA, GET_VK, P1_FIRST, P2_UNIFIED, orchardPath(account) + transparentAccountPath(account).toApduBytes())
        val answer = collectViewingKey(transport.send(first, ::zcashError))
        return readString(answer, UFVK_HRP, "unified full viewing key")
    }

    /**
     * [account]'s Orchard-only `u1…` address; [display] asks the user to confirm it, shown with the transparent
     * address of the account's first external key.
     */
    public suspend fun getUnifiedAddress(account: Int, display: Boolean = false): String {
        val path = DerivationPath(transparentAccountPath(account).indices + listOf(0u, 0u))
        val apdu = Apdu(CLA, GET_SHIELD_ADDR, if (display) 1 else 0, P2_UNIFIED, orchardPath(account) + path.toApduBytes())
        return readString(transport.send(apdu, ::zcashError), UNIFIED_ADDRESS_HRP, "unified address")
    }

    /**
     * Streams [transaction], which the user reviews when the last packet arrives, then collects a signature for
     * every [ZcashActionBundle.signIndices] entry and every transparent input.
     */
    public suspend fun signTransaction(transaction: ZcashTransaction): ZcashSignatures {
        val header = transport.send(Apdu(CLA, PCZT_HEADER, P1_FIRST, P2_CONTINUE, transaction.headerPacket()), ::zcashError)
        header.requireAnswerSize(0, "PCZT header answer")
        val commands = buildList {
            add(PCZT_TRANSPARENT_INPUT to transparentInputPackets(transaction.transparentInputs))
            add(PCZT_TRANSPARENT_OUTPUT to transparentOutputPackets(transaction.transparentOutputs))
            add(PCZT_ORCHARD_ACTION to actionPackets(transaction.orchard, ironwood = false))
            if (transaction.isV6) add(PCZT_IRONWOOD_ACTION to actionPackets(transaction.ironwood, ironwood = true))
        }
        commands.forEachIndexed { index, (ins, packets) -> sendPackets(ins, packets, finished = index == commands.lastIndex) }
        return ZcashSignatures(
            orchard = spendAuthSignatures(SIGN_ORCHARD, transaction.orchard),
            ironwood = spendAuthSignatures(SIGN_IRONWOOD, transaction.ironwood),
            transparent = transaction.transparentInputs.indices.associateWith { transparentSignature(it) },
        )
    }

    private suspend fun collectViewingKey(first: ByteArray): ByteArray {
        if (first.size < LENGTH_SIZE) throw LedgerException.MalformedResponse("viewing key length")
        val total = LENGTH_SIZE + first.readUInt16(0)
        var answer = first
        repeat(MAX_VK_CONTINUATIONS) {
            if (answer.size >= total) return answer
            val next = transport.send(Apdu(CLA, GET_VK, P1_CONTINUE, P2_UNIFIED), ::zcashError)
            if (next.isEmpty()) throw LedgerException.MalformedResponse("empty viewing key continuation")
            answer += next
        }
        return answer
    }

    private suspend fun sendPackets(ins: Int, packets: List<ByteArray>, finished: Boolean) {
        val answer = transport.sendChunks(packets, ::zcashError) { index, packet ->
            val last = index == packets.lastIndex
            val p1 = when {
                index == 0 -> P1_FIRST
                last -> P1_LAST
                else -> P1_CONTINUE
            }
            Apdu(CLA, ins, p1, if (finished && last) P2_FINISHED else P2_CONTINUE, packet)
        }
        answer.requireAnswerSize(0, "PCZT packet answer")
    }

    private suspend fun spendAuthSignatures(ins: Int, bundle: ZcashActionBundle?): Map<Int, ByteArray> =
        bundle?.signIndices.orEmpty().associateWith { index ->
            transport.send(Apdu(CLA, ins, P1_FIRST, index), ::zcashError)
                .requireAnswerSize(SPEND_AUTH_SIGNATURE_SIZE, "spend authorization signature")
        }

    private suspend fun transparentSignature(index: Int): ByteArray {
        val signature = transport.send(Apdu(CLA, SIGN_TRANSPARENT, P1_FIRST, index), ::zcashError)
        if (!isStrictDerSignature(signature, SIGHASH_ALL)) throw LedgerException.MalformedResponse("transparent signature")
        return signature
    }

    private companion object {
        const val CLA = 0xE0
        const val GET_VK = 0x50
        const val GET_SHIELD_ADDR = 0x51
        const val PCZT_HEADER = 0x52
        const val PCZT_TRANSPARENT_INPUT = 0x53
        const val PCZT_TRANSPARENT_OUTPUT = 0x54
        const val SIGN_TRANSPARENT = 0x55
        const val PCZT_ORCHARD_ACTION = 0x56
        const val SIGN_ORCHARD = 0x57
        const val PCZT_IRONWOOD_ACTION = 0x58
        const val SIGN_IRONWOOD = 0x59

        const val P1_FIRST = 0x00
        const val P1_CONTINUE = 0x80
        const val P1_LAST = 0x01
        const val P2_UNIFIED = 0x00
        const val P2_CONTINUE = 0x00
        const val P2_FINISHED = 0x01

        const val LENGTH_SIZE = 2
        const val SPEND_AUTH_SIGNATURE_SIZE = 64

        // A UFVK is about 300 characters; the app's own test client stops after four continuations.
        const val MAX_VK_CONTINUATIONS = 4

        const val COIN_TYPE = 133u
        const val ORCHARD_PURPOSE = 32u
        const val BIP44_PURPOSE = 44u

        const val UFVK_HRP = "uview"
        const val UNIFIED_ADDRESS_HRP = "u"

        fun accountIndex(account: Int): UInt {
            require(account >= 0) { "Negative account $account" }
            return account.toUInt() + HARDENED
        }

        fun orchardPath(account: Int): ByteArray =
            DerivationPath(listOf(ORCHARD_PURPOSE + HARDENED, COIN_TYPE + HARDENED, accountIndex(account))).toApduBytes()

        fun transparentAccountPath(account: Int): DerivationPath =
            DerivationPath(listOf(BIP44_PURPOSE + HARDENED, COIN_TYPE + HARDENED, accountIndex(account)))

        /** A big-endian length and that many bytes of bech32m under [hrp], nothing after. */
        fun readString(answer: ByteArray, hrp: String, what: String): String {
            if (answer.size < LENGTH_SIZE || answer.size != LENGTH_SIZE + answer.readUInt16(0)) {
                throw LedgerException.MalformedResponse("$what length")
            }
            val text = answer.decodeToString(LENGTH_SIZE, answer.size)
            if (!isBech32m(text, hrp)) throw LedgerException.MalformedResponse(what)
            return text
        }
    }
}

private val INVALID_REQUEST_STATUS_WORDS = setOf(0x6700, 0x6986, 0x6A80, 0x6B00, 0x6F02, 0xB007)

/** Key and signing commands share a per-run budget of Orchard derivations; past it they answer "not enough memory". */
private fun zcashError(statusWord: Int): LedgerException =
    if (statusWord == StatusWord.DATA_TOO_LARGE) {
        LedgerException.KeyDerivationLimit()
    } else {
        appError(statusWord, INVALID_REQUEST_STATUS_WORDS)
    }
