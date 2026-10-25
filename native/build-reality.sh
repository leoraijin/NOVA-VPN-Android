#!/usr/bin/env bash
set -euo pipefail
# Build on Linux with Android NDK, Rust and cargo-ndk. No signing keys needed.
root="$(cd "$(dirname "$0")/.." && pwd)"
revision=994ce4ef5792c23a0298db755db9561eea6726e2
# Exact integration of upstream PRs #34 and #35; neither is merged upstream yet.
source_ref=refs/heads/integration/android-uid-routing
source_dir="$root/native/work/core"
mkdir -p "$root/native/work"
if [[ ! -d "$source_dir/.git" ]]; then
  git clone https://github.com/leoraijin/vpn-core.git "$source_dir"
fi
[[ -z "$(git -C "$source_dir" status --porcelain)" ]] || {
  echo 'Edited or previously patched core sources exist. Preserve them and use a fresh build directory.' >&2; exit 1;
}
git -C "$source_dir" fetch https://github.com/leoraijin/vpn-core.git "$source_ref"
git -C "$source_dir" cat-file -e "$revision^{commit}"
git -C "$source_dir" checkout --detach "$revision"
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME}"
export RUSTFLAGS="${RUSTFLAGS:-} -C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384"
abis=("$@")
if [[ ${#abis[@]} == 0 ]]; then abis=(arm64-v8a armeabi-v7a x86_64); fi
for abi in "${abis[@]}"; do
  case "$abi" in
    arm64-v8a) target=aarch64-linux-android; compiler=aarch64-linux-android24-clang ;;
    armeabi-v7a) target=armv7-linux-androideabi; compiler=armv7a-linux-androideabi24-clang ;;
    x86_64) target=x86_64-linux-android; compiler=x86_64-linux-android24-clang ;;
    *) echo "Unsupported ABI: $abi" >&2; exit 1 ;;
  esac
  rustup target add "$target"
  (cd "$source_dir" && cargo ndk -t "$abi" -p 24 build --locked --release -p reality-ffi)
  dest="$root/app/src/main/jniLibs/$abi"
  mkdir -p "$dest"
  cp "$source_dir/target/$target/release/libreality.so" "$dest/"
  "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/$compiler" \
    -shared -fPIC -O2 -Wall -Wextra -Werror -Wl,-z,max-page-size=16384,-z,common-page-size=16384 \
    -I"$source_dir/ffi/include" "$root/native/reality-jni.c" \
    -L"$dest" -lreality -Wl,--no-undefined -o "$dest/libnova_reality_jni.so"
done
# Exact reviewed source: no NOVA patches are applied to the core.
git -C "$source_dir" archive "$revision" -o "$root/native/work/reality-core-source.tar"
cp "$source_dir/LICENSE" "$root/native/REALITY-LICENSE"
find "$root/app/src/main/jniLibs" -name '*.so' -print0 | sort -z | xargs -0 sha256sum
