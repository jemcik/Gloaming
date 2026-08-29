#!/bin/bash
# Build the debug APK. Log goes to /tmp/build.log
set -e
export PATH=/opt/homebrew/bin:$PATH
cd "$(dirname "$0")/.."
./gradlew :app:assembleDebug > /tmp/build.log 2>&1
echo "EXIT=$?" >> /tmp/build.log
grep -E 'BUILD|^e: ' /tmp/build.log | head -20
