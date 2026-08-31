#!/bin/bash
# Build the debug APK. Log goes to /tmp/build.log
export PATH=/opt/homebrew/bin:$PATH
cd "$(dirname "$0")/.."
APK=app/build/outputs/apk/debug/app-debug.apk

# Deliberately NOT `set -e` around gradle. With it, a failed build exits here
# and the grep below never runs, so the script prints NOTHING on the one
# occasion its output matters most - which cost real time before it was noticed.
./gradlew :app:assembleDebug > /tmp/build.log 2>&1
STATUS=$?
echo "EXIT=$STATUS" >> /tmp/build.log

# Stamp the APK with the time of this successful build. Gradle decides
# UP-TO-DATE by content hash, so a file whose mtime moved without its content
# changing leaves the APK untouched - and deploy.sh's freshness check would then
# refuse forever, with build.sh unable to clear it. Touching here makes the
# APK's mtime mean "the last time a build confirmed these sources", which is the
# question deploy.sh is actually asking.
if [ $STATUS -eq 0 ] && [ -f "$APK" ]; then touch "$APK"; fi

grep -E 'BUILD|^e: ' /tmp/build.log | head -20
exit $STATUS
