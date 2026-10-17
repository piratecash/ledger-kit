# ledger-kit

Kotlin Multiplatform library that talks to Ledger hardware wallets over APDU, for the P.CASH wallet.

- `LedgerTransport` — one channel to a device:
  - Android USB HID (`AndroidUsbLedger.open(usbManager, device)`, the app holds the USB permission)
  - desktop USB HID via [hid4java](https://github.com/gary-rowe/hid4java) (`DesktopHidLedger.openFirst()`)
  - the [speculos](https://github.com/LedgerHQ/speculos) emulator APDU port (`SpeculosTransport.connect(host, port)`),
    on both targets, for tests and debug builds
- Ledger HID framing (64-byte reports, channel `0x0101`, tag `0x05`), APDU builder and chunking,
  status words mapped to typed `LedgerException`s
- `LedgerSession` — serializes every exchange of one operation and opens the requested app
  (`GET_APP_AND_VERSION`, `QUIT_APP`, `OPEN_APP`) with a minimum app version
- `BitcoinAppClient` — the Bitcoin app 2.x (`CLA E1`): master fingerprint, xpubs, addresses of
  single-signature `pkh`/`sh(wpkh)`/`wpkh`/`tr` wallet policies, PSBTv2 signing with per-input partial signatures
- `BtchipLegacyClient` — the legacy btchip protocol (`CLA E0`) of the Litecoin, Dogecoin, Dash and Bitcoin Cash
  apps: public keys and addresses, legacy and BIP143 (segwit, `SIGHASH_FORKID`) signing through trusted inputs
- `EthereumAppClient` — the Ethereum app 1.x (`CLA E0`): public key and address, signing of EIP-155 legacy and
  EIP-2930/EIP-1559 transactions from their unsigned RLP, `personal_sign`, EIP-712 typed data from its JSON
  (struct definitions and implementations, no pre-hashed mode) and CAL-signed ERC-20 descriptors passed through as
  given. Signatures come back as the app reports them plus the checked y parity; `EthereumSignature` documents how
  to read `v` for each kind. Pre-EIP-155 transactions are refused: they are valid on every chain
- `SolanaAppClient` — the Solana app (`CLA E0`): public key, signing of serialized legacy and v0 messages and of
  off-chain messages, and the trusted name and CAL token descriptors that let the app show an SPL transfer in clear,
  passed through as given after `loadPkiCertificate` has loaded the certificate they are signed under
- `TronAppClient` — the Tron app (`CLA E0`): public key and address, signing of a `Transaction.raw` protobuf
  (`r || s || v`, `v` the recovery id)
- `StellarAppClient` — the Stellar app (`CLA E0`): public key, signing of a transaction by its signature base, which
  the app parses and shows (no hash signing)
- `TonAppClient` — the TON app (`CLA E0`): public key, signing of a v4R2 wallet transfer from its fields, with the
  body as a cell reference (hash and depth) plus a comment or jetton transfer hint the app shows in clear; a body
  without a hint signs blind. The answer carries the signed transfer cell hash, which the caller compares with the
  hash of the cell it sends
- `ThorchainAppClient` — the THORChain app (`CLA 55`): compressed public key and `thor1…` address, signing of a legacy
  amino JSON sign doc the caller builds (`thorchain/MsgSend`, `thorchain/MsgDeposit`); the app's DER signature comes
  back as the 64-byte low-s `r || s` Cosmos SDK expects
- `ZcashAppClient` — the Zcash app 3.9+ (`CLA E0`): the `uview1…` unified full viewing key (transparent + Orchard)
  and Orchard-only `u1…` address of an account, and PCZT signing of transparent inputs and Orchard and Ironwood
  (V6) actions from the fields of a proved PCZT; dummy actions are left to the host. A key or signing command past
  the app's per-run derivation cap raises `LedgerException.KeyDerivationLimit`; reopening the app clears it. Transparent addresses come from
  `BtchipLegacyClient`

Each client maps its app's refusal for a disabled setting (blind signing; Tron's transactions data or custom
contracts) to `LedgerException.BlindSigningRequired`. Ed25519 apps (Solana, Stellar, TON) take hardened paths only.

Targets: **Android** (minSdk 27, JVM 17 bytecode) and **desktop JVM** (JDK 21).

## Installation

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.piratecash.ledger-kit:ledgerkit:<version>")
}
```

## Build and test

JDK 21:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew :ledgerkit:desktopTest :ledgerkit:testDebugUnitTest :ledgerkit:assemble
./gradlew -PVERSION_NAME=0.0.0-SNAPSHOT publishToMavenLocal
./gradlew -p consumer-smoke compileKotlinDesktop compileDebugKotlinAndroid
```

The speculos tests (`*SpeculosTest`, `SpeculosIntegrationTest`) run only when `SPECULOS_APDU_PORT` points at a
running emulator started with the default seed; `SPECULOS_API_URL` (default `http://127.0.0.1:5000`) is its REST API,
used to approve signing screens. Each test class skips unless its own app (`Bitcoin`, `Bitcoin Test`, `Litecoin`, `Dogecoin`, `Bitcoin Cash`, `Dash`,
`Ethereum`, `Solana`, `Tron`, `Stellar`, `TON`, `THORChain`)
is the one running; the Ethereum, Solana, Tron and TON tests switch the app's blind signing (Tron: transactions data)
setting through its menu as they need it.
CI builds the Nano S Plus app ELFs with `scripts/build-speculos-apps.sh` and runs them one app at a time;
[apps/README.md](apps/README.md) lists the pinned apps, why Tron is not among them, and how to run the tests locally.

## Publishing

JitPack builds numeric SemVer tags (`0.1.0`, no `v` prefix) pushed from `main`; `jitpack.yml` runs
`./gradlew publishToMavenLocal` on OpenJDK 21. CI checks the tag on JitPack after all jobs pass.
A published tag is never rebuilt: a fix needs a new tag.

## License

MIT — see [LICENSE](LICENSE). Third-party code and test vectors are listed in [NOTICE](NOTICE).
