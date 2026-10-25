# Pinned Reality FFI header

`reality.h` is the unmodified header from `leoraijin/vpn-core`, commit `994ce4ef5792c23a0298db755db9561eea6726e2`, path `ffi/include/reality.h`.

Source: https://github.com/leoraijin/vpn-core/blob/994ce4ef5792c23a0298db755db9561eea6726e2/ffi/include/reality.h

SHA-256: `efdee71beccba21aa6a39d65435afbebfc83d97f5cd329dbaa6a71bc3f7969c4`.

The Windows `Build-RealityJni.ps1` helper rebuilds only the NOVA C bridge against existing matching Rust libraries. It writes reviewable outputs to the workspace `work/reality-jni-16k/`; it does not replace application libraries automatically. The Linux build script remains the complete pinned Rust-core build path. Preserve the upstream source license when distributing the native source bundle.
