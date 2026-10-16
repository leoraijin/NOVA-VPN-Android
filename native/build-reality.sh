#!/usr/bin/env bash
set -euo pipefail
# Build on Linux with Android NDK, Rust and cargo-ndk. No signing keys needed.
root="$(cd "$(dirname "$0")/.." && pwd)"
revision=51a811f1eb3dc3f158b6f7d1a38e3da1d3cf62bc
source_dir="$root/native/work/core"
mkdir -p "$root/native/work"
if [[ ! -d "$source_dir/.git" ]]; then
  git clone https://github.com/ERGFT/vpn-core.git "$source_dir"
fi
git -C "$source_dir" checkout --detach "$revision"
if ! git -C "$source_dir" apply --reverse --check "$root/native/reality-validation.patch" 2>/dev/null; then
  git -C "$source_dir" apply "$root/native/reality-validation.patch"
fi
if [[ -f "$root/native/reality-android-fixes.patch" ]] && ! git -C "$source_dir" apply --reverse --check "$root/native/reality-android-fixes.patch" 2>/dev/null; then
  git -C "$source_dir" apply "$root/native/reality-android-fixes.patch"
fi
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME}"
abis=("$@")
if [[ ${#abis[@]} == 0 ]]; then abis=(arm64-v8a armeabi-v7a x86_64); fi
for abi in "${abis[@]}"; do
  case "$abi" in
    arm64-v8a) target=aarch64-linux-android; compiler=aarch64-linux-android24-clang ;;
    armeabi-v7a) target=armv7-linux-androideabi; compiler=armv7a-linux-androideabi24-clang ;;
    x86_64) target=x86_64-linux-android; compiler=x86_64-linux-android24-clang ;;
  esac
  rustup target add "$target"
  (cd "$source_dir" && cargo ndk -t "$abi" -p 24 build --locked --release -p reality-ffi)
  dest="$root/app/src/main/jniLibs/$abi"
  mkdir -p "$dest"
  cp "$source_dir/target/$target/release/libreality.so" "$dest/"
  "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/$compiler" \
    -shared -fPIC -O2 -Wall -Wextra -Werror -Wl,-z,max-page-size=16384 \
    -I"$source_dir/ffi/include" "$root/native/reality-jni.c" \
    -L"$dest" -lreality -Wl,--no-undefined -o "$dest/libnova_reality_jni.so"
done
# Exact corresponding source; NOVA patches are shipped in the app source.
git -C "$source_dir" archive "$revision" -o "$root/native/work/reality-core-source.tar"
cp "$source_dir/LICENSE" "$root/native/REALITY-LICENSE"
find "$root/app/src/main/jniLibs" -name '*.so' -print0 | sort -z | xargs -0 sha256sum
