#!/usr/bin/env bash
# Checks the native libraries in an AAR: all ABIs are present and every LOAD segment of the
# 64-bit ones is aligned to at least 16 KB, required for devices with 16 KB memory pages.
# Usage: check-native-libraries.sh <aar>
set -euo pipefail

aar=$1
expected_abis="arm64-v8a armeabi-v7a x86 x86_64"
# Devices with 16 KB pages are 64-bit only
abis_requiring_16kb="arm64-v8a x86_64"
min_alignment=0x4000

ndk=$(sed -nE 's/^ndk[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' gradle/libs.versions.toml)
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
readelf=$(ls "$sdk"/ndk/"$ndk"/toolchains/llvm/prebuilt/*/bin/llvm-readelf 2>/dev/null | head -n 1)
if [ -z "$readelf" ]; then
  echo "::error::llvm-readelf of NDK $ndk not found in $sdk"
  exit 1
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
unzip -q "$aar" 'jni/*' -d "$work"

failed=0
abis=$(cd "$work/jni" && ls | sort | tr '\n' ' ' | sed 's/ $//')
if [ "$abis" != "$expected_abis" ]; then
  echo "::error::Expected ABIs: $expected_abis, found: $abis"
  failed=1
fi

for library in "$work"/jni/*/*.so; do
  name=${library#"$work"/jni/}
  abi=${name%%/*}
  alignments=$("$readelf" -lW "$library" | awk '$1 == "LOAD" { print $NF }')
  for alignment in $alignments; do
    if [[ " $abis_requiring_16kb " == *" $abi "* ]] && (( alignment < min_alignment )); then
      echo "::error::$name has a LOAD segment aligned to $alignment, at least 0x4000 required"
      failed=1
    fi
  done
  echo "$name: LOAD segments aligned to $(echo "$alignments" | sort -u | tr '\n' ' ')"
done

exit $failed
