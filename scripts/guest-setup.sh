#!/bin/bash
# Run INSIDE proot Ubuntu. Installs OpenClaude + Android SDK.
set -e
npm install -g @gitlawb/openclaude
openclaude --version
# Android SDK (opt-in, large)
# mkdir -p /root/android-sdk/cmdline-tools
# cd /root/android-sdk/cmdline-tools
# curl -o tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
# unzip tools.zip && mv cmdline-tools latest
# yes | /root/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses
# /root/android-sdk/cmdline-tools/latest/bin/sdkmanager "build-tools;35.0.0" "platforms;android-34"
