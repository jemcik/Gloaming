# Gloaming

Android bedtime app. Schedules Do Not Disturb, grayscale and wallpaper dimming
for a nightly sleep window.

## Why it exists

Google's Digital Wellbeing bedtime mode never fires on Honor/MagicOS phones.
Diagnosed 28 Aug 2026 on a Magic 8 Pro (BKQ-N49, MagicOS 10.0.0.199,
Android 16): Wellbeing's `WindDownBedtimeSynchronizerWorker` is a WorkManager
job, and MagicOS adds an undocumented JobScheduler constraint that is never
satisfied:

    Required:    HN_USER_EXPERIENCE [0x200400]
    Unsatisfied: HN_USER_EXPERIENCE [0x400]

The job never runs, so bedtime only triggers when the app is foregrounded.
The constraint cannot be satisfied or removed, even over adb.

`setExactAndAllowWhileIdle` is **not** gated by it. Gloaming uses exact alarms
to flip the zen rule itself. Measured: fires within ~150 ms, app closed, screen
off. Verified overnight 28–29 Aug with no battery whitelist and standby bucket
10 — i.e. the real user path, not a privileged one.

## Architecture

    core/Scheduler.kt        exact alarms; a window is start + duration, never
                             two independent times. The day-of-week selection
                             is the MORNING a window ENDS on, and Scheduler
                             works backwards to the evening that reaches it
    core/ZenController.kt    owns the AutomaticZenRule: policy + device effects
    core/BedtimeReceiver.kt  START / END / BOOT_COMPLETED / MY_PACKAGE_REPLACED
    core/Clock.kt            clock times in the phone's own 12/24 format
    core/Prefs.kt            SharedPreferences
    core/Journal.kt          on-device log; read it over adb, see Build
    ui/BedtimeDial.kt        24-hour dial, draggable handles, sweep gradient
    ui/SettingsScreen.kt     theme mode, a link to the system language picker
    ui/Theme.kt              Dusk/Dawn tokens, Baloo 2 + Figtree type scale,
                             and the arc's ground-aware variants (stopsOn,
                             nightOn) for thin strokes and fills on Dusk

The rule holds the policy; we hold the trigger. `component=null` on the rule is
deliberate — there is no condition provider, which is precisely what avoids the
blocked path.

## Vendor limitations (all measured, not assumed)

MagicOS accepts these API values and silently ignores them:

| Requested | Stored in rule | Applied |
|---|---|---|
| grayscale | yes | **yes** |
| dimWallpaper | yes | **yes** |
| nightMode | yes | no |
| suppressAmbientDisplay | yes | no |
| rule icon in status bar | yes | no (confirmed by swapping to a square) |

**Device effects do not depend on the rule's interruption filter.** Measured
29 Aug 2026, mid-window, all four cells, with `dumpsys color_display` ->
`Global saturation: Activated` as the witness:

| Rule zenMode | grayscale asked | zen_mode | saturation |
|---|---|---|---|
| IMPORTANT_INTERRUPTIONS | no | 1 | false |
| IMPORTANT_INTERRUPTIONS | yes | 1 | **true** |
| OFF (`INTERRUPTION_FILTER_ALL`) | yes | 0 | **true** |
| OFF (`INTERRUPTION_FILTER_ALL`) | no | 0 | false |

So an ACTIVE rule that filters nothing still carries its effects. That is what
makes a second, effects-only rule possible: rewriting it would never touch the
DND rule's condition, so `zen_mode` would never drop and the system would never
re-announce Do Not Disturb. It also confirms grayscale genuinely applies, which
until now rested on the eye - `screencap` does not capture the display's colour
transform, so a screenshot looks fully coloured either way.

**The system's own Do Not Disturb screen cannot open this app, and cannot be
made to.** Honor routes every row in Settings > Do Not Disturb to its built-in
clock-schedule editor. `configurationActivity` is honoured for exactly one
package, hardcoded in `ZenModeScheduleRulePreference.<init>`
(`SettingsOversea.apk`, classes2.dex):

    0076: const-string v3, "com.google.android.apps.wellbeing"
    0078: invoke-virtual {v6}, ComponentName.getPackageName()
    007c: equals()  0080: if-eqz -> skip
    0088: iput-object v6, ....m       // else the target stays null

Tapping "Gloaming" therefore opens "Edit time period" with Repeat/From/To
blank - there is no schedule to show. `type` is not consulted: TYPE_SCHEDULE_TIME,
SCHEDULE_CALENDAR, DRIVING, IMMERSIVE and THEATER were all tried and all behave
identically, and TYPE_BEDTIME is refused outright ("Only the 'Wellbeing' package
can use AutomaticZenRules with TYPE_BEDTIME"). Our manifest already declares the
`android.app.action.AUTOMATIC_ZEN_RULE` filter, correctly, and it is never
queried - the string does not appear in Settings at all.

What that screen can do to us, all measured 29 Aug 2026:

| Action | Effect |
|---|---|
| the check mark | nothing - no schedule was parsed, so nothing is written |
| **Delete** | rule removed, `zen_mode` 0, Gloaming still armed with no rule |
| **toggle off** | `enabled=FALSE`, `zen_mode` 0 |
| **toggle back on** | `enabled=TRUE` but `state=STATE_FALSE` - DND does NOT come back |

Hence `ZenController.reconcile` and `ZenStatusReceiver`. The platform broadcasts
`ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED` to a rule's owner; we treat it only
as a hint to look, and decide from what `getAutomaticZenRule` reports, so a
spoofed broadcast cannot drive anything. A missing rule is rebuilt; a rule
switched off in Settings is followed rather than fought - the app turns its own
switch off and says so - and turning the app back on re-enables the rule,
because `syncRule` forces a push while `!isEnabled && p.enabled`. `MainActivity`
reconciles on ON_RESUME too, since before this the app only repaired on a COLD
start: resuming it did not, so a rule deleted at 20:00 cost the whole night.

System dark mode cannot be set by any app on any Android device:
`MODIFY_DAY_NIGHT_MODE` is `signature|privileged|role`; `pm grant` refuses it.
Writing `Settings.Secure.ui_night_mode` succeeds but the service ignores it.

Honor's AOD settings screen is unreachable — `com.hihonor.aod/.ui.AODSettingsActivity`
requires `WRITE_SECURE_SETTINGS`, `Settings$HomeAndUnlockSettingsActivity` needs
`HW_SIGNATURE_OR_SYSTEM`, `LauncherModeSettingsActivity` is not exported.

Blue-light filter is not in `ZenDeviceEffects` on any device. Honor's
`eye_comfort_*` keys READ via `Settings.System` but are registered in
`sSystemMovedToSecure`, so writes fail with "You cannot keep your settings in
the secure settings" even with `WRITE_SETTINGS` granted.

Detection: `DarkCapability` measures (throwaway rule, 2.5 s, observe).
`AmbientCapability` infers from one verified key — do not add keys without
confirming on real hardware.

## Gotchas that cost real time

- **Logcat is encrypted** for third-party apps on MagicOS — entries come back as
  `(HKS)...(HKE)`. Hence `Journal.kt`. Never swallow an exception; three bugs
  hid behind `runCatching { }` with no logging.
- **`clip(RoundedCornerShape(n))` on a short container eats content.** A 28 dp
  radius cut off labels; a 12 dp radius trimmed ~12 dp off each end of a 1 dp
  rule. Don't clip anything under ~40 dp tall whose child touches the edge.
- **Compose state goes stale**, in three separate ways. The receiver writes
  prefs while the app is backgrounded, so the screen re-reads everything on
  `ON_RESUME`. `now` goes stale with the screen simply left open — the
  countdown, the dial's marker and the crown marker all read it, so `Home` runs
  a one-minute ticker. And `pointerInput(Unit)` runs its block once and keeps
  it, so any callback captured inside is frozen at the first composition: the
  centre-tap cycle appeared to work, then did nothing on the second tap,
  because the lambda still closed over the index it was built with. Wrap
  callbacks used inside `pointerInput` in `rememberUpdatedState`.
- **`updateAutomaticZenRule` clears the rule's condition.** The rule falls back
  to `STATE_FALSE`, i.e. rewriting a live rule switches it off. So any path that
  rewrites the rule mid-window must re-assert the state afterwards, and
  `setActive` decides whether to skip by asking `getAutomaticZenRuleState`,
  never by what it last wrote. Caching that belief lost a whole evening of DND:
  the 18:11 upgrade rewrote the rule, the state went false, and the app declined
  to re-arm because it still thought it was on.
- **Every rewrite of a live rule is visible.** The system's own log spells the
  sequence out: `updateAutomaticZenRule` -> `condition: COND->null, (->inactive)`
  -> `set_zen_mode: off`, then our `setAzrState` -> `(->active)` ->
  `set_zen_mode: important_interruptions`, 1.5 ms later. Zen genuinely goes off
  and on, so the system posts `zenmode_notification_tag` afresh and the "Do Not
  Disturb is on" notification reappears. There is no API to change a rule
  without deactivating it, so the only lever is rewriting less: the times reach
  the rule as `triggerDescription` and nothing else, so they sit past `COSMETIC`
  in `ruleSignature` and a live rule is never rewritten for them - the caption
  goes out with the next real push, or at the next START, when the rule is
  inactive and rewriting is free. `InterruptionsScreen` writes prefs per tap but
  pushes once on ON_PAUSE or dispose, so a visit costs one rewrite rather than
  one per switch. What remains: a screen-effect chip and either master switch
  still blink once each, which for the switches is the honest answer anyway.
  Read the log with `dumpsys notification --noredact`, section `Zen Log:` ->
  `State Changes:`; note BSD grep has no `\s`, which silently made an early
  version of that probe report "no churn" for everything.
- **A needless rule rewrite can leave the phone's own Do Not Disturb icon stuck
  OFF while zen is still ON.** Reported as "the app says bedtime is on but Do Not
  Disturb clearly isn't". It was: `mZenMode=ZEN_MODE_IMPORTANT_INTERRUPTIONS`,
  `mInterruptionFilter=2`, `zen_mode 1`, rule `STATE_TRUE` - and no moon in the
  status bar. The moon does track zen correctly the rest of the time (checked
  against four captures from one evening), so this is SystemUI dropping the icon
  on the off half of a rewrite's off/on pair and not putting it back 5 ms later.
  A deliberate off/on with a real gap between them restores it.
  The cause was ours: `MY_PACKAGE_REPLACED` cleared `ruleSignature` to force a
  push, so every app upgrade rewrote the rule whether or not anything had
  changed. `syncRule` now compares `getAutomaticZenRule(id)` against
  `buildRule(...)` on the forced path and only pushes on a real difference -
  `AutomaticZenRule.equals` is reliable here, verified by an upgrade producing no
  zen transition at all. The repair it exists for still works, because a rule
  edited from Settings genuinely differs.
- **`am broadcast` cannot reach the receiver** — it is `exported="false"`, which
  is correct. Test through real alarms.
- **Any `Typography` role left unset falls back to Roboto**, and the trap is the
  same shape as the violet one below: it is invisible until a component reaches
  for a role we never write ourselves. `TimePicker` reads `displayLarge` for its
  hour and minute fields, which were rendering in Roboto next to the app's own
  Baloo numerals. All fifteen roles are named now, so nothing can fall through.
- **Any `ColorScheme` role left unset falls back to Material's baseline violet.**
  `TimePicker` reads `primaryContainer` for the selected field and
  `surfaceContainerHighest` for the clock face and the unselected field; neither
  was set, so the picker arrived lavender on cream and purple on black. Both
  schemes now name the container and tertiary families, and the picker names all
  nine colours it reads at the call site so a missed role cannot resurface.
- **Editing the schedule mid-window used to drop DND on the spot.**
  `rescheduleAll` asked `currentWindowEnd(start, end, days)` whether a window
  was running, so deselecting today made it answer null and the else branch
  called `setActive(false)`. `Prefs.activeDay` pins only the DATE the night
  began on, and `liveWindowEnd` then describes the window with the current
  handles on that date — start plus duration, like everything else here. Two
  earlier attempts pinned an instant and were both too blunt: pin the END and
  the wake handle stops working (the dial read 13:40 while the card said "until
  16:00"); pin the BEGINNING and the bedtime handle stops working (drag it past
  now and the app still claims to be running). With only the day pinned,
  day-of-week edits cannot cut the night short while both handles still mean
  what they say. The receiver clears the pin on
  END *before* rescheduling: an alarm landing a hair early would otherwise
  reopen the window and rearm END for the same instant, forever.
- **Seven 48 dp touch targets do not fit the content column.** Android's
  minimum is 48x48 dp; seven of them need 336 dp and the column is 311 dp on a
  359 dp-wide screen. Each day now takes an equal share of the row at 48 dp
  tall, which is the largest this width allows without bleeding past the screen
  margin — 44.4 x 48 rather than 40 x 40. Google Clock ships the same
  compromise. Do not "fix" this by shrinking the gaps; there are none left.
- **A trailing lambda binds to the LAST parameter, and Compose will happily
  run it as content.** `AllowRow(kind, title, subtitle, locked, onClick,
  trailing)` called as `AllowRow(...) { editing = "conv" }` binds the lambda to
  `trailing`, not `onClick`. It compiles, and then Compose invokes it as
  composable content on every composition — so `editing` was set permanently, a
  `ModalBottomSheet` was always open, and its window swallowed every touch on
  the screen beneath. The visible symptoms were a screen that would not scroll,
  switches that did nothing, and a sheet that opened on arrival; none of them
  looked like a parameter-order problem. Put the `@Composable` slot BEFORE the
  click lambda so the natural call site is the correct one.
- **Pushing an identical `AutomaticZenRule` re-applies it.** `updateAutomaticZenRule`
  on a live rule makes the system re-assert its device effects, so grayscale and
  Do Not Disturb visibly blinked on every settings tap, screen return and app
  launch. Three causes stacked: `commit()` synced the rule and then
  `rescheduleAll` synced it again; `syncRule` updated unconditionally; and
  `setActive` re-asserted `STATE_TRUE` on an already-active rule.
  `Prefs.ruleSignature` holds everything `buildRule` reads, and nothing is
  pushed unless it changed. `setActive(force = true)` exists for alarms and
  boot, which must assert regardless — and boot clears the signature, or a rule
  edited from Settings would never be repaired.
- **Do not hand-draw icons.** Three rounds went into a phone handset built
  from arcs and capsules; it read as the letter C, then a horseshoe, then a
  limp hook. Icon geometry is craft and it is already done: the path data lives
  in `google/material-design-icons`, and `res/drawable/ic_*.xml` are the real
  Google paths, fetched and checked in. Tint at the call site so they follow
  row state. No dependency, ~90 lines of Canvas deleted.
- **Screenshots are not colour-accurate.** `adb exec-out screencap` on this
  device returns values a few units off the source hex, consistently in the
  same direction. Fine for comparing two captures, useless for verifying an
  exact colour — sample both and compare, never assert a hex from a capture.
- **`veil` is invisible at 1 dp** despite being the hairline token by name. The
  stepped-time rules used `line` instead; those rules are gone, but the next
  1 dp stroke will hit the same wall.
- **`Arc.night` barely registers on a dark ground.** 8.4:1 against Dawn's cream,
  1.8:1 against Dusk's surface, 1.6:1 as a fill on `raise`. A hairline looks
  like it starts halfway along; a filled block stops reading as a block. Hence
  `Arc.stopsOn(dark)` and `Arc.nightOn(dark)`, which begin at `dusk` instead.
  The ring itself keeps `night` — at 17 dp it carries its own weight.

## Build

    ./tools/build.sh     debug APK, full log at /tmp/build.log
    ./tools/deploy.sh    install on the attached device
    ./tools/check.sh     what the PHONE thinks: zen state, the rule, our prefs,
                         the next alarms and the journal, side by side. Read-only.
    ./gradlew test       the scheduling core, on the JVM, in under a second
    ./gradlew lint       0 errors expected; the 5 warnings left are policy
    python3 tools/check_translation.py app/src/main/res/values-ru/strings.xml ru
    adb shell run-as com.jemcik.gloaming cat files/journal.log

A pre-push hook runs the tests, lint and both translation checkers. It is
versioned in `tools/hooks/`, so a fresh clone has to opt in once:

    git config core.hooksPath tools/hooks

`git push --no-verify` skips it deliberately.

Toolchain: AGP 9.3.2, Gradle 9.7.1, Compose compiler 2.3.21, BOM 2026.08.00,
compileSdk 37, targetSdk 36, minSdk 35. AGP 9 removed the separate
`kotlin.android` plugin — Kotlin support is built in.

minSdk is 35 because the app cannot work below it: `ZenDeviceEffects`,
`AutomaticZenRule.Builder`, `AutomaticZenRule.getType` and
`NotificationManager.getAutomaticZenRuleState` are all API 35. It was 34, with
no `SDK_INT` guard anywhere, so on Android 14 the app would have installed and
then died the first time it built a rule — and not recoverably: a missing class
or method raises `NoSuchMethodError` / `NoClassDefFoundError`, which are `Error`,
not `Exception`, so none of the `runCatching` / `catch (e: Exception)` around the
zen calls would have caught it.

## Portability

Nothing vendor-specific executes unconditionally. Audited 29 Aug 2026, before
trying the app on anything but the Honor:

- `AmbientSettings.locationHint()` is the only `Build.MANUFACTURER` test. It
  returns an Always On Display path for Honor/Huawei and null everywhere else,
  and the caller draws nothing when it is null.
- `AmbientCapability.KNOWN_PARALLEL_AOD` is an EXCLUSION list, consulted only
  after the AOSP `doze_always_on` key turns out to be absent. An unknown device
  is treated as supported, so a vendor we have never seen gets a switch that
  might work rather than a chip that certainly does nothing.
- There is no eye-comfort code at all. `EyeComfort` was an empty object holding
  a comment, with six preferences nothing read; both are gone, and the finding
  it recorded is in "Vendor limitations" above, which is where it belongs.
- There are no writes to `Settings.System` / `Secure` / `Global` anywhere. The
  two reads are capability detection.
- Whether dark theme works is MEASURED per device by `DarkCapability`, keyed to
  `Build.FINGERPRINT` so an OS update reopens the question, and the "handled by
  your phone" sentence is driven by that verdict rather than by a device list.
  On a phone that honours night mode the chip is live and the sentence does not
  appear.

Everything else naming a vendor is a comment.

Worth knowing on ANY device: `DarkCapability.probe` creates and activates a
throwaway zen rule for 2.5 s on first launch, so a "Do Not Disturb is on"
notification flashes once. That is not vendor-specific.

## Tests

`app/src/test/` holds `SchedulerTest` - 22 cases over the scheduling core, on the
JVM, no device. They are written as the QUESTION the code answers ("if I deselect
today while tonight is already running, does tonight survive?") rather than as
coverage of a method, because none of the bugs were ever in a method - they were
in an assumption. Every case is a fact this app got wrong at least once: the
midnight wrap, days-as-mornings, the one-off, and the `activeDay` pin that broke
twice in opposite directions.

Two things worth knowing before adding to them:

- The scheduling core takes `from: LocalDateTime` and, since the purity refactor,
  `enabled`/`activeDay` rather than a whole `Prefs`. Keep it that way: the moment
  a function in there needs a `Context`, it stops being testable in milliseconds.
- `liveWindowEnd` deliberately returns a window even when the app is switched
  OFF, for a repeating schedule. That is not an oversight - the dial draws its
  marker on `insideWindow`, so an unarmed schedule still shows where you would
  be. `runningNow = enabled && insideWindow` applies the switch. A test asserting
  null there was written, failed, and turned out to be wrong about the app.

`InterruptionsTest` runs under Robolectric, because the sentences are built out
of resources and the point is the wording per locale. It found a real bug on its
first run: `ListFormatter.getInstance()` with no argument uses
`Locale.getDefault()` — the SYSTEM language, not the app's. Those are the same
thing until someone uses the per-app language picker, which this app now offers,
and then Ukrainian resources were joined with an English conjunction:
«Будильники, дзвінки, and ще 6». It takes the locale from `Resources` now.

Note the unit tests run on **JDK 21**, pinned in `app/build.gradle.kts`.
Robolectric's bytecode instrumentation cannot read Java 25 class files
("Unsupported class file major version 69") and Gradle auto-provisions a 25 JDK
here. Only the test JVM is pinned; the app compiles against the toolchain above.

`ScreensTest` covers interactions, never appearance - no test checks a colour or
a spacing, because those are judgement and the phone is the instrument. It checks
the things that have gone quiet before: a row that stops responding, a control
reporting the wrong value, a screen you cannot leave. Note that `performClick()`
does not toggle an `AllowRow` under Robolectric, though it works on the
`selectable` rows in Settings; that one test drives the semantics action instead,
which is also closer to what it means to assert (the ROW carries the action, the
switch is only the indicator).

`PrefsMigrationTest` covers the one-shot `days` migration, which is the only
code here that can corrupt data silently: it rewrites the user's schedule, it
runs before any screen is drawn, and a mistake would simply make the nights wrong
with nothing to report it. Four failure modes are covered and each was confirmed
by breaking the migration on purpose - running on every construction (`Prefs` is
built in the receiver, on every screen and in `reconcile`, so that would walk the
schedule forward a day at a time), shifting daytime windows that never needed it,
shifting the wrong direction, and never setting the flag so a fresh install gets
migrated later.

What they do NOT cover, and cannot: everything that depends on the vendor.
Whether an exact alarm fires with the screen off, whether MagicOS applies
grayscale, whether `updateAutomaticZenRule` nulls the condition. That is what the
journal is for.

## Outstanding

Design brief lives in the conversation history, not the repo. Nothing is left
outstanding from it.

Tabular figures were the last item, and the measurement is worth keeping: at 36sp
proportional, "20:05" is 86.9dp and "13:50" is 79.7dp - the same five characters,
7.1dp apart - so both window times jumped and the centred countdown jittered
every minute. With `fontFeatureSettings = "tnum"` both measure 92.0dp exactly.
Both Baloo 2 and Figtree carry tnum; the feature was verified in the font tables
before relying on it, because setting it on a font that lacks it is a silent
no-op. Tabular digits are wider: the countdown grew from 128dp to 141dp, which
still leaves about 25dp inside the ring on each side at the worst case.

**It belongs on the display family and nowhere else.** It was applied to
bodyLarge/Medium and labelSmall too, on the reasoning that they also show a
number which changes - and that was wrong twice over. labelSmall is the overline
role and never renders a digit at all. bodyLarge is the prose role for the whole
app, and there the padding is visible and pointless: pointless because the line
is left-aligned, so a width change moves no other pixel, and visible because
tabular padding widens the narrow glyphs most. Figtree goes further and swaps in
a DIFFERENT "1" for tabular - 5.1dp of ink against 3.1dp proportional - so
"Starts in 12 hr" read as "Starts in 1 2 hr". Reported as exactly that. The rule
now: numerals get tnum, sentences do not. Removing it took the line from 111.7dp
to 107.4dp and cost nothing, because nothing there was ever aligned to anything.

The brief's **app bar with wordmark was built and then removed**, deliberately.
It cost 66dp of the space above the fold - a 48dp row plus an 18dp gap - on every
visit, to hold a wordmark that is not doing any work (the launcher icon and the
task switcher already say which app this is) and an entry to a screen you visit
once, to set the theme or the language, and then never again. Settings is a row
at the FOOT now, behind a section rule, where "Show activity" used to sit. The
master card starts at y=62dp instead of y=128dp. It is also easier to reach
one-handed down there than the top-right corner was.

**Settings deliberately does NOT have a 12/24-hour switch.** Android owns that
preference and `core/Clock.kt` follows it; an app-level override on top of a
system one is usually a sign the app got it wrong. The language row deep-links to
the system's own per-app picker rather than reimplementing it - the picker exists
because the manifest declares `localeConfig`, and Google's guidance is to provide
the entry point, not a second picker that drifts from the first.

**Dynamic colour (Material You) is parked, not rejected.** It would be a second
SOURCE for the existing light/dark modes rather than a third theme, and it would
have to leave the dial alone: the arc runs night to dawn with a moon at one end
and a sun at the other, and a wallpaper-derived gradient says nothing about the
time of day. The cost is that all twenty tokens carry measured decisions - see
the contrast notes throughout this file - and a generated palette invalidates
every one of them, differently on every phone.

Dropped on 29 Aug 2026, deliberately, not forgotten:

- The **"Turn on tonight" CTA**. Its behaviour shipped as the "Once" preset —
  no days chosen means the window runs once and the app switches itself off —
  and a second affordance for it was judged unnecessary.
- The brief's **"bottom block pinned with slack pooling above it"**. The master
  switch moved to the top, so the block it was meant to pin is not there.
- The **"Five things" hero card** and **× remove buttons** on the allowlist.
  The rewrite gave every exception its own row with its own control, so there
  is nothing left for an × to remove, and the lead sentence lists what gets
  through instead of counting.

The per-state centre sublabels arrived instead as the tappable centre readout.

**"Send a test alert" shipped as a status readout, not an alert.** A
notification this app sends itself cannot test the allowlist: DND's people
exceptions match on a notification's attached `Person` and whether that person
is a contact, so a self-posted one has nobody behind it and always behaves like
an ordinary app notification. Faking one needs READ_CONTACTS, which the app
avoids. A test alert could therefore only ever demonstrate silence — and
silence is indistinguishable from a button that does nothing. The screen asks
`getCurrentInterruptionFilter()` instead and reports what the SYSTEM says is in
effect, which is both cheaper (no runtime POST_NOTIFICATIONS, no channel) and
better authority in an app that exists because the platform does not always do
what it is asked. It is read every composition rather than remembered, so it
refreshes on any interaction — but it will not update on its own if Do Not
Disturb changes from the quick settings tile while the screen is open.

## Deviations from the design brief (deliberate, worth revisiting with designer)

- Master switch stays in one place across all states, and since 29 Aug 2026
  sits at the top of the screen with the state lamp folded into it — green
  armed, red off. The brief hides the switch while running, which left no way
  out of DND until the wake alarm, and carried a separate running-only status
  pill that said nothing the row does not.
- Selection-as-a-fill has its own `selectFill` / `onSelect` tokens — day
  toggles, effect chips, allowlist avatars, the switch track. `stateOn` has to
  stay legible as text on the ground ("Allowed", the chosen row in a choice
  sheet), which forces it dark in Dawn, and at `#56633F` every selected day
  read as a hole punched in the screen. Dawn now fills with `#BDCB9F`; Dusk is
  unchanged. Note the tradeoff, chosen deliberately: the number that governs
  this is selected-vs-unselected, not selected-vs-background, and `#BDCB9F`
  sits at 1.3:1 against the unselected chip `#EBDDC5`. The two states are
  therefore separated mostly by hue, which is the distinction red-green
  colourblindness collapses. `#8FA36C` (2.1:1) was tried and judged too heavy.
  If this ever needs fixing without darkening the fill, give the selected chip
  an outline rather than a stronger colour.
- The switch track has its own `switchTrack` / `switchThumb` pair too, wired
  through the scheme's primary/onPrimary so both switches in the app follow it.
  Dawn's track is `#8FA36C` — the same green rejected as too heavy for the
  chips. That is not an inconsistency: a 44 dp chip and a 32 dp track need
  different weights to read as the same intensity, and `#BDCB9F` on a track
  under a near-white thumb leaves the control with no definition at all.
  Three green pairs now exist — lamp, chip fill, switch — so reharmonising the
  greens means touching all three.
- The state lamp has its own `lampOn` / `lampOff` tokens rather than reusing
  `stateOn`. A fill has to be dark in Dawn so `onState` reads on top of it; a
  lamp carries no text, so at 10 dp it needs chroma instead of value contrast
  or it reads as a dark speck rather than a colour.
- Dawn deepens while running rather than lifting. "Deeper" is not "brighter".
- Bedtime and Wake sit ABOVE the dial and share a top edge. The brief put them
  below it, stepped by 18 dp, reading as a passage. Below the dial they are
  under your hand for the whole drag, so the one thing you cannot see is the
  value you are setting. Once above it, the step just read as lopsided.
  Confirmed on hardware 29 Aug 2026: the hand no longer covers them.
- The pair is set off by moon and sun glyphs on the labels plus one shallow
  "crown" arc beneath, not by two per-column rules. Colour alone made the wake
  side something you had to learn; the glyphs are the marks already on the ring,
  and they survive a dimmed screen and a colourblind reader. The crown also
  works: one tick per whole hour makes the window countable at rest, and inside
  the window it splits spent from remaining with a marker at now — the same
  grammar the ring uses, so the arc between the moon and the sun reads as the
  distance still to go. Drawn on `insideWindow`, not `runningNow`: unarmed, the
  marker goes hollow rather than vanishing, so you can still see where you
  would be. That is deliberately unlike the ring, which shows progress only
  while armed.
- The time picker wears the handle being edited — night for bedtime, dawn for
  wake, in the dial's own handle inks. The dialog has no title, so colour is
  what tells you which of the two adjacent numbers you tapped.
- The dial centre is tappable and cycles through whatever is true right now:
  running, time-left ↔ total window; scheduled, total window ↔ time until
  bedtime; off, one reading and no tap. Two dots under the label carry the
  affordance, since a tappable numeral with nothing to say so is a secret. The
  choice is deliberately not persisted — each visit opens on the most useful
  reading for the current state. Tap advances; a horizontal swipe moves either
  way. Both live in the dial's own gesture layer as a circle of radius
  `R_WELL_TOUCH`, which is also the drag handler's dead zone — one boundary, so
  no band belongs to both the readout and the handles, and none to neither. A
  rectangular hit area was tried first and does not work here: wide enough for
  the numeral puts its corners into the ring, where they eat handle drags.
  Only horizontal movement is consumed, so a vertical drag over the centre
  still scrolls the screen.
- The day row is present in every state and can be emptied. It used to vanish
  once bedtime started, which reads as a bug, and the last selected day refused
  to be deselected — a buzz with no reason given. Both were guarding the
  truncation bug above, fixed at the source now.
- **No days chosen means the window runs once, then the app switches itself
  off** — the convention alarm clocks use for a non-repeating alarm (Google
  Clock, iOS, Alarmy). Empty first meant "no nights scheduled", which produced
  a dead state: switch on, lamp green, nothing scheduled, ever. "On, but
  never" is a promise the app cannot keep. `Scheduler.isOneOff(days)` is the
  single test — a one-off queues no following START, and `BedtimeReceiver`
  clears `enabled` when its END fires. `liveWindowEnd` only treats a one-off
  as running while the switch is on, or the dial would draw a phantom window
  on every day of the week. This is also the brief's unbuilt "Turn on tonight"
  behaviour arriving early, without its CTA.
- The day row carries state in shape as well as colour: selected is a filled
  circle, unselected a hollow ring. The two fills sit at 1.3:1 against each
  other (see the selection-fill note above), so colour alone was not carrying
  it. Same filled-vs-hollow the lamp and the crown marker use. Labels are two
  letters off `TextStyle.SHORT`, not `NARROW` — narrow repeats itself as
  M T W T F S S. Each day is `toggleable` with `Role.Checkbox` and the full day
  name as its content description, so TalkBack no longer announces a bare "T".
- **The day chips are MORNINGS, and that is what is stored.** Two earlier
  attempts had them mean the evening a window starts on, which is the natural
  reading of "start + duration" but not of the control: ask for the weekend on
  an overnight schedule and Fri+Sat light up, which reads as a bug. Checked
  first — Google Clock sidesteps the question entirely by giving bedtime and
  wake-up separate day pickers, and Apple does not document it at all, so there
  is no convention to follow. Storing the morning is what makes the chips match
  what a user is actually choosing ("do not wake me on Saturday"), and it stays
  stable when a window stops crossing midnight, which deriving-for-display does
  not. `Prefs.init` migrates the old meaning once, behind `daysAreMornings`.
  Presets are then the plain calendar sets. When the window crosses midnight
  the row is captioned "Bedtime starts the evening before", which is the only
  thing left to explain. A fourth preset, "Once", clears the row — it is the
  single-tap way to reach the one-off, and the only way to deselect every day
  without seven taps. At the effect chips' 14 dp side padding the four come to
  355 dp against 311 dp of content width, hence `EffectChip(compact = true)` at
  8 dp — which fits them with "Every night" intact. The effect chips below have
  room to spare and keep the roomier padding. The chip and the card badge both
  say "Once": they name the same thing, so they use the same word.
- **The dial is a 24-hour face, not the brief's 12-hour clock.** A 12-hour face
  cannot draw a window longer than 12 hours: the arc laps itself and the wake
  handle lands mid-arc, which is what a >12 h window produced, because
  `MAX_WINDOW` was enforced in the drag handler and not in the time picker.
  Apple's Sleep ring is a day for the same reason. Two things fell out of it:
  `timeAt` no longer has to guess between two candidate times per angle, so a
  handle can now be dragged straight across noon (it could not before), and the
  twelve tick marks became two-hourly with the emphasised ones landing on
  midnight, 06:00, noon and 18:00 rather than nothing in particular. The cost
  is angular resolution — a 5-minute step is 1.25 degrees instead of 2.5.
- Blocks are grouped by whitespace and named, not boxed. Material's own order
  is whitespace, then dividers, then cards, with cards reserved for groupings
  that need "more separation than whitespace or dividers can provide" — and the
  screen was using none of it: `spacedBy` gave one 14 dp gap to everything, so
  the day row sat as close to the effects as the times sit to the dial. `GROUP`
  28 dp / `TIGHT` 10 dp now, plus `DIAL_TRIM`, which takes 18 dp off each end
  of the dial's *layout* box — its canvas is 260 dp but the ring stops at a
  105 dp radius, and that empty margin was adding to its neighbours' gaps
  rather than absorbing them. Whitespace alone still could not group the window
  block (measured ~30 dp inside against ~35 dp between, where proximity needs
  nearer 3x), because the dial is an airy object and no gap tuning makes its
  neighbours look attached; the tick ring sits only 5 dp inside the canvas
  edge, so trimming further collides with it. Hence `SectionLabel` on the two
  control groups. The window block deliberately has none: BEDTIME and WAKE UP
  are `labelSmall` overlines themselves, so a third above them reads as a
  stutter rather than a hierarchy, and `onSurfaceMid` cannot differentiate it
  because Dawn defines it identically to `onSurfaceLow`.
- Whitespace and labels together still were not enough, so `SectionRule` — a
  1 dp full-width line in `line` — sits between blocks, with `GROUP` dropped to
  18 dp either side of it. Mocked up against filled cards, outlined cards,
  cards on the control groups only, and full-bleed bands; rules won on being
  the only option that separates without boxing the dial or eating horizontal
  space. Cards were the real cost: the day row and the preset chips are both at
  the width limit already, so any container with inner padding would have
  forced them to shrink or the card to bleed past the margin. Measured 1.9:1 on
  Dusk and 1.6:1 on Dawn — faint by design, but `veil` would have been invisible
  (see above).
- **Four text sizes: 36 numerals, 19 titles, 16 row titles, 14 everything at
  reading size, 11 overlines.** Every adjacent step is at least 14%, which is
  about where a size difference starts reading as hierarchy rather than as a
  mistake. Three pairs used to be under 10%: the dial centre at 36 against the
  two window times at 34, same family, same weight, inches apart on one screen;
  body 14 against subtitles 13; and later row titles 15 against body 14, which
  would have put a title 1sp above its own subtitle. The times now share the
  numeral role, and 14 carries prose, subtitles, chips and buttons alike, split
  by WEIGHT (Figtree 400 for prose, 600 for chips and buttons) and by colour
  (`onSurfaceLow` for anything secondary) rather than by a 1sp difference nobody
  can see. Three weights (400, 600, 700) and two families — Baloo for numerals
  and titles, Figtree for the rest.

  Growing the 600-weight labels from 13 to 14 was the risky one, because the
  preset row and the day row are both at the content-width limit. Measured on
  a 359dp screen: the four presets still end at 326.9dp, exactly where they did
  at 13sp, because the row is `SpaceBetween` and the gaps absorbed it — 24dp
  down to 17dp. There is no room left there. Any further growth of a chip
  label, or a longer word than "Every night", needs the padding cut first. There is not one `fontSize` or `fontWeight` override outside `Theme.kt`,
  and it should stay that way; the one deliberate borrow is the allowlist's `›`,
  which takes `titleLarge` for its size because there is no chevron drawable and
  at label size it vanishes.
- **`raise` carries every container in the app** — both home cards, the
  permission panel, both dialogs, the choice sheets, every allowlist row, every
  unselected chip, and the dial's own track — and it is wired into Material's
  `surfaceVariant`, `secondaryContainer`, `tertiaryContainer` and the
  `surfaceContainer` ladder, so a component that reaches for a container gets the
  same colour. One token moves all of them, which is the point. It sat at 1.13:1
  against the page in Dawn and 1.15:1 in Dusk, which is below where the eye
  resolves a surface as a separate plane at all — the cards did not read as
  cards. Now `#E2D5BD` at 1.22:1 in Dawn and `#29323D` at 1.40:1 in Dusk. The two
  are deliberately NOT the same ratio: 1.40 was tried in both and judged too dark
  on the cream, where a container that reads as a plane starts competing with the
  dial, while on the near-black it reads as exactly right.
  The number to watch when changing either is the RUNNING state, not the resting
  one. Dawn deepens to `surfaceRunning` while bedtime is on, which costs about
  0.07 of whatever separation is chosen — and that is the state the screen is in
  for most of the night. The old `#EBDDC5` fell to 1.07:1 there, which is
  nothing; 1.22 holds 1.15. That is deliberately near the floor — 1.40 and 1.28
  were both tried on the device first and judged too heavy on the cream, where a
  container that reads as a distinct plane competes with the dial. Chosen by eye
  on real hardware in real light, which is the only way this particular call can
  be made; the numbers here are the record, not the reason. If it ever needs to
  go lower, it cannot: `#EBDDC5` at 1.13/1.07 was the starting point and was
  invisible.
  The unselected DAY circles are the one deliberate exception: no fill at all, a
  hollow ring in `line`. That is the filled-vs-hollow grammar described below and
  it stays.
- **The master card's status line is capped at one line, and that is load-bearing.**
  It changes on every drag step, so a language where it wraps makes the card grow
  and shrink under the finger dragging the dial - the whole page below it moves.
  Measured: the Ukrainian «Почнеться через %1$s» needed more than the ~190dp the
  line has beside the switch and wrapped to two, putting the BEDTIME overline
  20dp lower than in English. Russian was one value away from the same fault.
  Both are «Через %1$s» now, and `maxLines = 1` makes the height a constant
  rather than a hope. `BUDGET-EACH` in `values/strings.xml` holds the limit, and
  `check_translation.py` fails anything longer - because past this width the text
  no longer wraps, it truncates. Note the dial itself does no work while
  dragging: `commit()` runs on `onDragFinished`, so the stutter was never
  computation, only relayout.
- Do Not Disturb is not one of the screen effects. It was a chip beside
  grayscale and wallpaper dimming while the screen that configures it sat in
  another section — and turning the chip off left that screen fully live but
  inert, because the filter had become `INTERRUPTION_FILTER_ALL`. The switch
  and what it governs are now one card. The allowlist screen groups People
  before everything else, which is how both Android's DND settings and iOS
  Focus split it, with repeat callers inside People where both platforms put
  it. Calls and Messages have independent scopes, as `ZenPolicy` models them;
  they were previously written by two paths that disagreed. Two things it
  fixed that nobody had reported: media sounds could never be silenced (the
  pref existed, defaulted on, and no UI wrote it), and the alarms row claimed
  "Android won't let an app silence these", which is false — `ZenPolicy` will
  silence alarms if asked. We do not ask, because an app that can mute your
  morning alarm is a footgun, and the row now says that instead.
- Every switch toggles from anywhere on its row: `Modifier.toggleable` with
  `Role.Switch` on the row, `onCheckedChange = null` on the `Switch` itself, so
  the switch is the indicator and not a second target competing for the taps
  people aim most carefully. One semantics node too, so TalkBack reads the row
  once. The master switch routes through a single `setBedtime()` so the row and
  the switch cannot diverge — including the confirm dialog while running.
- Haptics are one effect per KIND of interaction, listed at the top of
  `Haptics.kt`: toggle, select, open, confirm, plus the dial's own drag
  vocabulary. Presets and the centre readout used to fire `toggle(true)` though
  neither is a toggle, and sheet options fired `confirm`, so choosing an option
  felt exactly like committing a destructive dialog.
- The home screen's `ScrollState` lives in `Root`, not `Home`. `Root` swaps the
  two screens, so `Home` is disposed while the allowlist is open and came back
  scrolled to the top. Nothing else is hoisted: the rest of `Home` re-reads
  prefs on return, which is what should happen.
- One numeral size in the dial centre, in every state. The brief had a 44 sp
  "countdown hero" while running and 36 sp at rest; the jump was visible as the
  window opened. 36 sp also fits the worst case — "11h 59m" is seven glyphs and
  a window runs to twelve hours, which at 44 sp left ~11 dp before the centre
  well, a margin a larger system font scale would eat. `displayMedium` is gone
  from the type scale; the dial was its only caller.
- One now-marker form in every state; the brief switches shape inside the window.
  It is a small filled caret in the lane between the track and the hour ticks,
  not a hand. The hand ran from inside the centre well out past the ring,
  colliding with the numeral, the arc and the ticks in one stroke. A gap cut
  through the track was tried first and rejected on test: an absence in a
  continuous band reads as a rendering bug, whatever you put in it. A solid
  shape cannot. It is lightly round-joined because everything else on the dial
  is round-capped, and the join is kept at 1.2 dp — thicker blunts the apex,
  which is the corner doing the pointing. Base 6.5 dp, length 10.5 dp: the base
  is an arc chord, so `dAng × radius` decides its width, and a value that makes
  the triangle wider than it is long has no point at all.
