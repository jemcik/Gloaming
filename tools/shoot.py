"""Capture Gloaming's screenshots on the Honor, per language and per theme.

Drives the phone rather than the emulator because the panel is the thing being
photographed. Everything it changes - app locale, clock format, system night
mode - is a shell setting, so nothing here needs the app rebuilt.

Taps are located by TEXT from the running view hierarchy, not by coordinates:
the rows move between languages and the whole point is to shoot all three.
"""
import re, subprocess, sys, time, pathlib

DEV = "AUNN025C15000871"
PKG = "com.jemcik.gloaming"
OUT = pathlib.Path("/Users/jemcik/Public/code/Gloaming/docs/screenshots")

# EN reads the clock at 12 hours, the Slavic locales at 24 - asked for, and it
# is what each audience actually has set.
LANGS = {"en": ("12", "What is allowed", "Settings", "how the screen will look"),
         "uk": ("24", "Що дозволено", "Налаштування", "як виглядатиме екран"),
         "ru": ("24", "Что разрешено", "Настройки", "как будет выглядеть экран")}
THEMES = {"light": "no", "dark": "yes"}

# Where the effects section's heading sits in the effects shot, in screen
# pixels: the framing the README has always shown - the day chips just under
# the bar, WHAT CAN WAKE YOU, and the three effect rows filling the bottom.
EFFECTS_HEADING_Y = 1677


def sh(*a, quiet=True):
    r = subprocess.run(["adb", "-s", DEV, *a], capture_output=True, text=True)
    if not quiet and r.stdout.strip():
        print("   ", r.stdout.strip()[:120])
    return r.stdout


def shell(cmd, quiet=True):
    return sh("shell", cmd, quiet=quiet)


def grab(path):
    path.parent.mkdir(parents=True, exist_ok=True)
    png = subprocess.run(["adb", "-s", DEV, "exec-out", "screencap", "-p"],
                         capture_output=True).stdout
    path.write_bytes(png)
    print(f"    {path.relative_to(OUT.parent.parent)}  {len(png)//1024}kB")


def bounds(text):
    """Where is the node carrying this text? Returns its centre, or None."""
    # The OLD dump is removed first, and the dump retried: uiautomator refuses
    # to dump while the window is still moving, and reading the previous file
    # then answers from the screen as it was - the top of Home, with nothing
    # below the fold in it - which is how the effects heading went "missing"
    # a second after a swipe.
    xml = ""
    for _ in range(4):
        shell("rm -f /sdcard/ui.xml; uiautomator dump /sdcard/ui.xml")
        xml = shell("cat /sdcard/ui.xml")
        if "<node" in xml:
            break
        time.sleep(1)
    # NODE BY NODE. A single regex over the whole dump reads text="" first and
    # its [^>]* then swallows the content-desc on the same node, so finditer
    # never offers the description - which is the only thing the gear carries.
    for node in xml.split("<node")[1:]:
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        if not b:
            continue
        labels = re.findall(r'(?:text|content-desc)="([^"]*)"', node)
        if any(text.lower() in l.lower() for l in labels if l):
            x1, y1, x2, y2 = map(int, b.groups())
            return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def tap(text, what):
    p = bounds(text)
    if not p:
        print(f"    !! could not find {what} ({text!r})")
        return False
    shell(f"input tap {p[0]} {p[1]}")
    time.sleep(2)
    return True


def shoot(lang, theme):
    clock, allowed_text, settings_text, effects_heading = LANGS[lang]
    print(f"  {lang} / {theme} / {clock}h")
    shell(f"cmd locale set-app-locales {PKG} --user 0 --locales {lang}")
    shell(f"settings put system time_12_24 {clock}")
    shell(f"cmd uimode night {THEMES[theme]}")
    time.sleep(1)
    shell(f"am force-stop {PKG}")
    shell(f"am start -n {PKG}/.MainActivity")
    time.sleep(4)

    d = OUT / lang / theme
    grab(d / "home.png")

    # The effects live below the fold on Home. A fixed swipe FLINGS, and where
    # it lands moved the day the page grew - "end bedtime at your alarm" put a
    # card and a chip between the dial and the days on 11 Sep 2026, and the
    # old 1200px stopped on WHICH DAYS with the effects cut off. Lengthening
    # it overshot; shortening it undershot; the fling is not linear in the
    # distance. So: a rough swipe, then MEASURE where the section's heading
    # landed and drag it, slowly enough not to fling, to where the README's
    # framing has it - again if the first drag flung a little.
    shell("input swipe 628 2400 628 720 500"); time.sleep(3)
    for _ in range(3):
        p = bounds(effects_heading)
        if not p:
            print("    !! could not find the effects heading"); break
        delta = p[1] - EFFECTS_HEADING_Y
        if abs(delta) <= 24:
            break
        # ~150px/s, under the fling threshold on this panel; never off-screen.
        end = max(300, min(2600, 1500 - delta))
        shell(f"input swipe 628 1500 628 {end} {max(600, int(abs(1500 - end) / 0.15))}")
        time.sleep(2)
    grab(d / "effects.png")

    if tap(allowed_text, "the allowlist row"):
        grab(d / "allowed.png")
        shell("input keyevent KEYCODE_BACK"); time.sleep(2)

    # Back to the top, then the gear in the app bar.
    shell("input swipe 628 900 628 2200 300"); time.sleep(1)
    shell("input swipe 628 900 628 2200 300"); time.sleep(2)
    if tap(settings_text, "the settings gear"):
        grab(d / "settings.png")
        shell("input keyevent KEYCODE_BACK"); time.sleep(2)


if __name__ == "__main__":
    only = sys.argv[1:] or list(LANGS)
    for lang in only:
        for theme in THEMES:
            shoot(lang, theme)
    # Leave the phone as a person would want it.
    shell("cmd uimode night yes")
    shell(f"cmd locale set-app-locales {PKG} --user 0 --locales en")
    print("done")
