<div align="center">

<img src="docs/icon.png" width="112" alt="Gloaming">

# Gloaming

**A bedtime app for Android that keeps its own schedule.**

One sleep window a night — Do Not Disturb and the screen effects that go with
it — driven by exact alarms, so it fires with the app closed.

[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)
[![Android](https://img.shields.io/badge/Android-15%2B%20(API%2035)-3DDC84)](#requirements)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4)](https://developer.android.com/jetpack/compose)
[![Tests](https://img.shields.io/badge/tests-163-success)](#tests)

<img src="docs/screenshots/home.png" width="19%" alt="Home, mid-window">
<img src="docs/screenshots/home-dark.png" width="19%" alt="Home, dark, mid-window">
<img src="docs/screenshots/effects.png" width="19%" alt="How the screen looks">
<img src="docs/screenshots/allowed.png" width="19%" alt="What is allowed">
<img src="docs/screenshots/settings.png" width="19%" alt="Settings">

</div>

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
ordinary user path — no battery whitelist, standby bucket 10. **Screen off is not
doze**, so that measurement never covered the state the app spends the night in;
the overnight path was measured separately by forcing light and deep idle, and
fired at the scheduled second in both.

That is one vendor on one build, and it is where the app came from rather than
what it is for. Background work being quietly dropped is a common shape across
Android OEMs; the alarm-driven approach here does not depend on which vendor is
doing it, or on any vendor doing it at all.

## What it does

- **A 24-hour dial.** One window, dragged at either end. It is a full day rather
  than a clock face because a 12-hour face cannot draw a window longer than
  twelve hours; Apple's Sleep ring makes the same choice. Days are chosen as the
  **mornings** you want the window to end on, so asking for the weekend means
  Saturday and Sunday, not Friday night.
- **End bedtime at your alarm.** AOSP's own schedules carry this rule as
  `exitAtAlarm` and Android Settings calls it "Alarm can override end time"; both
  apply it only when the alarm falls *inside* the window, and so does this — a
  2 pm alarm is a real alarm and would not be a wake-up. The switch and the wake
  handle are **one setting seen twice**: turning it on moves your wake time to
  the alarm, and dragging the wake time onto the alarm turns it on. Two controls
  for one value is how a screen ends up showing a wake time of 8:30 and a
  countdown to 7:30 at the same moment. Where no alarm is set at all the section
  is not drawn, because there is nothing for the night to end at.
- **A Quick Settings tile**, wearing the same faces as the switch in the app: an
  hourglass while a window is only scheduled, a tick once it is actually in
  effect, and the app's own mark when bedtime is off. Armed is not running, and a
  tile that collapsed the two would be lying for most of the evening — a tick
  sitting there all evening while nothing is happening is the one thing a tick
  must not mean. Long-press opens the app.
- **Do Not Disturb**, with a per-night allowlist — who can call, who can
  message, conversations, repeat callers, reminders, calendar events, media. The
  screen reports what the system says is actually in effect, rather than what the
  app believes it asked for. Alarms are always allowed, and the app says that is
  its own choice rather than pretending the platform forbids silencing them.
- **Screen effects**, as far as your phone honours them: greyscale, wallpaper
  dimming, dark theme, always-on display. All four are standard
  `ZenDeviceEffects`, and a device will happily accept an effect it has no
  intention of applying — so which ones take effect varies. Where an effect does
  nothing and nothing else can reach it, the control is not drawn at all rather
  than offering a switch that lies.
- **A boot watch.** Some vendors withhold `BOOT_COMPLETED` from apps their
  launch manager has decided are unimportant, which leaves a bedtime app armed
  with no alarms behind it. Gloaming compares the boot it handled against the
  boot it is running on, says so when one goes unreported, and offers a button
  to the screen that fixes it.
- **It watches its own alarms.** Vendor interference is the failure this app was
  built around, so it checks rather than assumes. One throwaway alarm, eleven
  minutes after install, asks whether this phone delivers alarms in the
  background at all — silent unless the answer is no. Every window's END is
  watched too, and if it comes due and does not arrive on time you are told, with
  a button to the setting that usually explains it. The verdict is **lateness,
  not arrival**: a blocked alarm is not dropped but held, and released the moment
  you open the app, which is exactly what a naive check scores as success.
- **Two themes that were measured rather than picked.** Dusk and Dawn are
  stated in Material 3's own tone scale, and checked against what Google's apps
  actually ship on a phone rather than against the documentation — the two
  agree, which is worth knowing before treating the spec as an ideal nobody
  follows. Every contrast figure sits in the source beside the colour it
  belongs to, including the ones that only just pass and the one that
  deliberately does not.
- **English, Russian and Ukrainian**, with a per-app language picker, and clock
  times in whichever 12- or 24-hour format the phone is set to.
- **A journal, and a way to send it.** The app keeps its own on-device log of
  every decision it makes overnight — useful anywhere, and necessary on devices
  that encrypt logcat for third-party apps. **Send diagnostics** turns that, the
  schedule and the capability answers into plain text, alongside what the
  *system* reports about the zen rule. The two are kept deliberately apart and in
  that order, because every bug worth the feature lives in the gap between what
  the phone says and what the app intended. It cannot tell you whether a
  notification made a sound; nothing an ordinary app can reach can.
- **Reset, in the one order that is safe.** Android's own *Clear storage* is a
  trap here: the prefs are the app's only handle on its zen rule, so wiping them
  leaves the rule live, applying its effects, and unreachable — measured. **Reset
  Gloaming** switches bedtime off, puts the vendor's always-on keys back, removes
  the rule and only then clears the store. The journal survives, because someone
  resetting is usually resetting because something went wrong.

## Devices

Nothing vendor-specific runs unconditionally, and no device is special-cased
into working:

- Capability is **measured at runtime, not looked up in a device list**. Whether
  always-on display can be suppressed is settled by reading the AOSP key the
  effect acts on; where that key is present, the switch is simply live.
- There are **two** `Build.MANUFACTURER` tests, and both are last resorts where
  no probe exists. One returns a vendor's own always-on keys and null everywhere
  else, so every caller stops at null. The other decides whether a phone applies
  the zen rule's screen effects at all — it cannot be read back, since the
  platform's own getters are `@hide` — and it expires by itself the moment the
  effects are observed working.
- The one device list is an **exclusion** list, consulted only when the AOSP key
  it would otherwise read is absent — so an unrecognised phone is treated as
  capable rather than as broken.
- The only writes to `Settings.*` in the whole app are the always-on routes
  below, and each is gated on a permission the app does not hold by default. On
  Honor and Huawei the keys live in `Settings.Secure`, which needs an adb grant,
  so that path is inert on an ordinary install. On Samsung they live in
  `Settings.System`, where `WRITE_SETTINGS` is the guard — and that one the user
  can grant from a normal settings screen, so the control is offered rather than
  hidden.

Run on three phones so far. This table is what each **platform** does on its
own, before the app compensates — not what Gloaming does on it:

| | Honor BKQ-N49<br>MagicOS 10 | OnePlus CPH2653<br>LineageOS 23 | Galaxy S23<br>One UI 8 |
|---|---|---|---|
| exact alarms reach a closed app | yes | yes | yes |
| `BOOT_COMPLETED` delivered | only if auto-launch is on | yes | yes |
| DND survives a reboot mid-window | yes | **no** — the rule comes back deactivated | yes |
| the zen rule's screen effects applied | yes | yes | **none of them** |
| always-on display suppressed on request | no | yes | no |

Gloaming repairs the middle row, so bedtime does survive a restart on all three.
It works around the second and the last — a notice for the withheld broadcast, a
vendor route for the display. The fourth it cannot repair, and rather than leave
switches that quietly do nothing, it hides them there and offers the phone's own
bedtime screen instead.

None of that is looked up in a device list. Each row is a question the app asks
at runtime, and the answers shape what it draws: a switch that cannot move is
not shown, and a door that opens onto nothing is not offered.

Two of those cells are worth calling out, because they are the opposite of what
anyone would guess. The reboot bug is on the **AOSP-derived** phone and absent on
both vendor ones. And One UI stores the zen rule's screen effects and applies
none of them — not a missing capability, since its own Sleep mode drives the
very same display saturation, but third-party rules were simply never wired to
it. Reports from other hardware are welcome.

## Requirements

Android 15 (API 35) or newer — `ZenDeviceEffects` and `AutomaticZenRule.Builder`
are API 35, and the app is built entirely around them.

Two permissions, both user-granted from inside the app: notification policy
access (Do Not Disturb) and exact alarms.

### Honor, Huawei and other phones with an "app launch" manager

Some vendors withhold `BOOT_COMPLETED` from apps their launch manager has
decided are unimportant. Measured on an Honor Magic8 Pro: with Gloaming set to
"Manage automatically", the app was never told the phone had restarted, so it sat
armed with no alarms behind it until it was next opened — silently, all night.

Fix it once, in **Settings ▸ Apps ▸ App launch ▸ Gloaming ▸ Manage manually**,
with auto-launch on. Battery optimisation is a different setting and does not
help; it was tested both ways.

Gloaming notices this by itself and offers a button straight to the right
screen. The notice clears once a boot arrives normally, and it never asks which
phone you have, so it works on any vendor doing this.

The app also offers the setup once, when bedtime is first switched on — and
because those two switches cannot be read back, going to look at them is not
treated as having set them. Come back and the card asks rather than assumes:
*did you turn them on?* One tap answers it for good. Refusing it outright is
final too, and survives a reset, since a reset restores what the app owns and
those switches are not among them.

### Optional: always-on display on Honor and Huawei

Some phones ship their own always-on display and ignore the platform's request
to suppress it — measured on an Honor Magic8 Pro, where even the system's own
lever is disregarded. On those phones the always-on row is not shown, because
nothing the app can reach would move it.

There is one route, and it needs a permission Android will not grant to an
ordinary app. If you want it, connect the phone and run:

```bash
adb shell pm grant com.jemcik.gloaming android.permission.WRITE_SECURE_SETTINGS
```

The row then appears and bedtime switches the display off, restoring whatever
you had when the window ends. The app records your previous values before it
writes anything and restores them on END, on boot, and after a crash. Revoking
the permission, or never granting it, leaves the whole path inert.

Note the app cannot undo this if you uninstall it mid-window; switch bedtime off
first, or turn always-on back on in Settings.

### Samsung: always-on display, without adb

One UI keeps its always-on keys in `Settings.System` rather than
`Settings.Secure`, and that one difference decides everything: `Settings.System`
is guarded by **`WRITE_SETTINGS`**, which you grant on an ordinary settings
screen. No adb, no root.

The row appears with the ask written on it, sends you to Android's *Modify
system settings* screen, and once granted the display goes off for the window
and comes back afterwards — the previous value is recorded before anything is
written, and restored on END, on boot, and after a crash.

The keys were found by diffing every settings namespace across One UI's own
Sleep mode, which is also how Honor's were found.

### Samsung: the screen effects are not shown, and that is deliberate

One UI accepts the zen rule's `ZenDeviceEffects` and applies none of them —
greyscale, wallpaper dimming and dark theme are all inert, checked across a
screen-off cycle because AOSP defers night mode there on purpose. A switch that
does nothing is worse than an absent one, so those three are hidden on such a
phone.

Settings then offers **System bedtime mode**, which opens One UI's own Sleep
mode — where its greyscale genuinely works. Every other route was chased to a
measured dead end and the results are in
[docs/DECISIONS.md](docs/DECISIONS.md): there is no settings key for greyscale,
`setWallpaperDimAmount` is `@hide`, and `UiModeManager.setNightMode` compiles,
throws nothing and changes nothing without a signature permission.

### When something goes wrong, the app says so

Vendor interference is the failure this app was built around, so it watches
itself rather than assuming:

- **One throwaway alarm**, eleven minutes after install, asks whether this phone
  delivers alarms in the background at all. It is silent unless the answer is no.
  The verdict is *lateness*, not arrival — a blocked alarm is not dropped but
  held, and released the moment you open the app, which is exactly what a naive
  check would score as success.
- **Every window's END is watched.** If it comes due and does not arrive on time,
  you are told, with a button to the setting that usually explains it.
- **A restart that never reached the app** is detected after the fact, because no
  vendor lets that be read in advance.

None of these ask which phone you have.

When it does go wrong on a phone nobody here owns, **Settings ▸ Send
diagnostics** is the answer: one tap produces a plain-text report — the rule as
the system reports it, the schedule as the app intended it, every capability
answer, and the journal — which you can read before it goes anywhere.

## Build

    ./tools/build.sh      debug APK, full log at /tmp/build.log
    ./tools/deploy.sh     install on the attached device. Refuses if the APK is
                          older than the sources; -f installs it anyway
    ./tools/check.sh      what the phone thinks: zen state, the rule, the app's
                          own view, the next alarms and the journal. Read-only.
    ./gradlew lint        0 errors
    python3 tools/render_icon.py
                          re-render docs/icon.png from the adaptive icon's own
                          numbers — cropped to the 72dp a launcher shows and
                          masked to a squircle, so its corners are transparent
                          rather than a square of background

## Tests

    ./gradlew test        163 tests, JVM only, seconds
    ./gradlew coverage    JaCoCo HTML at app/build/reports/jacoco/coverage

They cover the scheduling core (pure functions of times, days and an injected
`now`), the sentence assembly in all three languages, the screen interactions,
the boot detection, the two delivery watches — including the case that matters
most, an alarm that arrives only because the app was opened — and the one-shot
preferences migrations, the only code here that could corrupt data without saying
anything. The alarm that ends a window early is driven through a real
`setAlarmClock`, so the tests exercise the same `getNextAlarmClock` the app reads
rather than a stub of it. Newer ones cover the diagnostics report, the reset
teardown — no rule survives it, not even one whose id was already lost — and the
dial's own gesture, where a vertical swipe across the ring must scroll the page
rather than move the schedule. Some of them measure real text
layout at each locale's own widths, because a row that fits in English and wraps
in Ukrainian is a bug you cannot see from the source — at the width the row
actually has on the screen, which is not the width of the screen.

What they cannot cover is everything that depends on the device: whether an
exact alarm fires with the screen off, whether greyscale is really applied,
whether `updateAutomaticZenRule` clears a rule's condition. That is what the
journal and `tools/check.sh` are for.

A pre-push hook runs the tests, lint and the translation checkers. Opt in once
per clone:

    git config core.hooksPath tools/hooks

## Translation

    python3 tools/translate.py ru          # or uk
    python3 tools/check_translation.py app/src/main/res/values-ru/strings.xml ru

Gemini Pro translates, two models side by side; the checker verifies what a
human will not spot by eye — dropped format placeholders, missing plural forms
(Russian and Ukrainian need four where English needs two), keys that vanished,
strings left in English, and rows that grew past the width they have to fit.

Needs `GEMINI_API_KEY` in `.env.local`, which is git-ignored.

## Releases

Tag a version and CI does the rest:

```bash
gh release create 0.3 --target main --generate-notes --title 0.3
```

`v0.3` works too. The workflow derives `versionName` and `versionCode` from the
tag, builds a signed release APK and attaches it to the GitHub Release — release
assets are downloadable without a GitHub account, which workflow artifacts are
not.

Note that **0.1 and 0.2 cannot be upgraded from**. They were published as debug
builds, and a CI runner generates a fresh debug key per run, so those two are
signed by different throwaway certificates and Android will not install over
them. If you have either, uninstall before installing anything newer.

## Licence

Apache License 2.0 — see [LICENSE](LICENSE).

The repository also redistributes two SIL Open Font License fonts and a set of
Material Design icon paths; [NOTICE.md](NOTICE.md) lists them with their
licences.

## CLAUDE.md and docs/DECISIONS.md

The real documentation, in two parts.

**[CLAUDE.md](CLAUDE.md)** is the working reference: the architecture, the rules
that will break something if ignored, and the commands. Short enough to read
before touching anything.

**[docs/DECISIONS.md](docs/DECISIONS.md)** is the lab notebook behind it — every
measurement, and every wrong first attempt kept beside the answer, because how a
thing was got wrong is usually the more useful half. Among them: that
`updateAutomaticZenRule` silently clears a rule's condition, so rewriting a live
rule switches Do Not Disturb off underneath you; that a dark warm colour is
always brown; that a screenshot can compare two captures but can never establish
that a colour is right, because on a panel not running in sRGB the framebuffer
and the glass disagree; and that centring a crescent by its own centroid moves
it somewhere it does not look centred.

It also keeps its own reversals. The launcher icon's entry opens by saying no
flat colour can serve the artwork, then records how a white tile did — the
evidence was right and the conclusion drawn from it was too narrow. Much of the
file is written against one phone, because that is the phone it could be
measured on.
