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
                                 never two independent times. `endAt` lets the
                                 NEXT alarm set the end, either way: when it is
                                 this night's - after the night began, inside
                                 the window or later on the day the scheduled
                                 end falls on - the night ends at it; otherwise
                                 at the wake handle, which is the fallback and
                                 not a ceiling. It was AOSP's shorten-only rule
                                 until 11 Sep 2026. `liveWindow` is the window
                                 as a PAIR, began and ends, and refuses a night
                                 the last END has closed (`Prefs.endedAt`). The
                                 day-of-week selection is the MORNING a window
                                 ENDS on, and Scheduler works backwards to the
                                 evening that reaches it. `endingAlarm` is the
                                 gate the alarm passes through - the next alarm,
                                 but only where the switch lets it act. It is
                                 one line and it has a name because six callers
                                 were writing it out and two forgot; a seventh,
                                 `insideWindow`, forgot the alarm itself
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
                                 a door that opens onto nothing is never drawn.
                                 Includes the clock app's alarm LIST, by the
                                 platform intent every clock answers - never a
                                 prefilled editor, which Honor's Clock answers
                                 with someone else's alarm and Google's by
                                 creating one; the alarm's own `showIntent`
                                 where the clock fills it (Honor's does not).
                                 The list's activity is behind the NORMAL
                                 SET_ALARM permission, declared, and the app
                                 that answers is named to TalkBack
    core/Delivery.kt             the one rule AlarmWatch and BackgroundProbe
                                 share: delivered means ARRIVED ON TIME
    core/ScreenEffects.kt        does this phone APPLY the rule's device
                                 effects. The one question here with no probe,
                                 so a manufacturer prior that a measured
                                 transition can overrule
    core/Routines.kt             Samsung's Modes and Routines, driven through
                                 the door it leaves open: a content provider
                                 behind a NORMAL permission that lists the
                                 user's manual routines and starts or ends one
                                 by uuid, and an importer that takes a routine
                                 FILE we write. On a Galaxy the grayscale,
                                 dark-theme and wallpaper-dim switches are each
                                 backed by one generated routine - Samsung's
                                 own built-in actions; dimming only WITH the
                                 dark theme, because One UI dims only in dark
                                 mode, so its row lives under that switch, and
                                 as a PAIR of polarities of which the window
                                 runs only the one that makes the night differ
                                 from the phone's own setting, which dims by
                                 default - saved once (the Save in Samsung's
                                 editor is the floor: inserting directly is
                                 signature-level), then run exactly while the
                                 window does. Adoption is on EVIDENCE - the
                                 provider lists the routine by the name the
                                 file carried - never on the screen having
                                 been shown. The Save is EXPLAINED, four ways:
                                 a note in the section, a one-time explainer
                                 on the first tap, the row's own "not saved"
                                 after a trip back with nothing, a snackbar on
                                 adoption. A capability probe, like Doors
    core/RoutineFile.kt          the routine file Samsung's importer takes,
                                 byte for byte as its own writer lays it out:
                                 512-byte header, plain JSON body, footer.
                                 One built-in action per effect, with the
                                 parameters its handler reads; the dark
                                 theme's is UiModeManager's NUMBER as a
                                 string, and "true" there draws the row OFF
    core/BackgroundProbe.kt      one throwaway alarm that asks whether this
                                 phone delivers alarms at all. Silent unless
                                 the answer is no
    core/AlarmWatch.kt           did our own END actually arrive? The backstop
                                 for every cause BackgroundLimit cannot see - a
                                 frozen app misses its alarm with the appop still
                                 reading `allow`. A miss is a RECORD, not a flag:
                                 when bedtime actually ended, and whether the
                                 app was on screen as the END arrived - which is
                                 how a parked alarm is released, and the only
                                 evidence for "until you opened the app". The
                                 card has two faces (after Allow it says when it
                                 will know; the switch cannot be read) and a Got
                                 it per incident, keyed on the END's due instant
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
                                 tap it cannot honour opens the APP instead.
                                 A FOURTH face lives in the MANIFEST, not here:
                                 `android:icon` on the service is what the "Add
                                 tile" tray draws, and render() cannot have run
                                 for a tile nothing is listening to yet, so it
                                 must be the mark the tile wears at rest or
                                 adding it changes its icon under your hand
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

- The alarm-set end belongs in the WINDOW calculation, not in the END alarm.
  Shortening only the alarm leaves the window still containing `now`, so the next
  reschedule walks back into the night and switches zen on again. `SchedulerTest`
  pins it.

- **The next alarm SETS the end, one way, and the handle is the fallback.** With
  the switch on, tonight ends at the next alarm when it is this night's -
  earlier than the wake handle or later - and at the handle otherwise. The
  handle is the user's own time and is never overwritten. Dragging it, or
  setting it in the picker, is the one way OUT of following: `commitWake`
  switches the rule off on a real move, and stays separate from `commit`, which
  the switch itself calls. The switch used to COPY the alarm into the handle so
  the two could not disagree; they could, the moment the alarm moved in the
  clock app, in both directions - and the user's own wake time was gone. With
  NO ALARM ON THE PHONE the rule switches itself off, in `rescheduleAll`, and
  the switch is drawn off and DISABLED; nothing switches it on by itself. A
  standing rule was built first and the owner rejected it on sight: ON over
  "No alarm set" reads as on-but-doing-nothing. The cost, chosen knowingly: the
  platform reports "no alarm" identically for a deleted one and a one-time one
  that has rung, so after a one-time alarm the rule is off until switched on
  again. The SECTION IS ALWAYS DRAWN - it used to leave with the switch, and a
  control must never remove itself when used. The row carries the live state -
  the time alone when it is this window's, "Mon 07:30" over "Not in this
  sleep window" when it is not - never "tonight": a window can be a nap -
  and "No alarm set" over what the row does; never when bedtime ends, which
  the dial says three times already. The platform names ONE alarm,
  the next to ring; there is no list to choose from, so the row says which one
  the way the lock screen does. The section is TWO
  ROWS WITH TWO ROLES, straight under the window pill with no heading and no
  rule above - the first row's title is the heading: the rule, a switch row
  with its own title and no icon; and the alarm row - icon, its face, and
  OPEN-IN-NEW, not a chevron, because the tap leaves for the clock app. Four
  shapes preceded it in one day and DECISIONS has them: twin rows with a
  chevron (read as two settings), an assist chip (floated), a split row
  (Android's Wi-Fi idiom; its 160dp body cut every Slavic line short), and
  this, the owner's pick from six sketches once the lines had room to mean
  something. Copy budgets, measured on the phone: the rule title ~22
  Cyrillic characters, the alarm headline ~24, its second line ~30.
- **A night whose END has come DUE is closed**, landed or not, until a window
  that begins later. At the END the clock app has already moved "next alarm" to
  tomorrow, so judged from the handles the night still contains now and the
  reschedule walks back in; a snoozed alarm is the same door ten minutes later;
  and on the Honor the clock's broadcast beats our grid-held END to the
  receiver, so the receiver's own write is not enough - `rescheduleAll` closes
  the night the moment `endDue` has passed, which also ends a night whose END
  was eaten at the open instead of extending it to the handle. `Prefs.endedAt`
  is the END's DUE instant - not its landing, or a parked END released at 23:00
  would end the night that began at 22:30; and NOT written for an END that
  lands early beyond the tolerance, which re-opens the window as `Delivery`
  says. `liveWindow` refuses any window begun before it, strictly, so a window
  set to begin the instant the last one ended is a new night. Deliberately not
  cleared by a user's edit: drag the handle past now after the END and bedtime
  waits for tonight; the bar says "Starts in". `SchedulerTest` used to pass the
  RUNG alarm at 07:31, which the phone never will; `NextAlarmTest` drives the
  real path, the race included. The alarm may extend a night only to before the
  NEXT day's start: an evening alarm on the ending date would otherwise run
  one night into the next. And a BOOT does not switch the rule off for "no
  alarm": the clock app may not have re-registered yet (`alarmsKnown`).
- **Every number that describes TONIGHT takes the alarm-set end**, and the
  list is longer than it looks: the wake numeral, the arc, the handle, the
  countdown, its caption, the sleep-window total, the sentence, the row and the
  app bar - and `insideWindow`, which asked WITHOUT the alarm and read "not
  running" at 08:45 under a 09:00 alarm. It reached the phone twice with some of
  them converted and some not, and a screen answering one question two ways is
  worse than either answer being wrong. `endsTonight` is derived ONCE in
  `HomeState` for that reason, and the WAKE UP overline reads NEXT ALARM with
  the alarm's glyph while tonight ends at the alarm - which is what tells the
  user that the handle sitting there is not theirs, and that dragging it lets
  go. The handle lets go at the FIRST
  MOVEMENT, not at the release: `dragWake` flips the rule as the finger moves,
  so the numeral, the arc and the overline follow the finger rather than
  showing the alarm for the length of the drag. Until it moves, the handle
  stays drawn where it was grabbed, which is the alarm: drawn from the setting
  on the grab, it jumped to 6:50 under a finger resting on 10:00 and jumped
  back with the first move. A grab that never moves changes nothing. The
  picker opens on the time the numeral shows, for the same reason.
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
- **An alarm can land EARLY, and an early END re-enters the night.** The Honor
  delivers exact alarms on its own five-minute grid: measured 11 Sep 2026, the
  END due 08:30:00 landed at 08:29:20, and judged at that moment the window
  still had forty seconds to run - zen off, straight back on, a second END armed
  and held by the phone to 08:34:20. `Delivery.asOf` judges an alarm inside the
  tolerance at the instant it was ARMED for, and that instant rides in the alarm
  as `EXTRA_DUE`, not in prefs: a parked END released by the app being opened
  can land after the resume's reschedule has already re-armed `endDue` for
  tomorrow, and judged against tomorrow it read as punctual and un-latched the
  miss. `AlarmWatchTest` pins both.
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
  phone that was simply always dark, and un-hid the broken switches. And only a
  transition the RULE could have made: on a Galaxy a routine of ours drives the
  dark theme, and the second version took that routine's own work as proof,
  redrew the zen switches and ended the routine, in one second. Where a dark
  routine exists there is no evidence either way, and a record made that way is
  withdrawn. `ScreenEffectsTest` pins it.
- **One UI 8 stores `ZenDeviceEffects` and applies none of them** - grayscale
  and night mode both, across a screen-off cycle. Not a capability gap: One
  UI's own Sleep mode drives the same `Global saturation`. On a Galaxy the
  grayscale and dark-theme switches therefore run through routines Gloaming
  generates (`Routines.kt`); the link to One UI's own Sleep mode editor that
  used to stand in for them is gone, because a second bedtime system with its
  own schedule and DND fights this one. Samsung's Routines SDK was tried and
  its discovery is closed to non-privileged apps.
- Honor's auto-launch and run-in-background states are **unreadable** — absent
  from settings, appops and the package dump, measured either side of a clean
  toggle. Run-in-background is answered by `BackgroundProbe` instead, whose
  verdict is LATENESS not arrival, because a parked alarm is still delivered the
  moment the app is opened;
  auto-launch only by a real reboot, after the fact, in `BootWatch`.
- minSdk is **35** because `ZenDeviceEffects`, `AutomaticZenRule.Builder` and
  `getAutomaticZenRuleState` are all API 35, and a missing method raises `Error`,
  which none of the `runCatching` here would catch.
- **Never decide against a reading your own action changes.** The dim
  routines flip One UI's own "apply dark mode to wallpaper" key, and the
  choice of which polarity the night needs was read off that very key - twice,
  in two builds, and flickered both times, because our routine had just set it
  or Samsung's revert had not yet landed. `Prefs.dimBaseline` records the
  phone's setting when the window opens, before anything of ours moves it.
- **A shell write to `Settings.System` proves nothing about the APP's write.**
  A third-party app may write only AOSP's `PUBLIC_SETTINGS` keys there, whatever
  it holds; `aod_mode` works only because Samsung allowlists it, and
  `display_night_theme_wallpaper` was refused with "You cannot keep your
  settings in the secure settings" after a shell test had passed. Measure a
  vendor key through the app before building on it.
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
  **And the setting lies in BOTH directions.** Same phone, hours later, on the
  Play build: `enabled_notification_policy_access_packages` read EMPTY while the
  app plainly had access - it had created a live `ZenRule[name=Gloaming]`,
  enabled, `STATE_TRUE`, with zen actually on. Listing a package is not evidence
  of access and omitting one is not evidence of its absence. The setting is not
  weak evidence; it is none. The rule in `dumpsys notification` is the answer.
- `am broadcast` cannot reach `BedtimeReceiver` — it is `exported="false"`,
  correctly. Test through real alarms.
- **`dumpsys batterystats --history` is the alarm log that works on a RELEASE
  build.** Every delivery is a `+tmpwhitelist=… com.jemcik.gloaming.END/u0` line
  with a wall-clock stamp, every foreground session a `+top=`, and the screen
  state is there too; it reaches back about a week. It is how the Honor's grid
  was read on 11 Sep 2026 with `run-as` refused and the journal unreadable.
- **Samsung's Modes and Routines can be driven, and only from the APP.** Its
  external provider (`Routines.kt`) sits behind `READ_ROUTINE_INFO`, which is
  `protectionLevel normal` - but the shell does not hold it, so `content query`
  from adb is refused where the app is answered. Test through the app and read
  Samsung's side in logcat (`Routine@Core`), which is readable there. The
  importer DOES take a file from the shell over `file:///sdcard/…`, which is how
  a generated routine is tried before it is built in. Grayscale and the dark
  theme are BUILT-IN routine actions (`gray_scale`, `dark_mode_v3`), absent from
  the editor's picker but present in the catalogue and executing; a mode cannot
  be switched on directly (`READ/WRITE_MODE_INFO` are signature). The one step
  nobody can take for the user is Save in Samsung's editor - every insertion
  path is signature-level, the Bixby capsule provider included, and Good Lock's
  Routines+, which HOLDS the insert permission, still hands an imported routine
  to that same editor. Measured 6 Sep 2026; DECISIONS has the contract, the
  file format and the Good Lock reading.
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
  it that way — and note that the grep does not come back empty. Two call sites
  hand `MaterialTheme.typography.<role>.fontSize` to `TextAutoSize` as the
  CEILING a shrinking label may not exceed, which reads a size the scale owns
  rather than inventing one. Both say so where they stand, and `Theme.kt` states
  the rule beside the scale it governs, because a rule whose exceptions are
  unmarked cannot be told from a rule that has rotted.
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
                         and one ModifierParameter in BedtimeDial). Both are
                         DELIBERATE and both now carry the reason at the line
                         lint points at, so neither reads as an oversight to
                         whoever runs it next
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
    adb shell dumpsys batterystats --history | grep gloaming    the same on a
                         release build: every alarm delivery and every
                         foreground session, stamped

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

`app/src/test/`, 300 cases, no device. They are written as the QUESTION the code
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
                          the one-off, the activeDay pin, `endAt` - the next
                          alarm sets the end either way when it is this
                          night's, and the afternoon case the rule trades for
                          that - and a night the END has closed staying closed
                          while the next alarm reads tomorrow
    NextAlarmTest         the same through the REAL path: rescheduleAll reading
                          getNextAlarmClock, the END armed at a later alarm,
                          the pin on the night's own evening, the receiver
                          recording the END by its due instant
    DoorsTest             the clock app's alarm list is a door only where it
                          resolves, and the alarm's own showIntent wins
    SentencesTest         the sentence builders, driven directly
    WindowSentenceTest    the window in words, per locale, through Home, plus
                          the alarm-set end - earlier, later, another morning -
                          driven through a real setAlarmClock so it exercises
                          getNextAlarmClock
    PlanNoteTest          the "not in effect" note, through Home
    InterruptionsTest     the allowlist sentence, per locale
    ClockTest             12/24-hour formatting
    PrefsMigrationTest    the one-shot days migrations - the only code here
                          that can corrupt data silently. Two of them now: days
                          as mornings, and the screen effects that used to
                          default ON, where an install that predates the change
                          must keep them
    RowFitTest            does the text fit, in en/ru/uk, by MEASURING -
                          Home's rows, the allowlist's, Settings', the alarm
                          section's every face AND its heading, and the APP BAR's status
                          line, which is capped at one line and so truncates
                          rather than wraps - measured on the worst case that
                          is actually reachable, a one-morning-a-week schedule
                          whose countdown reaches three digits. AT HOME'S OWN
                          WIDTH:
                          a row built bare gets a 360dp card and the real one
                          is 311, and those 49dp are six characters this test
                          passed for a fortnight
    ScreensTest           interactions, never appearance - including the
                          alarm switch leaving the handle alone, NEXT ALARM
                          over the numeral while tonight ends at the alarm,
                          the picker letting go of it, the section's faces
                          with no alarm and with another morning's, and the
                          door into the clock app
    AlarmWatchTest        did our own END arrive, and ON TIME - the case the
                          notice exists for, which it could not report; what a
                          miss RECORDS; an alarm judged by its own due instant
                          across the resume-versus-release race; Got it and
                          Allow per incident; and the forty-second-early END
                          that must end the night rather than re-enter it
    BootWatchTest         the withheld-boot detection
    BackgroundProbeTest   the delivery probe: lateness rather than arrival is
                          the verdict, and the latch that stops its own retest
                          erasing it
    RoutinesTest          Samsung's routines, one per screen effect: they run
                          exactly while the window does and only where the
                          switch is on, a daytime reconcile never ends a run
                          the user began by hand, a refused end is owed until
                          it succeeds, a routine deleted in Samsung's app is
                          forgotten while one switched off there is kept, and
                          an offered routine is adopted on evidence, never on
                          the offer. Against FakeRoutines, which carries the
                          provider's contract as read from the decompiled
                          original
    ScreenEffectsTest     does night mode following the window prove the rule's
                          effects work - yes on a bare Galaxy, no where a
                          routine of ours drives the theme, and a record made
                          that way is withdrawn
    RoutineFileTest       the routine file Samsung's importer is handed, read
                          back the way its reader reads it: the 512-byte
                          header's sizes are true, the padding is NUL, each
                          effect's action carries the parameters its handler
                          reads
    ResetTest             starting over: no rule survives - not even one whose
                          id was already lost - the store comes back EMPTY so
                          the next launch takes the fresh-install branch and not
                          the upgrade one, and the journal outlives it
    DiagnosticsTest       the report a user sends: does it still speak when the
                          phone answers nothing, is a refused lookup kept
                          distinct from a deleted rule, and does the system's
                          account stay ahead of - and apart from - our own

Coverage: **81% of instructions, 68% of branches**. The shape is the point — what
is covered is what can be reasoned about without a phone; what is not is what
talks to the platform (`BedtimeTile` 0%, `AmbientControl` 36%,
`BedtimeReceiver` 56% - its END and next-alarm branches are driven directly
now, see `NextAlarmTest` - `Journal` 63%, `Doors` 87%). That gap is what the
journal is for.

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
