#!/bin/bash
# What the phone actually thinks, in one command.
#
# Read-only: it taps nothing and changes nothing. Half the lines come from the
# system and half from the app's own prefs and journal, deliberately - a
# disagreement between the two halves is the bug worth finding. The app believing
# bedtime is on while zen_mode reads 0 is how the worst night here started.
set -u
PKG=com.jemcik.gloaming
command -v adb >/dev/null || { echo "adb not on PATH"; exit 1; }
adb get-state >/dev/null 2>&1 || { echo "no device attached"; exit 1; }

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
adb shell dumpsys notification --noredact > "$TMP/notif" 2>/dev/null
adb shell dumpsys alarm                   > "$TMP/alarm" 2>/dev/null
# 2>&1, not 2>/dev/null: WHY run-as failed is the useful part. A release build
# refuses it outright, and the two halves of this report stop agreeing - see
# the note above "THE APP'S OWN VIEW".
adb shell run-as $PKG cat shared_prefs/gloaming.xml > "$TMP/prefs" 2>&1
adb shell run-as $PKG cat files/journal.log        > "$TMP/journal" 2>&1
DATE=$(adb shell date | tr -d '\r')
ZEN=$(adb shell settings get global zen_mode | tr -d '\r')
LOC=$(adb shell cmd locale get-app-locales $PKG 2>/dev/null | tr -d '\r')
NOW=$(adb shell date +%s | tr -d '\r')
OFF=$(adb shell date +%z | tr -d '\r')

python3 - "$TMP" "$DATE" "$ZEN" "$LOC" "$NOW" "$OFF" <<'PY'
import re, sys, pathlib
tmp, date, zen, loc, now, off = sys.argv[1:7]
now = int(now)
def read(n): return pathlib.Path(f"{tmp}/{n}").read_text(errors="ignore")
def head(t): print(f"\n\033[1m{t}\033[0m")

notif, alarm, prefs, journal = read("notif"), read("alarm"), read("prefs"), read("journal")

head("WHEN");  print("  device     ", date)

head("DO NOT DISTURB   (the system's answer, not ours)")
print(f"  zen_mode    {zen}   0 off · 1 priority · 2 none · 3 alarms")
for k in ("mZenMode", "mInterruptionFilter"):
    m = re.search(rf"{k}=(\S+)", notif)
    print(f"  {k:<19} {m.group(1) if m else '?'}")

head("OUR RULE")
live = re.search(r"mConfigs\[u=0\](.*?)deletedRules", notif, re.S)
# One chunk per rule, and every field must come from OUR chunk.
# This used to be a single regex running `.*?` from the first ZenRule[ to
# name=Gloaming, which on any phone holding another zen rule - which is most
# of them - read id, state and enabled off whatever rule sorted first while
# still picking up our OWN effects and caption. Half right is the most
# misleading answer available: it reported our live rule as
# STATE_FALSE/enabled=FALSE on two different phones while zen was genuinely
# on, and it read as a vendor quirk twice before a second device made it
# obvious the tool was at fault, not the phone.
ours = next((c for c in re.split(r"ZenRule\[", live.group(1) if live else "")
             if "name=Gloaming" in c), None)
if ours is None:
    print("  MISSING - the system holds no Gloaming rule")
else:
    def f(pat):
        m = re.search(pat, ours, re.S)
        return m.group(1) if m else "?"
    print(f"  id          {f(r'\Aid=(\w+)')}")
    print(f"  state       {f(r'state=(STATE_\w+)')}      "
          f"enabled={f(r'enabled=(\w+)')}")
    print(f"  effects     [{f(r'deviceEffects=\[([^\]]*)\]')}]")
    print(f"  caption     {f(r'triggerDescription=([^,]*)')}")

# Everything below comes through `run-as`, which works ONLY on a debuggable
# build. Against a release APK it fails and every field here would fall back to
# its placeholder - and one of those placeholders, "none - runs once, then
# switches off", is a legitimate app state rather than an absence. That is a
# confident wrong answer dressed as data, the same shape as the rule-identity
# bug this script had: half the output right, the wrong half plausible. So say
# the half is missing, and say why, instead of printing it.
def unreadable(blob):
    """None if the prefs really were read, else why not - in one phrase."""
    if "<map" in blob: return None
    if "not debuggable" in blob:
        return ("this is a RELEASE build, so run-as is refused",
                "the system half above is still true; install a debug build for the rest")
    if "unknown package" in blob or "is unknown" in blob:
        # NB: PKG is a shell var and this heredoc is quoted - do not use it here.
        return ("the app is not installed", "nothing to read")
    if "No such file" in blob:
        return ("the app has never written its prefs",
                "a fresh install that has not been opened yet")
    return ("prefs could not be read", (blob.strip().splitlines() or [""])[0][:60])

head("THE APP'S OWN VIEW")
why = unreadable(prefs)
if why:
    print(f"  UNAVAILABLE - {why[0]}")
    print(f"  {why[1]}")
def pref(name, kind="value"):
    m = re.search(rf'name="{name}" value="([^"]*)"', prefs)
    return m.group(1) if m else "-"
def hhmm(secs):
    try: s = int(secs)
    except ValueError: return "?"
    return f"{s//3600:02d}:{s%3600//60:02d}"
ORDER = ["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"]
days = re.search(r'name="days">(.*?)</set>', prefs, re.S)
chosen = set(re.findall(r"<string>(\w+)</string>", days.group(1))) if days else set()
if not why:
    print(f"  switch      {pref('enabled')}")
    print(f"  window      {hhmm(pref('start'))} -> {hhmm(pref('end'))}")
    print("  mornings    " + (" ".join(d[:2].title() for d in ORDER if d in chosen)
                              if chosen else "none - runs once, then switches off"))
    print(f"  activeDay   {pref('activeDay')}")
    print(f"  effects     dnd={pref('fxDnd')}  grayscale={pref('fxGrayscale')}  dim={pref('fxDimWallpaper')}")
    print(f"  theme       {pref('themeMode')}   (0 system · 1 light · 2 dark)")
# the per-app locale comes from `cmd locale`, not from run-as, so it survives
print(f"  language    {loc.split('are ')[-1] if 'are' in loc else loc}")

head("ALARMS")
from datetime import datetime, timedelta, timezone
tz = timezone(timedelta(hours=int(off[:3]), minutes=int(off[0] + off[3:])))
seen, any_found = set(), False
for block in alarm.split("RTC_WAKEUP")[1:]:
    block = block.split("ELAPSED")[0]
    if "Alarm{" not in block: continue
    act = re.search(r"com\.jemcik\.gloaming\.(START|END)", block)
    ms = re.search(r"origWhen (\d{13})", block)
    if not (act and ms): continue
    at = int(ms.group(1)) // 1000
    if (act.group(1), at) in seen: continue
    seen.add((act.group(1), at)); any_found = True
    when = datetime.fromtimestamp(at, tz)
    left = at - now
    sign = "in" if left >= 0 else "OVERDUE by"
    h, m = abs(left) // 3600, abs(left) % 3600 // 60
    print(f"  {act.group(1):<6} {when:%a %d %b %H:%M}   {sign} {h}h {m:02d}m")
if not any_found: print("  none scheduled")

head("JOURNAL   (last 8)")
jwhy = unreadable(journal) if "<map" not in journal else None
if journal.strip() and jwhy and not journal.strip().startswith(("0","1","2")):
    print(f"  UNAVAILABLE - {jwhy[0]}")
elif not journal.strip():
    print("  empty - nothing logged yet")
else:
    for line in journal.strip().split("\n")[-8:]:
        print("  " + line.strip())
print()
PY
