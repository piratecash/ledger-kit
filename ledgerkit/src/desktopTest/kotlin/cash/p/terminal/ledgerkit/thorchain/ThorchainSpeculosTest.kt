package cash.p.terminal.ledgerkit.thorchain

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.Speculos
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The "THORChain" app on speculos. The expected key and address were derived from the speculos seed with embit 0.8.0
 * (BIP-32) and the bech32 1.2.0 reference encoder, the signatures with python-ecdsa RFC 6979 (low s) over the SHA-256
 * of sign docs shaped like thornode app/params/amino_test.go (made-up account numbers and recipients).
 */
class ThorchainSpeculosTest {
    private val client get() = ThorchainAppClient(Speculos.transport("THORChain"))

    @Test
    fun getAddress_firstAccount_matchesIndependentDerivation() = runBlocking {
        val address = client.getAddress(PATH)

        assertEquals("thor19hzdfmktztey2vuqpa3tv7dqe0kw3u24km6yca", address.address)
        assertEquals("0335f35211b872a0a6a5e0cb54885e82e8d71c7c945111d6f782db1254ba2a2d8e", address.publicKey.toHexString())
    }

    @Test
    fun signTransaction_msgSend_matchesIndependentSignature() = runBlocking {
        val signature = Speculos.approving { client.signTransaction(PATH, MSG_SEND.encodeToByteArray()) }

        assertEquals(
            "2f25b7c73bcdf0aa48a064352b7b4b19452a99d1cf508acdfca52e58c4548f2c64831512675ac3cf3e9bf0c724dfdc0ec924c11da493b02fc20857f0a46a1d01",
            signature.toHexString(),
        )
    }

    @Test
    fun signTransaction_msgDeposit_matchesIndependentSignature() = runBlocking {
        val signature = Speculos.approving { client.signTransaction(PATH, MSG_DEPOSIT.encodeToByteArray()) }

        assertEquals(
            "3f8124310136b2a775fcb4aac8113700f87b81f83c62b4cd09cd30e51c1cad3a5a256f14226d158d1055a4b42fb5c9852f995d7ef078c2b7a04b01b92bb4cec0",
            signature.toHexString(),
        )
    }

    private companion object {
        val PATH = DerivationPath.parse("m/44'/931'/0'/0/0")
        const val MSG_SEND =
            """{"account_number":"123","chain_id":"thorchain-1","fee":{"amount":[],"gas":"50000000"},"memo":"ledger-kit test","msgs":[{"type":"thorchain/MsgSend","value":{"amount":[{"amount":"150000000","denom":"rune"}],"from_address":"thor19hzdfmktztey2vuqpa3tv7dqe0kw3u24km6yca","to_address":"thor1mwyrp6lj85swy5e5g4hjlaacm33g6rw3p4qmq4"}}],"sequence":"456"}"""
        const val MSG_DEPOSIT =
            """{"account_number":"123","chain_id":"thorchain-1","fee":{"amount":[],"gas":"50000000"},"memo":"","msgs":[{"type":"thorchain/MsgDeposit","value":{"coins":[{"amount":"100000000","asset":"THOR.RUNE"}],"memo":"=:BTC.BTC:bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq:0/1/0","signer":"thor19hzdfmktztey2vuqpa3tv7dqe0kw3u24km6yca"}}],"sequence":"456"}"""
    }
}
