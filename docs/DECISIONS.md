# Gloaming — decisions, and what they cost

The lab notebook. Every finding here was **measured on hardware**, and most of
them were got wrong at least once first; where that happened, the wrong version
is kept, because the way a thing was got wrong is usually more useful than the
answer.

`CLAUDE.md` is the working reference — architecture, rules, commands, and the
traps in short form. This file is the evidence behind it. When the two disagree,
CLAUDE.md is what the code does now and this is how it got there.

Two conventions worth knowing before reading:

**A superseded finding is marked, not deleted.** A constraint that expired still
records how it was found, and several entries below say plainly that their own
reasoning no longer holds. Read those to the end before quoting them.

**Numbers are from this project's own devices** — an Honor Magic8 Pro on
MagicOS 10 and a OnePlus CPH2653 on LineageOS 23.2 — and are dated where it
matters.

---

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
    core/AmbientControl.kt   the vendor's own always-on keys, behind an adb-only
                         permission; inert and hidden without it
core/Clock.kt            clock times in the phone's own 12/24 format
    core/Prefs.kt            SharedPreferences
    core/Journal.kt          on-device log; read it over adb, see Build
    ui/Rows.kt               the app's one list row, on M3's ListItem:
                             SwitchRow / LinkRow / StaticRow / ActionRow /
                             RadioRow. Six hand-rolled rows used to disagree
    ui/BedtimeDial.kt        24-hour dial, draggable handles, sweep gradient
    ui/SettingsScreen.kt     theme mode, a link to the system language picker
    ui/Theme.kt              Dusk/Dawn tokens, Baloo 2 + Figtree type scale,
                             and the arc's ground-aware variants (stopsOn,
                             nightOn) for thin strokes and fills on Dusk.
                             Both palettes were rebuilt 31 Aug 2026 against
                             Material's tone table and against Google Health,
                             Dialer, Calendar, Tasks and Messages sampled on the
                             Honor. Dusk is a soft charcoal (page tone 14, not
                             7); Dawn is a warm white (page tone 98, not 93);
                             and one accent - a desaturated slate - fills the
                             day discs, the preset pill AND the checked switch
                             track, which is a deliberate break with Material
                             and is argued in "Deviations" below. Several
                             constraints recorded elsewhere in this file were
                             bought with the OLD grounds and have expired; each
                             is marked where it sits rather than deleted,
                             because the measurement is still the record of how
                             it was found.

The rule holds the policy; we hold the trigger. `component=null` on the rule is
deliberate — there is no condition provider, which is precisely what avoids the
blocked path.

`Home` was 938 lines and could not be split, which is a different complaint from
being long. It held fourteen pieces of state, a `commit()` closure, a
`setBedtime()` closure and a lifecycle observer in one scope, and every section
read half a dozen of those locals — so the FILE could be cut up but the FUNCTION
could not, and any section lifted out of it needed fifteen parameters to work.
Hoisting the state into `HomeState` is what made the sections extractable; `Home`
is now 88 lines and reads as the page order, with each section a private
composable taking `s: HomeState` and at most two derived values.

Three things were deliberately left OUT of the holder, and the reasons differ:

- `now`, `ambientZen` and `ambientRow` read the PLATFORM, not the holder's own
  state, so `derivedStateOf` would never know when to recompute them. They stay
  as `remember(s.tick)` in the composable that uses them, which is the existing
  keying and is load-bearing: `ambientZen` is keyed on tick precisely so an adb
  grant is picked up on the next resume.
- `missedBoot` is deliberately NOT keyed on tick, because it reads prefs and on
  first run WRITES them. Re-asking it every minute would be pointless and impure
  both. It lives in the holder but is only re-asked in `onResume`.
- `insideWindow` and `runningNow` are computed in `Home` and passed down rather
  than read per section, so every section on one frame agrees about the time.
  Two sections each calling `LocalTime.now()` can straddle a minute boundary.

What this bought beyond readability: each section is now its own recomposition
scope, so toggling grayscale no longer recomposes the dial. What it did NOT
change: nothing under `core/` was touched, and the off/on round trip was
re-measured on the phone afterwards — `zen_mode` 1→0 with `activeDay` cleared,
then 0→1 with `activeDay` re-derived, the END alarm restored to the same minute
and the next START re-queued.

One trap worth recording, because it nearly shipped. The mechanical rename of
`enabled`/`start`/`end` to `s.enabled`/`s.start`/`s.end` collides with Compose
NAMED ARGUMENTS of the same name — `enabled = ready`, `start = start`,
`padding(end = 12.dp)` — and with the string literals `"start"` and `"end"` that
`picking` holds. A word-boundary replace corrupts all three silently: the code
still compiles when a named argument is renamed to a value that happens to
exist. Renaming was done with string literals and comments masked, named
arguments detected across line breaks (the opening paren is often on the
previous line), and the four surviving collisions checked by hand.

## Vendor limitations (all measured, not assumed)

MagicOS accepts these API values and silently ignores them:

| Requested | Stored in rule | Applied |
|---|---|---|
| grayscale | yes | **yes** |
| dimWallpaper | yes | **yes** |
| nightMode | yes | **yes** - but only once the screen turns off |
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

System dark mode is **device-dependent, not impossible** - the earlier claim
here that no app can set it on any device was wrong, and was corrected by
reading UiModeManagerService rather than the docs. It gates both `setNightMode`
and `setCustomNightModeStart/End` like this:

    if (isNightModeLocked() && checkCallingOrSelfPermission(
            MODIFY_DAY_NIGHT_MODE) != PERMISSION_GRANTED) { return }

so the permission is needed ONLY when the device locks night mode, and
`mNightModeLocked = res.getBoolean(config_lockDayNightMode)` is a per-build
boolean. Neither call carries `@RequiresPermission` in the public API;
`setNightModeCustomType`, right beside them, does. On a phone that does not lock
it, an app can set the system's own dark-theme schedule with NO permission - and
that would beat the zen device effect, because the system would then do the
switching and it would keep working with this app dead.

AOSP defaults the boolean to `true`, so most phones will refuse. A `NightModeControl`
object read it - `Resources.getSystem().getIdentifier("config_lockDayNightMode", ...)`,
no permission, no side effects - and wrote one line to the journal. Measured on both
phones and both lock it: `config_lockDayNightMode=true` on the OnePlus running
LineageOS 23.2 and on the Honor Magic8 Pro running MagicOS 10, each agreeing
with that device's own `dumpsys uimode` `mNightModeLocked=true`. So the door is
shut on the two phones this app has ever run on - but by their choice, not by
Android's. **That object is deleted** (`b72ff93`): it answered a question about a
mechanism the zen effect does not use, and nothing read its answer. Re-read this
paragraph before rebuilding it - the finding is the value, not the code.

Deliberately NOT built on the back of this: the feature it would enable. There
is no phone here on which it could be tested, and shipping an untestable path
into the one part of the app that runs unattended overnight is not a trade worth
making. The probe costs a resource lookup and a journal line; the feature can be
written the day a device answers `false`.

What IS closed on every device: `MODIFY_DAY_NIGHT_MODE` cannot be granted at all
- `pm grant` returns "managed by role" even over adb. And writing
`Settings.Secure.ui_night_mode` or the `dark_theme_custom_*` keys directly does
nothing even holding `WRITE_SECURE_SETTINGS` (which an app CAN be granted over
adb, tested): the service reads those keys at init and on `ACTION_SETTING_RESTORED`
only, with no `ContentObserver` on them, so a raw write is never noticed.

**Ambient suppression is ignored on the Honor — and establishing that took
three witnesses that turned out to measure nothing.** The old claim in
`AmbientCapability`, "there is no way to observe this the way night mode can be
observed", was half right in a way that matters: the platform's *request* is
observable, the vendor's *response* is not.

`dumpsys power` prints `AmbientDisplaySuppressionController` with
`ambientDisplaySuppressed` and `mSuppressionTokens`, so whether the platform
asked is easy. Whether Honor obeyed is the hard half, and these all read
IDENTICALLY with AOD switched on and switched off, measured 30 Aug 2026 against
ground truth set from Honor's own Settings:

| Proposed witness | AOD on | AOD off |
|---|---|---|
| `mWakefulness` | Dozing | Dozing |
| `Display State` / `mScreenState` | DOZE | DOZE |
| `aod_doze_state` | 1 | 1 | ← **this row was wrong, see below** |
| `AOD#` layers in `SurfaceFlinger --list` | present | present |
| AOD window holds focus (`mCurrentFocus`) | yes | yes |
| that layer's `frame=` counter over 70 s | static at 4 | static |
| `adb exec-out screencap` while dozing | all black, max=0 | all black, max=0 |

Every one would have "confirmed" whatever it was pointed at. The screencap is
the sharpest warning: the doze layer is not captured at all, the same blind spot
that keeps `screencap` from seeing the colour transform.

**The witness that works is a tap and a pair of eyes** — superseded 1 Sep 2026
by `aod_doze_state`, read with the screen asleep; see the correction below. The
tap protocol is still what validated everything up to that point.

 This phone's AOD is
tap-to-show (`aod_display_type=2`, `aod_touch_time=5`), so the passive screen is
dark either way; tapping the sleeping screen shows the clock. Validated with a
negative control, which is the step that makes it evidence: with AOD off in
Settings, a tap shows nothing. So the indicator can go negative.

Against that witness: with `cmd power suppress-ambient-display` armed — the
platform's OWN lever, the one `DefaultDeviceEffectsApplier` pulls,
`ambientDisplaySuppressed=true` — a tap shows the clock exactly as it does with
the token released. The platform records the suppression and `com.hihonor.aod`
ignores it.

**CORRECTION, 1 Sep 2026: `aod_doze_state` is a real witness, and this table
libelled it.** Re-measured on the same phone, reading it with the screen ASLEEP:

| condition | `ambientDisplaySuppressed` | `aod_doze_state` |
|---|---|---|
| AOD on, no token | false | 1 |
| AOD off — all four keys 0 | false | **0** |
| AOD on, platform token armed | **true** | **1** |
| anything, screen ON | – | 0 |

It reads 0 when the AOD is genuinely off and 1 only when the AOD is actually
lit, so it tracks the DISPLAY rather than the setting, and it can go negative.

**Scope, found the same evening and worth stating before anyone trusts it too
far: this holds in TAP TO SHOW mode. It does not hold in SCHEDULED mode**, where
`aod_doze_state` stayed 1 through every arm - schedule covering now, schedule
excluding now, written from adb, written through the UI, and even
`aod_switch=0`. In that mode it discriminates nothing, so it is not a witness
there and no conclusion about Honor's schedule can be drawn from it. Which mode
you are in is `aod_display_mode`, below.
It read 1/1 on 30 Aug because the AOD was genuinely on in both arms of that
test — the experiment had no condition in which it was off, so a correct witness
reporting "no change" was mistaken for a broken one. The lesson is the one this
file keeps relearning: a negative control is not a formality, and without one
you cannot tell a dead instrument from a true null result.

Two consequences. The 30 Aug CONCLUSION stands and is now stronger: with the
platform's own token armed, `ambientDisplaySuppressed=true`, Honor's AOD is
still lit, so `com.hihonor.aod` ignores it — and that is now provable from adb
alone rather than by tapping the screen and looking. And the always-on path
through `AmbientControl` can be verified the same way, with no human in the
loop.

**But Honor's AOD IS controllable, by four Secure keys written together.**
Toggling it in Settings moves `aod_display_type` 2 to 0, `aod_switch` 1 to 0,
`aod_touch_time` 5 to 0 and `fingerprint_touch_time` 5 to 0. Writing
`aod_switch=0` ALONE changes nothing — which is why an earlier attempt here
concluded the keys were inert mirrors, the same way the eye-comfort keys are.
They are not: writing all four back from adb restored AOD, confirmed by tap.
`aod_display_type` is the one that matters.

That is a real lever, and it is gated: these are `Settings.Secure`, so a writer
needs `WRITE_SECURE_SETTINGS`, which is signature|privileged and cannot be held
by an ordinary install. It IS grantable over adb. So scheduling AOD off at
bedtime is possible on this phone only as an opt-in path for a user willing to
run one adb command. Not built — see Outstanding.

The permissive branch of `AmbientCapability` remains unverified: where
`doze_always_on` exists we return true and ship a live switch on the strength of
that key alone. The OnePlus has never been checked. Note that checking it needs
the tap-and-look protocol above, not the dumpsys fields — those measure nothing.

**So the always-on row is now three-state, and one of the states is absent.**
Where the zen effect works, it is a switch backed by the rule. Where it does not
but `AmbientControl` can write the vendor's keys, it is a switch backed by those.
Where neither is true the row is NOT DRAWN - no chevron, no footnote, no
"handled by your phone". It used to be a door to Display settings plus a sentence
naming the rest of the walk, and on Honor that door opens onto a screen that
cannot reach always-on at all. A control that neither toggles anything nor opens
the right screen is only noise, so it goes, and the card ends after Dark theme.
`AmbientSettings` is deleted with it, along with `fx_ambient_sub_unsupported` and
`fx_always_on_lives_in`.

The vendor route is opt-in and cannot be reached by accident:

    adb shell pm grant com.jemcik.gloaming android.permission.WRITE_SECURE_SETTINGS

Verified end to end on the Honor 30 Aug 2026. Without the grant the section shows
three rows and no footnote; with it the row appears, and toggling it mid-window
moved all four keys to 0 and back, with `ambientSaved` holding the prior values
and the journal recording both directions. The order matters and is deliberate:
the prior values are written to prefs BEFORE the first key is touched, so a write
that fails halfway still has something to restore from; and `ambientSaved` is
cleared only on a successful restore, so a failure is retried rather than
forgotten with the display left off. The hook is at the top of
`ZenController.setActive`, which every path funnels through - alarms, boot, the
UI, reconcile - so a phone that dies mid-window restores the display when it
comes back, with no case of its own.

Two things it deliberately does not solve: uninstalling mid-window leaves
always-on off, since nothing of ours runs again; and if you change always-on in
Settings while a window is running, the restore at the end overwrites it.

Honor's AOD settings screen is unreachable, and the schedule route is shut too —
read from `HnAOD.apk`'s own manifest rather than by trial:
`com.hihonor.aod/.ui.AODSettingsActivity` is `exported=true` but carries
`android:permission="android.permission.WRITE_SECURE_SETTINGS"`;
`.AODSettingsActivityAlias` is exported and carries
`hihonor.android.permission.HW_SIGNATURE_OR_SYSTEM`; and `.CommonReceiver`,
which handles `com.hihonor.aod.action.START_ALARM_ACTION` / `END_ALARM_ACTION`
and so is how Honor schedules its own AOD, is `exported=false`. Also
`Settings$HomeAndUnlockSettingsActivity` needs `HW_SIGNATURE_OR_SYSTEM` and
`LauncherModeSettingsActivity` is not exported.

**MagicOS withholds `ACTION_BOOT_COMPLETED`, and that is the worst bug found
here.** With the app set to "Manage automatically" in Settings > Apps > App
launch, the broadcast never arrives. Measured 30 Aug 2026 across four reboots,
each waited out to five minutes: journal unchanged, and NO alarms at all. The app
sat armed, showing the right times, with nothing behind them until it was next
opened. It is the same shape as the Wellbeing bug this app exists to route
around - a trigger the vendor quietly declines to deliver - and it is invisible.
Setting that app to auto-launch delivered `BOOT_COMPLETED` 32 s after boot.

**Battery optimisation is NOT the mechanism**, tested twice: once added over adb,
once granted from Honor's own Settings screen, exempt and surviving the reboot in
standby bucket 5. The broadcast still never came. Diffing every Settings
namespace across that toggle showed Honor writes exactly one thing - the AOSP
`deviceidle` whitelist entry - so unlike the AOD keys there was no vendor state
hiding behind the UI.

`BootWatch` detects it WITHOUT asking which phone this is. A vendor test would
fire on every Honor whether or not anything was wrong, could never tell whether
the user acted on it - the setting lives inside `com.hihonor.systemmanager` and
is not readable - and would miss every other vendor doing the same under another
name. So it measures the symptom: `BedtimeReceiver` records which boot it
handled, the app compares that against the boot it is running on, and a mismatch
means the phone restarted with the broadcast withheld. The notice clears itself
once a boot is handled, which is the only confirmation available when the setting
cannot be read. Confirmed both ways on the Honor - the notice appears with
auto-launch off and is gone after a boot that reached us.

The door is named as a COMPONENT, not by action, and that is deliberate: two
activities answer `hihonor.intent.action.HSM_STARTUPAPP_MANAGER`, and
`.appcontrol.activity.StartupAppControlActivity` is gated behind
`com.hihonor.permission.external_app_settings.USE_COMPONENT`, so resolving the
action lands on Honor's chooser where one of the two choices simply fails.
`.startupmgr.ui.StartupNormalAppListActivity` is exported with no permission
attribute - read from `HnSystemManager.apk` - and opens App launch directly. It
needs a `<queries><package>` entry or package visibility hides it.

Blue-light filter is not in `ZenDeviceEffects` on any device. Honor's
`eye_comfort_*` keys READ via `Settings.System` but are registered in
`sSystemMovedToSecure`, so writes fail with "You cannot keep your settings in
the secure settings" even with `WRITE_SETTINGS` granted.

Night mode's deferral is the whole story, and it cost a subsystem to learn.
`DefaultDeviceEffectsApplier.updateOrScheduleNightMode` applies the theme
IMMEDIATELY only when the rule was activated manually, or during init, or when
the screen is off, or the device is locked - otherwise it registers a
screen-off receiver and waits, because "Changing the theme can be disruptive for
the user (Activities are likely recreated, may lose some state)". Only nightMode
is deferred; grayscale, dimWallpaper and suppressAmbientDisplay apply at once.

`DarkCapability` used to probe this with a throwaway rule for 2.5 s, from the
app, screen on, unlocked - which is exactly the case that always defers. It
could therefore only ever return UNSUPPORTED, on any device, and did: both
phones were recorded as ignoring night mode when neither does. Measured on the
Honor 30 Aug 2026: rule active with `deviceEffects=[grayscale, dimWallpaper,
nightMode]`, screen ON gives `mAttentionModeThemeOverlay=1000` and
`mComputedNightMode=false`; press power and it is `1001` and `true`, and stays
dark on waking. The probe is deleted. Dark theme is a plain toggle, and the
screen says "Dark theme applies when the screen turns off", because a control
that does nothing while you watch it looks broken - which is how this was
reported.

Note this is a DIFFERENT mechanism from `UiModeManager.setNightMode`: the zen
effect calls `setAttentionModeThemeOverlay`, which is not gated by
`config_lockDayNightMode`. The Honor locks night mode and the overlay applies
anyway.
`AmbientCapability` infers from one verified key — do not add keys without
confirming on real hardware.

## Gotchas that cost real time

- **Logcat is encrypted** for third-party apps on MagicOS — entries come back as
  `(HKS)...(HKE)`. Hence `Journal.kt`. Never swallow an exception; three bugs
  hid behind `runCatching { }` with no logging.
- **`run-as` can READ this app's data but not write it.** Not a MagicOS trait,
  though it was found there: measured again 30 Aug 2026 on a OnePlus running
  LineageOS 23.2 and the write is refused identically, so treat it as how
  Android behaves rather than as something a vendor did. `cat
  shared_prefs/gloaming.xml`
  works and `cat > shared_prefs/gloaming.xml` fails with `Read-only file
  system`. Reading is unaffected, which is why the journal probe works and
  `check.sh` works — both only read. The consequence is that a setting cannot
  be corrected from the host: it has to go through the UI, with `adb shell
  input tap` against a `uiautomator dump`, matching rows by their TEXT rather
  than by coordinates, and re-reading the prefs afterwards to confirm what
  actually changed. Note the first failure looks like a different bug —
  `sh -c 'cat > shared_prefs/...'` reports `No such file or directory`, because
  the inner shell does not inherit `run-as`'s working directory; switching to
  an absolute path is what surfaces the real `Permission denied` /
  `Read-only file system`.
- **`clip(RoundedCornerShape(n))` on a short container eats content.** A 28 dp
  radius cut off labels; a 12 dp radius trimmed ~12 dp off each end of a 1 dp
  rule. Don't clip anything under ~40 dp tall whose child touches the edge.
  It caught the Repeat row too, and that one is worth knowing because the clip
  was added for an unrelated reason: the row is the only switch row with no card
  behind it, so its ripple arrived as a bare rectangle across the content width,
  and clipping to `CORNER` to round that off promptly bit the corners off
  "Repeat" and its subtitle. The ripple is `indication = null` there now. The
  press is still acknowledged - the row's interaction source reaches the switch,
  so the thumb grows to 28 dp under the finger, measured - which is where a
  switch's feedback belongs anyway. Two lessons, not one: a ripple with nothing
  to clip it is a design problem, and reaching for `clip` to solve it is how you
  turn one problem into two.
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
- **A role can be WRONG as well as unset, and that is harder to see.** The two
  traps below are about roles nobody wrote. This one was written, and written to
  the wrong token: the scheme mapped `outline = g.line` while `line` sits at
  tone 34 and 79 - which IS Material's `outlineVariant` (30 dark / 80 light).
  So the strong outline role and the quiet one were the same colour, and every
  outline that had to be READ was drawn in the one meant to whisper. Measured:
  the unselected day's ring managed 1.91:1 in Dusk and 1.66:1 in Dawn, and the
  unchecked switch's border 1.33 and 1.43, against the 3:1 a UI part that must
  be identified has to reach - M3's own baseline manages 3.51 for that border.
  `outline` is its own token now. It carried EVERY border that bounds a
  selection control - day rings, switch, preset segments - and that is now split
  three ways, by STATE and by GROUND: `selectBorder` for anything selected (the
  checked track, the filled day, the active preset, the "in effect" pill),
  `outline` for anything unselected on the PAGE (the day ring, the inactive
  preset), and `veilOutline` for anything unselected on VEIL (the unchecked
  track, the "off" pill). The last split is the one that is not obvious: the two
  grounds are 6 tones apart, so one value cannot clear 3:1 on veil without
  landing near tone 50 on the page - which is precisely the "the border around
  days is black" report below. One token per JOB is right; one token per job per
  GROUND is what the measurement actually demanded.
  What deliberately kept `line`: section rules, card dividers, the dial's ticks
  and dots. They separate blocks rather than bounding a control, and quiet is
  right there - which is why the fix was a second token and not a louder `line`.
  The way to catch this class is to check what each M3 role is SUPPOSED to be
  (the tone table above) against what the app actually assigned it, rather than
  only checking for roles left null.
  **Two corrections to the first version of this fix, both worth more than the
  fix.** It shipped at Material's own tones - 60 dark, 50 light - and applied
  them to the day ring and the switch but NOT the preset row, which names its
  border at its own call site. That left tone 50 and tone 79 sixty dp apart on
  one screen, reported on sight as "the border around days is black" next to
  the preset row's "nice calm grey". The contrast numbers were re-measured after
  the change; the two controls were never looked at TOGETHER. A measurement is
  not a review.
  And tone 50 was over-correcting. 3:1 is for the visual information required to
  identify a component and its state - here the state is fill-versus-no-fill and
  the day letter sits at 8.28:1, so the ring reinforces rather than reports. It
  is tone 70 in Dawn - this entry said 66 and the code has said 70 for some
  time; the code is right - measuring 2.24:1, deliberately under the guideline
  and chosen after building the compliant version and looking at it. Tone 58 is
  the lightest that clears 3:1 if that judgement is ever revisited.
  **Re-checked when the selected day became a DARK disc with white letters, and
  the argument got stronger rather than weaker.** The state is now filled-dark
  versus hollow-thin, which is about as separated as two states get, so the
  ring's own contrast carries even less than when this was written. Contrast
  the alert pill's rim, which looks like the same kind of exception and is not:
  there the FILL is 1.02:1 against the card, so the rim is the only separation
  there is and it does have to reach 3:1.
  **Both themes were audited pair by pair, and the same two came up under floor
  in each.** All text passes in both. The alert rim was a real defect in both
  and is `#C5665B` in each now - one mid-tone clears 3:1 against a light card
  and a dark one alike, 3.06 and 3.20. Keep the DIRECTION: on a dark card that
  rim had to go LIGHTER, and darkening it, which is the instinct, makes it
  worse - tone 46 gives 2.39.
  And `selectBorder` is the fill in both themes now. It bounded a selected
  control against its ground and neither fill needs it: Dawn's is dark on a
  light page, Dusk's lifts off its own at 2.59:1. Decided by looking rather than
  by the ratio - at 17dp a lighter ring on a dark disc reads as a HALO, and the
  day row came out looking outlined instead of filled. The token stays so a
  future accent that does need bounding can diverge.

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
- **Starred contacts are SHOWN by the contacts app, not by us.** Asked for as
  "display my starred contacts", and the app has never held READ_CONTACTS - see
  the test-alert note. It does not need to:
  `com.android.contacts.action.LIST_STARRED` (the deprecated
  `ContactsContract.Intents.UI.LIST_STARRED_ACTION`) opens the contacts app's own
  starred screen, and launching an activity needs no permission. Verified from
  inside the app rather than over adb, which would have proved nothing: the tap
  lands in `com.google.android.contacts` showing "3 starred contacts". The system
  list is also authoritative and current, where a copy of ours would go stale the
  moment a star was added elsewhere.
  **It is not a Google-Contacts action, which was checked and not assumed.** The
  action is AOSP's, so vendor forks inherit it: Honor's own contacts app declares
  `com.android.contacts.action.LIST_STARRED` on
  `com.android.contacts.activities.PeopleActivity` inside a `com.hihonor.*`
  package. Verified by disabling Google Contacts, enabling Honor's, and tapping
  the row - it lands on Honor's "Favourites" screen with the same contacts, and
  both packages were put back afterwards. On the first pass this looked
  Google-only, because the phone's own contacts app happened to be disabled and
  so resolved to nothing; the resolver told the truth about the phone and not
  about the platform.
  Note vendors name it differently - Honor says Favourites where Android says
  starred - so our copy names the STAR, which is the thing you tap either way.
  A `<queries><intent>` entry is required or `resolveActivity` returns null and
  the row hides on a phone where it would have worked. It IS resolved rather than
  assumed, and hidden where it does not resolve - a door onto nothing is the
  always-on row's mistake.
  The sheets also open FULLY expanded now. M3's default is partial with a drag
  for the rest, and with the explanation and this link added the last row fell
  below that height and could not be tapped. Found by tapping it and going
  nowhere, which is the only way that shows up.

- **"Conversations" needs explaining and the sheet is where, not a tooltip.**
  It is an Android 11 notion that Android itself barely explains - reported by
  someone with ten years on the platform who did not know what it was. The
  explanation sits under the title in the choice sheet, because a tooltip on
  Android is a LONG-PRESS, and a person who does not know what a thing is will
  not long-press it to find out. The sheet is where the choice is made, so it is
  where the sentence gets read. `ChoiceSheet` takes an optional `why`; only this
  one passes it, and the other two sheets are self-explanatory without it.

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
- **The effects are ROWS now, not chips.** The chips were already one per line -
  no two Russian labels fit a 311dp row, the closest pair missing by 9dp - so
  they were full-width elements shaped like pills with dead space to the right
  of each. Their explanations had nowhere to live and collected at the foot of
  the section, two sentences away from the controls they described. As rows
  (icon, name, supporting line, switch) each carries its own, the dead space
  holds the switch, and the section is the same shape as "What can wake you"
  above it. Subtitles have about 185dp - measured - which is roughly 27
  characters; three of the first drafts wrapped and were shortened. The one
  thing still written as a footnote is the Always On Display path, and it earns
  it: Honor locks every route to that screen, so the row's chevron can only
  reach Display settings and the app has to say where to walk the rest.

- **The effect chips told TalkBack nothing, and two of them were not chips.**
  They were a hand-rolled `Surface(onClick)`, so read off the device all four
  came back `checkable=false checked=false` - on-or-off existed only as a fill
  colour, and a blind user could not find out whether greyscale was on. The day
  circles have declared `Role.Checkbox` since they were written, so this was
  inconsistent with the app before it was inconsistent with M3. They are
  `FilterChip` now, with shape, colours, height and leading icon all passed, so
  the semantics changed and the appearance did not.
  The second half matters more. Two of the six call sites never toggled
  anything: when a phone ignores night mode or ambient suppression, that chip
  calls `startActivity` and LEAVES for the system screen - while looking
  identical to the ones beside it that flip a setting, and announcing, like
  them, as an untyped button. Those are `AssistChip` now, which declares
  `Role.Button`, and they are outlined rather than filled so the eye is told the
  same thing: not a state, a door. Measured after: the two toggles report
  `checkable=true checked=true`, the two doors report `checkable=false`.
  Worth noting an objection that was wrong: `FilterChip` was rejected once over
  its elevation. Every flat elevation token is `Level0` except hover, which a
  touchscreen never raises.

  **This has now happened three times, which makes it a pattern rather than a
  slip.** The day circles declared `Role.Checkbox` from the start; the effect
  chips did not; and the choice sheets did not either - every option came back
  `checkable=false selected=false`, so which one was chosen existed ONLY as a
  fill colour and a check mark. They are `Modifier.selectable(role =
  Role.RadioButton)` in a `selectableGroup()` now, measured after: all four
  report `checkable=true` and the chosen one `checked=true`. The rule to apply
  when building any new control: if state is visible, it must be in the
  semantics, and the way to find out is to read it off the device rather than
  the source.

- **The "right now" readout is a status PILL, and finding that out found a bug
  in what it said.** It reports `getCurrentInterruptionFilter` - the one thing
  on any screen that is not the app's own belief, and the whole premise here,
  since a vendor can accept a request and quietly ignore it. It lives inside the
  "What can wake you" card, gated on `runningNow`, because that is the card
  which asks for Do Not Disturb and this is the evidence it happened.
  It used to be one sentence whose INK went `cta` when the phone disagreed. Two
  things were wrong with that. Colour alone says nothing to anyone reading shape
  and cannot be asserted in a test. And the words were wrong: the failure case
  reused `filter_all`, "Do Not Disturb is off. Everything is allowed.", which is
  the opposite of what happened - it was not off, it was ignored. The app's
  headline failure printed the reverse of the truth for as long as it existed.
  Now: a tonal pill with same-hue ink carrying the verdict, and a sentence under
  it saying what that means - the grammar Google Health uses for "In range" and
  "Out of range", measured on this phone. `filter_ignored` exists for the case
  that had no words of its own. Three treatments: `selectFill`/`onSelect` when
  the phone is doing what was asked, `veil`/`onSurfaceLow` when Do Not Disturb
  was not asked for, and `alert`/`onAlert` for the failure - a new pair on
  Material's error-container tones. It was matched to the off-lamp's red so the
  two reds on screen would be one red; the lamp is gone now, so it is the ONLY
  red, which is the stronger position - a red here means exactly one thing.
  `errorContainer` was another unset scheme role.
  **It has a rim now, and the reason it needed one is the general lesson.**
  All three pills were left bare except the two that gained borders with the
  switch; the alert pill was excused on the grounds that a failure state is
  already the loudest thing on screen. That was true at 1.14:1 against a tone-95
  card and false at 1.02:1 against a tone-91 one - the same lightness, leaving
  only HUE to separate a pill from its card, which is the one thing this app
  does not let a state rest on. `alertBorder` fixes it: 2.69:1 in Dawn, 2.74:1
  in Dusk.
  The trap to carry forward: `alert` is Material's `errorContainer` tone, 90
  light and 30 dark, and THAT TONE ASSUMES A SURFACE AT 95 OR LIGHTER. Darkening
  a surface silently invalidates every container tone borrowed from a spec that
  assumed the old one. Nothing warns you; the pill simply stops being a pill.
  A test asserts the ignored case says `filter_ignored` and NOT `filter_all`.
  That test was impossible while the difference was a colour.
- **Every switch on Home is a PLAN, and for months nothing said so.** Reported
  as the sharpest version: with the master switch OFF, the Do Not Disturb switch
  and the four effect switches are fully live, so a person turns Do Not Disturb
  "on" and reasonably expects to be left alone tonight. They are not inert -
  each tap is written to prefs and reaches the phone when the window opens - so
  they are not "unavailable" in the sense every guideline is about. They are a
  plan that looks like a state.
  The obvious import is Google's own settings dependency pattern - parent switch
  disables the dependent controls - and it is wrong HERE for two reasons.
  `Prefs.enabled` defaults to FALSE, so a fresh install opens with the switch
  off and disabling the rest would mean a new user can configure nothing until
  they arm a schedule they have not set up yet. And M3 disabled content is ~38%
  opacity and explicitly exempt from contrast minimums, which undoes the work
  that put `onSurfaceLow` at 6.9:1 / 7.7:1 for an app read in a dark room -
  and disabled controls are not focusable, so TalkBack loses them entirely.
  So: nothing is disabled, and `planNote` says the truth at the foot of each
  card instead, in the slot the "right now" readout already established. It is
  a `NoticeStrip` - full width, no inset, at the TOP of the card, drawn in
  `selectFill` behind `onSelect`. Three things there were reversed on
  31 Aug 2026 after use, and all three reversals are worth keeping.
  It sat at the FOOT, which meant the caveat arrived after the rows it
  qualifies had already been read. A warning you meet on the way out has failed.
  It was pitched as a RUNG rather than an alert - 1.32:1 / 1.11:1 from the card,
  the same separation `raise` has from the page - which was a deliberate choice
  and the wrong one: it did not attract attention, which for a warning about
  controls that look live and are not is its only job.
  And it has no colour of its own. **This took four shapes and the first three
  are worth recording as costs, not as history.** A themed `notice` pair drifted
  34 tones apart between the two schemes, because separate values are exactly
  what lets that happen. A single shared pair taken from `Arc.dawn` - the sun on
  the dial, asked for by name - could not then be tuned per theme at all. A
  re-themed pair could, and immediately hit a DEAD BAND: between tone 48 and 58
  on that hue neither ink clears 4.5:1, the sun's own rays falling to 4.21 and
  white reaching only 2.85, so "a bit darker" had two landing zones and nothing
  between them. The fourth shape has none of those problems by construction:
  the band is `selectFill`, the same token as a selected day, the preset pill
  and a checked switch. There is nothing to keep in sync because it is not a
  copy of the accent, it IS the accent. 4.90:1 in Dusk and 6.79:1 in Dawn, at
  2.07:1 and 1.26:1 against the card.
  The honest cost, so nobody has to rediscover it: the band is the colour of the
  controls it warns about. That was sharper when the accent was GREEN, which
  conventionally reads "on"; the accent is the arc's slate now, so the band no
  longer says the opposite of what it means - but it still says "selected", and
  the WORDS are what do the semantic work, which is where that work belongs.
  It earns its place by being unmistakably part of this app rather than a
  fourth hue on a screen that already carries the arc's two and the alert red.
  **Its text is `labelLarge`, not `bodySmall`, and that was asked for.** Same
  14sp, weight 600 rather than 400 - so it costs no height and adds no
  `fontWeight` override outside `Theme.kt`, which is a rule this app holds to.
  At 400 the warning was the same weight as the supporting lines it qualifies,
  which is the typographic version of the mistake the band's POSITION already
  fixed once: a caveat that arrives looking like the thing it is qualifying has
  not been read.
  A lesson that generalises past this band: a dark warm colour IS brown. Three
  rounds were spent trying to make a dark orange strip not look brown, and the
  only exits are to strip the chroma out (reads warm grey), move the hue off
  orange, or stop being dark. That is perception, not a palette bug.
  **The reason originally recorded for that no longer holds, and the correction
  matters more than the band does.** Colouring the TEXT was rejected as measured
  shut: `cta` as ink was 2.49:1 on a card in Dawn and the old `lampOff` 3.80:1,
  both
  under the 4.5:1 body minimum and under even the 3:1 large-text exemption - the
  cream could not carry an accent hue as text, which was the WAKE UP finding a
  second time. On the warm white those are 5.71:1 and 4.92:1, and both pass. The
  band stays because area was always the better answer for something that must
  be noticed, not because coloured text is still impossible. Anyone re-deriving
  "the ground cannot carry an accent as text" from this paragraph would be
  quoting a constraint that expired on 31 Aug 2026. See `NoticeStrip` in
  MainActivity.kt for the rest of the numbers. Three
  readings, because the reason differs and a person can act on the difference -
  switched off, armed and waiting, and armed with nothing to run. It is drawn
  only when NOT running: while the window is live the Do Not Disturb card has
  the phone's own answer to show there instead, and the effects card has the
  screen in front of you.
  Note the half that was NOT reported and is the same fault: armed but before
  the window opens, the master switch is GREEN and still nothing is silenced.
  Any fix that only handled "off" would have left that standing.
  `section_how_the_screen_looks` has a second, future-tense form for the same
  reason - "how the screen LOOKS" is a claim about now, and it is false whenever
  the window is not running, which is most of the time anyone reads it.

- **`java.time` gives you the NOMINATIVE and nothing else, which is not a
  sentence in Russian or Ukrainian.** `getDisplayName` returns "среда" for both
  `FULL` and `FULL_STANDALONE` - checked, not assumed - so the window sentence
  shipped as «С 22:30 среда до 8:30 четверг» and the plan note as «заработают
  в 22:30 среда». Neither is grammatical, and English hides it completely
  because English does not decline.
  The weekday is a RESOURCE now, in TWO sets, because the two sentences need
  different cases and one set will not do:
  - `day_span_*` for the window sentence, where the time comes first and the day
    reads as a possessive - "from Wednesday's 22:30". Genitive: «С 22:30 среды
    до 8:30 четверга».
  - `day_note_*` for the plan note, where the day is a point in time and needs
    its own preposition: «заработают в 22:30 в среду».
  The preposition lives INSIDE the string deliberately. Russian alternates в/во
  by the following sound - «во вторник» - and no format string can express that;
  put the preposition in the format and Tuesday is wrong forever.
  `day_today` / `day_tomorrow` are adverbs, decline for nothing, and serve both
  sets - which is also why the bug only ever showed for a window more than a day
  out, and why it survived this long.
  The test asserts PROPERTIES, never the wording: English must MATCH java.time,
  Russian and Ukrainian must differ from it, and their two sets must differ from
  EACH OTHER - that last one is what catches one set pasted over the other, and
  it was confirmed by doing exactly that and watching it fail. A test on the
  literal strings would only prove the translation equals itself and would have
  to be edited by anyone improving the phrasing.

- **A clock time with no day word reads as TODAY, and that has now cost two
  fixes.** The window sentence was the first. The plan note was the second,
  reported within minutes of shipping: the app bar said "Starts in 35h 20m" over
  a note saying "None of this is in effect until 12:00 PM", which are both true
  and read as a contradiction. `dayWord` is lifted out of `windowSentence` and
  shared now, so the two cannot disagree on one screen - and the next thing that
  prints a time has one obvious function to call.

- **The Do Not Disturb row has no supporting line, and that is the fix rather
  than the omission.** It used to assert what gets silenced - "Calls, messages
  and alerts stay quiet", later shortened to "Calls and alerts are quiet" - while
  the row DIRECTLY BELOW it reported the truth dynamically: "Alarms, calls, and
  2 more". Allow calls from starred contacts and the card contradicted itself,
  one line apart. Reported as exactly that. Any static sentence there can only
  duplicate the dynamic one or disagree with it, so there is none: the row is a
  one-line `ListItem` at 56dp. It is also now immune to the wrapping trap below,
  since a row with no supporting text cannot become a three-line one.

- **An M3 `ListItem` centres its trailing content on a two-line item and
  TOP-ALIGNS it on a three-line one**, and that is not configurable - the layout
  hard-codes `y = if (isThreeLine) topPadding else CenterVertically.align(...)`.
  So a supporting line long enough to WRAP silently moves the switch off its own
  row's centre: Do Not Disturb's sat 56px high, Media sounds' 35px. Reported as
  "the toggle is not vertically aligned", which is exactly what it was.
  Always-centring was considered and rejected: it would mean abandoning
  `ListItem` for a hand-rolled row that copies M3's metrics, to deviate from M3
  on the one line we had just adopted it for. The fix is to keep rows at two
  lines instead, which is what a list item's supporting text is for. That is
  enforced rather than eyeballed: an allowlist row has ~151dp beside a 44dp
  avatar and a switch, about 24 characters, and `BUDGET-EACH 24` in
  `values/strings.xml` makes `check_translation.py` fail a string that would push
  a row back to three lines.
  The Alarms row stays three lines deliberately - it carries no trailing content,
  so nothing can be misaligned, and its sentence is the point of the row.

- **Settings was the last screen still doing everything its own way**, and it
  had three left edges: section labels and prose at 24dp, the language row at
  40, the theme radios at 80. The cause was a workaround. Its rows were bare
  `ListItem`s rather than cards, so the screen padded 8dp and added 16 back onto
  every non-row element to line the two up - a compensation that had to be
  remembered at each call site, and was not: ABOUT, its body and the version
  line never got it. The rows live in cards now, like every list in the app,
  which puts the ListItem's own 16dp INSIDE the card and lets the screen use the
  usual 24dp. Measured after: 24dp for labels and prose, 80dp for every row.
  `BackRow` is deleted with it - both detail screens carry Back as a `TopAppBar`
  navigation icon, and it had no call sites left.
  `Section(rule = false)` exists for the first block on a screen: there is
  nothing above to divide it from, and a rule directly under the app bar's own
  edge is just a second line.

- **The translation checker never measured English.** Budgets are declared in
  `values/strings.xml` and enforced against the file being translated, so the one
  language nobody checked was the one the numbers were written in: "When the
  screen turns off" sat 3 characters over its own limit and passed every run. The
  source is measured now too, and the check was confirmed by making an English
  string too long and watching it fail - a check that has never been seen to fire
  is not yet a check.

- **`RadioRow` is the one row in the app that is NOT a `ListItem`, deliberately.**
  A ListItem's height comes from tokens by line count - 56dp for one line - and
  Compose M3 offers no density parameter, so three theme options in a card were
  three 56dp boxes around three 20dp radio buttons. The rows were already flush;
  all the air was inside them. Reported as too much vertical separation.
  It is a `Row` at `heightIn(min = 48.dp)` now, which is Android's touch minimum
  and so as tight as a row of this kind may be. Note the leading `Box(24.dp)`
  around the button: `RadioButton` otherwise claims its own 48dp interactive size
  and pushes the label 24dp right of every other row on the screen. Measured
  after: 48.0dp rows, labels still at 80dp with everything else.

- **A duration under an hour drops the hour, rather than printing "0h".** Both
  places that show one - the dial centre and the app bar's status line - go
  through `span()`, so it is one change. The minutes stay zero-padded WHEN there
  is an hour ("5h 05m"), so the countdown does not change width every ten
  minutes, and unpadded when there is not ("5m"), where there is no column to
  hold. It also buys width on the line that needs it most: the status line is
  capped at one line and TRUNCATES rather than wraps.

- **`bodySmall` is a label role, not a paragraph role, and its leading was set
  for a paragraph.** 14sp on 20sp is a ratio of 1.43 - looser than M3's own
  bodySmall, which is 12/16 at 1.33 - and every one of its four call sites is a
  short supporting line: a row's subtitle, the two captions under the day row,
  the right-now readout. When one of those wrapped, which in Russian and
  Ukrainian they do, the two lines read as separate sentences with a gap rather
  than one sentence running on. Reported as exactly that. It is 18sp now; prose
  is `bodyLarge` and keeps 20. Measured: a two-line subtitle went 36.9dp to
  34.9dp.

- **Four hand-built sections, four different rhythms.** A section is a rule, a
  name and the block it introduces, and each of the four was assembled by hand
  at its call site - so the gap between the LABEL and its content came out as
  `TIGHT` under "which days", 18dp under "what can wake you", `TIGHT` again
  under "how the screen looks", and something else again on the allowlist.
  Nothing chose those numbers: they were whatever the enclosing `Column`'s
  `Arrangement` happened to be, which differed because some sections wrapped
  their content in an inner Column and some did not. Reported, fairly, as
  "really ugly, this must be unified".
  `ui/Section.kt` states the rhythm once and the call sites cannot disagree:
  GROUP above the rule, GROUP below it, GROUP between the name and what it
  names - "what can wake you"'s spacing, chosen by eye against the other three -
  and `TIGHT` only for gaps WITHIN a block. Measured after: 18.0dp under every
  label on both screens.
  Note the gap ABOVE the rule is deliberately the PARENT's: a Column's
  arrangement adds it whether the section wants it or not, so adding one inside
  as well simply doubled it. Both screens' columns are `spacedBy(GROUP)` for
  that reason, and `GROUP`/`TIGHT` live in `Section.kt` rather than being
  redeclared privately per file.

- **Rows are a GROUPED LIST now, not a divided card.** Every list on Home and
  the allowlist was one `Surface` with `HorizontalDivider`s between the rows.
  Each row is its own `Surface` instead, spaced 2dp, with the group's OUTER
  corners at `CORNER` and the corners BETWEEN items at 6dp - so it still reads
  as one block made of parts. `ui/Section.kt` owns it: `GroupedList(color,
  items)` plus `listItemShape(i, n)`.
  The corner asymmetry is the whole trick and it was not the first attempt. With
  uniform corners and a 6dp gap it read as several unrelated cards, which is the
  obvious objection to separating rows at all - and the objection is right for
  that version. M3 states the same idea in its connected-button-group tokens:
  a large `ContainerShape`, a small `InnerCorner`, `BetweenSpace` 2dp. At 2dp
  the separation is a hairline of the PAGE showing through.
  Two things fell out that were not the point. It retires the question of what
  colour a divider should be, because there is no divider left - and that
  question had just cost two rounds. And the vertical cost, which was the main
  argument against, is 165dp to 175dp for three rows: ten. That argument was
  wrong and was made confidently.
  Items are passed as a LIST, not a trailing lambda, because each one's shape
  depends on how many there are and Compose cannot count children it has not
  composed. `buildList` at the call site keeps conditional rows readable - the
  notice strip is an item, so the top outer corner belongs to whichever row is
  actually first, and a hidden ambient row just makes the group one shorter with
  the corners re-forming around what is left.
  NOT converted, deliberately: Settings' theme radios. A radio group is ONE
  control - the choice sheets do not divide either - and splitting it into three
  containers would say the options are three separate things. The permission
  panel and the boot notice stay plain cards for the same reason: they are one
  container with padded prose, not a list.

- **One row, six implementations.** `AllowRow`, `EffectRow`, two rows written
  inline on Home, `LinkRow` and `PermissionRow` were the same conceptual thing
  and agreed about nothing: leading element, trailing element, subtitle style,
  container, and the "go here" affordance, which existed at 22dp, at 24dp, and
  as a literal `›` sized by borrowing `titleLarge`. They are all `ui/Rows.kt`
  now, on M3's `ListItem`.
  Two things are passed in rather than inherited from M3, deliberately.
  TYPOGRAPHY: `ListItem` defaults to BodyLarge headline and BodyMedium
  supporting; this app's scale puts row titles at `titleMedium` and reading text
  at `bodySmall`, which is measured (see the four-sizes note), so slot content is
  ours. CONTAINER: M3 list items sit on `surface`, these sit inside the app's
  `raise` cards, so the container is transparent and the card shows through.
  What DOES come from M3 is the metrics, and they moved: rows measured 64.0dp on
  two lines against the spec's 72, and 84.0dp on three against 88. Both are on
  spec now, measured on the device.
  Note the one screen where this needed care. Settings has no cards, so its bare
  `ListItem`s bring their own 16dp start padding - which stacked with the
  screen's 24dp and put the radio buttons 16dp right of their own section
  labels. The screen pads 8dp now and the non-row elements carry the other 16,
  so list text and subheaders share one inset, which is where M3 puts them.
  Still hand-rolled and deliberately so: the **Repeat** row, which has no card
  behind it and therefore no ripple (`indication = null`) - see the clip note
  above for why that matters.

- **The launcher icon is a FLAT WHITE tile, and the claim this entry used to
  open with - "no flat colour can serve it" - was overturned by asking for the
  one flat colour nobody had tried.** Read the reversal before re-deriving the
  old rule from the evidence below, because the evidence is all still true.
  The foreground spans tone 27.7 at the crescent's night horn to 73.4 at its
  dawn horn, so any single background value does contrast with one end and
  swallow the other. `#12161B` read the dawn horn at 8.79:1 and the night horn
  at 1.79:1 - a black tile with an orange fingernail on it, reported as the
  darkest icon on the launcher it sat in. That was answered with a diagonal
  GRADIENT, light behind the cool horn and deep behind the warm one, and it
  worked.
  **White inverts the problem instead of repeating it, and that is the whole
  difference.** On white the night horn holds 10.15:1 and the DAWN horn is the
  one swallowed, at 2.07:1. Black's swallowed end could only be rescued by
  LIGHTENING the night horn, which is to stop it being night. White's can be
  rescued in the artwork: a dawn orange deepened from tone 73 to 62 is still
  unmistakably a dawn orange. So the warm half of `ic_launcher_foreground.xml`
  moved with the ground - `#D4814C` at the tip, 2.98:1 on white, ember deepened
  to match. One end of that ramp had somewhere to go and the other never did.
  Note the app's own dial keeps the ORIGINAL `Arc` stops, deliberately: the dial
  is ~200dp on a known ground, the icon ~40dp on someone's wallpaper.
  **And the strongest argument for white is not aesthetic.** Sampled off the
  render, the worst warm-versus-ground pair in the gradient version was 1.00:1 -
  orange and blue-grey at identical luminance - in the shipped icon as much as
  in any variant. On a tinted ground no ratio could referee this artwork at all;
  on white every pair is a real number again. It also puts the icon in the same
  family as Google's own on this phone, which are white tiles with a saturated
  mark: Drive, Files, Gmail, Google, Maps, Meet, Messages. Confirmed in the
  drawer beside Gemini, Gmail and Google.
  **The mark is centred and scaled 0.92, the crescent 0.86 again inside it, and
  how that was got wrong first is the useful half.** In the 72dp the launcher
  shows, the mark sat with margins of 3.5 left, 3.5 right, 3.5 top and 15.0
  BOTTOM - hugging three edges with a gap under it, reported as asymmetric. It
  is 6.2 / 6.2 / 8.8 / 16.8 now, and the second scale opens a visible gap
  between the moon and its ring where the two used to nearly touch at the lower
  left. Both are transform GROUPS; no path data changed. The crescent's scale
  shares the arc's pivot, which is what keeps its outer disc concentric.
  **The top and bottom margins are deliberately unequal, and the version that
  made them equal shipped first and was reported within minutes.** Balancing the
  BOUNDING BOX put the INK 3.7dp below the tile centre, with 36% of it above the
  centre line - seen immediately as the icon sitting low. The box lies about
  this mark: the arc's 113 degree gap is at the bottom, so the lower half of the
  box is mostly empty and centring the box drags the mass down after it. What
  ships centres the ink CENTROID instead, which is what optical centring means.
  The general rule, since this file now has the same lesson twice in one
  artwork: for any shape that is not roughly convex and even, measure the INK -
  centroid and the above/below split - not the bounding box, and not the
  centroid of one component (which is the crescent mistake recorded above).
  **The crescent's proportions are the DIAL HANDLE'S, carried as ratios.**
  `BedtimeDial.drawHandle` draws the bedtime moon as r*0.50 bitten by r*0.44 at
  (+r*0.26, -r*0.20) - a bite of 0.880 R offset 0.656 R at -37.6 degrees,
  leaving it 0.776 R thick at the thickest. The icon's own numbers were a bite
  of 0.940 R offset 0.439 R, so 0.499 R thick: bigger AND closer, which is what
  made it thin, and it was reported as not matching the dial. Ratios are what
  transfer between the two - the radii differ by an order of magnitude, so no
  absolute number here is portable.
  **And the optical centre MOVES when the artwork does.** The arc is 4dp now,
  thickened INWARD - the outer extent is pinned at 32.6dp by the launcher's
  33dp safe circle, so the path radius went 31.1 to 30.6 and the four waypoints
  were recomputed at that radius, preserving their ANGLES. Because the gap is at
  the bottom, a thicker arc adds ink almost entirely above the centre line, and
  `translateY` went 2.76 to 3.88 to keep the ink centroid on centre - and then
  back to 2.51 when the moon was fattened to the dial's proportions, which adds
  ink low and left. Three changes, three different translates. Any future change
  to the stroke, the crescent, the gap or the scales has to re-solve it; none of
  them are independent, and the value is meaningless without the rest.
  Note the gradient lives inside that group and scales with it, so
  `render_icon.py` has to scale the crescent's gradient AXIS too or the exported
  `docs/icon.png` stops matching the drawable it is supposed to depict.
  Centring the CRESCENT's own centroid was the obvious first move and is wrong.
  A sickle's mass sits nowhere near where it looks centred - 6.1dp left and
  9.1dp down of the tile centre - so moving that point to the middle shoves the
  shape up and right into the ring. What reads is that the crescent's outer disc
  is concentric with the arc, and it already was.
  And EVEN margins on four sides are not achievable, so do not chase them: the
  mark is 65dp wide against 53.5dp tall, a near-circle cut at the bottom, and a
  square tile cannot give a non-square mark equal air all round. The target is
  vertical CENTRING plus adequate side margin.
  What follows is the gradient's own record. It is kept because the two
  measurements in it are about the ARTWORK, not the ground, and would be needed
  again by anyone reintroducing a tinted tile - both were wrong on the first
  attempt.
  THE AXIS came off the rendered icon rather than the path data - the cool half
  of the artwork centres at x=0.35 of the tile and the warm half at x=0.63, so
  the split is nearly horizontal. Reasoning from the crescent's -32 degree
  rotation gave 45 degrees and put the light in the wrong corner. Sampling a
  screenshot for the CENTROID of each hue is a two-minute check and settles it.
  THE RAMP stays inside one hue family. A warm-light to cool-dark gradient loses
  its chroma in the middle - measured at 7 against the 18 a single-hue ramp
  holds - so the icon came out grey-brown through its centre while both ends
  were fine. Check the MIDPOINT's chroma, not just the stops.
  The two ends are NOT equally free, which is the practical note: the light end
  holds 9.0:1 against the cool horn where it needs 3, and the deep end only
  2.61:1 against the warm one. So "make it lighter" is answered by moving the
  LEFT stop; move the right and the dawn horn starts dissolving into its own
  ground.
  That 2.61 is below the 3:1 a graphical object is meant to hold, and it is
  deliberate rather than unnoticed. The step that clears the guideline was built,
  installed and compared on the device, and this one was chosen with the
  shortfall on the table: the dawn horn is a ~40dp shape, not a hairline, and it
  separates from this ground by HUE as much as by luminance - orange on blue -
  which a WCAG ratio does not model. The floor exists for small parts whose only
  cue is lightness. It is not licence to go under 3:1 elsewhere, and the numbers
  are in the drawable so the next person can disagree from the same evidence.
  **Asked to be lighter again, and the left stop had nothing left to give** - it
  is tone 96.2. So the lift went into a THIRD stop at offset 0.55, tone ~85,
  which lightens the two thirds of the tile the eye reads as "the colour of the
  icon" while leaving offset 1.00 exactly where the dawn horn needs it. Moving
  the right stop was built and compared: tone 50 takes the horn from 2.61:1 to
  2.19:1, and there is no version of a LIGHT ground under a light horn that
  works, which is the whole reason the ramp runs diagonally.
  **And that 2.61 describes one pair, not the worst one.** Sampled off the
  render rather than reasoned about, the worst warm-artwork-versus-ground pair
  in this icon is 1.00:1 - orange and blue-grey at identical luminance - and it
  is 1.00:1 in the SHIPPED icon too, not something the lighter variants
  introduced. So no contrast ratio can referee this artwork at any stop value.
  The hue argument above was right and was understated: hue is not helping
  luminance here, in places it is doing the whole job. Compare icon variants on
  a launcher, never in a spreadsheet.
  Note the near-miss in measuring that: the 1.00:1 result looked so wrong that
  the instrument was accused before the numbers were - `contrast()` was tested
  against black-on-white and came back 21.0, correctly. Check the tool, but
  believe it when it passes.

- **Row icons carry one colour each, and getting there found three perceptual
  traps in a row.** `ui/IconTint.kt` holds fourteen pairs, one per row, spaced
  by hue within the SECTION each row appears in - which is where they have to be
  told apart. There is no standard to copy: Apple does not document its own
  Settings colours and Material says nothing, so they are the associations
  people already carry (a phone is green, a calendar orange, alarms red).
  Tone and chroma are ONE pair for all of them - Dawn 48/58, Dusk 78/46 - so
  they differ by hue and agree on weight, which is what stops fourteen colours
  reading as a rainbow. The three exceptions are corrections, not breaks:
  TONE 30 SHOWS NO HUE. The first version put them at Material's container tone
  and every one read as the same dark blob. Reported on sight. A hue simply does
  not read that deep, whatever chroma it carries, and no amount of chroma fixes
  it - that is what moved the pair to 48.
  RED NEEDS MORE CHROMA THAN THE REST. Dnd and Alarm carry 92 where everything
  else has 58, because red is where sRGB has least room: M3's own error is
  chroma 76, Material Red 700 is 83, iOS systemRed is 95. At 58 red reads as
  brick. Matching PERCEIVED intensity means a bigger number, the same argument
  this file already makes for the app's three different greens.
  YELLOW HAS THE MIRROR PROBLEM AND CANNOT WIN. Amber only reads as amber high
  up: measured at hue 80 the crossover is tone 58, which is exactly 3.00:1 on a
  card - below it is olive, above it is under the 3:1 a non-text graphic holds.
  There is no third option in that hue, so Reminders left yellow entirely.
  And note Dusk could not follow the reds: at tone 78, hue 25 clamps at chroma
  33.6, the gamut ceiling. All three red candidates produced the identical hex
  there. Every one of these was found by building it and looking, never by
  reasoning from the numbers - the numbers said tone 30 was fine (8:1 contrast)
  and it was the worst of them.

- **Do not hand-draw icons.** Three rounds went into a phone handset built
  from arcs and capsules; it read as the letter C, then a horseshoe, then a
  limp hook. Icon geometry is craft and it is already done: the path data lives
  in `google/material-design-icons`, and `res/drawable/ic_*.xml` are the real
  Google paths, fetched and checked in. Tint at the call site so they follow
  row state. No dependency, ~90 lines of Canvas deleted.
- **`Prefs.ruleId` is the app's only handle on its rule, and losing it strands
  the rule.** "Clear storage", or any restore that drops prefs, leaves the
  system holding a rule we can no longer name: enabled, listed on the phone's
  own Do Not Disturb screen as a second "Gloaming", and unreachable forever. The
  Honor was holding two. `ZenController.sweepOrphans` removes any rule we own
  whose id is not the tracked one, and runs both where an orphan is born (right
  after `addAutomaticZenRule`) and in `reconcile`, above the enabled check -
  a stray rule is just as visible while the app is switched off.
  `ic_dnd` and `ic_allowlist` were added the same way (`do_not_disturb_on`
  and `checklist`, Material Symbols Rounded) when "What can wake you" turned
  out to be the only card whose rows had no leading icon: its text started
  40dp from the screen edge where the card directly below started at 80,
  which is a ragged left edge between two cards on one screen. All three are
  at 80.0dp now, measured.

- **`dumpsys notification` prints the live config AND a `Zen Log:` history, so
  grepping the whole dump reports long-deleted rules as present.** This said the
  orphan sweep had failed when it had worked. Any check of what rules exist has
  to stop at the history: `sed '/Zen Log:/q'` first, then grep. The same shape as
  the encrypted-logcat and the screencap traps - the tool answers a question next
  to the one being asked.

- **`removeAutomaticZenRule` returns whether it removed anything.** Wrapping it
  in `runCatching { }.onSuccess { }` logs a plain `false` as a success, because
  not throwing is not the same as doing the thing. The journal claimed a removal
  that never happened. Log what the call ANSWERED. `setAutomaticZenRuleState`
  and friends are worth the same suspicion.

- **Neither obvious way to fake a withheld boot works.** `am force-stop` before
  a reboot does not reproduce it - with auto-launch ON the Honor delivers
  `BOOT_COMPLETED` to a stopped app anyway. Jumping the clock past the tolerance
  does not either: the time change itself re-triggers the receiver, which
  re-records the stamp and erases the mismatch being tested for. The only real
  test is turning auto-launch off and rebooting. Both false starts looked like
  the detection was broken when it was not.

- **A witness that never moves is not evidence.** Chasing whether Honor honours
  ambient suppression produced seven plausible readouts - wakefulness, display
  state, `aod_doze_state`, SurfaceFlinger layers, window focus, the layer's frame
  counter, and `screencap` - and every one of them read the same with AOD ON as
  with AOD OFF. Any of them would have "confirmed" the first conclusion reached.
  The fix is cheap and should come first: set the ground truth BOTH ways by hand
  and check that the proposed witness actually differs, before using it to test
  anything. The one that survived here was a human tapping the sleeping screen,
  validated by a negative control. Related but distinct from the `DarkCapability`
  error - that probe observed the right field at a time it could not have moved;
  these observed fields that never move at all.

- **A screenshot cannot settle a COLOUR on this phone, and that cost a whole
  review round.** `adb exec-out screencap` grabs the framebuffer before the
  panel's own pipeline, and on the Honor that pipeline is doing real work:
  `dumpsys display` reports `supportedColorModes [0, 7, 9]` with
  `mActiveColorMode=0` - the vendor profile, NOT sRGB (7), though the panel
  supports it and P3 (9) - and `settings get global color_temperature_cie`
  returns per-channel gains `0.99007, 1, 0.975868`, i.e. blue attenuated about
  2.4% and red about 1%. So a warm shift and a vendor gamut mapping sit between
  the PNG and the eye.
  This bit hardest on a pale, low-chroma, COOL green: a fern accent at chroma 16
  on a warm near-white ground measured identical in the capture and the source -
  `#C9DCCE` both - and still looked wrong in the hand. Nothing was broken; the
  instrument was.
  It is the same blind spot as the grayscale transform and the doze layer,
  making three. The rule that follows: a screenshot may COMPARE two captures,
  and may never establish that a colour is right. Hue and chroma decisions go on
  the panel, in the room the app is used in.
  A corollary learned the slow way, over four rounds on the notice strip: when
  the app ALREADY CONTAINS the colour being asked for, match it rather than
  deriving a new one. "Make it more like the sun on the dial" is answerable in
  one step by reading `Arc.dawn` out of the source; it took four because each
  round moved a different axis - chroma, then hue, then tone - against a
  screenshot, while the reference sat on the same screen the whole time. Note
  the near-miss inside it: at tone 82 hue 50 reads as PEACH, so a hue that was
  right the whole way through looked wrong, and the next move was very nearly to
  abandon it. Pale plus weak reads pink; the sun is nine tones deeper and half
  again as saturated at the same hue.
  Note the trap inside the trap: sampling a screenshot and quoting the result as
  though it were the palette value. That happened here - a fill generated at
  chroma 34.0 sampled as 28.3, and the low number was reported as a gamut
  ceiling that did not exist (hue 165 reaches chroma 67.7 at tone 86). It sent
  the light theme to a different hue on a false premise. If a number describes
  the palette, read it from the palette.

- **A debug palette left running is indistinguishable from a shipped bug.** The
  review builds took the accent from an intent extra
  (`--es palette FERN`), and an Activity keeps the intent it was launched with.
  So a capture loop that ends on its last variant leaves THAT variant on screen,
  and the next person to pick the phone up is looking at a colour nobody chose.
  It happened, was reported as "this is not what you shipped", and produced a
  confident wrong self-diagnosis before the intent was checked. Two cheap
  habits: end any such loop with a force-stop and a plain relaunch, and read
  `dumpsys activity recents | grep intent` before believing anything about what
  is on screen.
  The same loops also knock `Prefs.themeMode` - a blind `input tap` from a stale
  `uiautomator dump` lands on the theme radios often enough that it happened
  three times in one session, and `run-as` cannot write prefs back, so each one
  costs a walk through the UI. Check `themeMode` in the prefs dump before
  trusting that `cmd uimode night` decided what you are looking at.

- **`veil` is invisible at 1 dp** despite being the hairline token by name. The
  stepped-time rules used `line` instead; those rules are gone, but the next
  1 dp stroke will hit the same wall. It already did: the divider inside the Do
  Not Disturb card was a hand-rolled `Box` filled with `veil`, measuring 1.02:1
  on `raise` in both themes - not a faint edge, no edge. It is a
  `HorizontalDivider` in `line` now, 1.38:1, matching `SectionRule` outside the
  card. Note that swapping the component alone would not have fixed it: M3's
  divider defaults to `outlineVariant`, which this app wires to `veil`, so it
  would have drawn exactly the same invisible line with more ceremony.
- **`Arc.night` barely registers on a dark ground, and lifting that ground made
  it worse rather than better.** 9.6:1 against Dawn's warm white, but 1.5:1
  against Dusk's surface and 1.2:1 as a fill on `raise`. A hairline looks like
  it starts halfway along; a filled block stops reading as a block. Hence
  `Arc.stopsOn(dark)` and `Arc.nightOn(dark)`, which begin at `dusk` instead and
  reach 2.0:1 on a card. The ring itself keeps `night` — at 17 dp it carries its
  own weight, checked on the phone after the rebuild.
  This is the one place the 31 Aug 2026 rebuild cost something. The arc is a
  fixed four-stop ramp shared by both themes, so raising Dusk's page from tone 7
  to tone 14 walked the ground up towards the arc's dark end while the arc
  stayed put. If the low end ever stops reading, the fix is to lift `night` and
  `dusk` for the dark theme rather than to push the ground back down.

## Build

    ./tools/build.sh     debug APK, full log at /tmp/build.log
    ./tools/deploy.sh    install on the attached device. INSTALLS ONLY - it does
                         not build - but it REFUSES when the APK is older than
                         the sources, which is the trap that used to be silent:
                         `gradlew test`, `lint` and `check` all compile without
                         assembling, so a green run leaves a stale APK and the
                         screenshot proving your fix shows the PREVIOUS build.
                         Cost real time three times in one session before the
                         guard existed. `deploy.sh -f` installs it anyway.
                         The freshness check is mtime against the APK, and
                         `build.sh` touches the APK on success on purpose:
                         Gradle decides UP-TO-DATE by content hash, so a file
                         whose mtime moved without its content changing would
                         otherwise leave deploy refusing forever with build
                         unable to clear it. `build.sh` also no longer runs
                         under `set -e` around gradle - it used to exit before
                         its own error grep, printing NOTHING on the one
                         occasion the output matters
    ./tools/check.sh     what the PHONE thinks: zen state, the rule, our prefs,
                         the next alarms and the journal, side by side. Read-only.
    ./gradlew test       the scheduling core, on the JVM, in under a second
    ./gradlew coverage   JaCoCo over the unit tests, HTML + XML under
                         app/build/reports/jacoco/coverage
    ./gradlew lint       0 errors expected; the 5 warnings left are policy
    python3 tools/check_translation.py app/src/main/res/values-ru/strings.xml ru
    adb shell run-as com.jemcik.gloaming cat files/journal.log

Releases are `.github/workflows/release.yml`: tag it, or press "Create a new
release", and CI attaches `gloaming-<version>-debug.apk` to the release. Release
ASSETS are downloadable without a GitHub account where workflow ARTIFACTS are
not, which is the whole reason releases exist here rather than just CI builds.

Two things about it were wrong until 31 Aug 2026 and are worth not
reintroducing. It listened only for `v*` while this repo's own first tag is
`0.1`, so the tag path had never once fired - 0.1 shipped through the
`release: published` event and a bare `0.2` tag would have done nothing at all.
Both styles match now. And `versionCode`/`versionName` were hardcoded in
`app/build.gradle.kts`, so EVERY release would have shipped versionCode 1 -
which is not cosmetic, because Android refuses to install an APK whose
versionCode is not higher than the installed one. 0.2 would have failed with
"app not installed" for everyone who already had 0.1: the release that upgrades
nobody. CI derives both from the tag now and passes them as
`-PgloamingVersionName` / `-PgloamingVersionCode`, with the hardcoded values
left as the fallback for local builds. `MAJOR*10000 + MINOR*100 + PATCH` keeps
it monotonic and starts above the 1 already published: 0.1 is 100, 0.1.1 is 101,
1.0 is 10000.

A tag that is not `MAJOR.MINOR[.PATCH]` now FAILS the release rather than
publishing an APK whose version is a lie. That is a deliberate behaviour change:
before, "nightly" would have published happily.

**A correct versionCode was necessary and not sufficient, and only a SECOND
release could reveal it.** CI published DEBUG APKs, and a runner generates a
throwaway debug keystore per run - so 0.1 and 0.2 went out signed by different
certificates (`789ee6a5...` and `7fe1791e...`) and Android refuses to install
over a package signed by a different one. Measured on the phone rather than
argued: `INSTALL_FAILED_UPDATE_INCOMPATIBLE, signatures do not match newer
version`. A stable signing identity is the other half of an upgradable APK, and
0.1 and 0.2 are both stranded - anyone holding either has to uninstall once,
losing their schedule, before any later release will install.

Releases are signed now, from four repository secrets
(`GLOAMING_KEYSTORE_BASE64`, `_KEYSTORE_PASSWORD`, `_KEY_ALIAS`,
`_KEY_PASSWORD`). Two things about the shape are deliberate. With no keystore
the release build is left UNSIGNED rather than falling back to the debug key,
because a fallback produces something that looks releasable and cannot be
upgraded - the exact bug being fixed. And the workflow refuses to publish an APK
it cannot verify: AGP names an unsigned build `app-release-unsigned.apk`, so the
filename alone catches a missing key, and `apksigner verify` catches a bad one.
Both halves were confirmed locally against a throwaway key - unsigned gives
apksigner exit 1, signed gives exit 0 with the expected certificate and the
version from the tag properties.

The keystore never enters the repo. `.gitignore` has refused `*.jks`,
`*.keystore` and `keystore.properties` since before any existed, and the comment
there is the reason: a key committed once has to be rotated, because rewriting
history does not un-publish it.

Minification stays OFF through all of this. Turning it on is a real change to an
app that runs unattended overnight and belongs in its own pass with its own
testing, not riding along with signing.

The derivation reads `BASH_REMATCH`, so the step declares `shell: bash`. Testing
it in the local shell first gave versionCode 0 for every tag, because zsh sets
`$match` instead and the arithmetic silently used empty strings - the same class
of trap as the witnesses that never move. Verified under real bash afterwards.

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

**Honor's AOD has MODES, and its schedule lives in Settings.Secure after all —
1 Sep 2026.** This is the difference from LineageOS, which offers a plain on/off:
Honor's "Display mode" screen offers *Tap to show*, *Scheduled* (with Start and
End times) and *All day*. Selecting each one moves keys, and selecting Scheduled
CREATES two pairs that do not exist until then:

| key | meaning |
|---|---|
| `aod_display_mode` | 0 = tap to show · 1 = scheduled · (all day untested) |
| `aod_display_type` | 2 in tap-to-show, 0 otherwise — NOT a simple master gate |
| `aod_start_hour` / `aod_start_minute` | schedule start, 24-hour, absent until set |
| `aod_end_hour` / `aod_end_minute` | schedule end, 24-hour, absent until set |
| `aod_scheduled_switch` | still unexplained; sat at -1 throughout |

Two things follow, and only the first is settled.

**Settled: the keys are reachable.** Writing `aod_start_hour` etc. from adb lands
in `Settings.Secure` and Honor's own Settings screen reads the written values
back and displays them. So the schedule is not sealed inside `com.hihonor.aod`'s
private storage, and `WRITE_SECURE_SETTINGS` — which `AmbientControl` already
requires — would be enough to write it. That materially revises the old
conclusion that the scheduling route is shut: `CommonReceiver` is shut, the
SETTINGS route is not.

**Also settled, an hour later: the service HONOURS a schedule written from adb.**
`aod_doze_state` was useless for this — it read 1 in every arm — so the witness
was a pair of human eyes on the sleeping screen, four arms, at 02:08:

| arm | schedule (written from adb) | screen asleep shows |
|---|---|---|
| A control | 00:00–23:59, covers now | **AOD lit** |
| B | 09:00–12:00, excludes now, written WITHOUT touching the phone | still lit |
| B′ | same keys, after one wake + sleep | **dark** |
| C reverse | 00:00–23:59 again, after wake + sleep | **lit again** |

Arm A is what made the rest legible: it proved Note 2's "disabled when the phone
sleeps at night" was NOT suppressing anything at 02:08, so the later darkness in
B′ could be attributed to the schedule rather than to the confound. Arm C is the
reverse control — without it, B′ going dark could have been a timeout, the
battery, or the night rule arriving late.

Two design facts fall out. **There is no ContentObserver**: B left the AOD lit
even though the keys had already changed, so the service does not watch them.
And **it re-reads on a screen transition**: one wake-and-sleep was enough. Which
of the two edges triggers it was not isolated — both happened together — and an
implementation would want to know, because writing the schedule at a window
boundary while the phone is asleep would not take effect until the next
transition. For a bedtime app that is close to harmless, since someone is
usually about to pick the phone up, but it is real behaviour and not a detail to
discover in the field.

Not tested: whether the schedule survives a reboot. `Settings.Secure` persists,
so it almost certainly does, but nobody has watched it happen. Also still
unexplained: what Note 2's night rule actually means, given it did not fire at
02:08, and `aod_scheduled_switch`, which sat at -1 throughout.

Why this matters: `AmbientControl` currently flips keys at each window boundary,
which is what makes an uninstall mid-window leave the AOD off and lets a user's
own change be overwritten at window end. Setting Honor's own schedule instead
hands the boundaries to the vendor's service and removes both. It costs no new
permission — `WRITE_SECURE_SETTINGS` is the same grant `AmbientControl` already
needs. NOT BUILT, and deliberately so: it is a different shape of change from
what this app does elsewhere, it would want the reboot question answered first,
and the honest comparison against the existing key-flip has not been made. The
finding is the value here, not a mandate.

**`AmbientControl` works from Scheduled mode too — a worry, raised here and then
tested rather than left standing.** Its four keys were derived entirely from TAP
TO SHOW mode, and a phone sitting in Scheduled already has `aod_display_type=0`
and `aod_touch_time=0`, so only `aod_switch` and `fingerprint_touch_time` move —
and `aod_switch` alone was recorded above as doing nothing. That looked like a
defect shipped in 0.5.

It is not. Measured by eye, single variable, Scheduled mode with the window
covering the current time: with `aod_switch=1` the AOD is lit; with the app
suppressing (`aod_switch=0`, everything else unchanged) it is dark; the app's own
restore brings it back and clears `ambientSaved`. The saved string records the
truth of that mode — `aod_switch=1|aod_touch_time=0|fingerprint_touch_time=5|aod_display_type=0`
— so the restore is correct rather than lucky.

Which refines the `aod_switch` claim above rather than contradicting it.
`aod_display_type` is the gate for TAP TO SHOW; `aod_switch` is the master that
bites in SCHEDULED. Neither is "the one that matters" on its own — it depends on
`aod_display_mode`. Writing all four zeroes whichever key is load-bearing in the
current mode, so the existing implementation is robust across modes by
construction, even though the reasoning recorded for it only ever covered one.

**The `canControl` branch, verified after the HomeState refactor, 1 Sep 2026.**
`ambientRow` is `ambientZen || AmbientControl.canControl(ctx)`, and the refactor
moved it into `ScreenEffectsSection`. Only the FIRST branch had been exercised
since — on the OnePlus, where the zen effect works — so the vendor-keys branch
shipped in 0.5 untested. Checked on the Honor with `WRITE_SECURE_SETTINGS`
granted: the row is drawn, and a full cycle behaves as designed. Enabling it
mid-window put all four keys to 0 with `ambientSaved` holding
`aod_switch=1|aod_touch_time=5|fingerprint_touch_time=5|aod_display_type=2`, and
journalled `ambient off (was ...)`; ending the window restored all four to
exactly those values, cleared `ambientSaved`, and journalled
`ambient restored (...)`. The journal ORDER is evidence in itself —
`ambient restored` precedes `zen state -> OFF`, which is the hook sitting at the
top of `ZenController.setActive`, where every path funnels through it.

Read directly rather than inferred: `doze_always_on` is **null** on the Honor, so
`AmbientCapability.isSupported` falls through to the parallel-AOD check, finds
`aod_switch`, and returns false. The ordering inside that function — AOSP key
first, exclusion list second — is therefore never exercised here, but it stays
load-bearing: a phone shipping BOTH keys would get a live switch that does
nothing, and nothing in the code says so out loud.

One key seen for the first time and NOT understood: `aod_scheduled_switch=-1`.
Honor evidently has some notion of a scheduled AOD in Secure settings, and no
companion start/end key is visible while it sits at -1. Not investigated. If a
scheduled always-on for Honor is ever revisited, start there rather than at
`CommonReceiver`, which is `exported=false` and shut.

**Second device, 30 Aug 2026.** Everything below was an audit until a OnePlus
CPH2653 running LineageOS 23.2 (Android 16, SDK 36) was attached. It installed
and ran first time, with the phone in Ukrainian on a 12-hour clock - a
combination this app had never rendered on hardware - and the times, the day
row, the captions and the chips all came up correct.

What the two phones AGREE on, which was not predictable:

- `ZenDeviceEffects.setShouldUseNightMode` is not honoured on either. The
  comment in `DarkCapability` claimed AOSP applies it; that was a guess and it
  was wrong. Dark theme is a door on both, not a toggle.
- `run-as` cannot write app data on either.

What they DIFFER on, which is the case the design exists for:

- Ambient suppression is supported on the OnePlus and not on the Honor. The
  probe found it without being told, and the screen renders the difference by
  itself: on the OnePlus the "hide always-on" chip is a live FilterChip, and the
  "handled by your phone" sentence names one feature instead of two.
- The always-on row rendered per device by itself, which is what the design is
  for. (`AmbientSettings` is gone now - see the always-on note below.)


Nothing vendor-specific executes unconditionally. Audited 29 Aug 2026, before
trying the app on anything but the Honor:

- `AmbientControl.offValues()` is the only `Build.MANUFACTURER` test. It returns
  Honor/Huawei's always-on keys and null everywhere else, and every caller stops
  at null - so on any other phone the object does nothing at all.
- `AmbientCapability.KNOWN_PARALLEL_AOD` is an EXCLUSION list, consulted only
  after the AOSP `doze_always_on` key turns out to be absent. An unknown device
  is treated as supported, so a vendor we have never seen gets a switch that
  might work rather than a chip that certainly does nothing.
- There is no eye-comfort code at all. `EyeComfort` was an empty object holding
  a comment, with six preferences nothing read; both are gone, and the finding
  it recorded is in "Vendor limitations" above, which is where it belongs.
- There is exactly ONE write to `Settings.System` / `Secure` / `Global`, and it
  cannot happen by accident: `AmbientControl` writes the vendor's own always-on
  keys, and only when the app holds `WRITE_SECURE_SETTINGS`, which is
  signature|privileged with no runtime prompt and can be granted only over adb.
  `canControl` is false on every ordinary install, so the code is inert and the
  row is not drawn. Everything else here is still read-only capability
  detection.
- Dark theme is offered on every device, because the platform applies it and
  the vendor is not consulted - see the night-mode note above. It was gated on a
  per-device probe until that probe turned out to be structurally incapable of
  observing the thing it measured.

Everything else naming a vendor is a comment.

**Second device, 1 Sep 2026 — what it caught.** The OnePlus was reattached to
check one path the Honor physically cannot draw: the always-on row, which is
gated on `ambientRow` and is false on a phone with no vendor keys. It renders
correctly, and so do two other Honor-invisible paths — `PermissionSection` on a
fresh install, and both sections' `NoticeStrip` with the future-tense heading
while bedtime is off.

The unplanned find was worth more. `tools/check.sh` had been reporting our rule
as `STATE_FALSE  enabled=FALSE` while zen was genuinely ON, and it did so on
BOTH phones. One phone reads as a vendor quirk and was shrugged off twice; two
unrelated phones reading identically is a tool bug. Its regex ran `.*?` from the
first `ZenRule[` in the live config to `name=Gloaming` — 3,202 characters across
four rules on the OnePlus — so id, state and enabled came off whatever rule
sorted first, while `deviceEffects` and `triggerDescription`, which sit AFTER
the name, were correctly ours. Half right is the worst possible answer: the
fields that were wrong are the ones you consult to decide whether the app is
working, and they contradicted the architecture (`enabled` is TRUE in both
states — the rule holds the policy, we flip the condition) without anyone
noticing. Fixed by splitting on `ZenRule[` and parsing only the chunk carrying
our name.

Two more answers, both previously attributed to MagicOS on inference:

- Logcat IS readable for a third-party app here. `Journal` is a MagicOS
  workaround, not an Android one, and on this device ordinary logcat works.
- `font_scale` is 1.15 on this phone and the Ukrainian always-on title wraps to
  two lines, making a three-line row. It renders correctly — the card grows and
  the switch stays centred, not top-aligned — but `RowFitTest` measures at the
  default font scale only, so the wrap is outside what the suite can see.

Worth knowing on ANY device: nothing flashes a "Do Not Disturb is on"
notification at first launch any more. That was `DarkCapability.probe`
activating a throwaway rule, and it is gone with the probe.

## Tests

`app/src/test/` holds `SchedulerTest` - cases over the scheduling core, on the
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

`RowFitTest` answers "does the text fit, in every language?" by measuring rather
than counting. The character budgets in `values/strings.xml` are a proxy and a
poor one - Cyrillic is wider, so a 24-character Ukrainian subtitle wrapped where
a 25-character English one did not, and three rows shipped misaligned because the
app had only ever been LOOKED at in English. This renders Home and the allowlist
in en, ru and uk and asserts no row reaches 88dp, M3's THREE-line height.

The threshold is deliberately the three-line height and not the two-line one,
and the distinction matters: what makes a ListItem three-line - and so
top-aligns its trailing content - is a wrapped SUPPORTING line. A wrapped
HEADLINE is a different thing. It lands at 74dp with the switch still centred,
measured on the device, and "Hide always-on" has a long and correct name in
Russian and Ukrainian that is worth two lines. At 72 the test failed both the
same way, and acting on it cost real accuracy: the Russian title was shortened
to «Скрыть часы» - "hide the clock" - which is wrong, because an always-on
display can show an image or a signature and not a clock at all. Reported.

Two things were needed to make it a measurement rather than a ritual, and both
are worth knowing. It needs a WIDTH - `setQualifiers("+uk-w360dp-h800dp")` -
because Robolectric's default screen is not a phone's. And it needs
`@GraphicsMode(NATIVE)`: the default stubs text measurement, every glyph the same
width, which had every row reporting an identical 86dp in all three languages.
That number looked like data and was not. Validated by lengthening a Ukrainian
string and watching it fail at 88dp, because a check that has never been seen to
fire is not yet a check.

It found one on its first real run: the Russian and Ukrainian titles for "Hide
always-on" wrapped, at 74dp. That 74 had already appeared in a device measurement
earlier the same day and been dismissed as a clipped row.

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

**A test that reads the wall clock passes only in the hours it was written in.**
`a window still to come does not claim today` built its window as
`now.plusHours(3)` and asserted the sentence would NOT say "today" - which is
only true between 21:00 and midnight, the one window where adding three hours
lands on the next date. It ran green for weeks and failed the first time the
suite was run after midnight, and the failure was in the assertion, not the app.
A one-off three hours out is today at 09:00 and tomorrow at 23:00, and both are
correct; what must hold at every hour is that the sentence names the day the
window really begins on. That is what it checks now. Worth suspecting any test
here that calls `LocalTime.now()` and then asserts a fixed answer.

`PrefsMigrationTest` covers the one-shot `days` migration, which is the only
code here that can corrupt data silently: it rewrites the user's schedule, it
runs before any screen is drawn, and a mistake would simply make the nights wrong
with nothing to report it. Four failure modes are covered and each was confirmed
by breaking the migration on purpose - running on every construction (`Prefs` is
built in the receiver, on every screen and in `reconcile`, so that would walk the
schedule forward a day at a time), shifting daytime windows that never needed it,
shifting the wrong direction, and never setting the flag so a fresh install gets
migrated later.

Coverage, measured rather than asserted: **74% of instructions, 59% of
branches** (69/51 when this paragraph was written; the shape below is the
point, not the number). The shape matters more than the number, and it is the shape this file
predicts - what is well covered is what can be reasoned about without a phone
(SectionKt 100%, RowsKt 95%, Interruptions 94%, GloamColors 93%, SettingsScreen
92%, ThemeKt 88%, Clock 81%, Scheduler 80%, Prefs 77%, MainActivityKt 73%), and
what is not is what talks to the platform (MainActivity 0% - the Activity
itself; BedtimeReceiver 3% - system broadcasts; AmbientControl 21% -
Settings.Secure writes; Journal 33%; BootWatch 33%; ZenController 44% - zen
rules; BedtimeDial 40% - Canvas).

The number went 70/52 to 69/51 while SEVEN tests were added, which is worth
understanding rather than chasing: the plan note, `dayWord` and `NoticeStrip`
are all covered, but they grew the denominator faster than they grew the
covered half, and most of what they added lands in MainActivityKt, already the
largest file here. A coverage percentage moves when the code moves, not only
when the tests do.

Two traps in getting that number, both of which produced a confident wrong
answer first. `tmp/kotlin-classes` still held classes from the app's FORMER
package name, so the report measured code that had indeed never run and said 0%.
And Robolectric loads classes through its own sandbox classloader, which the
JaCoCo agent does not see without `isIncludeNoLocationClasses` - without it only
the plain-JVM tests counted and the report read 1%. Both look like answers.

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

**Detail screens DO have a top app bar; Home does not, and that is not an
inconsistency.** What was removed from Home was a wordmark bar costing 66dp
above the fold to say which app this is - something the launcher icon already
says. A detail screen's bar carries Back, which has to stay put: the allowlist's
back affordance used to be a row INSIDE the scrolling column, so on a nine-row
screen the only visible way out scrolled off the top. `InterruptionsScreen` uses
a real `TopAppBar` with a navigation icon now, one constant `raise` for the same
reason Home's bar is constant - M3's default goes transparent-to-`raise` the
instant anything scrolls under it, which reads as a blink. Note `SettingsScreen`
still uses the old `BackRow` and has not been looked at yet.

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

**Shizuku is rejected, 30 Aug 2026.** It is the standard way to grant an
adb-only permission without a computer, and it would have made the always-on
row reachable by tapping a button instead of typing a command. It cannot be
bundled: the mechanism REQUIRES a process running as the shell user, obtainable
only through adb pairing or root, so `dev.rikka.shizuku:api` is a client that
does nothing unless the separate Shizuku app is installed, paired over Wireless
debugging and running. It removes the computer, not the setup - the user still
enables developer options and pairs. Requiring a second app to make this one work
is not a trade worth making, and it would have been the first third-party
dependency here. The adb command in the README stays the only route; it does not
care whether adb ran from a laptop or from a terminal app on the phone, so
anyone who wants that path already has it without our help.

Bundling adb ourselves, LADB-style, was considered and is worse: it ships a
native binary, implements the pairing flow, is fragile across versions, would be
refused by Play, and STILL requires the user to enable Wireless debugging. Nobody
escapes that step - it is the security boundary, and the same reason auto-launch
cannot be switched on programmatically.

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
  sits at the top of the screen. The brief hides the switch while running, which
  left no way out of DND until the wake alarm, and carried a separate
  running-only status pill that said nothing the row does not.
  **All three states live in the switch itself now, and the lamp beside it is
  deleted.** That dot was the THIRD telling of one fact: the status line under
  the title names every state in words ("Off", "Starts in 3h 50m", "On now,
  until 8:30 AM"), the switch shows on-versus-off, and the lamp then said it
  again in colour alone. The only thing it uniquely carried was armed versus
  running, and that moved into the thumb - `ic_check` while armed, `ic_bedtime`
  (Google's own crescent) while a window is actually running - which is where a
  person looks for this control's state anyway.
  The complaint that started it was that the lamp drew the eye, and the
  diagnosis is worth keeping because it was not the size: its OFF state was a
  RED, on the calmest surface in the app, and a red on a surface reads as a
  fault. Being switched off is a choice, not a failure. `lampOn` / `lampOff`
  are deleted with it.
  **The sizing this rests on is a trap worth naming.** M3 grows the thumb to
  24dp whenever `thumbContent` is non-null and leaves it at 16dp only when it is
  null - so an icon on the UNCHECKED side would silently flatten the 16/24dp
  asymmetry recorded below as load-bearing, while looking like it added a cue.
  Passing null while unchecked is what keeps it, and the glyph swap costs
  nothing because both states that carry a glyph are checked and already 24dp.
  `GloamSwitch` takes the drawable as a parameter defaulting to `ic_check`, so
  the app's other switches cannot sprout moons by accident.
- Selection-as-a-fill has its own `selectFill` / `onSelect` tokens — day
  toggles, effect chips, the switch track. The allowlist's leading avatars used
  to be in that list and should not have been: they were filled with `selectFill`
  identically whether the row was allowed or blocked, so a SELECTION colour was
  carrying no state at all. They are bare 24dp leading icons now, which is what
  the effects rows already used, so the two lists draw the same idea the same
  way. **Single choice is
  the exception and uses M3's `RadioButton`**: the allowlist sheets and the theme
  picker in Settings both went that way, with no container fill and no trailing
  check, because the radio already says what those said. Before that they were
  two different hand-drawn circles for the same job. `stateOn` has to
  stay legible as text on the ground ("Allowed", the chosen row in a choice
  sheet), which is why it is a separate token from the fill.
- **`selectFill` and the switch track are ONE token, and that is a deliberate
  break with Material.** M3 has two accent slots and they are not
  interchangeable: `primary` (tone 80 dark / 40 light) for small parts - a
  switch track, an icon, active text - and `secondaryContainer` (tone 30 / 90)
  for filled areas. Gloaming used `primary`'s brightness for BOTH, which is what
  made the day row glow: the selected discs measured 9.23:1 against the page
  where Google's own selected containers measure 1.80:1. The fix was to move the
  fills to the container role, and the further decision - asked for and worth
  recording as a choice, not a slip - was that one accent used identically for
  every selection control is what makes a palette read as authored. So
  `switchTrack` / `switchThumb` are DELETED and the switch takes `selectFill` /
  `onSelect`. Collapsed rather than merely set equal, so the requirement cannot
  drift back apart.
  **The cost of that was written up here as larger than it is, and the
  correction is the more useful note.** A dim checked track does drop the
  track's own on-versus-off lightness separation to 1.80:1 in Dusk and 1.19:1 in
  Dawn - but the switch was never resting on track lightness. Measured off the
  device rather than reasoned about: the checked thumb is 24.0dp and the
  unchecked one 16.0dp (2.25x the area), and M3 outlines the UNCHECKED track
  while leaving the checked one bare. Both are SHAPE cues, both survive any
  colour vision, neither touches the accent. With position and the thumb's own
  glyph that is four shape cues before a colour is counted, and the 16/24dp
  matches Google Messages' own switch on this phone. That glyph now carries a
  fifth thing on the master switch - a check while armed, a crescent while
  running - which is the whole reason the state lamp could go.
  **The fix once recorded here as WRONG - set `checkedBorderColor` - is what
  ships now, and the reversal is the more useful note.** The objection was that
  outlining the checked track destroys the outline/no-outline asymmetry, and
  that was true and beside the point: the border it protects measured 1.87:1 in
  BOTH themes, against M3's own 3.51:1 for it, so it was a cue too faint to be
  read being credited with work it was not doing. Meanwhile the checked TRACK
  measured 1.26:1 against its card in Dawn and 2.06:1 in Dusk, under the 3:1 a
  component needs to be identifiable against its background - which is what 1.4.11
  actually requires, as distinct from the state-to-state exemption quoted below.
  Both are fixed: `selectBorder` rims the checked track, `veilOutline` lifts the
  unchecked one to 3.19:1 and 3.17:1. The asymmetry survives regardless - thumb
  POSITION, thumb SIZE and the thumb GLYPH are three shape cues that owe nothing
  to a border. The lesson is not "outline it after all", it is that an argument
  resting on a cue nobody can see should be re-measured before it is quoted.
  WCAG 1.4.11 says the same thing, and quoting it is worth the space because the
  intuition runs the other way: it "does not require that changes in color that
  differentiate between states of an individual component meet the 3:1 contrast
  ratio when they do not appear next to each other". Each state must be
  identifiable against ITS OWN neighbours, which both are, on the thumb:
  4.90 / 3.59 in Dusk and 4.52 / 3.39 in Dawn.
  The genuinely weak part is elsewhere and is a separate call: the UNCHECKED
  outline is `line`, kept deliberately quiet, so it measures 1.33:1 and 1.43:1
  on its own track where M3's baseline reaches 3.51:1.
  Selected-versus-unselected is filled-versus-hollow, not two fills: the
  unselected day is a ring in `line`, so the old worry about the two states
  being separated mostly by hue - the distinction red-green colourblindness
  collapses - no longer applies. The selected fill sits at 2.61:1 on the page in
  Dusk and 1.37:1 in Dawn.
  **The accent came off the dial, and then the LIGHT theme left it again. Read
  this whole entry before quoting any part of it.**

  Where it ended up: Dusk keeps the arc's night stop, `#566479`. Dawn is
  `#596F8B` - hue 254, chroma 24, TONE 46 - which is the arc's own hue at a
  tone dark enough to carry WHITE text, and it is the single value behind every
  selected thing: the day discs, the preset pill, the status pills, the notice
  strip and the switch track.
  It went via a washed sage, `#D3E1D0` at tone 88, which is where the light/dark
  grammar flipped: a PALE fill with dark ink, versus a DARK fill with white ink.
  Both work; they are different screens. The sage's own reasoning is below and
  is still correct for a pale accent. That was chosen with the departure on the
  table, so the story this rebuild was named for holds for one theme only.

  What moved it, in order, because each step invalidated the last:

  GREY READS DISABLED. The arc's cool end at 55% over a warm ground gives chroma
  11, and a selected day and a checked switch at that chroma were reported as
  looking unavailable. M3's own `secondaryContainer` carries chroma 20 and the
  sage this replaced carried 24, so 11 was half of what Material treats as a
  tint at all. That is the floor, and it is a real one.

  24-26 READS BRIGHT. Which puts the usable band at roughly 13 to 20 - narrow,
  and narrower than it looks, because dropping chroma also costs separation from
  the card: 5.8 perceptual units at chroma 17 down to 4.9 at 11.8.

  WHITE TEXT HAS ITS OWN CEILING, and it is what fixes the final value: tone 48
  gives white 4.80:1 and tone 50 gives 4.47:1, which fails. So a dark fill can
  be lightened by about two tones from where this one sits and no further -
  past that the answer is not a lighter fill but dark text on a pale one.
  Note `stateOn` does NOT follow the fill. It is TEXT on a card, where tone 46
  reaches only 4.13:1; it sits at tone 38 for 5.51:1. A token that looks like it
  should track the accent, and cannot.

  AND "WASHED OUT" IS TONE, NOT CHROMA. Asked to soften it further, the first
  three attempts lowered chroma, which drains a colour rather than fading it.
  Fading it means moving the fill TOWARD its ground. The ceiling there is tone
  88: past that the notice strip and the switch track dissolve into the card at
  tone 90.8, while the day discs - which sit on the page at tone 99.3 - would
  still have room. One token serves both, so the tighter constraint wins.

  Two things are worth keeping from the version this replaced, since both are
  still true of the DARK theme and of any future accent:

  THE RAMP IS NOT A CONTINUUM. It is two hue families with a grey hole between
  them, chroma collapsing to 7.6 at the midpoint. It offers two colours, not a
  spectrum.

  AND THE ARC IS DRAWN AT alpha 0.55 WHEN OFF, which is the version worth
  sampling - at full strength it is too intense to wear. Composited there, Dusk
  keeps chroma 18-25 across all four stops, but Dawn's cool end washes out to
  11.4, which is what sent the light theme looking elsewhere in the first place.

  The original entry follows, and its opening sentence is the one now false.

  **The accent comes off the dial, and specifically off the dial AS DRAWN WHEN
  BEDTIME IS OFF.** It is `Arc.night`'s hue - the bedtime handle's own colour -
  in both themes: Dusk `#566479` at tone 42 chroma 18.7, Dawn `#D1D8E5` at tone
  86. Asked for directly, on the reasoning that a palette which quotes the one
  ornament on the screen will read as balanced, and it does.
  Two measurements decided the shape of it and neither was guessable.
  THE RAMP IS NOT A CONTINUUM. It is two hue families with a grey hole between:
  cool at hue 255-261 chroma ~24 over t 0.0-0.42, warm at hue 49-50 chroma 34-45
  over t 0.7-1.0, and at the midpoint chroma collapses to 7.6 and the hue is
  meaningless. So "pick a value from the gradient" offers two colours, not a
  spectrum - the same thing the launcher icon's ramp taught, from the other end.
  AND THE ARC IS DRAWN AT alpha 0.55 WHEN OFF (`BedtimeDial.arcAlpha`), which is
  the version that was asked for - at full strength it is too intense to wear.
  Composited over the track that softens it: in Dusk all four stops keep chroma
  18-25, but in DAWN the cool end washes out to chroma 11.4 and 10.8. So the
  light theme literally cannot take a faithful cool sample; what ships there is
  the arc's HUE at a chroma the warm white can carry.
  The ink is tone 23, not the container role's 29. At chroma 11.4 the fill is
  near the floor of what reads as a hue, and the label on it was the first thing
  to suffer - 8.38:1 at tone 23 against 6.76:1 at 29, reported on sight. It
  still holds chroma 13.3, so it is a blue ink and not a near-black one.
  What this replaced, kept because the elimination is the record: a muted fern
  green at hue 165, itself reached over three rounds - the old sage (hue 130
  tone 75) glowed; a generic slate blue read almost greyscale in Dusk and too
  cool in Dawn; a lilac was disliked outright. Note the slate rejection did NOT
  survive: this is a slate, and the difference is that it is the ARC's slate and
  it now sits directly under an arc of the same hue. A colour that fails alone
  can work in company.
- The state lamp had its own `lampOn` / `lampOff` tokens rather than reusing
  `stateOn`, and the reasoning is kept although the mark is gone, because it is
  right about small marks in general: a lamp carries no text, so at 10 dp it
  needs chroma instead of value contrast or it reads as a speck rather than a
  colour, which is what put it at 6.47:1 and 4.92:1 on the app bar. That is
  also exactly why it drew the eye. **A mark small enough to be unobtrusive
  needs chroma to be seen at all, and chroma is what makes it obtrusive** -
  there is no setting of that dial that yields a quiet 10 dp state light. The
  way out was not a quieter lamp but no lamp; see the master-switch note above.
- **The page does not move with the state, and that is a reversal.** There were
  three grounds - a LADDER, the more the app was doing the deeper the page -
  crossfading over a second, with a radial `bloom` on top while running. All of
  it is gone, asked for directly, and Dawn now sits permanently on what used to
  be the OFF rung: tone 99.3, named as the correct one after seeing all three.
  Three things are worth keeping from how it ended.
  `raiseRunning` WAS NEVER A DESIGN CHOICE. It existed only because in Dawn the
  running page deepened TOWARDS the cards and would have swallowed them, so it
  dropped them to tone 90 to keep 1.14:1. With a still page it has nothing to
  compensate for, and it left with the thing it was compensating for. Ask of any
  token whether it states something or only corrects something else.
  THE CROSSFADE WAS NOT DECORATION and should come back with any moving ground.
  The instant version repainted the page mid-drag as the dial crossed the "now"
  boundary, and a state change that takes 0ms reads as a glitch - reported as
  "the background changes, what is that?".
  AND THE LADDER WAS SPENDING WHAT IT BOUGHT. Deepening the CARD to separate it
  from the page was the obvious fix for a flat 1.08:1, and it is wrong here:
  every container that darkens moves TOWARDS the checked switch track sitting on
  it, so card-versus-page improves and switch-versus-card gets worse - measured
  1.26:1 bare, 1.16-1.18:1 for every variant that deepened the card, and 1.26:1
  kept by lifting the PAGE instead. Reported before it was measured. When two
  things sit on one surface, moving the surface pays one and charges the other.
- Bedtime and Wake sit ABOVE the dial and share a top edge. The brief put them
  below it, stepped by 18 dp, reading as a passage. Below the dial they are
  under your hand for the whole drag, so the one thing you cannot see is the
  value you are setting. Once above it, the step just read as lopsided.
  Confirmed on hardware 29 Aug 2026: the hand no longer covers them.
- **The window is also stated in words, under the dial.** Home served two
  mindsets and missed one: spatial (the ring and its arc) and numeric (the two
  numerals, the centre duration, "starts in 36m"). Nothing was verbal. The
  sentence answers what a circle is worst at - WHICH morning - since a window
  crossing midnight is only obvious on a clock face to someone who already reads
  clock faces, and it is the only part of that block a screen reader can make
  anything of.
  It asks `liveWindowEnd` with **`enabled = true` regardless of the switch**, and
  falls back to `nextStart`. Asking with the real switch was wrong in a way that
  only appears at night: OFF at 23:34, inside a 6:55 PM window, a ONE-OFF returns
  null - `liveWindowEnd` treats a one-off as running only while the switch is on
  - so it fell through and said "from 6:55 PM TOMORROW" while the dial above drew
  the marker inside the arc. It was also a wrong PREDICTION, not merely an odd
  phrasing: switch on at that moment and the one-off starts immediately.
  A repeating schedule hides the bug completely, because there `liveWindowEnd`
  answers whether the switch is on or not. The regression test therefore uses an
  EMPTY day set; written with seven days it passed with the fix reverted, which
  is how it was caught.
  It is built from the actual window rather than from the two handles - so a one-off, a window five days
  out and a window running now each name their own days and cannot drift from
  what is scheduled. Day words are `today` / `tomorrow` and then the localised
  weekday from `java.time`, because past a day "tomorrow" would be a lie by
  omission.

- The pair is set off by moon and sun glyphs on the labels alone, not by two
  per-column rules and not by the shallow "crown" arc that used to sit beneath
  them. That arc was drawn as the crown of the same circle it sat above, which
  is a real idea and wrong on the screen: a few dp from the ring at a very
  different radius, two curves nearly rhyme and do not, and it read as a
  slightly wrong copy of the ring. Flattening it into a ruler was better - the
  hour ticks make more sense on a straight baseline - but it was still a second
  telling of what the dial already says, so it is gone. What it uniquely carried
  was a COUNTABLE window length, in hour ticks; the centre readout says the same
  thing as a number. Colour alone made the wake
  side something you had to learn; the glyphs are the marks already on the ring,
  and they survive a dimmed screen and a colourblind reader. **The WAKE UP word
  itself is now the same ink as BEDTIME**, and only the sun stays warm. In
  `Arc.dawn` that word measured 1.7:1 on the cream, against BEDTIME's 14:1 in the
  same row - a tint rather than a shade. At 11sp it does not reach WCAG's
  large-text exemption, and on that ground nothing warm enough to still read as
  dawn cleared 4.5:1: `cta` reached 3.0 and the best that passed was `#9E5426`,
  which arrives as rust. So the warmth moved to the glyph, which needs only 3:1
  because it is not text - 6.2:1 in Dawn and 7.2:1 in Dusk - and the two sides
  are finally symmetric: grey word, own glyph, both ends. Changed in Dusk too,
  where contrast was never the problem, because one rule in both themes beats a
  rule that holds in one.
  Note that the CONSTRAINT here expired with the 31 Aug 2026 palette rebuild, in
  the same way the notice strip's did: on the warm white `cta` is 6.15:1 as text,
  so a warm WAKE UP would now pass. The decision stands anyway, and for better
  reasons than the one that forced it - symmetry between the two ends, and a
  glyph surviving a dimmed screen and a colourblind reader where a hue alone
  does not. Keep the decision; do not keep the arithmetic as a reason. The crown also
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
  **The dots' space is always reserved, even when they are not drawn.** The
  column is centred in the dial, so 12dp of dots appearing moved the numeral up
  by 6 - measured, top 1136 to 1115 - and toggling the master switch made the
  time visibly jump on the spot. Note what the report was NOT about, because two
  wrong fixes came before the right one: the READING changing is correct and has
  to stay ("until bedtime" is offered only while armed, since a countdown to
  something that will not happen is a lie). Offering it either way was tried and
  rejected; crossfading the change was tried and was decoration over the wrong
  diagnosis. The value may change; where it sits may not.
- The day row is present in every state and can be emptied. It used to vanish
  once bedtime started, which reads as a bug, and the last selected day refused
  to be deselected — a buzz with no reason given. Both were guarding the
  truncation bug above, fixed at the source now.
- **No days chosen means the window runs once, then the app switches itself
  off** — the convention alarm clocks use for a non-repeating alarm (Google
  Clock, iOS, Alarmy). Empty first meant "no nights scheduled", which produced
  a dead state: switch on and checked, nothing scheduled, ever. "On, but
  never" is a promise the app cannot keep. `Scheduler.isOneOff(days)` is the
  single test — a one-off queues no following START, and `BedtimeReceiver`
  clears `enabled` when its END fires. `liveWindowEnd` only treats a one-off
  as running while the switch is on, or the dial would draw a phantom window
  on every day of the week. This is also the brief's unbuilt "Turn on tonight"
  behaviour arriving early, without its CTA.
- **The day toggles morph their corners while held, and that is the only piece
  of Material 3 Expressive this app takes.** They had no press feedback at all:
  the ripple is `bounded = false`, so it reads as a halo AROUND the chip rather
  than as the chip responding, which is the same complaint that got the switch
  its thumb-growth back. M3 Expressive answers it by morphing a round toggle's
  corners in while pressed, and says so in tokens - `ConnectedButtonGroupSmall`
  has `ContainerShape = CornerFull` and `PressedInnerCornerCornerSize =
  CornerValueExtraSmall`. 20dp to 12dp here, on M3's own "fast spatial" spring,
  damping 0.6 and stiffness 800.
  Both sets of numbers are COPIED rather than referenced, and that is the thing
  to know before reaching for more of Expressive: `material3` 1.4.0, which the
  BOM resolves, ships the expressive TOKENS but not the composables. There is no
  `ButtonGroup.kt`, no `ToggleButton.kt` - they live in `material3-expressive`,
  which is not on this classpath and would be the project's first new dependency
  since it was written. `ExpressiveMotionTokens` is `internal` on top of that.
  So the behaviour is four lines of `animateDpAsState` and the tokens are
  transcribed with a note to re-read them if they drift. Taking a dependency to
  save four lines is not a trade worth making; taking one for a real component
  might be, and that decision is still open.
  Two Expressive ideas were priced and REJECTED at the same time. Leading accent
  caps on the effects rows: Health uses them to separate CATEGORIES across a
  dozen heterogeneous cards, and these three rows are one category under a
  heading that says so. The one true distinction between them - grayscale and
  dimming apply at once, dark theme waits for screen-off - is already in the
  supporting lines, in words, which need no legend. And the day row as a
  connected button group: unavailable as above, and it would put two segmented
  bars directly on top of each other, since the preset row above it already is
  one. Note the objection that was WRONG when first raised - that seven segments
  would not fit. A connected group has no inter-segment gaps, so 311/7 = 44.4dp
  is exactly what the circles already occupy. The width was never the problem.

- The day row carries state in shape as well as colour: selected is a filled
  circle, unselected a hollow ring. The two fills sit at 1.3:1 against each
  other (see the selection-fill note above), so colour alone was not carrying
  it. Same filled-vs-hollow the crown marker uses. Labels are two
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
  and it should stay that way. `ic_chevron.xml` is Google's own path, fetched
  the same way `ic_check` was, so there is no `fontSize` or `fontWeight` override
  left anywhere outside `Theme.kt`. Note this was claimed here once BEFORE it was
  true: the drawable was added and the allowlist call site converted, while
  Home's "What is allowed" row kept its literal `›` sized by borrowing
  `titleLarge` for months afterwards. Converting one call site is not the same as
  removing a pattern, and the note said the pattern was gone.
- **Secondary text was thinnest exactly where it is read most: on a card.**
  `onSurfaceLow` is the ink 24 of the app's text usages take - subtitles, the
  master card's status line, the dial's centre label - and its worst ground is
  not the page but `raise`. On the old palette it measured 4.54:1 in Dawn -
  passing AA by 0.04 - and 4.30:1 in Dusk, which did not pass at all, and the
  ink was darkened to buy 6.4:1. The 31 Aug 2026 rebuild made that free: on the
  lifted grounds it is 6.86:1 in Dusk and 7.68:1 in Dawn, with titles at 10.4
  and 15.1 on the same ground, so the hierarchy is intact and the floor moved
  up. Note the ladder if it ever needs revisiting: each step of about 8 in
  luminance is worth roughly 0.7 of contrast on a card, and the step past this
  one starts to flatten the difference between a title and its subtitle, which
  matters in an app read in the dark.
  The number that was nearly missed here is worth keeping: the bedtime numeral
  is dimmed to 62% alpha once the window is running, and on the old ink that
  landed at 2.51:1 - below the 3:1 that large text needs. It is 6.16:1 in Dusk
  and 4.58:1 in Dawn now, so the margin is comfortable, but it is still the
  number to check if that alpha is ever tuned.

- **`raise` carries every container in the app** — both home cards, the
  permission panel, both dialogs, the choice sheets, every allowlist row, every
  unselected chip, and the dial's own track — and it is wired into Material's
  `surfaceVariant`, `secondaryContainer`, `tertiaryContainer` and the
  `surfaceContainer` ladder, so a component that reaches for a container gets the
  same colour. One token moves all of them, which is the point.
  It is 1.26:1 against the page in Dusk and **1.24:1 in Dawn**, and the Dawn
  number arrived in two moves that pull in opposite directions: the PAGE went up
  to tone 99.3 (1.08 to 1.12), then the CARD went down to tone 91 (1.12 to 1.24).
  Doing only the second was rejected earlier for spending switch-versus-card to
  buy card-versus-page; doing both is affordable because the controls gained
  RIMS in between, so the checked track's raw fill can sit at 1.13:1 with its
  rim carrying the boundary at 1.81:1. The order mattered - the card could not
  have been darkened first.
  **Dawn's card is a green-grey now, not a cream.** It was hue 76 against an
  accent at hue 258, nearly opposite, reported as looking inappropriate together;
  it is hue 130 at chroma ~3.4. The whole neutral family moved with it - `veil`,
  `line`, `outline`, `veilOutline` - because shifting only the card would have
  relocated the clash onto the day rings, which sit on the page.
  Worth knowing before tuning that hue: **Material derives its neutrals from the
  ACCENT's hue**, not from grey and not from a chosen one -
  `core_palette.ts` builds n1 at chroma 4 for surfaces and n2 at chroma 8 for
  the outline family, both `fromHueAndChroma(sourceHue, ...)`. Hue 130 is a
  deliberate departure from that, chosen by eye over the compliant hue-258
  version, and it comes with a constraint: at Material's chroma 8 the outline
  family at hue 130 reads OLIVE rather than grey. It passes as neutral paper
  only while its chroma stays near 3. A hue that is not the accent's has to stay
  quieter than Material's own numbers to keep the same job.
  There is no longer a running variant. Formerly the RUNNING
  state is the one to watch rather than the resting one, because that is where
  the screen spends the night: 1.35:1 and 1.14:1 there.
  Those look thin and are not. Google Health's own cards measure 1.10:1 against
  their page in light and 1.12:1 in dark, sampled on the Honor - so this ladder
  is at or above what Material ships. The reason it can be flatter than the
  1.40:1 Dusk used to hold is that the old number was bought with a tone-7 page,
  and lifting the ground off near-black is what the whole rebuild was for. A
  card reads as a plane because of where the ground sits, not only because of
  the gap.
  What has NOT changed is the failure mode at the bottom: the palette this
  replaced started at 1.13:1 resting and 1.07:1 running, and at those numbers
  the cards did not read as cards at all. That is the floor to stay above.
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
  the switch cannot diverge.
  It is also the ONE switch that carries a `contentDescription`, and that was a
  real gap rather than a nicety: every other switch sits in a row that names it,
  the app bar has no row, and read off the device the node came back
  `text='' content-desc='' checkable=true` - TalkBack announcing a bare
  "on, switch" with nothing said about what. Found while looking for a stable
  test selector, which is the second time this app's accessibility has been
  fixed by needing to address a control from outside it.
- **Switching bedtime off mid-window no longer asks.** There was a confirm
  dialog - "End bedtime now?" with End now / Keep going - and it is deleted.
  A confirmation is for something destructive or hard to undo, and this is
  neither: measured on the phone, off gives `zen_mode` 0 with `activeDay`
  cleared, and one tap back on gives `zen_mode` 1 with `activeDay` re-derived by
  `rescheduleAll`, the END alarm restored to the same minute and the next START
  re-queued. Nothing is spent and nothing is lost.
  Its real job was never consent, it was EXPLANATION - "the next one runs as
  scheduled", answering "have I just cancelled my whole schedule?" - and the
  screen behind it already answers that the instant the switch moves: the status
  line goes to "Off", the dial still draws the window, the days stay filled, and
  the plan note appears on both cards saying these settings take effect once you
  turn it on. A modal charges a decision every single time to deliver a fact you
  need once, and this one fired at the worst possible moment: a dark room, a
  grayscale screen, someone who wants the window over NOW. It was also the only
  switch in the app that argued back.
  `end_bedtime_title`, `end_bedtime_body`, `end_bedtime_body_once`, `end_now`
  and `keep_going` are gone from all three locales. A `ScreensTest` case pins
  the one-tap behaviour; note it needs BOTH
  `setNotificationPolicyAccessGranted(true)` and the static
  `ShadowAlarmManager.setCanScheduleExactAlarms(true)`, or the switch renders
  disabled and the click is silently ignored while the node still reads On -
  a green-looking test that asserts nothing.
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
