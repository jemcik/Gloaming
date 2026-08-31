#!/bin/bash
# Install the debug APK on the attached device.
#
# INSTALLS ONLY - it does not build, deliberately. What it does now is REFUSE
# when the APK is older than the sources, because the silent version of that
# was a recurring trap: `gradlew test`, `gradlew lint` and `gradlew check` all
# compile without assembling, so a run that looks like a successful build
# leaves a stale APK on disk. Deploying it puts the OLD strings and colours on
# the phone, and the screenshot proving your change then shows the previous
# build - which reads as "the change did not work" and has cost real time more
# than once.
#
#   -f, --force   install anyway, without the freshness check
set -e
cd "$(dirname "$0")/.."
APK=app/build/outputs/apk/debug/app-debug.apk
# `case`, not a || && chain: under `set -e` that chain's exit status is the
# whole list's, so a plain `deploy.sh` with no argument can abort the script.
FORCE=""
case "$1" in -f|--force) FORCE=1 ;; esac

if [ ! -f "$APK" ]; then
    echo "no APK at $APK - run ./tools/build.sh first" >&2
    exit 1
fi

if [ -z "$FORCE" ]; then
    # Anything that ends up inside the APK. -newer is a plain mtime compare, so
    # this is cheap and needs no build-system cooperation.
    NEWER=$(find app/src build.gradle.kts app/build.gradle.kts settings.gradle.kts \
                 gradle/libs.versions.toml -newer "$APK" 2>/dev/null | head -5)
    if [ -n "$NEWER" ]; then
        echo "REFUSING: the APK is older than the source." >&2
        echo "" >&2
        echo "$NEWER" | sed 's/^/    /' >&2
        echo "" >&2
        echo "  ./tools/build.sh    then deploy again" >&2
        echo "  ./tools/deploy.sh -f  to install the stale one anyway" >&2
        exit 1
    fi
fi

/opt/homebrew/bin/adb install -r "$APK" | tail -1
