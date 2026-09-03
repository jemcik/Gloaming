# Play store listing

Paste-ready copy for the Play Console listing. Kept here rather than only in the
Console so it is reviewable, diffable, and translatable alongside the app's own
strings.

Assets live in `docs/play/`, built by `python3 tools/play_assets.py`.

Character limits are Play's: **title 30**, **short description 80**, **full
description 4000**. `tools/play_assets.py` does not check them; the counts below
were measured and are re-checked by eye when the copy changes.

---

## Title (30 max)

```
Gloaming
```

## Short description (80 max)

```
Bedtime that keeps its own schedule: Do Not Disturb, greyscale, dimmed screen.
```

## Full description (4000 max)

```
Gloaming makes your phone boring at night.

At a time you choose, it switches on Do Not Disturb, drains the screen to greyscale and dims the wallpaper — so when you pick the phone up at 1am out of habit, there is nothing there to hold you. Colour and notifications come back in the morning.

WHY THIS ONE

Android already has a bedtime mode. On a lot of phones it silently never fires. It is scheduled as a background job, and every vendor's battery management gets a say in whether background jobs ever run. When something in that chain defers it there is no error and no notification — just a screen that stayed colourful all night.

Gloaming uses exact alarms instead. They are a separate, user-granted permission with a scheduling guarantee, and they do not go through the job scheduler at all. The app holds its own Do Not Disturb rule and flips it from its own alarms, so bedtime happens whether or not the app is open.

WHAT YOU GET

• A 24-hour dial. One window, dragged at either end. Days are chosen as the mornings you want the window to end on, so "the weekend" means Saturday and Sunday mornings.

• End bedtime at your alarm. Turn it on and the window follows whatever time your alarm is set for, instead of you keeping two schedules in step.

• A Quick Settings tile with three states rather than two: an hourglass while bedtime is only scheduled, a tick once it is actually running. A tick sitting there all evening while nothing is happening is the one thing a tick must not mean.

• An allowlist for the night — who can call, who can message, conversations, repeat callers, reminders, calendar events, media. The screen reports what the system says is in effect, not what the app believes it asked for.

• Screen effects, as far as your phone honours them: greyscale, wallpaper dimming, dark theme, always-on display. Where an effect does nothing on your device the switch is not drawn at all, rather than offering one that lies.

• It watches its own alarms. If the end of a night comes due and does not arrive on time, you are told, with a button to the setting that usually explains it.

• A boot watch, for phones that withhold the restart broadcast from apps their launch manager has decided are unimportant — which otherwise leaves a bedtime app armed with no alarms behind it.

• English, Russian and Ukrainian, and clock times in whichever 12- or 24-hour format your phone is set to.

PRIVACY

Gloaming has no internet permission. Not "does not send data" — it cannot. There is no analytics, no advertising, no account and no third-party code. Your schedule and the app's own log stay on the phone, and uninstalling removes them.

REQUIREMENTS

Android 15 or newer. Two permissions, both granted from inside the app: notification policy access (for Do Not Disturb) and exact alarms.

OPEN SOURCE

Apache License 2.0. The whole app is public at github.com/jemcik/Gloaming
```

---

## Still to write

- **Russian and Ukrainian listings.** Play localises the listing per language and
  the app already ships `values-ru` and `values-uk`, so leaving the store in
  English only is the odd one out. Not drafted here yet — the app's own copy is
  reviewed by a native speaker before it ships and this should be too.
- **Contact email.** The policy page points at GitHub issues. Play requires a
  contact email on the listing itself regardless; decide which address before
  filling the Console form, since it is displayed publicly.
