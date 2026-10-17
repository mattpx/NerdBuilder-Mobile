#!/bin/bash
# Build Ubuntu 20.04 ARM64 rootfs. Needs: debootstrap, qemu-user-static, zstd.
# Run on x86_64 with qemu OR native ARM64.
set -e
OUT=${1:-ubuntu-core.tar.zst}
WORK=$(mktemp -d)
sudo debootstrap --arch=arm64 --foreign focal "$WORK/rootfs" http://ports.ubuntu.com/ubuntu-ports
sudo cp /usr/bin/qemu-aarch64-static "$WORK/rootfs/usr/bin/"
sudo chroot "$WORK/rootfs" /debootstrap/debootstrap --second-stage
sudo chroot "$WORK/rootfs" apt-get update
sudo chroot "$WORK/rootfs" apt-get install -y nodejs npm git ca-certificates curl coreutils bash
sudo rm -f "$WORK/rootfs/usr/bin/qemu-aarch64-static"
sudo tar --numeric-owner -C "$WORK/rootfs" -cf - . | zstd -19 -o "$OUT"
sha256sum "$OUT"
echo "Upload $OUT, put URL+SHA in RuntimeInstaller.kt"
