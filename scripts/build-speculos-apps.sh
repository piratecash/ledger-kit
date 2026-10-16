#!/usr/bin/env bash
# Builds the Nano S Plus app ELFs the speculos tests run, from the commits pinned below, with Ledger's
# app builder images. The ELFs are not committed: every Ledger app links the SDK's STMicroelectronics
# USB library, whose license does not allow redistributing it outside ST devices.
#
# usage: scripts/build-speculos-apps.sh [--out DIR] [APP...]   (default: every app, into apps/)
set -euo pipefail

LITE_IMAGE=ghcr.io/ledgerhq/ledger-app-builder/ledger-app-builder-lite@sha256:9f6f8779096e551dac8ba080227ec21287f286d1866f2dee0929e21dbb480dd0
FULL_IMAGE=ghcr.io/ledgerhq/ledger-app-builder/ledger-app-builder@sha256:fc400a4b69e299b9c7ad4c600e62cae321151ac76b3629387452a3fcf23154a1

# ELF name | LedgerHQ repository | commit | submodules | build | ELF path in the checkout
APPS="
bitcoin|app-bitcoin-new|017aee4744722613ca211ca66c8137272c35ad0d||make COIN=bitcoin|build/nanos2/bin/app.elf
bitcoin_testnet|app-bitcoin-new|017aee4744722613ca211ca66c8137272c35ad0d||make COIN=bitcoin_testnet|build/nanos2/bin/app.elf
litecoin|app-bitcoin-legacy|fa287121d830a9ea4518babd09993764d32439fb|lib-app-bitcoin|make COIN=litecoin|build/nanos2/bin/app.elf
dogecoin|app-bitcoin-legacy|fa287121d830a9ea4518babd09993764d32439fb|lib-app-bitcoin|make COIN=dogecoin|build/nanos2/bin/app.elf
dash|app-bitcoin-legacy|fa287121d830a9ea4518babd09993764d32439fb|lib-app-bitcoin|make COIN=dash|build/nanos2/bin/app.elf
bitcoin_cash|app-bitcoin-legacy|fa287121d830a9ea4518babd09993764d32439fb|lib-app-bitcoin|make COIN=bitcoin_cash|build/nanos2/bin/app.elf
ethereum|app-ethereum|f7985b427841ace20795b548905ababf6a5b17a4|ethereum-plugin-sdk|make|build/nanos2/bin/app.elf
solana|app-solana|22d6c9b78d6721acdc6d0ce49788a2fd9ea409a0||make|build/nanos2/bin/app.elf
monero|app-monero|f93f9d8180d22f6052081f666e9eec91e2c63dfa||make|build/nanos2/bin/app.elf
ton|app-ton|849962d6378567f5aaf708b116292ccf7b17a97c||make|build/nanos2/bin/app.elf
thorchain|app-thorchain|b5408e0539dbafeee76adf32ccd0f7d8621ea3d9|deps/ledger-zxlib|make|app/build/nanos2/bin/app.elf
stellar|app-stellar|6bfd82a72e3ab8f9d92f59211eca73e9c58416b2||cargo|target/nanosplus/release/stellar
zcash|app-zcash|a4c4b4f7b9e59d43b821cec3570243a355765d11||cargo|target/nanosplus/release/zcash
"

root=$(cd "$(dirname "$0")/.." && pwd)
out=$root/apps
if [ "${1:-}" = "--out" ]; then out=$2; shift 2; fi
work=$root/build/speculos-apps
mkdir -p "$out" "$work"

checkout() { # repo commit submodules
  local dir=$work/$1
  if [ "$(git -C "$dir" rev-parse HEAD 2>/dev/null)" != "$2" ]; then
    rm -rf "$dir"
    git init -q "$dir"
    git -C "$dir" fetch -q --depth 1 "https://github.com/LedgerHQ/$1.git" "$2"
    git -C "$dir" checkout -q FETCH_HEAD
  fi
  # lib-app-bitcoin is declared with an SSH URL.
  [ -z "$3" ] || git -C "$dir" -c url.https://github.com/.insteadOf=git@github.com: \
    submodule update -q --init --depth 1 -- "$3"
}

build() { # name repo commit submodules command elf
  echo "== $1 ($2 ${3:0:12})"
  checkout "$2" "$3" "$4"
  if [ "$5" = cargo ]; then
    docker run --rm -v "$work/$2:/app" -w /app "$FULL_IMAGE" cargo ledger build nanosplus
  else
    docker run --rm -v "$work/$2:/app" -w /app "$LITE_IMAGE" \
      bash -c "make clean >/dev/null; make BOLOS_SDK=\$NANOSP_SDK ${5#make}"
  fi
  cp "$work/$2/$6" "$out/$1.elf"
}

found=0
while IFS='|' read -r name repo commit submodules command elf; do
  [ -n "$name" ] || continue
  if [ $# -gt 0 ] && [[ " $* " != *" $name "* ]]; then continue; fi
  build "$name" "$repo" "$commit" "$submodules" "$command" "$elf"
  found=$((found + 1))
done <<< "$APPS"
[ "$found" -gt 0 ] || { echo "No such app: $*" >&2; exit 1; }
