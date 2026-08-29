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
adb shell run-as $PKG cat shared_prefs/gloaming.xml > "$TMP/prefs" 2>/dev/null
adb shell run-as $PKG cat files/journal.log        > "$TMP/journal" 2>/dev/null
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
r = re.search(r"ZenRule\[id=([a-f0-9]+),state=(STATE_\w+),enabled=(\w+).*?name=Gloaming.*?"
              r"deviceEffects=\[([^\]]*)\].*?triggerDescription=([^,]*)",
              live.group(1) if live else "", re.S)
if not r:
    print("  MISSING - the system holds no Gloaming rule")
else:
    print(f"  id          {r.group(1)}")
    print(f"  state       {r.group(2)}      enabled={r.group(3)}")
    print(f"  effects     [{r.group(4)}]")
    print(f"  caption     {r.group(5)}")

head("THE APP'S OWN VIEW")
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
print(f"  switch      {pref('enabled')}")
print(f"  window      {hhmm(pref('start'))} -> {hhmm(pref('end'))}")
print("  mornings    " + (" ".join(d[:2].title() for d in ORDER if d in chosen)
                          if chosen else "none - runs once, then switches off"))
print(f"  activeDay   {pref('activeDay')}")
print(f"  effects     dnd={pref('fxDnd')}  grayscale={pref('fxGrayscale')}  dim={pref('fxDimWallpaper')}")
print(f"  theme       {pref('themeMode')}   (0 system · 1 light · 2 dark)")
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
for line in journal.strip().split("\n")[-8:]:
    print("  " + line.strip())
print()
PY
