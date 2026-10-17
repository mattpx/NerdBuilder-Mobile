# NerdProject

Autonomous AI development workspace for Android. Chat with coding agents, run real Linux, build APKs. On phone. No root.

Fork-inspired by Mobile-Harness (MIT) + OpenClaude agent core.

## Architecture

- UI: Kotlin + Jetpack Compose
- Runtime: Ubuntu ARM64 in PRoot, C++ JNI spawn bridge
- Agents: OpenClaude core (open source), provider-agnostic
- Keys: Android Keystore AES-256-GCM
- Builds: on-device Gradle, no ADB

## Modules

- `app/src/main/java/com/nerdproject/runtime/` — PRoot bridge, installer, agent drivers
- `app/src/main/java/com/nerdproject/ui/` — Compose screens
- `app/src/main/java/com/nerdproject/data/` — Keystore vault, prefs
- `app/src/main/java/com/nerdproject/model/` — data models
- `app/src/main/cpp/` — pocket_spawn.c port (JNI process spawn)

## Build order

1. JNI spawn bridge
2. Runtime installer (rootfs download + verify)
3. Agent runner (OpenClaude stream parse)
4. Checkpoints (snapshot + diff)
5. UI screens
6. On-device APK build

## License

MIT
