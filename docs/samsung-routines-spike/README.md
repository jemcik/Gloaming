# Samsung Modes and Routines — the reverse-engineered contract

Reference only, and SUPERSEDED on 6 Sep 2026: this is the SDK-provider attempt,
in which Gloaming offered conditions to Modes and Routines and was never
called. What is built into the app is the other door - Routines' external
content provider and its routine-file importer - documented in
`docs/DECISIONS.md` ("Samsung round two" onwards) and implemented in
`core/Routines.kt` and `core/RoutineFile.kt`. This directory is kept for the
SDK contract, which is written nowhere public, in any language, and for the
record of what was tried first.

Established by decompiling `/system/priv-app/Routines/Routines.apk` (One UI 8,
Galaxy S23) with jadx, and by reading Samsung Clock's manifest, which is a
working example of the same contract.

## What is NOT true

Discovery is **not** restricted to system apps. That was the first conclusion
here and it was wrong:

- `MetaParserV3` (`cf.a`) calls `getInstalledApplications(GET_META_DATA)`
- `MetaParserV2` (`bf.a`) calls
  `queryIntentContentProviders(intent, GET_META_DATA | GET_SHARED_LIBRARY_FILES)`

Neither carries `MATCH_SYSTEM_ONLY`. The only permission check is on the
*declaring app's own* `android:permission` attribute, which an ordinary app does
not set. `READ_ROUTINE_INFO` is `protectionLevel: normal` and IS granted at
install with no prompt.

## The contract

Two shapes are collected, both wired up in `z8.d`:

**V3** — meta-data at **`<application>`** level (NOT on the provider; putting it
on the provider is why the first attempt was never seen):

    <meta-data android:name="com.samsung.android.sdk.routines.v3.meta.CONDITION"
               android:resource="@xml/routines_conditions" />

**V2** — a provider with an intent-filter for
`com.samsung.android.SDK.routine.ROUTINE_CONDITION_UPDATE` (note `SDK.routine`,
singular and capitalised) and the meta-data under
`com.samsung.android.SDK.routine.meta.CONDITION` on the provider itself.

The XML start tag is `condition`, from `RawConditionMeta.TABLE_NAME`. This
parser rejects `<configuration>`, `<parameters>` and `<parameter>` as
unrecognized children — Samsung Clock declares them and they are read by a
different parser instance — so the element is attributes only.

## The trap that invalidated three attempts

`MetadataSyncHistory.isMetaDataSyncedWithVersion` compares a hash and silently
skips collection when it matches. Reinstalling the same `versionCode` changes
nothing, **and neither does a reboot**. Bump `versionCode` to force a
re-collection; you will see `MetadataLoadManager: asyncCollectPackage` and then
`XmlParser[condition]` in logcat. Samsung's own logs are readable — filter on
`Routine@Core`, `MetaParser`, `XmlParser`, `MetadataLoader`.

## Where it stopped

With both shapes declared and the XML parsing without errors, the condition
still does not appear in the routine builder's picker. The next thing to check
is the attribute set: real `ConditionMeta` rows carry `attributes`,
`supportState`, `version` and `componentType` fields this XML does not supply.

## Why it would not have helped anyway

Even a working condition only lets the USER build a routine. Every path that
would let Gloaming *activate* anything is `signature|privileged`:
`ROUTINE_HOST` (to send `MANUAL_ROUTINE_EXECUTION`), `WRITE_ROUTINE_INFO`,
`WRITE_MODE_INFO`, `READ_MODE_INFO`. `LAUNCH_MODE` only opens the mode editor.
So the trigger would always have had to live on Samsung's side.
