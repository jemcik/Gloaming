# Gloaming

Android bedtime app. Schedules Do Not Disturb, grayscale and wallpaper dimming
for a nightly sleep window.

This file is the working reference: what the app is, what the rules are, and
which traps will bite. **The evidence lives in [docs/DECISIONS.md](docs/DECISIONS.md)**
— every measurement, every wrong first attempt, every superseded constraint and
why it expired. When something here says "measured", that is where the numbers
are. Read it before overturning anything; a good half of the entries there exist
because a reasonable-looking change had already been tried and had failed.

## Why it exists

Google's Digital Wellbeing bedtime mode never fires on Honor/MagicOS phones. Its
`WindDownBedtimeSynchronizerWorker` is a WorkManager job, and MagicOS adds an
undocumented JobScheduler constraint that is never satisfied, so the job never
runs and bedtime only triggers when the app is foregrounded.

`setExactAndAllowWhileIdle` is **not** gated by that constraint. Gloaming uses
exact alarms to flip the zen rule itself: measured firing within ~150 ms with
the app closed and the screen off, with no battery whitelist and standby bucket
10 — the real user path, not a privileged one. **Screen off is not doze**, so
that measurement never covered the state the app spends the night in; the
overnight path was measured separately on 1 Sep 2026 by forcing light and deep
idle, and fired at the scheduled second in both. What DOES hold an alarm
indefinitely is background restriction — see `core/BackgroundLimit.kt`.

## Architecture

    MainActivity.kt              the Activity, the theme decision, and Root.
                                 87 lines, and it should stay small

    core/Scheduler.kt            exact alarms. A window is start + duration,
                                 never two independent times. `endAt` is AOSP's
                                 exitAtAlarm rule, copied not invented: the
                                 morning alarm ends the night only when it falls
                                 INSIDE the window. The day-of-week
                                 selection is the MORNING a window ENDS on, and
                                 Scheduler works backwards to the evening that
                                 reaches it. `endingAlarm` is the gate the alarm
                                 passes through - the next alarm, but only where
                                 the switch lets it act. It is one line and it
                                 has a name because six callers were writing it
                                 out and two forgot
    core/Bedtime.kt              the master switch, as one function. The tile
                                 has no HomeState to borrow, so `set` and
                                 `runningNow` live here and both callers share
                                 them rather than agreeing by coincidence
    core/ZenController.kt        owns the AutomaticZenRule: policy + device
                                 effects, plus reconcile and the orphan sweep
    core/BedtimeReceiver.kt      START / END / BOOT_COMPLETED /
                                 MY_PACKAGE_REPLACED
    core/ZenStatusReceiver.kt    the platform's hint that our rule changed; a
                                 hint only, we decide from getAutomaticZenRule
    core/BootWatch.kt            detects a reboot whose broadcast never arrived
    core/Doors.kt                the system screens we can send someone to, and
                                 whether they exist here. Capability probes, so
                                 a door that opens onto nothing is never drawn
    core/Delivery.kt             the one rule AlarmWatch and BackgroundProbe
                                 share: delivered means ARRIVED ON TIME
    core/ScreenEffects.kt        does this phone APPLY the rule's device
                                 effects. The one question here with no probe,
                                 so a manufacturer prior that a measured
                                 transition can overrule
    core/BackgroundProbe.kt      one throwaway alarm that asks whether this
                                 phone delivers alarms at all. Silent unless
                                 the answer is no
    core/AlarmWatch.kt           did our own END actually arrive? The backstop
                                 for every cause BackgroundLimit cannot see - a
                                 frozen app misses its alarm with the appop still
                                 reading `allow`
    core/BackgroundLimit.kt      the one vendor restriction that can be READ:
                                 isBackgroundRestricted. Off, the phone parks
                                 our alarms until the app is next opened
    core/Interruptions.kt        the allowlist as a sentence
    core/AmbientControl.kt       the vendor's own always-on keys. TWO routes:
                                 Honor's in Settings.Secure behind an adb-only
                                 grant, Samsung's in Settings.System behind
                                 WRITE_SETTINGS, which the user can grant
    core/AmbientCapability.kt    can this phone hide its always-on display
    core/Clock.kt                clock times in the phone's own 12/24 format
    core/Prefs.kt                SharedPreferences, plus one migration
    core/Reset.kt                back to a fresh install, in the one ORDER that
                                 is safe. Android's own "Clear storage" is the
                                 trap it exists to replace: it wipes the prefs
                                 and leaves the rule behind, live and with no id
                                 left to remove it by
    core/Diagnostics.kt          the phone's whole answer, as text to send in
                                 one tap. The system's account and ours kept
                                 APART, in that order - every bug worth having
                                 this for lives in the gap between them. It
                                 cannot say whether a notification made a SOUND
    core/Journal.kt              on-device log; read it over adb, see Build
    core/SystemTheme.kt          the system's own light/dark answer

    ui/HomeState.kt              everything Home remembers, and the four things
                                 it can do about it: commit, setBedtime, the
                                 ON_RESUME re-read, and the re-arm on first
                                 composition. The screen's state lives here so
                                 a section is one parameter, not fifteen
    ui/HomeScreen.kt             the home screen. `Home` itself is the page
                                 ORDER and nothing else; each section below it
                                 is its own composable and decides for itself
                                 whether it draws
    ui/BedtimeTile.kt            the master switch in the shade. THREE states,
                                 not two: an hourglass while armed, a tick while
                                 a window is running - the same two faces the
                                 app bar's switch wears, and in that order,
                                 because a tick sitting there all evening while
                                 bedtime did nothing is what a tick must not mean
    ui/HomeParts.kt              what only Home draws — status pill, notice
                                 strip, day row, numerals, moon and sun glyphs
    ui/Sentences.kt              the schedule as language: windowSentence,
                                 planNote, dayWord, span, hhmm. No Compose
                                 state, no side effects — the testable part
    ui/InterruptionsScreen.kt    the allowlist
    ui/SettingsScreen.kt         theme mode, a link to the system language
                                 picker, and - where one resolves - a permanent,
                                 quiet link to the vendor's launch manager
    ui/BedtimeDial.kt            24-hour dial, draggable handles, sweep gradient
    ui/Rows.kt                   the app's one list row, on M3's ListItem:
                                 SwitchRow / LinkRow / StaticRow / ActionRow /
                                 RadioRow
    ui/Section.kt                section rhythm, GroupedList, DetailScaffold
    ui/Theme.kt                  Dusk/Dawn tokens, type scale, GloamSwitch, Arc
    ui/IconTint.kt               one colour per row icon
    ui/Haptics.kt                one effect per KIND of interaction

The rule holds the policy; we hold the trigger. `component=null` on the rule is
deliberate — there is no condition provider, which is what avoids the blocked
path.

## Rules

Things that will break something if ignored. Each is short here; the measurement
is in DECISIONS.md.

**Zen and scheduling**

- The alarm-shortened end belongs in the WINDOW calculation, not in the END alarm.
  Shortening only the alarm leaves the window still containing `now`, so the next
  reschedule walks back into the night and switches zen on again. `SchedulerTest`
  pins it.

- **The wake handle and "end at your alarm" are ONE state.** On means the wake
  time EQUALS the alarm: switching on moves the handle there, and setting the
  handle there switches it on. Two controls for one value is what produced three
  separate "it is lying" reports - the screen held a wake time of 8:30 and an
  effective end of 7:30 at once and had to show both somewhere. `commitWake` is
  deliberately NOT folded into `commit`: the switch commits too, and re-deriving
  there reads "the handle still equals the alarm" one instant after the user
  switched it OFF and turns it straight back on.
- **Every number that describes TONIGHT takes the alarm-shortened end**, and the
  list is longer than it looks: the wake numeral, the arc, the handle, the
  countdown, its caption, the sleep-window total, the sentence, the row and the
  app bar. It reached the phone twice with some of them converted and some not,
  and a screen answering one question two ways is worse than either answer being
  wrong. `endsTonight` is derived ONCE in `WindowBlock` for that reason. The wake
  handle itself still shows the SETTING while it is being dragged, or it freezes
  under the finger - the alarm has not moved, so the redraw would not follow.
- A rule carries a **`conditionOverride`** as well as a condition, and it wins.
  AOSP's `setManualZenMode` stamps `OVERRIDE_DEACTIVATE` on every active rule
  whenever zen goes off other than by the user in SystemUI — **a reboot
  qualifies**. The rule then reads `STATE_TRUE` in the config, reports
  `STATE_FALSE` through `getAutomaticZenRuleState`, and filters nothing. Pushing
  `STATE_TRUE` does not help: `reconsiderConditionOverride` drops an
  `OVERRIDE_DEACTIVATE` only when the condition goes **FALSE**. Push FALSE then
  TRUE. Measured 1 Sep 2026; it cost a whole window and looked like nothing was
  wrong.
- `updateAutomaticZenRule` **clears the rule's condition**, so rewriting a live
  rule switches it off. Any path that rewrites mid-window must re-assert the
  state afterwards, and `setActive` must decide by asking
  `getAutomaticZenRuleState` — never by what it last wrote.
- Every rewrite of a live rule is **visible**: zen genuinely goes off and on and
  the system re-posts its "Do Not Disturb is on" notification. Rewrite less.
  Nothing but a real difference should push the rule.
- Pushing an **identical** rule re-applies its device effects. `Prefs.ruleSignature`
  gates that; `force = true` exists for alarms and boot.
- The seven **visual effects are pinned, not inherited** — an unset one is filled
  from the phone's own default DND policy, and that default is NOT the same
  everywhere: measured 156 on MagicOS and LineageOS but **20** on One UI 8, which
  left our rule permitting the notification light and the always-on display at
  3am. Three of the seven (`statusBar`, `badge`, `notificationList`) are **inert**
  on Android 16 — the record carries `suppressedVisualEffects=511` and both
  SystemUIs draw the notification anyway — so they must NEVER get a switch, and
  nothing may claim bedtime hides a notification. Google's Bedtime pins all seven
  too, and is equally unable to hide one.
- `Prefs.ruleId` is the app's only handle on its rule. Losing it strands the rule
  forever — hence `sweepOrphans`, which runs after `addAutomaticZenRule` and in
  `reconcile`. It must NOT return early when `ruleId` is null: that is exactly
  the case where every rule is an orphan, and returning left a live rule greying
  the screen with nothing on screen to explain it.
- `Prefs.activeDay` pins only the DATE a night began on. Pinning an instant
  breaks a handle; this was got wrong twice, in opposite directions.
- Log what a call **answered**, not that it did not throw.
  `removeAutomaticZenRule` returns a boolean.

**Platform**

- **Logcat is encrypted for THIRD-PARTY app logs** on MagicOS — our own lines
  never appear, which is what `Journal` exists for. But SYSTEM and VENDOR tags
  are readable and worth reaching for: `HnAOD`, `DozeService` and
  `HWPowerManger_JNI` print plainly, and reading them is what settled how
  Honor's always-on behaves when guessing had failed for two days. Do not read
  the first sentence as "logcat is useless here". Never swallow an exception.
- **`run-as` can read this app's data but not write it** — on any Android, not
  just MagicOS. A setting can only be corrected through the UI. And it does not
  work at all on a RELEASE build, so `check.sh` loses its whole app-side half
  against a published APK; it now says so rather than printing placeholders.
- **MagicOS withholds `ACTION_BOOT_COMPLETED`** unless the app is set to
  auto-launch. `BootWatch` detects the symptom rather than the vendor.
- Samsung's always-on display IS controllable in-app: its AOD keys are in
  `Settings.System`, so WRITE_SETTINGS - user-grantable - is enough. Grayscale,
  wallpaper dim and dark theme are NOT, and each was chased to a measured dead
  end; see DECISIONS before trying again.
- The screen-effects section is HIDDEN where the phone throws the effects away
  (`ScreenEffects`). A switch that lies is worse than an absent one. This is the
  one manufacturer test besides `AmbientControl`, and only because the readbacks
  are `@hide`: `isSaturationActivated` and `getWallpaperDimAmount` both fail to
  compile. It expires by itself - night mode observed going OFF→ON in step with
  our rule overrules the prior for good. Watch the TRANSITION, never the state:
  the first version asked "is it dark while we want dark", answered yes on a
  phone that was simply always dark, and un-hid the broken switches.
- **One UI 8 stores `ZenDeviceEffects` and applies none of them** - grayscale and
  night mode both, across a screen-off cycle. Not a capability gap: One UI's own
  Sleep mode drives the same `Global saturation`. `BootWatch.hasSystemBedtime`
  offers the system's own bedtime screen where it resolves, which is the only
  working route to a grey screen there. Samsung's Routines SDK was tried and its
  discovery is closed to non-privileged apps.
- Honor's auto-launch and run-in-background states are **unreadable** — absent
  from settings, appops and the package dump, measured either side of a clean
  toggle. Run-in-background is answered by `BackgroundProbe` instead, whose
  verdict is LATENESS not arrival, because a parked alarm is still delivered the
  moment the app is opened;
  auto-launch only by a real reboot, after the fact, in `BootWatch`.
- minSdk is **35** because `ZenDeviceEffects`, `AutomaticZenRule.Builder` and
  `getAutomaticZenRuleState` are all API 35, and a missing method raises `Error`,
  which none of the `runCatching` here would catch.
- `am broadcast` cannot reach `BedtimeReceiver` — it is `exported="false"`,
  correctly. Test through real alarms.
- `dumpsys notification` prints a `Zen Log:` history as well as live config, so
  `sed '/Zen Log:/q'` before grepping or long-deleted rules read as present.
  It also prints the live config TWICE, so count rules by id, not occurrence.
  The live config holds SEVERAL `ZenRule[` records and ours is rarely first, so
  a pattern that runs `.*?` from `ZenRule[` to `name=Gloaming` reads id, state
  and enabled off a stranger's rule while still picking up OUR effects and
  caption. Split on `ZenRule[` and take the chunk carrying `name=Gloaming`.

**Compose**

- Compose state goes stale FOUR ways: the receiver writes prefs while
  backgrounded (re-read on `ON_RESUME`), `now` goes stale on an open screen (one-
  minute ticker), `pointerInput(Unit)` freezes its captured lambdas (wrap in
  `rememberUpdatedState`), and a **SYSTEM setting read inside a skippable
  composable never re-runs**. `WindowTime` took `(ctx, time, colour)` and called
  `Clock.reading` itself; when the phone's 12/24-hour setting changed under an
  open screen none of those three moved, so Compose skipped it - while the
  sentence under the dial, which is called straight from `WindowBlock`'s body,
  re-executed. The screen read "20:40" above "From 8:40 PM". Read a system
  setting where the ticker is, inside `remember(s.tick, ...)`, and pass the
  ANSWER down.
- Put a `@Composable` slot **before** any click lambda, or a trailing lambda at
  the call site binds to the slot and Compose runs it as content.

- **A draggable control inside the scrolling page must HIT-TEST, and must not
  use `detectDragGestures`.** That helper claims the gesture in any direction
  once slop is crossed, so a vertical swipe over it is taken from the page. The
  dial also grabbed "anywhere outside the centre well" - most of a 260dp square,
  corners included - and handed it to whichever handle was angularly nearest, so
  scrolling Home changed the schedule. It is `awaitEachGesture` now: decide from
  the DOWN position, within `GRAB` of a handle, and consume only once it is
  ours. The centre well already worked this way; the handles were the outlier.
- An M3 `ListItem` top-aligns trailing content on a **three-line** item. Keep
  rows to two lines; `RowFitTest` enforces it by measuring.
- `clip()` on a container under ~40dp tall eats content that touches the edge.
- Any `Typography` role left unset falls back to Roboto; any `ColorScheme` role
  left unset falls back to Material's baseline violet. All are named.
- **If state is visible, it must be in the semantics.** Read it off the device,
  not the source. This has been got wrong three times.

**Design**

- **Severity must match confidence.** A red notice claims a MEASUREMENT. Where
  the thing cannot be measured - the vendor launch switches - the honest form is
  an offer that can be refused: `TipCard`, plain icon, two text buttons, shown
  once. The version that showed a fault card to every phone with a launch
  manager was this app's worst UX failure, because it could not be cleared by
  fixing anything and taught the user to ignore the real warnings too.
- Anything the user might want to reach on purpose needs a door that is not a
  notice. Until Settings grew its `this phone` link, the only route to the
  launch manager was being told something was broken.

- **Going to look is not an answer.** An offer is closed by its REFUSAL only.
  The tip used to close itself when "Set up" was pressed, so opening the vendor
  screen, changing nothing and pressing back dismissed it for good - and since
  Honor's auto-launch state is unreadable, the app could never discover it had
  guessed wrong. Never let "the user saw the screen" stand in for "the user did
  the thing", least of all where the thing cannot be read back.

- There is not one `fontSize` or `fontWeight` override outside `Theme.kt`. Keep
  it that way.
- Tabular figures (`tnum`) belong on the **display** family only. Numerals get
  them; sentences do not.
- **A screenshot cannot settle a colour on this phone.** The panel runs a vendor
  colour mode with a warm correction, so a capture may COMPARE two states and may
  never establish that a colour is right. Hue and chroma go on the panel.
- For any shape that is not roughly convex, **measure the ink** — centroid and
  the above/below split — not the bounding box, and not one component's centroid.
- Icon geometry is craft and already done: `res/drawable/ic_*.xml` are Google's
  own paths. Do not hand-draw icons.

## Build

    ./tools/build.sh     debug APK, full log at /tmp/build.log
    ./tools/deploy.sh    install only. REFUSES when the APK is older than the
                         sources - `gradlew test`/`lint`/`check` all compile
                         without assembling, so a green run can leave a stale
                         APK and the screenshot proving your fix shows the
                         previous build. `-f` installs it anyway
    ./tools/check.sh     what the PHONE thinks: zen state, the rule, our prefs,
                         the next alarms, the journal. Read-only
    ./gradlew test       the whole suite, on the JVM, in seconds
    ./gradlew coverage   JaCoCo, HTML + XML under app/build/reports/jacoco
    ./gradlew lint       0 errors; 1 finding left is policy (targetSdk currency)
    python3 tools/check_translation.py app/src/main/res/values-ru/strings.xml ru
    python3 tools/render_icon.py        re-render docs/icon.png from the drawable
    adb shell run-as com.jemcik.gloaming cat files/journal.log

Unit tests run on **JDK 21**, pinned in `app/build.gradle.kts`. The reason —
Robolectric's ASM choking on Java 25 class files — is probably obsolete since
4.16.1 ships ASM 9.8, which reads them. The pin stays because it cannot be
disproved here: this machine has only a 21 JDK, so removing it passes for the
wrong reason, and both CI workflows pin 21 explicitly.

A pre-push hook runs tests, lint and both translation checkers. A fresh clone
opts in once:

    git config core.hooksPath tools/hooks

Releases are `.github/workflows/release.yml`: tag `MAJOR.MINOR[.PATCH]` or press
"Create a new release". CI derives `versionCode`/`versionName` from the tag and
**signs from four repository secrets**; with no keystore the build is left
unsigned rather than falling back to the debug key, and the workflow refuses to
publish an APK it cannot verify. A stable signing identity is half of an
upgradable APK — 0.1 and 0.2 shipped under throwaway debug keys and are
permanently stranded.

Toolchain: AGP 9.3.2, Gradle 9.7.1, Compose compiler 2.3.21, BOM 2026.08.00,
compileSdk 37, targetSdk 36, minSdk 35.

## Tests

`app/src/test/`, 167 cases, no device. They are written as the QUESTION the code
answers rather than as coverage of a method, because none of the bugs were ever
in a method — they were in an assumption.

    SchedulerTest         the scheduling core: midnight wrap, days-as-mornings,
                          the one-off, the activeDay pin, and `endAt` - the
                          alarm ends the night only from INSIDE the window
    SentencesTest         the sentence builders, driven directly
    WindowSentenceTest    the window in words, per locale, through Home, plus
                          the alarm-shortened end, driven through a real
                          setAlarmClock so it exercises getNextAlarmClock
    PlanNoteTest          the "not in effect" note, through Home
    InterruptionsTest     the allowlist sentence, per locale
    ClockTest             12/24-hour formatting
    PrefsMigrationTest    the one-shot days migrations - the only code here
                          that can corrupt data silently. Two of them now: days
                          as mornings, and the screen effects that used to
                          default ON, where an install that predates the change
                          must keep them
    RowFitTest            does the text fit, in en/ru/uk, by MEASURING -
                          Home's rows, the allowlist's, Settings', and the
                          alarm section's row AND heading. AT HOME'S OWN WIDTH:
                          a row built bare gets a 360dp card and the real one
                          is 311, and those 49dp are six characters this test
                          passed for a fortnight
    ScreensTest           interactions, never appearance - including the
                          wake handle and "at your alarm" moving each other,
                          and the dial following a moved alarm rather than the
                          handle left behind
    AlarmWatchTest        did our own END arrive, and ON TIME - the case the
                          notice exists for, which it could not report
    BootWatchTest         the withheld-boot detection
    BackgroundProbeTest   the delivery probe: lateness rather than arrival is
                          the verdict, and the latch that stops its own retest
                          erasing it
    ResetTest             starting over: no rule survives - not even one whose
                          id was already lost - the store comes back EMPTY so
                          the next launch takes the fresh-install branch and not
                          the upgrade one, and the journal outlives it
    DiagnosticsTest       the report a user sends: does it still speak when the
                          phone answers nothing, is a refused lookup kept
                          distinct from a deleted rule, and does the system's
                          account stay ahead of - and apart from - our own

Coverage: **76% of instructions, 59% of branches**. The shape is the point — what
is covered is what can be reasoned about without a phone; what is not is what
talks to the platform (`BedtimeTile` 0%, `BedtimeReceiver` 1%,
`AmbientControl` 18%, `Doors` 25%, `Journal` 33%, `ZenController` 71%). That gap
is what the journal is for.

Three things worth knowing before adding tests:

- The scheduling core takes `from: LocalDateTime` and plain values, not a
  `Prefs`. Keep it that way — the moment it needs a `Context` it stops being
  testable in milliseconds.
- **A test that reads the wall clock passes only in the hours it was written
  in.** Suspect any test that calls `LocalTime.now()` and then asserts a fixed
  answer.
- `RowFitTest` needs `setQualifiers` for a width and `@GraphicsMode(NATIVE)`, or
  text measurement is stubbed and every row reports the same number. That number
  looks like data and is not.

## Outstanding

- `note_unscheduled` is **unreachable**: `nextStart` cannot return null. The
  fallback stays because the signature is nullable; see `SentencesTest`.
- `BedtimeTile` is at **0%** and stays there for now. It is a `TileService`, so
  every branch needs a bound `Tile` the framework owns; what it decides -
  `Bedtime.runningNow` for the icon, `statusLine` for the subtitle - is covered
  where those live, and the tile itself is three assignments over them.
- Dynamic colour, Shizuku and a scheduled always-on for Honor are all considered
  and rejected, with reasons, in DECISIONS.md. Read those before proposing them.
