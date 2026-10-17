# NerdBuilder Mobile

Autonomous AI development workspace for Android. Chat with coding agents, run real Linux, build APKs. On phone. No root.

Inspired by Mobile-Harness (MIT) + OpenClaude agent core.

## Status: source-complete, needs device build

All Kotlin/C source is here. Cannot compile APK on plain Linux server (no Android SDK). Open in Android Studio to build.

## Quick start (Android Studio)

1. Open this folder in Android Studio (Hedgehog+).
2. Let Gradle sync. SDK 34, min 28, JDK 17.
3. Build PRoot: see `scripts/build-proot.sh`. Place `libproot.so` in `app/src/main/jniLibs/arm64-v8a/`.
4. Build rootfs: run `scripts/build-rootfs.sh` on ARM64 or qemu machine. Host tar.zst, put URL+SHA256 in `RuntimeInstaller.kt` (`CORE_URL`, `CORE_SHA256`).
5. Run on ARM64 device (Android 9+). Onboarding downloads rootfs, guest setup installs OpenClaude via `scripts/guest-setup.sh`.
6. Add API key in Settings, create project, chat.

## Architecture

- UI: Kotlin + Jetpack Compose (Material3)
- Runtime: Ubuntu 20.04 ARM64 in PRoot, C++ JNI spawn bridge (`nerd_spawn.c`)
- Agent: OpenClaude CLI in guest, stream-json parsed by `OpenClaudeBridge.kt`
- Keys: Android Keystore AES-256-GCM (`ApiKeyVault.kt`)
- Checkpoints: snapshot + LCS diff, per-file Undo/Keep (`WorkspaceCheckpoints.kt`)
- Android builds: Gradle in PRoot, install via system installer, no ADB (`AndroidBuilder.kt`)

## Project layout

- `app/src/main/java/com/nerdbuilder/mobile/runtime/` — spawn, installer, bridges, builder
- `app/src/main/java/com/nerdbuilder/mobile/ui/` — Compose screens
- `app/src/main/java/com/nerdbuilder/mobile/data/` — vault
- `app/src/main/java/com/nerdbuilder/mobile/model/` — models
- `app/src/main/cpp/` — JNI spawn + launcher
- `scripts/` — rootfs, proot, guest setup

## Scripts

- `scripts/build-rootfs.sh` — debootstrap Ubuntu ARM64 + node/git, pack zstd
- `scripts/build-proot.sh` — NDK cross-compile PRoot notes
- `scripts/guest-setup.sh` — inside guest: npm openclaude, Android SDK opt-in

## License

MIT
