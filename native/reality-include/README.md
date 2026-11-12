# Pinned Reality FFI header

`reality.h` is the unmodified header from `leoraijin/vpn-core`, commit `16fe5dd518652965fe2c469dc943054fb86f4b9c`, path `ffi/include/reality.h`.

Source: https://github.com/leoraijin/vpn-core/blob/16fe5dd518652965fe2c469dc943054fb86f4b9c/ffi/include/reality.h

SHA-256: `e31987931037a5c757050d2164c0db934a2eb8a2615c29b4bb19bca3ccfeafb1`.

The Windows `Build-RealityJni.ps1` helper rebuilds only the NOVA C bridge against existing matching Rust libraries. It writes reviewable outputs to the workspace `work/reality-jni-16k/`; it does not replace application libraries automatically. The Linux build script remains the complete pinned Rust-core build path. Preserve the upstream source license when distributing the native source bundle.
