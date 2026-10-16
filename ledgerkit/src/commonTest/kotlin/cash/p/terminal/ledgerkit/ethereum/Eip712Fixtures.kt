package cash.p.terminal.ledgerkit.ethereum

// LedgerHQ/ledger-live libs/ledgerjs/packages/hw-app-eth tests/fixtures: messages/<name>.json and the APDU log
// apdus/<n>.apdus the noFilter-signMessage tests replay for it.

internal class Eip712Fixture(val name: String, val json: String, val apdus: String)

internal val EIP712_FIXTURES = listOf(
    Eip712Fixture(
        "0",
        """{
    "domain": {
        "chainId": 5,
        "name": "Ether Mail",
        "verifyingContract": "0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC",
        "version": "1"
    },
    "message": {
        "contents": "Hello, Bob!",
        "from": {
            "name": "Cow",
            "wallets": [
                "0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826",
                "0xDeaDbeefdEAdbeefdEadbEEFdeadbeEFdEaDbeeF"
            ]
        },
        "to": {
            "name": "Bob",
            "wallets": [
                "0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB",
                "0xB0BdaBea57B0BDABeA57b0bdABEA57b0BDabEa57",
                "0xB0B0b0b0b0b0B000000000000000000000000000"
            ]
        }
    },
    "primaryType": "Mail",
    "types": {
        "EIP712Domain": [
            { "name": "name", "type": "string" },
            { "name": "version", "type": "string" },
            { "name": "chainId", "type": "uint256" },
            { "name": "verifyingContract", "type": "address" }
        ],
        "Mail": [
            { "name": "from", "type": "Person" },
            { "name": "to", "type": "Person" },
            { "name": "contents", "type": "string" }
        ],
        "Person": [
            { "name": "name", "type": "string" },
            { "name": "wallets", "type": "address[]" }
        ]
    }
}""",
        """=> e01a00000c454950373132446f6d61696e
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff09050776657273696f6e
<= 9000
=> e01a00ff0a422007636861696e4964
<= 9000
=> e01a00ff130311766572696679696e67436f6e7472616374
<= 9000
=> e01a0000044d61696c
<= 9000
=> e01a00ff0d0006506572736f6e0466726f6d
<= 9000
=> e01a00ff0b0006506572736f6e02746f
<= 9000
=> e01a00ff0a0508636f6e74656e7473
<= 9000
=> e01a000006506572736f6e
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff0b8301000777616c6c657473
<= 9000
=> e01c00000c454950373132446f6d61696e
<= 9000
=> e01c00ff0c000a4574686572204d61696c
<= 9000
=> e01c00ff03000131
<= 9000
=> e01c00ff03000105
<= 9000
=> e01c00ff160014cccccccccccccccccccccccccccccccccccccccc
<= 9000
=> e01c0000044d61696c
<= 9000
=> e01c00ff050003436f77
<= 9000
=> e01c000f0102
<= 9000
=> e01c00ff160014cd2a3d9f938e13cd947ec05abc7fe734df8dd826
<= 9000
=> e01c00ff160014deadbeefdeadbeefdeadbeefdeadbeefdeadbeef
<= 9000
=> e01c00ff050003426f62
<= 9000
=> e01c000f0103
<= 9000
=> e01c00ff160014bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
<= 9000
=> e01c00ff160014b0bdabea57b0bdabea57b0bdabea57b0bdabea57
<= 9000
=> e01c00ff160014b0b0b0b0b0b0b000000000000000000000000000
<= 9000
=> e01c00ff0d000b48656c6c6f2c20426f6221
<= 9000
=> e00c000115058000002c8000003c800000000000000000000000
<= 1c8a540510e13b0f2b11a451275716d29e08caad07e89a1c84964782fb5e1ad78864a0de235b270fbe81e8e40688f4a9f9ad9d283d690552c9331d7773ceafa5139000""",
    ),
    Eip712Fixture(
        "1",
        """{
    "domain": {
        "chainId": 5,
        "name": "Ether Mail",
        "verifyingContract": "0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC",
        "version": "1"
    },
    "message": {
        "contents": "Hello, Bob!",
        "from": {
            "name": "Cow",
            "wallets": [
                "0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826",
                "0xDeaDbeefdEAdbeefdEadbEEFdeadbeEFdEaDbeeF"
            ]
        },
        "to": [
            {
                "name": "Bob",
                "wallets": [
                    "0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB",
                    "0xB0BdaBea57B0BDABeA57b0bdABEA57b0BDabEa57",
                    "0xB0B0b0b0b0b0B000000000000000000000000000"
                ]
            }
        ]
    },
    "primaryType": "Mail",
    "types": {
        "EIP712Domain": [
            { "name": "name", "type": "string" },
            { "name": "version", "type": "string" },
            { "name": "chainId", "type": "uint256" },
            { "name": "verifyingContract", "type": "address" }
        ],
        "Group": [
          { "name": "name", "type": "string" },
          { "name": "members", "type": "Person[]" }
        ],
        "Mail": [
            { "name": "from", "type": "Person" },
            { "name": "to", "type": "Person[]" },
            { "name": "contents", "type": "string" }
        ],
        "Person": [
            { "name": "name", "type": "string" },
            { "name": "wallets", "type": "address[]" }
        ]
    }
}""",
        """=> e01a00000c454950373132446f6d61696e
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff09050776657273696f6e
<= 9000
=> e01a00ff0a422007636861696e4964
<= 9000
=> e01a00ff130311766572696679696e67436f6e7472616374
<= 9000
=> e01a00000547726f7570
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff128006506572736f6e0100076d656d62657273
<= 9000
=> e01a0000044d61696c
<= 9000
=> e01a00ff0d0006506572736f6e0466726f6d
<= 9000
=> e01a00ff0d8006506572736f6e010002746f
<= 9000
=> e01a00ff0a0508636f6e74656e7473
<= 9000
=> e01a000006506572736f6e
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff0b8301000777616c6c657473
<= 9000
=> e01c00000c454950373132446f6d61696e
<= 9000
=> e01c00ff0c000a4574686572204d61696c
<= 9000
=> e01c00ff03000131
<= 9000
=> e01c00ff03000105
<= 9000
=> e01c00ff160014cccccccccccccccccccccccccccccccccccccccc
<= 9000
=> e01c0000044d61696c
<= 9000
=> e01c00ff050003436f77
<= 9000
=> e01c000f0102
<= 9000
=> e01c00ff160014cd2a3d9f938e13cd947ec05abc7fe734df8dd826
<= 9000
=> e01c00ff160014deadbeefdeadbeefdeadbeefdeadbeefdeadbeef
<= 9000
=> e01c000f0101
<= 9000
=> e01c00ff050003426f62
<= 9000
=> e01c000f0103
<= 9000
=> e01c00ff160014bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
<= 9000
=> e01c00ff160014b0bdabea57b0bdabea57b0bdabea57b0bdabea57
<= 9000
=> e01c00ff160014b0b0b0b0b0b0b000000000000000000000000000
<= 9000
=> e01c00ff0d000b48656c6c6f2c20426f6221
<= 9000
=> e00c000115058000002c8000003c800000000000000000000000
<= 1ca7cd8fdac8707487908988d6111c2accdcaed9c43d6e993e8760ec8096cb2051394fc2a9b1a5107b28aa601b767a740761920c86797918365ac2460f6444ab989000""",
    ),
    Eip712Fixture(
        "2",
        """{
   "domain" : {
      "chainId" : 1,
      "name" : "Wyvern Exchange Contract",
      "verifyingContract" : "0x7f268357a8c2552623316e2562d90e642bb538e5",
      "version" : "2.3"
   },
   "message" : {
      "basePrice" : "2000000000000000000",
      "calldata" : "0x96809f90000000000000000000000000112f0732e59e7600768dfc35ba744b89f2356cd80000000000000000000000000000000000000000000000000000000000000000000000000000000000000000495f947276749ce646f68ac8c248420045cb7b5ebdf2657ffc1fadfd73cf0a8cde95d50b62d3df8c0000000000000700000000320000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000e00000000000000000000000000000000000000000000000000000000000000000",
      "exchange" : "0x7f268357a8c2552623316e2562d90e642bb538e5",
      "expirationTime" : "1646089435",
      "extra" : "0",
      "feeMethod" : 1,
      "feeRecipient" : "0x5b3256965e7c3cf26e11fcaf296dfc8807c01073",
      "howToCall" : 1,
      "listingTime" : "1645484541",
      "maker" : "0x112f0732e59e7600768dfc35ba744b89f2356cd8",
      "makerProtocolFee" : "0",
      "makerRelayerFee" : "1250",
      "nonce" : 0,
      "paymentToken" : "0x0000000000000000000000000000000000000000",
      "replacementPattern" : "0x000000000000000000000000000000000000000000000000000000000000000000000000ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
      "saleKind" : 0,
      "salt" : "21014297276898013168171430966355369260039074692095359200549020767078729356431",
      "side" : 1,
      "staticExtradata" : "0x",
      "staticTarget" : "0x0000000000000000000000000000000000000000",
      "taker" : "0x0000000000000000000000000000000000000000",
      "takerProtocolFee" : "0",
      "takerRelayerFee" : "0",
      "target" : "0xbaf2127b49fc93cbca6269fade0f7f31df4c88a7"
   },
   "primaryType" : "Order",
   "types" : {
      "EIP712Domain" : [
         {
            "name" : "name",
            "type" : "string"
         },
         {
            "name" : "version",
            "type" : "string"
         },
         {
            "name" : "chainId",
            "type" : "uint256"
         },
         {
            "name" : "verifyingContract",
            "type" : "address"
         }
      ],
      "Order" : [
         {
            "name" : "exchange",
            "type" : "address"
         },
         {
            "name" : "maker",
            "type" : "address"
         },
         {
            "name" : "taker",
            "type" : "address"
         },
         {
            "name" : "makerRelayerFee",
            "type" : "uint256"
         },
         {
            "name" : "takerRelayerFee",
            "type" : "uint256"
         },
         {
            "name" : "makerProtocolFee",
            "type" : "uint256"
         },
         {
            "name" : "takerProtocolFee",
            "type" : "uint256"
         },
         {
            "name" : "feeRecipient",
            "type" : "address"
         },
         {
            "name" : "feeMethod",
            "type" : "uint8"
         },
         {
            "name" : "side",
            "type" : "uint8"
         },
         {
            "name" : "saleKind",
            "type" : "uint8"
         },
         {
            "name" : "target",
            "type" : "address"
         },
         {
            "name" : "howToCall",
            "type" : "uint8"
         },
         {
            "name" : "calldata",
            "type" : "bytes"
         },
         {
            "name" : "replacementPattern",
            "type" : "bytes"
         },
         {
            "name" : "staticTarget",
            "type" : "address"
         },
         {
            "name" : "staticExtradata",
            "type" : "bytes"
         },
         {
            "name" : "paymentToken",
            "type" : "address"
         },
         {
            "name" : "basePrice",
            "type" : "uint256"
         },
         {
            "name" : "extra",
            "type" : "uint256"
         },
         {
            "name" : "listingTime",
            "type" : "uint256"
         },
         {
            "name" : "expirationTime",
            "type" : "uint256"
         },
         {
            "name" : "salt",
            "type" : "uint256"
         },
         {
            "name" : "nonce",
            "type" : "uint256"
         }
      ]
   }
}""",
        """=> e01a00000c454950373132446f6d61696e
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff09050776657273696f6e
<= 9000
=> e01a00ff0a422007636861696e4964
<= 9000
=> e01a00ff130311766572696679696e67436f6e7472616374
<= 9000
=> e01a0000054f72646572
<= 9000
=> e01a00ff0a030865786368616e6765
<= 9000
=> e01a00ff0703056d616b6572
<= 9000
=> e01a00ff07030574616b6572
<= 9000
=> e01a00ff1242200f6d616b657252656c61796572466565
<= 9000
=> e01a00ff1242200f74616b657252656c61796572466565
<= 9000
=> e01a00ff134220106d616b657250726f746f636f6c466565
<= 9000
=> e01a00ff1342201074616b657250726f746f636f6c466565
<= 9000
=> e01a00ff0e030c666565526563697069656e74
<= 9000
=> e01a00ff0c4201096665654d6574686f64
<= 9000
=> e01a00ff0742010473696465
<= 9000
=> e01a00ff0b42010873616c654b696e64
<= 9000
=> e01a00ff080306746172676574
<= 9000
=> e01a00ff0c420109686f77546f43616c6c
<= 9000
=> e01a00ff0a070863616c6c64617461
<= 9000
=> e01a00ff1407127265706c6163656d656e745061747465726e
<= 9000
=> e01a00ff0e030c737461746963546172676574
<= 9000
=> e01a00ff11070f737461746963457874726164617461
<= 9000
=> e01a00ff0e030c7061796d656e74546f6b656e
<= 9000
=> e01a00ff0c422009626173655072696365
<= 9000
=> e01a00ff084220056578747261
<= 9000
=> e01a00ff0e42200b6c697374696e6754696d65
<= 9000
=> e01a00ff1142200e65787069726174696f6e54696d65
<= 9000
=> e01a00ff0742200473616c74
<= 9000
=> e01a00ff084220056e6f6e6365
<= 9000
=> e01c00000c454950373132446f6d61696e
<= 9000
=> e01c00ff1a001857797665726e2045786368616e676520436f6e7472616374
<= 9000
=> e01c00ff050003322e33
<= 9000
=> e01c00ff03000101
<= 9000
=> e01c00ff1600147f268357a8c2552623316e2562d90e642bb538e5
<= 9000
=> e01c0000054f72646572
<= 9000
=> e01c00ff1600147f268357a8c2552623316e2562d90e642bb538e5
<= 9000
=> e01c00ff160014112f0732e59e7600768dfc35ba744b89f2356cd8
<= 9000
=> e01c00ff1600140000000000000000000000000000000000000000
<= 9000
=> e01c00ff04000204e2
<= 9000
=> e01c00ff03000100
<= 9000
=> e01c00ff03000100
<= 9000
=> e01c00ff03000100
<= 9000
=> e01c00ff1600145b3256965e7c3cf26e11fcaf296dfc8807c01073
<= 9000
=> e01c00ff03000101
<= 9000
=> e01c00ff03000101
<= 9000
=> e01c00ff03000100
<= 9000
=> e01c00ff160014baf2127b49fc93cbca6269fade0f7f31df4c88a7
<= 9000
=> e01c00ff03000101
<= 9000
=> e01c01ffff010496809f90000000000000000000000000112f0732e59e7600768dfc35ba744b89f2356cd80000000000000000000000000000000000000000000000000000000000000000000000000000000000000000495f947276749ce646f68ac8c248420045cb7b5ebdf2657ffc1fadfd73cf0a8cde95d50b62d3df8c0000000000000700000000320000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000
<= 9000
=> e01c00ff0700000000000000
<= 9000
=> e01c01ffff0104000000000000000000000000000000000000000000000000000000000000000000000000ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000
<= 9000
=> e01c00ff0700000000000000
<= 9000
=> e01c00ff1600140000000000000000000000000000000000000000
<= 9000
=> e01c00ff020000
<= 9000
=> e01c00ff1600140000000000000000000000000000000000000000
<= 9000
=> e01c00ff0a00081bc16d674ec80000
<= 9000
=> e01c00ff03000100
<= 9000
=> e01c00ff060004621419fd
<= 9000
=> e01c00ff060004621d54db
<= 9000
=> e01c00ff2200202e75abb71e521c4b779c983758c31b6078a3823ddf7414d8cf5658c81423948f
<= 9000
=> e01c00ff03000100
<= 9000
=> e00c000115058000002c8000003c800000000000000000000000
<= 1cbee323485a7138784ec593146955255b1290eac0c9959fbe57f01b7e3e8e2dfe5f489023947c1d7cc4a8bba5c96e0717236f635d97c79203f56d57f3727cb5d19000""",
    ),
    Eip712Fixture(
        "3",
        """{
    "domain": {
        "chainId": 1284,
        "name": "Ether Mail",
        "verifyingContract": "0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC",
        "version": "1"
    },
    "message": {
        "contents": "Hello, Bob!",
        "from": "0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826",
        "to": [
            "0xB0BdaBea57B0BDABeA57b0bdABEA57b0BDabEa57",
            "0xb1a22cc48f6784f629a994917cd6474923630c48"
        ],
        "id": 7
    },
    "primaryType": "Mail",
    "types": {
        "EIP712Domain": [
            { "name": "name", "type": "string" },
            { "name": "version", "type": "string" },
            { "name": "chainId", "type": "uint256" },
            { "name": "verifyingContract", "type": "address" }
        ],
        "Mail": [
            { "name": "from", "type": "address" },
            { "name": "to", "type": "address[]" },
            { "name": "contents", "type": "string" },
            { "name": "id", "type": "uint256"}
        ]
    }
}""",
        """=> e01a00000c454950373132446f6d61696e
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff09050776657273696f6e
<= 9000
=> e01a00ff0a422007636861696e4964
<= 9000
=> e01a00ff130311766572696679696e67436f6e7472616374
<= 9000
=> e01a0000044d61696c
<= 9000
=> e01a00ff06030466726f6d
<= 9000
=> e01a00ff0683010002746f
<= 9000
=> e01a00ff0a0508636f6e74656e7473
<= 9000
=> e01a00ff054220026964
<= 9000
=> e01c00000c454950373132446f6d61696e
<= 9000
=> e01c00ff0c000a4574686572204d61696c
<= 9000
=> e01c00ff03000131
<= 9000
=> e01c00ff0400020504
<= 9000
=> e01c00ff160014cccccccccccccccccccccccccccccccccccccccc
<= 9000
=> e01c0000044d61696c
<= 9000
=> e01c00ff160014cd2a3d9f938e13cd947ec05abc7fe734df8dd826
<= 9000
=> e01c000f0102
<= 9000
=> e01c00ff160014b0bdabea57b0bdabea57b0bdabea57b0bdabea57
<= 9000
=> e01c00ff160014b1a22cc48f6784f629a994917cd6474923630c48
<= 9000
=> e01c00ff0d000b48656c6c6f2c20426f6221
<= 9000
=> e01c00ff03000107
<= 9000
=> e00c000115058000002c8000003c800000000000000000000000
<= 1ca032f358f76041e239d34a449d859742d79ede0705e294552e68c6f6345cd9a65d14e1607eca346db7a1b4ded81c8dcc82821a8f2ceb24803b611095cf68d3209000""",
    ),
    Eip712Fixture(
        "4",
        """{
   "domain" : {
      "chainId" : 1,
      "name" : "Exchange",
      "verifyingContract" : "0x9757f2d2b135150bbeb65308d4a91804107cd8d6",
      "version" : "2"
   },
   "message" : {
      "data" : "0x0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000800000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000010000000000000000000000001cf0df2a5a20cd61d68d4489eebbf85b8d39e18a00000000000000000000000000000000000000000000000000000000000000fa",
      "dataType" : "0x23d235ef",
      "end" : 0,
      "makeAsset" : {
         "assetType" : {
            "assetClass" : "0x973bb640",
            "data" : "0x000000000000000000000000495f947276749ce646f68ac8c248420045cb7b5ebdf2657ffc1fadfd73cf0a8cde95d50b62d3df8c000000000000070000000032"
         },
         "value" : "1"
      },
      "maker" : "0x112f0732e59e7600768dfc35ba744b89f2356cd8",
      "salt" : "0xdbf0f98bc1746711579dcce549a4cc4e866fb71bf2e185bfefbb7d32f325972e",
      "start" : 0,
      "takeAsset" : {
         "assetType" : {
            "assetClass" : "0xaaaebeba",
            "data" : "0x"
         },
         "value" : "2000000000000000000"
      },
      "taker" : "0x0000000000000000000000000000000000000000"
   },
   "primaryType" : "Order",
   "types" : {
      "Asset" : [
         {
            "name" : "assetType",
            "type" : "AssetType"
         },
         {
            "name" : "value",
            "type" : "uint256"
         }
      ],
      "AssetType" : [
         {
            "name" : "assetClass",
            "type" : "bytes4"
         },
         {
            "name" : "data",
            "type" : "bytes"
         }
      ],
      "EIP712Domain" : [
         {
            "name" : "name",
            "type" : "string"
         },
         {
            "name" : "version",
            "type" : "string"
         },
         {
            "name" : "chainId",
            "type" : "uint256"
         },
         {
            "name" : "verifyingContract",
            "type" : "address"
         }
      ],
      "Order" : [
         {
            "name" : "maker",
            "type" : "address"
         },
         {
            "name" : "makeAsset",
            "type" : "Asset"
         },
         {
            "name" : "taker",
            "type" : "address"
         },
         {
            "name" : "takeAsset",
            "type" : "Asset"
         },
         {
            "name" : "salt",
            "type" : "uint256"
         },
         {
            "name" : "start",
            "type" : "uint256"
         },
         {
            "name" : "end",
            "type" : "uint256"
         },
         {
            "name" : "dataType",
            "type" : "bytes4"
         },
         {
            "name" : "data",
            "type" : "bytes"
         }
      ]
   }
}""",
        """=> e01a0000054173736574
<= 9000
=> e01a00ff15000941737365745479706509617373657454797065
<= 9000
=> e01a00ff0842200576616c7565
<= 9000
=> e01a000009417373657454797065
<= 9000
=> e01a00ff0d46040a6173736574436c617373
<= 9000
=> e01a00ff06070464617461
<= 9000
=> e01a00000c454950373132446f6d61696e
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff09050776657273696f6e
<= 9000
=> e01a00ff0a422007636861696e4964
<= 9000
=> e01a00ff130311766572696679696e67436f6e7472616374
<= 9000
=> e01a0000054f72646572
<= 9000
=> e01a00ff0703056d616b6572
<= 9000
=> e01a00ff1100054173736574096d616b654173736574
<= 9000
=> e01a00ff07030574616b6572
<= 9000
=> e01a00ff11000541737365740974616b654173736574
<= 9000
=> e01a00ff0742200473616c74
<= 9000
=> e01a00ff084220057374617274
<= 9000
=> e01a00ff06422003656e64
<= 9000
=> e01a00ff0b4604086461746154797065
<= 9000
=> e01a00ff06070464617461
<= 9000
=> e01c00000c454950373132446f6d61696e
<= 9000
=> e01c00ff0a000845786368616e6765
<= 9000
=> e01c00ff03000132
<= 9000
=> e01c00ff03000101
<= 9000
=> e01c00ff1600149757f2d2b135150bbeb65308d4a91804107cd8d6
<= 9000
=> e01c0000054f72646572
<= 9000
=> e01c00ff160014112f0732e59e7600768dfc35ba744b89f2356cd8
<= 9000
=> e01c00ff060004973bb640
<= 9000
=> e01c00ff420040000000000000000000000000495f947276749ce646f68ac8c248420045cb7b5ebdf2657ffc1fadfd73cf0a8cde95d50b62d3df8c000000000000070000000032
<= 9000
=> e01c00ff03000101
<= 9000
=> e01c00ff1600140000000000000000000000000000000000000000
<= 9000
=> e01c00ff060004aaaebeba
<= 9000
=> e01c00ff020000
<= 9000
=> e01c00ff0a00081bc16d674ec80000
<= 9000
=> e01c00ff220020dbf0f98bc1746711579dcce549a4cc4e866fb71bf2e185bfefbb7d32f325972e
<= 9000
=> e01c00ff03000100
<= 9000
=> e01c00ff03000100
<= 9000
=> e01c00ff06000423d235ef
<= 9000
=> e01c01ffff01000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000800000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000010000000000000000000000001cf0df2a5a20cd61d68d4489eebbf85b8d39e18a0000000000000000000000000000000000000000000000000000000000
<= 9000
=> e01c00ff030000fa
<= 9000
=> e00c000115058000002c8000003c800000000000000000000000
<= 1bcda73aa5e40061499b7faee1f85d6cefc371bb8f38bf407d6ab747aaaf1b047d702f036a1941e85f124f961e44c55fafe44533abd34aea8a5c4dba0df836925f9000""",
    ),
    Eip712Fixture(
        "5",
        """{
    "types": {
        "EIP712Domain": [
            {
                "type": "string",
                "name": "name"
            }
        ],
        "LDPSigningRequest": [
            {
                "type": "string[3][]",
                "name": "document"
            },
            {
                "type": "string[][4]",
                "name": "proof"
            },
            {
                "type": "uint8[][][][]",
                "name": "depthy"
            }
        ]
    },
    "primaryType": "LDPSigningRequest",
    "domain": {
        "name": "Eip712Method2021"
    },
    "message": {
        "depthy": [
            [
                [
                    [
                        "1",
                        "2"
                    ],
                    [
                        "3"
                    ]
                ]
            ]
        ],
        "document": [
            [
                "<did:key:z6MkgFneaaMjN6zybqLNXgt4YfmVx2XZhzPdDyk4ZK81daHZ>",
                "<https://www.w3.org/TR/owl-ref/#sameAs-def>",
                "\"did:ethr:0xf7398bacf610bb4e3b567811279fcb3c41919f89\""
            ],
            [
                "<urn:uuid:14ab6047-bf2d-4fde-9564-51dead126ffb>",
                "<https://www.w3.org/2018/credentials#credentialSubject>",
                "<did:key:z6MkgFneaaMjN6zybqLNXgt4YfmVx2XZhzPdDyk4ZK81daHZ>"
            ],
            [
                "<urn:uuid:14ab6047-bf2d-4fde-9564-51dead126ffb>",
                "<https://www.w3.org/2018/credentials#issuanceDate>",
                "\"2021-03-04T21:08:22.615Z\"^^<http://www.w3.org/2001/XMLSchema#dateTime>"
            ],
            [
                "<urn:uuid:14ab6047-bf2d-4fde-9564-51dead126ffb>",
                "<https://www.w3.org/2018/credentials#issuer>",
                "<did:ethr:0xf7398bacf610bb4e3b567811279fcb3c41919f89>"
            ],
            [
                "<urn:uuid:14ab6047-bf2d-4fde-9564-51dead126ffb>",
                "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                "<https://www.w3.org/2018/credentials#VerifiableCredential>"
            ]
        ],
        "proof": [
            [
                "_:c14n0",
                "<http://purl.org/dc/terms/created>",
                "\"2021-03-04T21:08:22.616Z\"^^<http://www.w3.org/2001/XMLSchema#dateTime>"
            ],
            [
                "_:c14n0",
                "<https://w3id.org/security#proofPurpose>",
                "<https://w3id.org/security#assertionMethod>"
            ],
            [
                "_:c14n0",
                "<https://w3id.org/security#verificationMethod>",
                "<did:ethr:0xf7398bacf610bb4e3b567811279fcb3c41919f89#Eip712Method2021>"
            ],
            [
                "_:c14n0",
                "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                "<https://w3id.org/security#Eip712Signature2021>"
            ]
        ]
    }
}""",
        """=> e01a00000c454950373132446f6d61696e
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a0000114c44505369676e696e6752657175657374
<= 9000
=> e01a00ff0e850201030008646f63756d656e74
<= 9000
=> e01a00ff0b85020001040570726f6f66
<= 9000
=> e01a00ff0ec201040000000006646570746879
<= 9000
=> e01c00000c454950373132446f6d61696e
<= 9000
=> e01c00ff1200104569703731324d6574686f6432303231
<= 9000
=> e01c0000114c44505369676e696e6752657175657374
<= 9000
=> e01c000f0105
<= 9000
=> e01c000f0103
<= 9000
=> e01c00ff3c003a3c6469643a6b65793a7a364d6b67466e6561614d6a4e367a7962714c4e5867743459666d567832585a687a506444796b345a4b38316461485a3e
<= 9000
=> e01c00ff2d002b3c68747470733a2f2f7777772e77332e6f72672f54522f6f776c2d7265662f2373616d6541732d6465663e
<= 9000
=> e01c00ff370035226469643a657468723a30786637333938626163663631306262346533623536373831313237396663623363343139313966383922
<= 9000
=> e01c000f0103
<= 9000
=> e01c00ff31002f3c75726e3a757569643a31346162363034372d626632642d346664652d393536342d3531646561643132366666623e
<= 9000
=> e01c00ff3900373c68747470733a2f2f7777772e77332e6f72672f323031382f63726564656e7469616c732363726564656e7469616c5375626a6563743e
<= 9000
=> e01c00ff3c003a3c6469643a6b65793a7a364d6b67466e6561614d6a4e367a7962714c4e5867743459666d567832585a687a506444796b345a4b38316461485a3e
<= 9000
=> e01c000f0103
<= 9000
=> e01c00ff31002f3c75726e3a757569643a31346162363034372d626632642d346664652d393536342d3531646561643132366666623e
<= 9000
=> e01c00ff3400323c68747470733a2f2f7777772e77332e6f72672f323031382f63726564656e7469616c732369737375616e6365446174653e
<= 9000
=> e01c00ff49004722323032312d30332d30345432313a30383a32322e3631355a225e5e3c687474703a2f2f7777772e77332e6f72672f323030312f584d4c536368656d61236461746554696d653e
<= 9000
=> e01c000f0103
<= 9000
=> e01c00ff31002f3c75726e3a757569643a31346162363034372d626632642d346664652d393536342d3531646561643132366666623e
<= 9000
=> e01c00ff2e002c3c68747470733a2f2f7777772e77332e6f72672f323031382f63726564656e7469616c73236973737565723e
<= 9000
=> e01c00ff3700353c6469643a657468723a3078663733393862616366363130626234653362353637383131323739666362336334313931396638393e
<= 9000
=> e01c000f0103
<= 9000
=> e01c00ff31002f3c75726e3a757569643a31346162363034372d626632642d346664652d393536342d3531646561643132366666623e
<= 9000
=> e01c00ff3300313c687474703a2f2f7777772e77332e6f72672f313939392f30322f32322d7264662d73796e7461782d6e7323747970653e
<= 9000
=> e01c00ff3c003a3c68747470733a2f2f7777772e77332e6f72672f323031382f63726564656e7469616c732356657269666961626c6543726564656e7469616c3e
<= 9000
=> e01c000f0104
<= 9000
=> e01c000f0103
<= 9000
=> e01c00ff0900075f3a6331346e30
<= 9000
=> e01c00ff2400223c687474703a2f2f7075726c2e6f72672f64632f7465726d732f637265617465643e
<= 9000
=> e01c00ff49004722323032312d30332d30345432313a30383a32322e3631365a225e5e3c687474703a2f2f7777772e77332e6f72672f323030312f584d4c536368656d61236461746554696d653e
<= 9000
=> e01c000f0103
<= 9000
=> e01c00ff0900075f3a6331346e30
<= 9000
=> e01c00ff2a00283c68747470733a2f2f773369642e6f72672f73656375726974792370726f6f66507572706f73653e
<= 9000
=> e01c00ff2d002b3c68747470733a2f2f773369642e6f72672f736563757269747923617373657274696f6e4d6574686f643e
<= 9000
=> e01c000f0103
<= 9000
=> e01c00ff0900075f3a6331346e30
<= 9000
=> e01c00ff30002e3c68747470733a2f2f773369642e6f72672f736563757269747923766572696669636174696f6e4d6574686f643e
<= 9000
=> e01c00ff4800463c6469643a657468723a307866373339386261636636313062623465336235363738313132373966636233633431393139663839234569703731324d6574686f64323032313e
<= 9000
=> e01c000f0103
<= 9000
=> e01c00ff0900075f3a6331346e30
<= 9000
=> e01c00ff3300313c687474703a2f2f7777772e77332e6f72672f313939392f30322f32322d7264662d73796e7461782d6e7323747970653e
<= 9000
=> e01c00ff31002f3c68747470733a2f2f773369642e6f72672f7365637572697479234569703731325369676e6174757265323032313e
<= 9000
=> e01c000f0101
<= 9000
=> e01c000f0101
<= 9000
=> e01c000f0102
<= 9000
=> e01c000f0102
<= 9000
=> e01c00ff03000101
<= 9000
=> e01c00ff03000102
<= 9000
=> e01c000f0101
<= 9000
=> e01c00ff03000103
<= 9000
=> e00c000115058000002c8000003c800000000000000000000000
<= 1b7b6a345381330b91ee63d05c83ecc2fa1bc7cd5871da3f45341827b09fd440367660f31b2cae89bc1826fbe153209b059968264b6387936551f2a5d80931490c9000""",
    ),
    Eip712Fixture(
        "6",
        """{
    "domain": {
        "chainId": 5,
        "name": "Ether Mail",
        "verifyingContract": "0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC",
        "version": "1"
    },
    "message": {
        "contents": "Hello, Bob!",
        "from": {
            "name": "Cow",
            "wallets": [
                "0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826",
                "0xDeaDbeefdEAdbeefdEadbEEFdeadbeEFdEaDbeeF"
            ]
        },
        "to": {
            "name": "test list",
            "members": [
                {
                    "name": "Bob",
                    "wallets": [
                        "0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB",
                        "0xB0B0b0b0b0b0B000000000000000000000000000"
                    ]
                },
                {
                    "name": "Alice",
                    "wallets": [
                        "0xB0BdaBea57B0BDABeA57b0bdABEA57b0BDabEa57"
                    ]
                }
            ]
        },
        "attach": {
            "list": [
                {
                    "name": "first",
                    "size": "100"
                },
                {
                    "name": "second",
                    "size": "3400"
                }
            ]
        }
    },
    "primaryType": "Mail",
    "types": {
        "EIP712Domain": [
            { "name": "name", "type": "string" },
            { "name": "version", "type": "string" },
            { "name": "chainId", "type": "uint256" },
            { "name": "verifyingContract", "type": "address" }
        ],
        "Attachment": [
            { "name": "name", "type": "string" },
            { "name": "size", "type": "uint16" }
        ],
        "Attachments": [
            { "name": "list", "type": "Attachment[]" }
        ],
        "MailingList": [
            { "name": "name", "type": "string" },
            { "name": "members", "type": "Person[]" }
        ],
        "Mail": [
            { "name": "from", "type": "Person" },
            { "name": "to", "type": "MailingList" },
            { "name": "contents", "type": "string" },
            { "name": "attach", "type": "Attachments" }
        ],
        "Person": [
            { "name": "name", "type": "string" },
            { "name": "wallets", "type": "address[]" }
        ]
    }
}""",
        """=> e01a00000a4174746163686d656e74
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff0742020473697a65
<= 9000
=> e01a00000b4174746163686d656e7473
<= 9000
=> e01a00ff13800a4174746163686d656e740100046c697374
<= 9000
=> e01a00000c454950373132446f6d61696e
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff09050776657273696f6e
<= 9000
=> e01a00ff0a422007636861696e4964
<= 9000
=> e01a00ff130311766572696679696e67436f6e7472616374
<= 9000
=> e01a0000044d61696c
<= 9000
=> e01a00ff0d0006506572736f6e0466726f6d
<= 9000
=> e01a00ff10000b4d61696c696e674c69737402746f
<= 9000
=> e01a00ff0a0508636f6e74656e7473
<= 9000
=> e01a00ff14000b4174746163686d656e747306617474616368
<= 9000
=> e01a00000b4d61696c696e674c697374
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff128006506572736f6e0100076d656d62657273
<= 9000
=> e01a000006506572736f6e
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff0b8301000777616c6c657473
<= 9000
=> e01c00000c454950373132446f6d61696e
<= 9000
=> e01c00ff0c000a4574686572204d61696c
<= 9000
=> e01c00ff03000131
<= 9000
=> e01c00ff03000105
<= 9000
=> e01c00ff160014cccccccccccccccccccccccccccccccccccccccc
<= 9000
=> e01c0000044d61696c
<= 9000
=> e01c00ff050003436f77
<= 9000
=> e01c000f0102
<= 9000
=> e01c00ff160014cd2a3d9f938e13cd947ec05abc7fe734df8dd826
<= 9000
=> e01c00ff160014deadbeefdeadbeefdeadbeefdeadbeefdeadbeef
<= 9000
=> e01c00ff0b000974657374206c697374
<= 9000
=> e01c000f0102
<= 9000
=> e01c00ff050003426f62
<= 9000
=> e01c000f0102
<= 9000
=> e01c00ff160014bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
<= 9000
=> e01c00ff160014b0b0b0b0b0b0b000000000000000000000000000
<= 9000
=> e01c00ff070005416c696365
<= 9000
=> e01c000f0101
<= 9000
=> e01c00ff160014b0bdabea57b0bdabea57b0bdabea57b0bdabea57
<= 9000
=> e01c00ff0d000b48656c6c6f2c20426f6221
<= 9000
=> e01c000f0102
<= 9000
=> e01c00ff0700056669727374
<= 9000
=> e01c00ff03000164
<= 9000
=> e01c00ff0800067365636f6e64
<= 9000
=> e01c00ff0400020d48
<= 9000
=> e00c000115058000002c8000003c800000000000000000000000
<= 1bab0071346cd7ab388c7b4fa71d9634f5f3e3445acbe900874b7594890404d14c35a8e8d682652915e9afc0c684fc8d5a5b00e87e43a478dc0d9eb639b43d29f59000""",
    ),
    Eip712Fixture(
        "7",
        """{
    "domain": {
        "chainId": 5,
        "name": "Ether Mail",
        "verifyingContract": "0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCccccccc1",
        "version": "1"
    },
    "message": {
        "contents": "Hello, Bob!",
        "from": {
            "name": "Cow",
            "wallets": [
                "0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826",
                "0xDeaDbeefdEAdbeefdEadbEEFdeadbeEFdEaDbeeF"
            ]
        },
        "to": [
            {
                "name": "Alice",
                "wallets": [
                    "0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB",
                    "0xB0B0b0b0b0b0B000000000000000000000000000"
                ]
            },
            {
                "name": "Bob",
                "wallets": [
                    "0xB0BdaBea57B0BDABeA57b0bdABEA57b0BDabEa57"
                ]
            }
        ]
    },
    "primaryType": "Mail",
    "types": {
        "EIP712Domain": [
            { "name": "name", "type": "string" },
            { "name": "version", "type": "string" },
            { "name": "chainId", "type": "uint256" },
            { "name": "verifyingContract", "type": "address" }
        ],
        "Group": [
          { "name": "name", "type": "string" },
          { "name": "members", "type": "Person[]" }
        ],
        "Mail": [
            { "name": "from", "type": "Person" },
            { "name": "to", "type": "Person[]" },
            { "name": "contents", "type": "string" }
        ],
        "Person": [
            { "name": "name", "type": "string" },
            { "name": "wallets", "type": "address[]" }
        ]
    }
}""",
        """=> e01a00000c454950373132446f6d61696e
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff09050776657273696f6e
<= 9000
=> e01a00ff0a422007636861696e4964
<= 9000
=> e01a00ff130311766572696679696e67436f6e7472616374
<= 9000
=> e01a00000547726f7570
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff128006506572736f6e0100076d656d62657273
<= 9000
=> e01a0000044d61696c
<= 9000
=> e01a00ff0d0006506572736f6e0466726f6d
<= 9000
=> e01a00ff0d8006506572736f6e010002746f
<= 9000
=> e01a00ff0a0508636f6e74656e7473
<= 9000
=> e01a000006506572736f6e
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff0b8301000777616c6c657473
<= 9000
=> e01c00000c454950373132446f6d61696e
<= 9000
=> e01c00ff0c000a4574686572204d61696c
<= 9000
=> e01c00ff03000131
<= 9000
=> e01c00ff03000105
<= 9000
=> e01c00ff160014ccccccccccccccccccccccccccccccccccccccc1
<= 9000
=> e01c0000044d61696c
<= 9000
=> e01c00ff050003436f77
<= 9000
=> e01c000f0102
<= 9000
=> e01c00ff160014cd2a3d9f938e13cd947ec05abc7fe734df8dd826
<= 9000
=> e01c00ff160014deadbeefdeadbeefdeadbeefdeadbeefdeadbeef
<= 9000
=> e01c000f0102
<= 9000
=> e01c00ff070005416c696365
<= 9000
=> e01c000f0102
<= 9000
=> e01c00ff160014bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
<= 9000
=> e01c00ff160014b0b0b0b0b0b0b000000000000000000000000000
<= 9000
=> e01c00ff050003426f62
<= 9000
=> e01c000f0101
<= 9000
=> e01c00ff160014b0bdabea57b0bdabea57b0bdabea57b0bdabea57
<= 9000
=> e01c00ff0d000b48656c6c6f2c20426f6221
<= 9000
=> e00c000115058000002c8000003c800000000000000000000000
<= 1b5059ff4b6e0e4ccc8f7bcf233da23618a1f0be05b88d70b068dbe187d6b3595506d7d716d3b9b351856d656a7d58e47f8d8922411cc3c68aa9a2d32d1f188f269000""",
    ),
    Eip712Fixture(
        "8",
        """{
    "domain": {
        "chainId": 5,
        "name": "Ether Mail",
        "verifyingContract": "0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC",
        "version": "1"
    },
    "message": {
        "contents": "Hello, Bob! 0123456780123456780123456780123456780123456780123456780123456780123456780123456780123456780123456780123456780123456780123456780123456780123456780123456780123456780123456780123456780123456780123456780123456780123456780123456780123456780123456780123456780123456780123456789999999999999999999999999999990123456789",
        "from": {
            "name": "Cow",
            "wallets": [
                "0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826",
                "0xDeaDbeefdEAdbeefdEadbEEFdeadbeEFdEaDbeeF"
            ]
        },
        "to": [
            {
                "name": "Bob",
                "wallets": [
                    "0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB",
                    "0xB0BdaBea57B0BDABeA57b0bdABEA57b0BDabEa57",
                    "0xB0B0b0b0b0b0B000000000000000000000000000"
                ]
            }
        ]
    },
    "primaryType": "Mail",
    "types": {
        "EIP712Domain": [
            { "name": "name", "type": "string" },
            { "name": "version", "type": "string" },
            { "name": "chainId", "type": "uint256" },
            { "name": "verifyingContract", "type": "address" }
        ],
        "Group": [
          { "name": "name", "type": "string" },
          { "name": "members", "type": "Person[]" }
        ],
        "Mail": [
            { "name": "from", "type": "Person" },
            { "name": "to", "type": "Person[]" },
            { "name": "contents", "type": "string" }
        ],
        "Person": [
            { "name": "name", "type": "string" },
            { "name": "wallets", "type": "address[]" }
        ]
    }
}""",
        """=> e01a00000c454950373132446f6d61696e
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff09050776657273696f6e
<= 9000
=> e01a00ff0a422007636861696e4964
<= 9000
=> e01a00ff130311766572696679696e67436f6e7472616374
<= 9000
=> e01a00000547726f7570
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff128006506572736f6e0100076d656d62657273
<= 9000
=> e01a0000044d61696c
<= 9000
=> e01a00ff0d0006506572736f6e0466726f6d
<= 9000
=> e01a00ff0d8006506572736f6e010002746f
<= 9000
=> e01a00ff0a0508636f6e74656e7473
<= 9000
=> e01a000006506572736f6e
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff0b8301000777616c6c657473
<= 9000
=> e01c00000c454950373132446f6d61696e
<= 9000
=> e01c00ff0c000a4574686572204d61696c
<= 9000
=> e01c00ff03000131
<= 9000
=> e01c00ff03000105
<= 9000
=> e01c00ff160014cccccccccccccccccccccccccccccccccccccccc
<= 9000
=> e01c0000044d61696c
<= 9000
=> e01c00ff050003436f77
<= 9000
=> e01c000f0102
<= 9000
=> e01c00ff160014cd2a3d9f938e13cd947ec05abc7fe734df8dd826
<= 9000
=> e01c00ff160014deadbeefdeadbeefdeadbeefdeadbeefdeadbeef
<= 9000
=> e01c000f0101
<= 9000
=> e01c00ff050003426f62
<= 9000
=> e01c000f0103
<= 9000
=> e01c00ff160014bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
<= 9000
=> e01c00ff160014b0bdabea57b0bdabea57b0bdabea57b0bdabea57
<= 9000
=> e01c00ff160014b0b0b0b0b0b0b000000000000000000000000000
<= 9000
=> e01c01ffff014248656c6c6f2c20426f62212030313233343536373830313233343536373830313233343536373830313233343536373830313233343536373830313233343536373830313233343536373830313233343536373830313233343536373830313233343536373830313233343536373830313233343536373830313233343536373830313233343536373830313233343536373830313233343536373830313233343536373830313233343536373830313233343536373830313233343536373830313233343536373830313233343536373830313233343536373830313233343536373830313233343536373830313233343536373830313233343536
<= 9000
=> e01c00ff45373830313233343536373830313233343536373830313233343536373839393939393939393939393939393939393939393939393939393939393930313233343536373839
<= 9000
=> e00c000115058000002c8000003c800000000000000000000000
<= 1cd6de64452d9a826ba639b736117d2a91ae04539577cb5a2a2445361ee9158034246339bfeba70f9dc2a9167b0da5169acc598797897d64cee52b3dbb998122cb9000""",
    ),
    Eip712Fixture(
        "9",
        """{
    "domain": {
        "chainId": 5,
        "name": "Ether Mail",
        "verifyingContract": "0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC",
        "version": "1"
    },
    "message": {
        "contents": "0x112345678912345678912345367891234567891234567891234567891234567891234567891234567891234567891234567891234567891234567891234567891234567891234567891234567891234567891234567899123456789234567899123456789234567899123456789234567899123456789234567899123456789234567891234567892345678911234567891234567891234536789123456789123456789123456789123456789123456789123456789123456789123456789123456789123456789123456789123456789123456789123456789123456789123456789912345678923456789912345678923456789912345678923456789912345678923456789912345678923456789123456789234567891123456789123456789123453678912345678912345678912345678912345678912345678912345678912345678912345678912345678912345678912345678912345678912345678912345678912345678912345678991234567892345678991234567892345678991234567892345678991234567892345678991234567892345678912345678923456789",
        "from": {
            "name": "Cow",
            "wallets": [
                "0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826",
                "0xDeaDbeefdEAdbeefdEadbEEFdeadbeEFdEaDbeeF"
            ]
        },
        "to": [
            {
                "name": "Bob",
                "wallets": [
                    "0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB",
                    "0xB0BdaBea57B0BDABeA57b0bdABEA57b0BDabEa57",
                    "0xB0B0b0b0b0b0B000000000000000000000000000"
                ]
            }
        ]
    },
    "primaryType": "Mail",
    "types": {
        "EIP712Domain": [
            { "name": "name", "type": "string" },
            { "name": "version", "type": "string" },
            { "name": "chainId", "type": "uint256" },
            { "name": "verifyingContract", "type": "address" }
        ],
        "Group": [
          { "name": "name", "type": "string" },
          { "name": "members", "type": "Person[]" }
        ],
        "Mail": [
            { "name": "from", "type": "Person" },
            { "name": "to", "type": "Person[]" },
            { "name": "contents", "type": "bytes" }
        ],
        "Person": [
            { "name": "name", "type": "string" },
            { "name": "wallets", "type": "address[]" }
        ]
    }
}""",
        """=> e01a00000c454950373132446f6d61696e
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff09050776657273696f6e
<= 9000
=> e01a00ff0a422007636861696e4964
<= 9000
=> e01a00ff130311766572696679696e67436f6e7472616374
<= 9000
=> e01a00000547726f7570
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff128006506572736f6e0100076d656d62657273
<= 9000
=> e01a0000044d61696c
<= 9000
=> e01a00ff0d0006506572736f6e0466726f6d
<= 9000
=> e01a00ff0d8006506572736f6e010002746f
<= 9000
=> e01a00ff0a0708636f6e74656e7473
<= 9000
=> e01a000006506572736f6e
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff0b8301000777616c6c657473
<= 9000
=> e01c00000c454950373132446f6d61696e
<= 9000
=> e01c00ff0c000a4574686572204d61696c
<= 9000
=> e01c00ff03000131
<= 9000
=> e01c00ff03000105
<= 9000
=> e01c00ff160014cccccccccccccccccccccccccccccccccccccccc
<= 9000
=> e01c0000044d61696c
<= 9000
=> e01c00ff050003436f77
<= 9000
=> e01c000f0102
<= 9000
=> e01c00ff160014cd2a3d9f938e13cd947ec05abc7fe734df8dd826
<= 9000
=> e01c00ff160014deadbeefdeadbeefdeadbeefdeadbeefdeadbeef
<= 9000
=> e01c000f0101
<= 9000
=> e01c00ff050003426f62
<= 9000
=> e01c000f0103
<= 9000
=> e01c00ff160014bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
<= 9000
=> e01c00ff160014b0bdabea57b0bdabea57b0bdabea57b0bdabea57
<= 9000
=> e01c00ff160014b0b0b0b0b0b0b000000000000000000000000000
<= 9000
=> e01c01ffff01a411234567891234567891234536789123456789123456789123456789123456789123456789123456789123456789123456789123456789123456789123456789123456789123456789123456789123456789123456789912345678923456789912345678923456789912345678923456789912345678923456789912345678923456789123456789234567891123456789123456789123453678912345678912345678912345678912345678912345678912345678912345678912345678912345678912345678912345678912345678912345678912345678912345678912345678991234567892345678991234567892345678991234567892345678
<= 9000
=> e01c00ffa79912345678923456789912345678923456789123456789234567891123456789123456789123453678912345678912345678912345678912345678912345678912345678912345678912345678912345678912345678912345678912345678912345678912345678912345678912345678991234567892345678991234567892345678991234567892345678991234567892345678991234567892345678912345678923456789
<= 9000
=> e00c000115058000002c8000003c800000000000000000000000
<= 1c6f5490a5f227b0a77eb59ebc9b51dad97759930a71692bb868b0b796245c57dd177634a3bada7e155beb1f79dc68bce7daf1a7931642a08aa2e82eb35c9546ec9000""",
    ),
    Eip712Fixture(
        "10",
        """{
    "domain": {
        "chainId": 5,
        "name": "Ether Mail",
        "verifyingContract": "0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC",
        "version": "1"
    },
    "message": {
        "val" : "0x973bb640"
    },
    "primaryType": "Test",
    "types": {
        "EIP712Domain": [
            { "name": "name", "type": "string" },
            { "name": "version", "type": "string" },
            { "name": "chainId", "type": "uint256" },
            { "name": "verifyingContract", "type": "address" }
        ],
        "Test": [
            { "name": "val", "type": "bytes4" }
        ]
    }
}""",
        """=> e01a00000c454950373132446f6d61696e
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff09050776657273696f6e
<= 9000
=> e01a00ff0a422007636861696e4964
<= 9000
=> e01a00ff130311766572696679696e67436f6e7472616374
<= 9000
=> e01a00000454657374
<= 9000
=> e01a00ff0646040376616c
<= 9000
=> e01c00000c454950373132446f6d61696e
<= 9000
=> e01c00ff0c000a4574686572204d61696c
<= 9000
=> e01c00ff03000131
<= 9000
=> e01c00ff03000105
<= 9000
=> e01c00ff160014cccccccccccccccccccccccccccccccccccccccc
<= 9000
=> e01c00000454657374
<= 9000
=> e01c00ff060004973bb640
<= 9000
=> e00c000115058000002c8000003c800000000000000000000000
<= 1ce71d7f114d3fc431f988e359bbea47c7ab81e755482201d3acd66557d7d611a44b3fb9d06c08ff11764f7f296af9b2e17c50aa9776581485a0284d4e66ea70c49000""",
    ),
    Eip712Fixture(
        "11",
        """{
    "domain": {
        "chainId": 1,
        "name": "Complex Structs Mail",
        "verifyingContract": "0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC",
        "version": "1"
    },
    "message": {
        "contents": "Hello, Bob!",
        "from": {
            "name": "Cow",
            "wallets": [
                "0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826",
                "0xDeaDbeefdEAdbeefdEadbEEFdeadbeEFdEaDbeeF"
            ]
        },
        "to": {
            "name": "test list",
            "members": [
                {
                    "name": "Bob",
                    "wallets": [
                        "0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB",
                        "0xB0B0b0b0b0b0B000000000000000000000000000"
                    ]
                },
                {
                    "name": "Alice",
                    "wallets": [
                        "0xB0BdaBea57B0BDABeA57b0bdABEA57b0BDabEa57"
                    ]
                }
            ]
        },
        "attach": {
            "list": [
                {
                    "name": "first",
                    "size": "100"
                },
                {
                    "name": "second",
                    "size": "3400"
                }
            ]
        }
    },
    "primaryType": "Mail",
    "types": {
        "EIP712Domain": [
            { "name": "name", "type": "string" },
            { "name": "version", "type": "string" },
            { "name": "chainId", "type": "uint256" },
            { "name": "verifyingContract", "type": "address" }
        ],
        "Attachment": [
            { "name": "name", "type": "string" },
            { "name": "size", "type": "uint16" }
        ],
        "Attachments": [
            { "name": "list", "type": "Attachment[]" }
        ],
        "MailingList": [
            { "name": "name", "type": "string" },
            { "name": "members", "type": "Person[]" }
        ],
        "Mail": [
            { "name": "from", "type": "Person" },
            { "name": "to", "type": "MailingList" },
            { "name": "contents", "type": "string" },
            { "name": "attach", "type": "Attachments" }
        ],
        "Person": [
            { "name": "name", "type": "string" },
            { "name": "wallets", "type": "address[]" }
        ]
    }
}""",
        """=> e01a00000a4174746163686d656e74
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff0742020473697a65
<= 9000
=> e01a00000b4174746163686d656e7473
<= 9000
=> e01a00ff13800a4174746163686d656e740100046c697374
<= 9000
=> e01a00000c454950373132446f6d61696e
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff09050776657273696f6e
<= 9000
=> e01a00ff0a422007636861696e4964
<= 9000
=> e01a00ff130311766572696679696e67436f6e7472616374
<= 9000
=> e01a0000044d61696c
<= 9000
=> e01a00ff0d0006506572736f6e0466726f6d
<= 9000
=> e01a00ff10000b4d61696c696e674c69737402746f
<= 9000
=> e01a00ff0a0508636f6e74656e7473
<= 9000
=> e01a00ff14000b4174746163686d656e747306617474616368
<= 9000
=> e01a00000b4d61696c696e674c697374
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff128006506572736f6e0100076d656d62657273
<= 9000
=> e01a000006506572736f6e
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff0b8301000777616c6c657473
<= 9000
=> e01c00000c454950373132446f6d61696e
<= 9000
=> e01c00ff160014436f6d706c65782053747275637473204d61696c
<= 9000
=> e01c00ff03000131
<= 9000
=> e01c00ff03000101
<= 9000
=> e01c00ff160014cccccccccccccccccccccccccccccccccccccccc
<= 9000
=> e01c0000044d61696c
<= 9000
=> e01c00ff050003436f77
<= 9000
=> e01c000f0102
<= 9000
=> e01c00ff160014cd2a3d9f938e13cd947ec05abc7fe734df8dd826
<= 9000
=> e01c00ff160014deadbeefdeadbeefdeadbeefdeadbeefdeadbeef
<= 9000
=> e01c00ff0b000974657374206c697374
<= 9000
=> e01c000f0102
<= 9000
=> e01c00ff050003426f62
<= 9000
=> e01c000f0102
<= 9000
=> e01c00ff160014bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
<= 9000
=> e01c00ff160014b0b0b0b0b0b0b000000000000000000000000000
<= 9000
=> e01c00ff070005416c696365
<= 9000
=> e01c000f0101
<= 9000
=> e01c00ff160014b0bdabea57b0bdabea57b0bdabea57b0bdabea57
<= 9000
=> e01c00ff0d000b48656c6c6f2c20426f6221
<= 9000
=> e01c000f0102
<= 9000
=> e01c00ff0700056669727374
<= 9000
=> e01c00ff03000164
<= 9000
=> e01c00ff0800067365636f6e64
<= 9000
=> e01c00ff0400020d48
<= 9000
=> e00c000115058000002c8000003c800000000000000000000000
<= 1cd0259bc5208c369532c6161486d7533966d382fc725bcb781a190c0f1a53f7717ebbf21c5569d2d2a480615d529b431d3b0dfce863f723d21e3370e860a8965c9000""",
    ),
    Eip712Fixture(
        "12",
        """{
    "domain": {
      "name": "Who are You?",
      "version": "1"
    },
    "message": {
      "banner": "Please sign this message with your wallet",
      "curDate": 1660659773,
      "id": 38
    },
    "primaryType": "Auth",
    "types": {
        "Auth": [
            {
                "name": "banner",
                "type": "string"
            },
            {
                "name": "curDate",
                "type": "uint32"
            },
            {
                "name": "id",
                "type": "uint8"
            }
        ],
        "EIP712Domain": [
            {
            "name": "name",
            "type": "string"
            },
            {
            "name": "version",
            "type": "string"
            }
        ]  
    }
  }""",
        """=> e01a00000441757468
<= 9000
=> e01a00ff08050662616e6e6572
<= 9000
=> e01a00ff0a42040763757244617465
<= 9000
=> e01a00ff054201026964
<= 9000
=> e01a00000c454950373132446f6d61696e
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff09050776657273696f6e
<= 9000
=> e01c00000c454950373132446f6d61696e
<= 9000
=> e01c00ff0e000c57686f2061726520596f753f
<= 9000
=> e01c00ff03000131
<= 9000
=> e01c00000441757468
<= 9000
=> e01c00ff2b0029506c65617365207369676e2074686973206d657373616765207769746820796f75722077616c6c6574
<= 9000
=> e01c00ff06000462fba83d
<= 9000
=> e01c00ff03000126
<= 9000
=> e00c000115058000002c8000003c800000000000000000000000
<= 1c4aef3aaff62fa0731f4e661c4dbb92a48f8c12d7225219fad74b55ef2ad0045b46d7e01804c33a99c4dc7dd7b2ac5e63d07ee4641b01cd3a598cc91d74bbe3e09000""",
    ),
    Eip712Fixture(
        "13",
        """{
  "domain": {
    "chainId": 1,
    "name": "Signed Ints test",
    "verifyingContract": "0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC",
    "version": "1"
  },
  "message": {
    "max": "115792089237316195423570985008687907853269984665640564039457584007913129639935",
    "neg256": "-57896044618658097711785492504343953926634992332820282019728792003956564819968",
    "pos256": "57896044618658097711785492504343953926634992332820282019728792003956564819967",
    "neg128": "-170141183460469231731687303715884105728",
    "pos128": "170141183460469231731687303715884105727",
    "neg64": "-9223372036854775808",
    "pos64": "9223372036854775807",
    "neg32": "-2147483648",
    "pos32": "2147483647",
    "neg16": "-32768",
    "pos16": "32767",
    "neg8": "-128",
    "pos8": "127"
  },
  "primaryType": "Test",
  "types": {
    "EIP712Domain": [
      {
        "name": "name",
        "type": "string"
      },
      {
        "name": "version",
        "type": "string"
      },
      {
        "name": "chainId",
        "type": "uint256"
      },
      {
        "name": "verifyingContract",
        "type": "address"
      }
    ],
    "Test": [
      {
        "name": "max",
        "type": "uint256"
      },
      {
        "name": "neg256",
        "type": "int256"
      },
      {
        "name": "pos256",
        "type": "int256"
      },
      {
        "name": "neg128",
        "type": "int128"
      },
      {
        "name": "pos128",
        "type": "int128"
      },
      {
        "name": "neg64",
        "type": "int64"
      },
      {
        "name": "pos64",
        "type": "int64"
      },
      {
        "name": "neg32",
        "type": "int32"
      },
      {
        "name": "pos32",
        "type": "int32"
      },
      {
        "name": "neg16",
        "type": "int16"
      },
      {
        "name": "pos16",
        "type": "int16"
      },
      {
        "name": "neg8",
        "type": "int8"
      },
      {
        "name": "pos8",
        "type": "int8"
      }
    ]
  }
}""",
        """=> e01a00000c454950373132446f6d61696e
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff09050776657273696f6e
<= 9000
=> e01a00ff0a422007636861696e4964
<= 9000
=> e01a00ff130311766572696679696e67436f6e7472616374
<= 9000
=> e01a00000454657374
<= 9000
=> e01a00ff064220036d6178
<= 9000
=> e01a00ff094120066e6567323536
<= 9000
=> e01a00ff09412006706f73323536
<= 9000
=> e01a00ff094110066e6567313238
<= 9000
=> e01a00ff09411006706f73313238
<= 9000
=> e01a00ff084108056e65673634
<= 9000
=> e01a00ff08410805706f733634
<= 9000
=> e01a00ff084104056e65673332
<= 9000
=> e01a00ff08410405706f733332
<= 9000
=> e01a00ff084102056e65673136
<= 9000
=> e01a00ff08410205706f733136
<= 9000
=> e01a00ff074101046e656738
<= 9000
=> e01a00ff07410104706f7338
<= 9000
=> e01c00000c454950373132446f6d61696e
<= 9000
=> e01c00ff1200105369676e656420496e74732074657374
<= 9000
=> e01c00ff03000131
<= 9000
=> e01c00ff03000101
<= 9000
=> e01c00ff160014cccccccccccccccccccccccccccccccccccccccc
<= 9000
=> e01c00000454657374
<= 9000
=> e01c00ff220020ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff
<= 9000
=> e01c00ff2200208000000000000000000000000000000000000000000000000000000000000000
<= 9000
=> e01c00ff2200207fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff
<= 9000
=> e01c00ff12001080000000000000000000000000000000
<= 9000
=> e01c00ff1200107fffffffffffffffffffffffffffffff
<= 9000
=> e01c00ff0a00088000000000000000
<= 9000
=> e01c00ff0a00087fffffffffffffff
<= 9000
=> e01c00ff06000480000000
<= 9000
=> e01c00ff0600047fffffff
<= 9000
=> e01c00ff0400028000
<= 9000
=> e01c00ff0400027fff
<= 9000
=> e01c00ff03000180
<= 9000
=> e01c00ff0300017f
<= 9000
=> e00c000115058000002c8000003c800000000000000000000000
<= 1cdaf758e25d9d7769adcab19c4d64953983d29fb44041e0ba4263a9d4686a3de303c52a566b18568ba71576e768ed321a6a90605365fe9766387db3bd24bebe969000""",
    ),
    Eip712Fixture(
        "14",
        """{
  "types": {
    "EIP712Domain": [
      {
        "name": "name",
        "type": "string"
      },
      {
        "name": "version",
        "type": "string"
      }
    ],
    "ChangeOwnerThreshold": [
      {
        "name": "newThreshold",
        "type": "uint256"
      }
    ]
  },
  "primaryType": "ChangeOwnerThreshold",
  "domain": {
    "name": "ChangeOwnerThreshold",
    "version": "1"
  },
  "message": {
    "newThreshold": "0x3"
  }
}""",
        """=> e01a0000144368616e67654f776e65725468726573686f6c64
<= 9000
=> e01a00ff0f42200c6e65775468726573686f6c64
<= 9000
=> e01a00000c454950373132446f6d61696e
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff09050776657273696f6e
<= 9000
=> e01c00000c454950373132446f6d61696e
<= 9000
=> e01c00ff1600144368616e67654f776e65725468726573686f6c64
<= 9000
=> e01c00ff03000131
<= 9000
=> e01c0000144368616e67654f776e65725468726573686f6c64
<= 9000
=> e01c00ff03000103
<= 9000
=> e00c000115058000002c8000003c800000000000000000000000
<= 1b15502f71994b3a6cade2f2aa0243058ccfd44d09ad1fb5392180f2a430ed396d1ecf5ac4964fd1068ad90b2586bc12e9ac9a77fb04331da54023ee16747944119000""",
    ),
    Eip712Fixture(
        "14bis",
        """{
  "types": {
    "EIP712Domain": [
      {
        "name": "name",
        "type": "string"
      },
      {
        "name": "version",
        "type": "string"
      }
    ],
    "ChangeOwnerThreshold": [
      {
        "name": "newThreshold",
        "type": "uint256"
      }
    ]
  },
  "primaryType": "ChangeOwnerThreshold",
  "domain": {
    "name": "ChangeOwnerThreshold",
    "version": "1"
  },
  "message": {
    "newThreshold": "0x03"
  }
}""",
        """=> e01a0000144368616e67654f776e65725468726573686f6c64
<= 9000
=> e01a00ff0f42200c6e65775468726573686f6c64
<= 9000
=> e01a00000c454950373132446f6d61696e
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff09050776657273696f6e
<= 9000
=> e01c00000c454950373132446f6d61696e
<= 9000
=> e01c00ff1600144368616e67654f776e65725468726573686f6c64
<= 9000
=> e01c00ff03000131
<= 9000
=> e01c0000144368616e67654f776e65725468726573686f6c64
<= 9000
=> e01c00ff03000103
<= 9000
=> e00c000115058000002c8000003c800000000000000000000000
<= 1b15502f71994b3a6cade2f2aa0243058ccfd44d09ad1fb5392180f2a430ed396d1ecf5ac4964fd1068ad90b2586bc12e9ac9a77fb04331da54023ee16747944119000""",
    ),
    Eip712Fixture(
        "15-permit",
        """{
  "domain": {
    "name": "USD Coin",
    "verifyingContract": "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
    "chainId": 1,
    "version": "2"
  },
  "primaryType": "Permit",
  "message": {
    "deadline": 1718992051,
    "spender": "0x111111125421ca6dc452d289314280a0f8842a65",
    "nonce": 0,
    "value": "115792089237316195423570985008687907853269984665640564039457584007913129639935",
    "owner": "0x6cbcd73cd8e8a42844662f0a0e76d7f79afd933d"
  },
  "types": {
    "EIP712Domain": [
      {
        "name": "name",
        "type": "string"
      },
      {
        "name": "version",
        "type": "string"
      },
      {
        "name": "chainId",
        "type": "uint256"
      },
      {
        "name": "verifyingContract",
        "type": "address"
      }
    ],
    "Permit": [
      {
        "name": "owner",
        "type": "address"
      },
      {
        "name": "spender",
        "type": "address"
      },
      {
        "name": "value",
        "type": "uint256"
      },
      {
        "name": "nonce",
        "type": "uint256"
      },
      {
        "name": "deadline",
        "type": "uint256"
      }
    ]
  }
}""",
        """=> e01a00000c454950373132446f6d61696e
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff09050776657273696f6e
<= 9000
=> e01a00ff0a422007636861696e4964
<= 9000
=> e01a00ff130311766572696679696e67436f6e7472616374
<= 9000
=> e01a0000065065726d6974
<= 9000
=> e01a00ff0703056f776e6572
<= 9000
=> e01a00ff0903077370656e646572
<= 9000
=> e01a00ff0842200576616c7565
<= 9000
=> e01a00ff084220056e6f6e6365
<= 9000
=> e01a00ff0b422008646561646c696e65
<= 9000
=> e01c00000c454950373132446f6d61696e
<= 9000
=> e01c00ff0a000855534420436f696e
<= 9000
=> e01c00ff03000132
<= 9000
=> e01c00ff03000101
<= 9000
=> e01c00ff160014a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48
<= 9000
=> e01c0000065065726d6974
<= 9000
=> e01c00ff1600146cbcd73cd8e8a42844662f0a0e76d7f79afd933d
<= 9000
=> e01c00ff160014111111125421ca6dc452d289314280a0f8842a65
<= 9000
=> e01c00ff220020ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff
<= 9000
=> e01c00ff03000100
<= 9000
=> e01c00ff0600046675bcb3
<= 9000
=> e00c000115058000002c8000003c800000000000000000000000
<= 1c9573c40857d73d28b43120231886cf4199b1456e00da8887a508d576b6985a6f18515302ca7809f9d36b95c8ea91509b602adc3c1653be0255ac5726969307bd9000""",
    ),
    Eip712Fixture(
        "16-permit2",
        """{
  "domain": {
    "name": "Permit2",
    "chainId": 137,
    "verifyingContract": "0x000000000022d473030f116ddee9f6b43ac78ba3"
  },
  "primaryType": "PermitSingle",
  "message": {
    "details": {
      "expiration": "1718184249",
      "amount": "69420000000000000000",
      "nonce": "0",
      "token": "0x7ceb23fd6bc0add59e62ac25578270cff1b9f619"
    },
    "spender": "0xec7be89e9d109e7e3fec59c222cf297125fefda2",
    "sigDeadline": "1715594049"
  },
  "types": {
    "PermitSingle": [
      {
        "name": "details",
        "type": "PermitDetails"
      },
      {
        "name": "spender",
        "type": "address"
      },
      {
        "name": "sigDeadline",
        "type": "uint256"
      }
    ],
    "PermitDetails": [
      {
        "name": "token",
        "type": "address"
      },
      {
        "name": "amount",
        "type": "uint160"
      },
      {
        "name": "expiration",
        "type": "uint48"
      },
      {
        "name": "nonce",
        "type": "uint48"
      }
    ],
    "EIP712Domain": [
      {
        "name": "name",
        "type": "string"
      },
      {
        "name": "chainId",
        "type": "uint256"
      },
      {
        "name": "verifyingContract",
        "type": "address"
      }
    ]
  }
}""",
        """=> e01a00000c454950373132446f6d61696e
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff0a422007636861696e4964
<= 9000
=> e01a00ff130311766572696679696e67436f6e7472616374
<= 9000
=> e01a00000d5065726d697444657461696c73
<= 9000
=> e01a00ff070305746f6b656e
<= 9000
=> e01a00ff09421406616d6f756e74
<= 9000
=> e01a00ff0d42060a65787069726174696f6e
<= 9000
=> e01a00ff084206056e6f6e6365
<= 9000
=> e01a00000c5065726d697453696e676c65
<= 9000
=> e01a00ff17000d5065726d697444657461696c730764657461696c73
<= 9000
=> e01a00ff0903077370656e646572
<= 9000
=> e01a00ff0e42200b736967446561646c696e65
<= 9000
=> e01c00000c454950373132446f6d61696e
<= 9000
=> e01c00ff0900075065726d697432
<= 9000
=> e01c00ff03000189
<= 9000
=> e01c00ff160014000000000022d473030f116ddee9f6b43ac78ba3
<= 9000
=> e01c00000c5065726d697453696e676c65
<= 9000
=> e01c00ff1600147ceb23fd6bc0add59e62ac25578270cff1b9f619
<= 9000
=> e01c00ff0b000903c3656232739e0000
<= 9000
=> e01c00ff06000466696939
<= 9000
=> e01c00ff03000100
<= 9000
=> e01c00ff160014ec7be89e9d109e7e3fec59c222cf297125fefda2
<= 9000
=> e01c00ff0600046641e341
<= 9000
=> e00c000115058000002c8000003c800000000000000000000000
<= 1bce7c4941157899c0db37c4363c773d919c896ddef669c878e856573659bb36550fed0222b941702c2fd5611ac13ac0217ed889586a56b047b0d5bf0566edbbb79000""",
    ),
    Eip712Fixture(
        "17-uniswapx",
        """{
  "domain": {
    "name": "Permit2",
    "chainId": 1,
    "verifyingContract": "0x000000000022d473030f116ddee9f6b43ac78ba3"
  },
  "primaryType": "PermitWitnessTransferFrom",
  "message": {
    "permitted": {
      "token": "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2",
      "amount": "100000000000000000"
    },
    "spender": "0x6000da47483062a0d734ba3dc7576ce6a0b645c4",
    "nonce": "1993354326232431306240697018571020274189859638820531235067341608173853199105",
    "deadline": "1718467096",
    "witness": {
      "info": {
        "nonce": "1993354326232431306240697018571020274189859638820531235067341608173853199105",
        "swapper": "0x224cbc440944c72e951507e97d8bf5ffa2e3d2b9",
        "additionalValidationContract": "0x0000000000000000000000000000000000000000",
        "reactor": "0x6000da47483062a0d734ba3dc7576ce6a0b645c4",
        "deadline": "1718467096",
        "additionalValidationData": "0x"
      },
      "decayStartTime": "1718467024",
      "decayEndTime": "1718467084",
      "exclusiveFiller": "0xff8ba4d1fc3762f6154cc942ccf30049a2a0cec6",
      "exclusivityOverrideBps": "100",
      "inputToken": "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2",
      "inputStartAmount": "100000000000000000",
      "inputEndAmount": "100000000000000000",
      "outputs": [
        {
          "startAmount": "348572327",
          "token": "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
          "endAmount": "332452012",
          "recipient": "0x224cbc440944c72e951507e97d8bf5ffa2e3d2b9"
        },
        {
          "endAmount": "833213",
          "token": "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
          "startAmount": "873614",
          "recipient": "0x27213e28d7fda5c57fe9e5dd923818dbccf71c47"
        }
      ]
    }
  },
  "types": {
    "PermitWitnessTransferFrom": [
      {
        "name": "permitted",
        "type": "TokenPermissions"
      },
      {
        "name": "spender",
        "type": "address"
      },
      {
        "name": "nonce",
        "type": "uint256"
      },
      {
        "name": "deadline",
        "type": "uint256"
      },
      {
        "name": "witness",
        "type": "ExclusiveDutchOrder"
      }
    ],
    "TokenPermissions": [
      {
        "name": "token",
        "type": "address"
      },
      {
        "name": "amount",
        "type": "uint256"
      }
    ],
    "ExclusiveDutchOrder": [
      {
        "name": "info",
        "type": "OrderInfo"
      },
      {
        "name": "decayStartTime",
        "type": "uint256"
      },
      {
        "name": "decayEndTime",
        "type": "uint256"
      },
      {
        "name": "exclusiveFiller",
        "type": "address"
      },
      {
        "name": "exclusivityOverrideBps",
        "type": "uint256"
      },
      {
        "name": "inputToken",
        "type": "address"
      },
      {
        "name": "inputStartAmount",
        "type": "uint256"
      },
      {
        "name": "inputEndAmount",
        "type": "uint256"
      },
      {
        "name": "outputs",
        "type": "DutchOutput[]"
      }
    ],
    "OrderInfo": [
      {
        "name": "reactor",
        "type": "address"
      },
      {
        "name": "swapper",
        "type": "address"
      },
      {
        "name": "nonce",
        "type": "uint256"
      },
      {
        "name": "deadline",
        "type": "uint256"
      },
      {
        "name": "additionalValidationContract",
        "type": "address"
      },
      {
        "name": "additionalValidationData",
        "type": "bytes"
      }
    ],
    "DutchOutput": [
      {
        "name": "token",
        "type": "address"
      },
      {
        "name": "startAmount",
        "type": "uint256"
      },
      {
        "name": "endAmount",
        "type": "uint256"
      },
      {
        "name": "recipient",
        "type": "address"
      }
    ],
    "EIP712Domain": [
      {
        "name": "name",
        "type": "string"
      },
      {
        "name": "chainId",
        "type": "uint256"
      },
      {
        "name": "verifyingContract",
        "type": "address"
      }
    ]
  }
}""",
        """=> e01a00000b44757463684f7574707574
<= 9000
=> e01a00ff070305746f6b656e
<= 9000
=> e01a00ff0e42200b7374617274416d6f756e74
<= 9000
=> e01a00ff0c422009656e64416d6f756e74
<= 9000
=> e01a00ff0b0309726563697069656e74
<= 9000
=> e01a00000c454950373132446f6d61696e
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff0a422007636861696e4964
<= 9000
=> e01a00ff130311766572696679696e67436f6e7472616374
<= 9000
=> e01a0000134578636c757369766544757463684f72646572
<= 9000
=> e01a00ff1000094f72646572496e666f04696e666f
<= 9000
=> e01a00ff1142200e6465636179537461727454696d65
<= 9000
=> e01a00ff0f42200c6465636179456e6454696d65
<= 9000
=> e01a00ff11030f6578636c757369766546696c6c6572
<= 9000
=> e01a00ff194220166578636c757369766974794f76657272696465427073
<= 9000
=> e01a00ff0c030a696e707574546f6b656e
<= 9000
=> e01a00ff13422010696e7075745374617274416d6f756e74
<= 9000
=> e01a00ff1142200e696e707574456e64416d6f756e74
<= 9000
=> e01a00ff17800b44757463684f75747075740100076f757470757473
<= 9000
=> e01a0000094f72646572496e666f
<= 9000
=> e01a00ff09030772656163746f72
<= 9000
=> e01a00ff09030773776170706572
<= 9000
=> e01a00ff084220056e6f6e6365
<= 9000
=> e01a00ff0b422008646561646c696e65
<= 9000
=> e01a00ff1e031c6164646974696f6e616c56616c69646174696f6e436f6e7472616374
<= 9000
=> e01a00ff1a07186164646974696f6e616c56616c69646174696f6e44617461
<= 9000
=> e01a0000195065726d69745769746e6573735472616e7366657246726f6d
<= 9000
=> e01a00ff1c0010546f6b656e5065726d697373696f6e73097065726d6974746564
<= 9000
=> e01a00ff0903077370656e646572
<= 9000
=> e01a00ff084220056e6f6e6365
<= 9000
=> e01a00ff0b422008646561646c696e65
<= 9000
=> e01a00ff1d00134578636c757369766544757463684f72646572077769746e657373
<= 9000
=> e01a000010546f6b656e5065726d697373696f6e73
<= 9000
=> e01a00ff070305746f6b656e
<= 9000
=> e01a00ff09422006616d6f756e74
<= 9000
=> e01c00000c454950373132446f6d61696e
<= 9000
=> e01c00ff0900075065726d697432
<= 9000
=> e01c00ff03000101
<= 9000
=> e01c00ff160014000000000022d473030f116ddee9f6b43ac78ba3
<= 9000
=> e01c0000195065726d69745769746e6573735472616e7366657246726f6d
<= 9000
=> e01c00ff160014c02aaa39b223fe8d0a0e5c4f27ead9083c756cc2
<= 9000
=> e01c00ff0a0008016345785d8a0000
<= 9000
=> e01c00ff1600146000da47483062a0d734ba3dc7576ce6a0b645c4
<= 9000
=> e01c00ff220020046832d5460b1e3ded505a7b75452a48a1ef52283c2dd2afd878e8a0bdefcb01
<= 9000
=> e01c00ff060004666dba18
<= 9000
=> e01c00ff1600146000da47483062a0d734ba3dc7576ce6a0b645c4
<= 9000
=> e01c00ff160014224cbc440944c72e951507e97d8bf5ffa2e3d2b9
<= 9000
=> e01c00ff220020046832d5460b1e3ded505a7b75452a48a1ef52283c2dd2afd878e8a0bdefcb01
<= 9000
=> e01c00ff060004666dba18
<= 9000
=> e01c00ff1600140000000000000000000000000000000000000000
<= 9000
=> e01c00ff020000
<= 9000
=> e01c00ff060004666db9d0
<= 9000
=> e01c00ff060004666dba0c
<= 9000
=> e01c00ff160014ff8ba4d1fc3762f6154cc942ccf30049a2a0cec6
<= 9000
=> e01c00ff03000164
<= 9000
=> e01c00ff160014c02aaa39b223fe8d0a0e5c4f27ead9083c756cc2
<= 9000
=> e01c00ff0a0008016345785d8a0000
<= 9000
=> e01c00ff0a0008016345785d8a0000
<= 9000
=> e01c000f0102
<= 9000
=> e01c00ff160014a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48
<= 9000
=> e01c00ff06000414c6caa7
<= 9000
=> e01c00ff06000413d0d0ac
<= 9000
=> e01c00ff160014224cbc440944c72e951507e97d8bf5ffa2e3d2b9
<= 9000
=> e01c00ff160014a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48
<= 9000
=> e01c00ff0500030d548e
<= 9000
=> e01c00ff0500030cb6bd
<= 9000
=> e01c00ff16001427213e28d7fda5c57fe9e5dd923818dbccf71c47
<= 9000
=> e00c000115058000002c8000003c800000000000000000000000
<= 1b63e951154de94e9f81ecefa38c0f11c2e4a9bfbaa3524b6c9744161211d6cc3b62480171ceb1f39f2c41ff06a2ecd483c0faaaf459063b278c09803b8bef3e4d9000""",
    ),
    Eip712Fixture(
        "18-1inch-fusion",
        """{
  "domain": {
    "name": "1inch Aggregation Router",
    "version": "6",
    "chainId": 137,
    "verifyingContract": "0x111111125421ca6dc452d289314280a0f8842a65"
  },
  "primaryType": "Order",
  "message": {
    "maker": "0x6cbcd73cd8e8a42844662f0a0e76d7f79afd933d",
    "makerAsset": "0x2791bca1f2de4661ed88a30c99a7a9449aa84174",
    "takerAsset": "0x0d500b1d8e8ef31e21c99d1db9a6444d3adf1270",
    "makerTraits": "62645329528782394789705396186445553993652106036464673180170288481221805604864",
    "salt": "102412815608494630857823958083264479936169442601352546394025598722002601410760",
    "makingAmount": "400000000",
    "takingAmount": "694455056739513017000",
    "receiver": "0xd8da6bf26964af9d7eed9e03e53415d37aa96045"
  },
  "types": {
    "EIP712Domain": [
      {
        "name": "name",
        "type": "string"
      },
      {
        "name": "version",
        "type": "string"
      },
      {
        "name": "chainId",
        "type": "uint256"
      },
      {
        "name": "verifyingContract",
        "type": "address"
      }
    ],
    "Order": [
      {
        "name": "salt",
        "type": "uint256"
      },
      {
        "name": "maker",
        "type": "address"
      },
      {
        "name": "receiver",
        "type": "address"
      },
      {
        "name": "makerAsset",
        "type": "address"
      },
      {
        "name": "takerAsset",
        "type": "address"
      },
      {
        "name": "makingAmount",
        "type": "uint256"
      },
      {
        "name": "takingAmount",
        "type": "uint256"
      },
      {
        "name": "makerTraits",
        "type": "uint256"
      }
    ]
  }
}""",
        """=> e01a00000c454950373132446f6d61696e
<= 9000
=> e01a00ff0605046e616d65
<= 9000
=> e01a00ff09050776657273696f6e
<= 9000
=> e01a00ff0a422007636861696e4964
<= 9000
=> e01a00ff130311766572696679696e67436f6e7472616374
<= 9000
=> e01a0000054f72646572
<= 9000
=> e01a00ff0742200473616c74
<= 9000
=> e01a00ff0703056d616b6572
<= 9000
=> e01a00ff0a03087265636569766572
<= 9000
=> e01a00ff0c030a6d616b65724173736574
<= 9000
=> e01a00ff0c030a74616b65724173736574
<= 9000
=> e01a00ff0f42200c6d616b696e67416d6f756e74
<= 9000
=> e01a00ff0f42200c74616b696e67416d6f756e74
<= 9000
=> e01a00ff0e42200b6d616b6572547261697473
<= 9000
=> e01c00000c454950373132446f6d61696e
<= 9000
=> e01c00ff1a001831696e6368204167677265676174696f6e20526f75746572
<= 9000
=> e01c00ff03000136
<= 9000
=> e01c00ff03000189
<= 9000
=> e01c00ff160014111111125421ca6dc452d289314280a0f8842a65
<= 9000
=> e01c0000054f72646572
<= 9000
=> e01c00ff220020e26b9977a477a09114d8b36effd41bd0dcef1df7944a0451599b62e5d94880c8
<= 9000
=> e01c00ff1600146cbcd73cd8e8a42844662f0a0e76d7f79afd933d
<= 9000
=> e01c00ff160014d8da6bf26964af9d7eed9e03e53415d37aa96045
<= 9000
=> e01c00ff1600142791bca1f2de4661ed88a30c99a7a9449aa84174
<= 9000
=> e01c00ff1600140d500b1d8e8ef31e21c99d1db9a6444d3adf1270
<= 9000
=> e01c00ff06000417d78400
<= 9000
=> e01c00ff0b000925a57ffab9586a22a8
<= 9000
=> e01c00ff2200208a8000000000000000000000125400000100666844fb00000000000000000000
<= 9000
=> e00c000115058000002c8000003c800000000000000000000000
<= 1b6f07ba3bb7fa9369ee9b5e4cc3bdc8545d75e3527fa242a5e4d23ead9d232af8412a55401fe955b996125682ad0a47277d3ce1b314ee3962956ae643b71166cb9000""",
    ),
)
