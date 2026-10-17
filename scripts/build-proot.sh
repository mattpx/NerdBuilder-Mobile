#!/bin/bash
# Build PRoot for arm64 via NDK. Needs Android NDK r26+.
# See Mobile-Harness third_party/proot for source.
set -e
echo "1. Clone proot source to third_party/proot"
echo "2. ndk-build APP_ABI=arm64-v8a"
echo "3. Copy libs -> app/src/main/jniLibs/arm64-v8a/libproot.so"
