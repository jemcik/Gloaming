#!/usr/bin/env python3
"""
Battery-drain bench for Gloaming.  ./tools/battery_bench.py [seconds]

Asks one question of every combination of the app's switches, with a window
RUNNING and nobody touching the phone: is anything happening that should not be?

Three witnesses, all independent of each other:

  cpu       app CPU jiffies (utime+stime from /proc/<pid>/stat). The direct
            measure - a loop burns CPU, and CPU is the battery.
  pushes    setAzrState entries the system logged DURING the window. Counted by
            timestamp rather than by total, because under a loop the zen log is
            a ring buffer that evicts its own history and a naive count of lines
            would UNDERCOUNT the very case we are hunting.
  flicker   how many saturation reads disagreed with the first. The visible
            symptom, and the one the user reported.

Controls are located by uiautomator dump on every pass, never by remembered
pixel coordinates: the page scrolls, and a tap aimed at yesterday's layout lands
on a different switch - which already cost one false finding, an apparent
setting change nobody had made.

Set up before running: bedtime ON and a window that is RUNNING NOW (an armed
but idle rule pushes nothing, so every row would read clean and prove nothing),
and a DEBUG build, since the store is read back with run-as.

For an mAh figure rather than jiffies, wrap a run in

    adb shell dumpsys battery unplug     # or nothing is accounted while charging
    adb shell dumpsys batterystats --reset
    ... wait ...
    adb shell dumpsys batterystats --charged com.jemcik.gloaming   # UID line
    adb shell dumpsys battery reset      # ALWAYS, or the phone keeps pretending

and note that the app's own UID line UNDERSTATES a rule-rewrite loop: the work
it makes system_server do is billed to uid 1000, not to us.
"""
import re
import subprocess
import sys
import time

PKG = "com.jemcik.gloaming"
SWITCH_X = 1024


def sh(cmd, timeout=40):
    try:
        return subprocess.run(["adb", "shell"] + cmd, capture_output=True,
                              text=True, timeout=timeout).stdout
    except subprocess.TimeoutExpired:
        return ""


def pid():
    return sh(["pidof", PKG]).strip()


def cpu(p):
    out = sh(["cat", f"/proc/{p}/stat"]).split()
    return int(out[13]) + int(out[14]) if len(out) > 14 else -1


def saturation():
    out = sh(["dumpsys", "color_display"])
    m = re.search(r"Global saturation:\s*\n\s*Activated:\s*(\w+)", out)
    return m.group(1) if m else "?"


PUSH = re.compile(r"(\d{4}-\d\d-\d\dT[\d:.]+) - set_zen_mode:.*setAzrState")


def push_stamps():
    out = sh(["dumpsys", "notification", "--noredact"], timeout=90)
    cut = out.find("Zen Log:")
    if cut < 0:
        return []
    return PUSH.findall(out[cut:])


def prefs():
    out = sh(["run-as", PKG, "cat", "shared_prefs/gloaming.xml"])
    d = dict(re.findall(r'name="(\w+)" value="(\w+)"', out))
    return {
        "dnd": d.get("fxDnd", "true"),
        "gray": d.get("fxGrayscale", "false"),
        "dim": d.get("fxDimWallpaper", "false"),
        "dark": d.get("fxDarkTheme", "false"),
        "enabled": d.get("enabled", "false"),
    }


LABELS = {"dnd": "Do Not Disturb", "gray": "Grayscale",
          "dim": "Dim wallpaper", "dark": "Dark theme"}


def find_row(label):
    """Vertical centre of a row, from the live tree. None when off screen."""
    sh(["uiautomator", "dump", "/sdcard/ui.xml"], timeout=60)
    x = sh(["cat", "/sdcard/ui.xml"], timeout=60)
    for m in re.finditer(
            r'<node[^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x):
        if m.group(1) == label:
            return (int(m.group(3)) + int(m.group(5))) // 2
    return None


def wake():
    sh(["input", "keyevent", "KEYCODE_WAKEUP"])
    time.sleep(0.6)


def set_config(want, tries=4):
    """Tap until the STORE agrees. A tap that missed is otherwise invisible."""
    wake()
    for _ in range(tries):
        have = prefs()
        wrong = [k for k in ("dnd", "gray", "dim", "dark") if have[k] != want[k]]
        if not wrong:
            return True
        for k in wrong:
            y = find_row(LABELS[k])
            if y is None or not (300 < y < 2700):
                # Off screen: page down and look again.
                sh(["input", "swipe", "628", "2000", "628", "1000", "250"])
                time.sleep(0.8)
                y = find_row(LABELS[k])
            if y is None:
                continue
            sh(["input", "tap", str(SWITCH_X), str(y)])
            time.sleep(1.0)
    return prefs() == {**prefs(), **want}


def probe(label, secs=20):
    p = pid()
    if not p:
        print(f"  {label}: app not running")
        return
    before = push_stamps()
    mark = before[-1] if before else ""
    c0 = cpu(p)
    first = saturation()
    flick = n = 0
    end = time.time() + secs
    while time.time() < end:
        s = saturation()
        n += 1
        if s != first:
            flick += 1
    c1 = cpu(p)
    after = push_stamps()
    new = [t for t in after if t > mark]
    if pid() != p:
        print(f"  {label}: process restarted, discarding")
        return
    print("  %-30s cpu=%4d j (%4.1f%%)  pushes=%3d  flicker=%2d/%-3d  sat=%s"
          % (label, c1 - c0, (c1 - c0) / (secs * 100) * 100,
             len(new), flick, n, first))


MATRIX = [
    ("dnd=on   fx=none",        dict(dnd="true",  gray="false", dim="false", dark="false")),
    ("dnd=on   fx=gray",        dict(dnd="true",  gray="true",  dim="false", dark="false")),
    ("dnd=on   fx=gray+dim",    dict(dnd="true",  gray="true",  dim="true",  dark="false")),
    ("dnd=on   fx=all",         dict(dnd="true",  gray="true",  dim="true",  dark="true")),
    ("dnd=OFF  fx=none",        dict(dnd="false", gray="false", dim="false", dark="false")),
    ("dnd=OFF  fx=gray",        dict(dnd="false", gray="true",  dim="false", dark="false")),
    ("dnd=OFF  fx=gray+dim",    dict(dnd="false", gray="true",  dim="true",  dark="false")),
    ("dnd=OFF  fx=all",         dict(dnd="false", gray="true",  dim="true",  dark="true")),
]

if __name__ == "__main__":
    secs = int(sys.argv[1]) if len(sys.argv) > 1 else 20
    print(f"window running? enabled={prefs()['enabled']}")
    for label, want in MATRIX:
        ok = set_config(want)
        got = prefs()
        real = "dnd=%s gray=%s dim=%s dark=%s" % (got["dnd"], got["gray"], got["dim"], got["dark"])
        if not all(got[k] == want[k] for k in want):
            print(f"  {label}: could not set (got {real}) - SKIPPED")
            continue
        probe(label, secs)
