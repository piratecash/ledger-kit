# Ledger app ELFs for the speculos tests

Nano S Plus builds the CI `speculos` job runs one at a time with the default speculos seed. Each test class
skips unless its own app is running; an app without its own tests still runs `SpeculosIntegrationTest`.

The ELFs are not committed (`apps/*.elf` is ignored): every Ledger app links the SDK's STMicroelectronics USB
device library, whose license (MCD-ST Liberty V2) does not allow redistributing it outside ST devices.
[`scripts/build-speculos-apps.sh`](../scripts/build-speculos-apps.sh) builds them from these pinned commits
with Ledger's app builder images (pinned by digest in the script): `ledger-app-builder-lite` for the C apps,
`ledger-app-builder` for the Rust ones.

| ELF | App, version | Repository | Commit | License | Build |
|---|---|---|---|---|---|
| `bitcoin.elf` | Bitcoin 2.5.1 | [LedgerHQ/app-bitcoin-new](https://github.com/LedgerHQ/app-bitcoin-new) | `017aee4744722613ca211ca66c8137272c35ad0d` | Apache-2.0 | C, `COIN=bitcoin` |
| `bitcoin_testnet.elf` | Bitcoin Test 2.5.1 | LedgerHQ/app-bitcoin-new | `017aee4744722613ca211ca66c8137272c35ad0d` | Apache-2.0 | C, `COIN=bitcoin_testnet` |
| `litecoin.elf` | Litecoin 2.4.13 | [LedgerHQ/app-bitcoin-legacy](https://github.com/LedgerHQ/app-bitcoin-legacy) (+ `lib-app-bitcoin` `e369bc30a714`) | `fa287121d830a9ea4518babd09993764d32439fb` | Apache-2.0 | C, `COIN=litecoin` |
| `dogecoin.elf` | Dogecoin 2.4.13 | LedgerHQ/app-bitcoin-legacy | `fa287121d830a9ea4518babd09993764d32439fb` | Apache-2.0 | C, `COIN=dogecoin` |
| `dash.elf` | Dash 2.4.13 | LedgerHQ/app-bitcoin-legacy | `fa287121d830a9ea4518babd09993764d32439fb` | Apache-2.0 | C, `COIN=dash` |
| `bitcoin_cash.elf` | Bitcoin Cash 2.4.13 | LedgerHQ/app-bitcoin-legacy | `fa287121d830a9ea4518babd09993764d32439fb` | Apache-2.0 | C, `COIN=bitcoin_cash` |
| `ethereum.elf` | Ethereum 1.23.0 | [LedgerHQ/app-ethereum](https://github.com/LedgerHQ/app-ethereum) | `f7985b427841ace20795b548905ababf6a5b17a4` | Apache-2.0 | C |
| `solana.elf` | Solana 1.15.2 | [LedgerHQ/app-solana](https://github.com/LedgerHQ/app-solana) | `22d6c9b78d6721acdc6d0ce49788a2fd9ea409a0` | Apache-2.0 | C |
| `monero.elf` | Monero 2.2.0 | [LedgerHQ/app-monero](https://github.com/LedgerHQ/app-monero) | `f93f9d8180d22f6052081f666e9eec91e2c63dfa` | Apache-2.0 | C |
| `ton.elf` | TON 2.9.1 | [LedgerHQ/app-ton](https://github.com/LedgerHQ/app-ton) | `849962d6378567f5aaf708b116292ccf7b17a97c` | Apache-2.0 | C |
| `thorchain.elf` | THORChain 2.5.2 | [LedgerHQ/app-thorchain](https://github.com/LedgerHQ/app-thorchain) | `b5408e0539dbafeee76adf32ccd0f7d8621ea3d9` | Apache-2.0 | C, root `make` builds `app/` |
| `stellar.elf` | Stellar 6.1.0 | [LedgerHQ/app-stellar](https://github.com/LedgerHQ/app-stellar) | `6bfd82a72e3ab8f9d92f59211eca73e9c58416b2` | Apache-2.0 | Rust |
| `zcash.elf` | Zcash 3.9.4 | [LedgerHQ/app-zcash](https://github.com/LedgerHQ/app-zcash) | `a4c4b4f7b9e59d43b821cec3570243a355765d11` | Apache-2.0 | Rust |

Not built: the Tron app ([LedgerHQ/app-tron](https://github.com/LedgerHQ/app-tron), MIT). It compiles
`proto/core/Contract.proto`, which carries a GPL-3.0 header, into the binary.

## Running the speculos tests locally

Docker is required. Build the ELFs (all of them, or the ones named) into `apps/`, or put your own Nano S Plus
builds there under the same names:

```bash
scripts/build-speculos-apps.sh                  # every app; checkouts are kept in build/speculos-apps
scripts/build-speculos-apps.sh ethereum stellar # only these
```

Then start one app and run the tests against it:

```bash
docker run -d --rm --name speculos -p 9999:9999 -p 5000:5000 -v "$PWD/apps:/speculos/apps" \
  ghcr.io/ledgerhq/speculos --model nanosp --display headless --apdu-port 9999 --api-port 5000 apps/ethereum.elf
SPECULOS_APDU_PORT=9999 ./gradlew :ledgerkit:desktopTest --tests '*Speculos*' --rerun
docker rm -f speculos
```
