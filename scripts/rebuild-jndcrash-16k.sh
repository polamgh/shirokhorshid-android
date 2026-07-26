#!/usr/bin/env bash
# Rebuild jndcrash native libraries with 16 KB ELF alignment and repack app/libs/jndcrash-release.aar.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_AAR="$ROOT/app/libs/jndcrash-release.aar"
WORK="$ROOT/build/third_party/jndcrash"
NDK_VERSION="${NDK_VERSION:-25.2.9519653}"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"

mkdir -p "$(dirname "$OUT_AAR")" "$WORK"
cd "$WORK"

if [[ ! -d .git ]]; then
  # Psiphon fork includes NDCrash.nativeInitializeStdErrRedirect (required by TunnelManager).
  git clone --depth 1 https://github.com/Psiphon-Inc/jndcrash.git .
  git submodule update --init --recursive
fi

# Patch CMake for 16 KB linker flags (idempotent).
if ! grep -q 'ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES' CMakeLists.txt; then
  cat >> CMakeLists.txt <<'EOF'

if (ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES)
    target_link_options(jndcrash PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")
endif()
EOF
fi

cat > build.gradle <<GRADLE
buildscript {
    repositories { google(); mavenCentral() }
    dependencies { classpath 'com.android.tools.build:gradle:8.5.2' }
}
repositories { google(); mavenCentral() }
apply plugin: 'com.android.library'
android {
    namespace 'ru.ivanarh.jndcrash'
    compileSdk 35
    ndkVersion '$NDK_VERSION'
    defaultConfig {
        minSdk 24
        externalNativeBuild {
            cmake {
                arguments "-DANDROID_STL=c++_static", "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
            }
        }
        ndk { abiFilters 'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64' }
    }
    externalNativeBuild { cmake { path "CMakeLists.txt" } }
}
GRADLE

echo 'android.useAndroidX=true' > gradle.properties
printf 'rootProject.name = "jndcrash"\n' > settings.gradle
cp "$ROOT/gradlew" ./gradlew 2>/dev/null || true
chmod +x ./gradlew 2>/dev/null || true

if [[ -x ./gradlew ]]; then
  ./gradlew mergeReleaseNativeLibs
else
  "$ROOT/gradlew" -p "$WORK" mergeReleaseNativeLibs
fi

NATIVE_DIR="$WORK/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib"
if [[ ! -d "$NATIVE_DIR/arm64-v8a" ]]; then
  echo "Native build output not found at $NATIVE_DIR" >&2
  exit 1
fi

REPACK=$(mktemp -d)
unzip -q "$OUT_AAR" -d "$REPACK"
for abi in arm64-v8a armeabi-v7a x86 x86_64; do
  cp "$NATIVE_DIR/$abi/libjndcrash.so" "$REPACK/jni/$abi/libjndcrash.so"
done
( cd "$REPACK" && zip -qr "$OUT_AAR" . )
echo "Updated $OUT_AAR with 16 KB-aligned libjndcrash.so"
