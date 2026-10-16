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

`SpeculosIntegrationTest` runs only when `SPECULOS_APDU_PORT` points at a running emulator.
CI starts speculos itself once a Nano S Plus app ELF is committed under `apps/`.

## Publishing

JitPack builds numeric SemVer tags (`0.1.0`, no `v` prefix) pushed from `main`; `jitpack.yml` runs
`./gradlew publishToMavenLocal` on OpenJDK 21. CI checks the tag on JitPack after all jobs pass.
A published tag is never rebuilt: a fix needs a new tag.

## License

MIT — see [LICENSE](LICENSE).
