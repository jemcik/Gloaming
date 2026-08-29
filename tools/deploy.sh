#!/bin/bash
# Install the debug APK on the attached device.
set -e
cd "$(dirname "$0")/.."
/opt/homebrew/bin/adb install -r app/build/outputs/apk/debug/app-debug.apk | tail -1
