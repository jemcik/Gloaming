# tools

Small helpers for working on Gloaming. Both use paths relative to their own
location, so they work wherever the project sits.

    ./tools/build.sh     build the debug APK, print the result
    ./tools/deploy.sh    install it on the attached device

Full build output goes to `/tmp/build.log`.

## Reading the app's journal

The app keeps its own log because MagicOS encrypts logcat for third-party
apps — entries come back as `(HKS)...(HKE)` blobs over adb, so there is no
other way to see what happened while the app was closed.

    adb shell run-as com.jemcik.gloaming cat files/journal.log

It records state changes rather than every reschedule: `START fired`,
`END fired`, and one line per genuine change of plan.

## Inspecting the zen rule

    adb shell dumpsys notification | grep -o 'deviceEffects=\[[^]]*\]'

Effects this device accepts and ignores are documented in
`core/SystemTheme.kt`, `core/AmbientCapability.kt` and `core/EyeComfort.kt`.
