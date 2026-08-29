# Gloaming

An Android bedtime app: one sleep window a night, with Do Not Disturb and the
screen effects that go with it — driven by exact alarms, so it fires with the
app closed.

## Why it exists

Android's own bedtime mode is scheduled as a **WorkManager job**, and jobs are
advisory. The system decides when they run, and every vendor's battery
management gets a say on top of that. When something in that chain defers a job
indefinitely, bedtime silently never happens — there is no error, no
notification, nothing to notice except that the screen stayed colourful.

Exact alarms are the other path. They are a separate, user-granted permission
with a scheduling guarantee, and they do not go through JobScheduler at all.
Gloaming owns its trigger that way: it holds an `AutomaticZenRule` for the
policy and flips that rule from its own alarms.

**The case that prompted it.** On an Honor Magic 8 Pro (BKQ-N49, MagicOS
10.0.0.199, Android 16), Digital Wellbeing's bedtime job carries an undocumented
constraint that is never satisfied:

    Required:    HN_USER_EXPERIENCE [0x200400]
    Unsatisfied: HN_USER_EXPERIENCE [0x400]

The job never runs, so bedtime only ever happens while Wellbeing is in the
foreground, and the constraint cannot be satisfied or removed even over adb.
`setExactAndAllowWhileIdle` is not gated by it: measured firing 149 ms after the
scheduled instant with the app swiped from recents and the screen off, on the
ordinary user path — no battery whitelist, standby bucket 10.

That is one vendor on one build, and it is where the app came from rather than
what it is for. Background work being quietly dropped is a common shape across
Android OEMs; the alarm-driven approach here does not depend on which vendor is
doing it, or on any vendor doing it at all.

## What it does

- **A 24-hour dial.** One window, dragged at either end. It is a full day rather
  than a clock face because a 12-hour face cannot draw a window longer than
  twelve hours; Apple's Sleep ring makes the same choice.
- **Do Not Disturb**, with a per-night allowlist — who can call, who can
  message, conversations, repeat callers, reminders, calendar events, media.
  Alarms are always allowed and the app says so rather than pretending the
  platform forbids silencing them.
- **Screen effects**, as far as your phone honours them: greyscale, wallpaper
  dimming, night mode, ambient display. All four are standard
  `ZenDeviceEffects`, and a device will happily accept an effect it has no
  intention of applying — so which ones actually take effect varies. The app
  measures rather than assumes, and where an effect does nothing it says so
  instead of offering a switch that lies.
- **English, Russian and Ukrainian**, with a per-app language picker.
- **A journal.** The app keeps its own on-device log of every decision it makes
  overnight. Useful anywhere, and necessary on devices that encrypt logcat for
  third-party apps.

## Devices

Nothing vendor-specific runs unconditionally, and no device is special-cased
into working:

- Capability is **measured at runtime, not looked up in a device list**. Whether
  night mode is honoured is settled by a probe on first launch, keyed to
  `Build.FINGERPRINT` so an OS update reopens the question. On a phone that
  applies it, the switch is simply live.
- The single `Build.MANUFACTURER` test in the codebase returns a settings
  deep-link for Honor and Huawei and null everywhere else; the caller draws
  nothing when it is null.
- The one device list is an **exclusion** list, consulted only when the AOSP
  key it would otherwise read is absent — so an unrecognised phone is treated as
  capable rather than as broken.
- There are no writes to `Settings.System`, `Secure` or `Global` anywhere. The
  two reads are capability detection.

Worth knowing on any device: the night-mode probe creates and activates a
throwaway zen rule for about 2.5 seconds on first launch, so a "Do Not Disturb
is on" notification flashes once.

Run so far on the Honor above. The design is device-independent by construction
and audited to be so, but that is an audit, not a second phone — reports from
other hardware are welcome.

## Requirements

Android 15 (API 35) or newer — `ZenDeviceEffects` and `AutomaticZenRule.Builder`
are API 35, and the app is built entirely around them.

Two permissions, both user-granted: notification policy access (Do Not Disturb)
and exact alarms.

## Build

    ./tools/build.sh      debug APK, full log at /tmp/build.log
    ./tools/deploy.sh     install on the attached device
    ./tools/check.sh      what the phone thinks: zen state, the rule, the app's
                          own view, the next alarms and the journal. Read-only.

## Tests

    ./gradlew test        46 tests, JVM only, about a second

They cover the scheduling core (pure functions of times, days and an injected
`now`), the sentence assembly in all three languages, the screen interactions,
and the one-shot preferences migration — the only code here that could corrupt
data without saying anything.

What they cannot cover is everything that depends on the device: whether an
exact alarm fires with the screen off, whether greyscale is really applied,
whether `updateAutomaticZenRule` clears a rule's condition. That is what the
journal and `tools/check.sh` are for.

A pre-push hook runs the tests, lint and the translation checkers:

    git config core.hooksPath tools/hooks

## Translation

    python3 tools/translate.py ru          # or uk
    python3 tools/check_translation.py app/src/main/res/values-ru/strings.xml ru

Gemini Pro translates, two models side by side; the checker verifies what a
human will not spot by eye — dropped format placeholders, missing plural forms
(Russian and Ukrainian need four where English needs two), keys that vanished,
strings left in English, and rows that grew past the width they have to fit.

Needs `GEMINI_API_KEY` in `.env.local`, which is git-ignored.

## Licence

Apache License 2.0 — see [LICENSE](LICENSE).

The repository also redistributes two SIL Open Font License fonts and a set of
Material Design icon paths; [NOTICE.md](NOTICE.md) lists them with their
licences.

## CLAUDE.md

The real documentation. It carries what the code cannot: the behaviour measured
on hardware, the design decisions and what was rejected, and the mistakes worth
not repeating — among them that `updateAutomaticZenRule` silently clears a
rule's condition, so rewriting a live rule switches Do Not Disturb off
underneath you. Much of it is written against one phone, because that is the
phone it could be measured on.
