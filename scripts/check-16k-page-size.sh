#!/usr/bin/env bash
# Verify arm64-v8a native libraries are 16 KB ELF- and ZIP-aligned per Android guidance.
set -euo pipefail

APK="${1:-}"
if [[ -z "$APK" || ! -f "$APK" ]]; then
  echo "Usage: $0 path/to/app.apk" >&2
  exit 1
fi

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
NDK_DIR="${ANDROID_NDK_HOME:-$(ls -d "$SDK_ROOT"/ndk/* 2>/dev/null | sort -V | tail -1)}"
BUILD_TOOLS="$(ls -d "$SDK_ROOT"/build-tools/* 2>/dev/null | sort -V | tail -1)"
OBJDUMP="$NDK_DIR/toolchains/llvm/prebuilt/$(uname | tr '[:upper:]' '[:lower:]')-x86_64/bin/llvm-objdump"
ZIPALIGN="$BUILD_TOOLS/zipalign"

if [[ ! -x "$OBJDUMP" ]]; then
  echo "llvm-objdump not found under $NDK_DIR" >&2
  exit 1
fi

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
unzip -q "$APK" "lib/arm64-v8a/*.so" -d "$TMP"

echo "=== ELF LOAD segment alignment (expect align 2**14) ==="
FAILED=0
while IFS= read -r -d '' so; do
  name=$(basename "$so")
  if ! "$OBJDUMP" -p "$so" | awk '/LOAD/ { if ($0 !~ /align 2\*\*1[4-9]/) bad=1 } END { exit bad ? 1 : 0 }'; then
    echo "UNALIGNED: $name"
    "$OBJDUMP" -p "$so" | awk '/LOAD/'
    FAILED=1
  else
    echo "ALIGNED:   $name"
  fi
done < <(find "$TMP/lib/arm64-v8a" -name '*.so' -print0)

if [[ -x "$ZIPALIGN" ]]; then
  echo
  echo "=== APK ZIP alignment (zipalign -P 16) ==="
  if "$ZIPALIGN" -c -P 16 -v 4 "$APK"; then
    echo "ZIPALIGN: OK"
  else
    echo "ZIPALIGN: FAILED"
    FAILED=1
  fi
else
  echo "zipalign not found; skipping ZIP check" >&2
fi

exit "$FAILED"
