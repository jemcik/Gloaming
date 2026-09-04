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
                                 bedtime did nothing is what a tick must not
                                 mean. NEVER Tile.STATE_UNAVAILABLE: SystemUI
                                 does not dispatch a click to one at all, so a
                                 tap it cannot honour opens the APP instead
    ui/HomeParts.kt              what only Home draws — status pill, notice
                                 strip, day row, numerals, moon and sun glyphs
    ui/Sentences.kt              the schedule as language: windowSentence,
                                 planNote, dayWord, span, hhmm. No Compose
                                 state, no side effects — the testable part
    ui/InterruptionsScreen.kt    the allowlist. Writes prefs per tap, pushes the
                                 RULE 800ms after the last one - a rewrite of a
                                 live rule blinks zen off and on, so six
                                 switches must not cost six. It used to wait for
                                 ON_PAUSE, which is invisible for every row that
                                 governs something not yet happening and wrong
                                 for media, which is audible while you change it
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
- **"Nothing is being filtered" is only a fault where we ASKED to filter.** With
  the Do Not Disturb switch off our rule sets `INTERRUPTION_FILTER_ALL`
  deliberately - it is there to carry device effects and to filter nothing - so
  the stuck-rule check read its own intent back as a fault, never took
  `setActive`'s early return, and rewrote the rule on every call. That loops:
  a rewrite broadcasts a status change, `ZenStatusReceiver` reconciles,
  reconcile reschedules, and rescheduling lands back in `setActive`. Measured
  at one push every 1-4 ms, with grayscale dropping out on a third of the
  samples because each rewrite clears the condition. It looked from outside like
  "the screen effects need Do Not Disturb"; effects have never depended on the
  filter. `looksStuck` takes `wantsDnd` for this reason and `StuckRuleTest`
  pins it.
- `updateAutomaticZenRule` **clears the rule's condition**, so rewriting a live
  rule switches it off. Any path that rewrites mid-window must re-assert the
  state afterwards, and `setActive` must decide by asking
  `getAutomaticZenRuleState` — never by what it last wrote.
- Every rewrite of a live rule is **visible**: zen genuinely goes off and on and
  the system re-posts its "Do Not Disturb is on" notification. Rewrite less.
  Nothing but a real difference should push the rule.
- The caption is **deferred, not dropped**. Skipping a cosmetic-only change to a
  live rule is right; what gets STORED must then be what the rule now holds, or
  the skip is filed as a push and the system's own Do Not Disturb screen keeps
  the old times for good. `carriedSignature` is that distinction, and
  `RuleCaptionTest` pins it.
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
- **A granted-looking DND setting proves nothing.**
  `enabled_notification_policy_access_packages` has listed this package while
  `NotificationManager` still refused every call. `cmd notification allow_dnd`
  took where `settings put` had not, and `settings delete` does not revoke at
  all. Verify by CREATING A RULE and finding it in `dumpsys notification` -
  reading the setting back once cost a whole verification run, in which a
  working fix measured as broken.
  **It goes the other way once the package is GONE.** Cleaning the Honor after
  an uninstall, 4 Sep 2026: `cmd notification disallow_dnd com.jemcik.gloaming`
  did nothing at all - with and without an explicit user id, and silently, no
  error - while `settings put secure enabled_notification_policy_access_packages
  ''` cleared the list at once. The command resolves a package name and there is
  no package left to resolve, so it no-ops on the one occasion you would reach
  for it. The grant OUTLIVES the app: uninstalling took the rule, the data, the
  appop and the AOD keys with it and left this behind, which on the next install
  means the permission card may never appear and a first run is not a first run.
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

- Compose state goes stale FIVE ways: the receiver writes prefs while
  backgrounded (re-read on `ON_RESUME`), `now` goes stale on an open screen (one-
  minute ticker), `pointerInput(Unit)` freezes its captured lambdas (wrap in
  `rememberUpdatedState`), and a **SYSTEM setting read inside a skippable
  composable never re-runs**. `WindowTime` took `(ctx, time, colour)` and called
  `Clock.reading` itself; when the phone's 12/24-hour setting changed under an
  open screen none of those three moved, so Compose skipped it - while the
  sentence under the dial, which is called straight from `WindowBlock`'s body,
  re-executed. The screen read "20:40" above "From 8:40 PM". Read a system
  setting where the ticker is, inside `remember(s.tick, ...)`, and pass the
  ANSWER down. The fifth has no event behind it at all: **the TILE writes prefs
  from over the top of a screen that is never paused.** Pulling the
  quick-settings shade down does not pause what is underneath - measured on the
  Honor, `topResumedActivity` stayed MainActivity throughout - so ON_RESUME
  never fires and nothing else is coming. Watch the WRITE, with
  `Prefs.watch`; and watch one key, because re-reading everything there fights
  the screen's own commits and would re-read the wake handle out from under a
  moving finger.
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

- **Copy may not name an effect as happening.** Which of the four device effects
  a phone applies is the PHONE's decision - One UI 8 stores all four and applies
  none - and the Do Not Disturb switch may be off, in which case the rule filters
  nothing deliberately. So "the grey screen stays on", "a screen that stayed
  colourful all night" and "«Не турбувати» і сірий екран лишаються увімкненими"
  each claim a configuration the reader may not have. Say instead what is true of
  EVERY configuration: bedtime did not end, and whatever it switched on stayed
  on. Three sentences shipped with this fault and were caught in one afternoon on
  4 Sep 2026 - the store listing, the site and the README - which is why this is
  a rule rather than three corrections. The same care the SWITCHES get: a switch
  that lies is not drawn, and a sentence that lies must not be written.

  And say WHY an effect is absent in the user's terms, not the platform's. "The
  effect does nothing on your device" invites the reader to conclude their phone
  cannot do it, which on a Galaxy is the opposite of true: One UI drives the very
  same `Global saturation` from its own Sleep mode, and what is withheld is
  ACCESS - the zen rule's effects are stored and ignored, `Settings.Secure` needs
  a grant no ordinary install has, and the Routines SDK's discovery is closed to
  non-privileged apps. Every route measured, every route shut. So the honest
  sentence is that the OS does not let an APP control it. Naming the app is what
  carries the distinction: the phone can, and only the vendor's own software may.
  This correction came from a Samsung owner reading the copy, against the
  argument that "the device accepts and ignores" was the whole story - it is the
  mechanism, not the consequence, and the consequence is what a listing is for.

- **A pronoun in ru/uk agrees with the NOUN JUST WRITTEN, not with the English
  it came from.** "Gloaming is a general-purpose utility. It is not directed at
  children" went across as «утилита общего назначения. Он не предназначен» -
  «утилита» is feminine and «он» agrees with nothing on the page. The same
  section carried the other half of the fault: «он не собирает их и у них», a
  word-for-word "it collects none from them either", where «их» means the data
  and «у них» means the children, two pronouns with different referents one word
  apart. Caught by a native speaker on the live site, not by any checker -
  `check_translation.py` counts strings and cannot see agreement. So: pick the
  predicate noun for the GENDER the rest of the document already uses
  («інструмент»/«инструмент», masculine, because Gloaming is «Він»/«Он»
  everywhere else), and where English chains pronouns, name the things instead.
  The same sentence pattern one section down had «он» sitting after «приложение»
  (neuter) and «код» (masculine), so it read as the code.

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
    ./tools/battery_bench.py  is anything happening that should not be? Walks
                         every combination of the four switches against a LIVE
                         window with the phone untouched, and reports app CPU
                         jiffies, rule pushes and effect flicker per row. Zero
                         pushes is the pass mark: steady-state traffic with
                         nobody touching the phone is a loop by definition.
                         Debug build only - it reads the store back with run-as
    ./tools/check.sh     what the PHONE thinks: zen state, the rule, our prefs,
                         the next alarms, the journal. Read-only
    ./gradlew test       the whole suite, on the JVM, in seconds
    ./gradlew coverage   JaCoCo, HTML + XML under app/build/reports/jacoco
    ./gradlew lint       0 errors; 2 findings left are policy (targetSdk currency,
                         and one ModifierParameter in BedtimeDial)
    python3 tools/check_translation.py app/src/main/res/values-ru/strings.xml ru
    python3 tools/render_icon.py        re-render docs/icon.png from the drawable
    python3 tools/play_assets.py        the Play store assets into docs/play/.
                         Play refuses what the README needs: an alpha channel on
                         the icon. Same geometry, `mask=False`, and Play applies
                         its own corner mask. Screenshots come out TWICE, both
                         at an EXACT 9:16 - Play enforces the ratio, it is not
                         guidance: a 1256x3108 captioned shot is 1:2.47 and the
                         Console answers "needs cropping", which on a phone
                         screenshot means losing the caption or the top of the
                         screen. Padding the SIDES costs nothing, so the canvas
                         is the smallest exact 9:16 that holds the shot, snapped
                         to a multiple of 9 so the height is integral - 1755x3120
                         captioned, 1584x2816 plain. Neither set crops: the
                         README's framing is what the app looks like, and
                         cropping to fit a store makes the two disagree.
                         No device frames - a bezel encodes nothing true and
                         costs pixels these screens need. A set PER LANGUAGE
                         into `<lang>/`, captions and all: Play carries one per
                         listing, and Ukrainian captions over Ukrainian screens
                         is the whole reason the shoot went trilingual. PIL does
                         NOT fall back per glyph the way the site's browser
                         does, so Figtree - Latin only - draws every Cyrillic
                         letter as a box; the Slavic bands are set in San
                         Francisco instead, at GRAD 620, which is how a face
                         with no Weight axis is made to match Figtree 600
                         without moving where the line wraps
    python3 tools/build_site.py         the SITE into docs/, for GitHub Pages.
                         Six pages - landing and privacy policy, in en/uk/ru -
                         from one template each, because six hand-written files
                         drift and a missing translation should be a KeyError at
                         build time rather than a hole on a live page. Palette
                         and type come from Theme.kt. NEITHER BUNDLED FONT HAS
                         CYRILLIC (figtree 391 codepoints, baloo2 856, Latin
                         both), so the stack names Golos Text after Figtree and
                         the browser falls back per glyph. docs/privacy-policy
                         .html is the URL given to Google Play - it must keep
                         its name and stay at the root
    python3 -m http.server 8765 --directory docs    preview it
    tools/shoot.py       RE-SHOOT the screenshots on the phone: three languages
                         x two themes x four screens, into docs/screenshots/
                         <lang>/<theme>/. EN captures at 12-hour and ru/uk at
                         24, because that is what each audience has set. Taps
                         are found by TEXT in the uiautomator dump rather than
                         by coordinate - the rows move between languages, and
                         shooting all three is the point. The flat files the
                         README uses are copies of the en set

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

Toolchain: AGP 9.4.0, Gradle 9.7.1, Compose compiler 2.3.21, BOM 2026.08.00,
compileSdk 37, targetSdk 36, minSdk 35.

## Tests

`app/src/test/`, 201 cases, no device. They are written as the QUESTION the code
answers rather than as coverage of a method, because none of the bugs were ever
in a method — they were in an assumption.

    StuckRuleTest         when "the phone is not filtering" is a FAULT and when
                          it is exactly what we asked for - the difference the
                          rewrite loop turned on
    RuleCaptionTest       the schedule line the phone's own Do Not Disturb
                          screen shows: skipped while the rule is live, because
                          a rewrite blinks zen - and still OWED afterwards
                          rather than filed as sent
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
