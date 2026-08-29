#!/usr/bin/env python3
"""
Translate res/values/strings.xml into another language with Gemini Pro.

Modelled on Radiopedia's ua-translate skill: Gemini is the primary translator,
Claude reviews, and the result is checked mechanically. Two Pro models are
called side by side and both candidates are kept, because they have different
strengths and the better line is not always from the same one.

    python3 tools/translate.py ru
    python3 tools/translate.py uk --only=3.1

Writes /tmp/gloaming-i18n/<lang>-<model>.xml. Nothing is copied into res/
automatically: the candidates are for review first.

Needs GEMINI_API_KEY in .env.local (git-ignored).
"""
import json, re, sys, time, urllib.request, urllib.error
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/res/values/strings.xml"
OUT = Path("/tmp/gloaming-i18n")

MODELS = [("gemini-2.5-pro", "2.5"), ("gemini-3.1-pro-preview", "3.1")]

LANGS = {
    "ru": ("Russian", ["one", "few", "many", "other"]),
    "uk": ("Ukrainian", ["one", "few", "many", "other"]),
}

# Terms that must be rendered the same way every time they appear. Left
# deliberately short: a glossary is a promise to be consistent, not a dictionary.
GLOSSARY = {
    "ru": """
bedtime            — «режим сна» (the app's own mode), not «время сна» when it means the feature
Do Not Disturb     — «Не беспокоить» (Android's own wording — must match the system)
starred contacts   — «избранные контакты» (Android's own wording)
repeat callers     — «повторные звонки»
conversations      — «разговоры» (the Android notification category, not «беседы»)
reminders          — «напоминания»
calendar events    — «события календаря»
grayscale          — «оттенки серого»
dim wallpaper      — «затемнение обоев»
always-on display  — «постоянный экран» (Honor/Android call it this)
""",
    "uk": """
bedtime            — «режим сну» (the app's own mode)
Do Not Disturb     — «Не турбувати» (Android's own wording — must match the system)
starred contacts   — «вибрані контакти» (Android's own wording)
repeat callers     — «повторні дзвінки»
conversations      — «розмови» (the Android notification category)
reminders          — «нагадування»
calendar events    — «події календаря»
grayscale          — «відтінки сірого»
dim wallpaper      — «затемнення шпалер»
always-on display  — «постійний екран»
""",
}

SYSTEM = """You are translating the UI of Gloaming, an Android bedtime app, from English into {lang}.

WHAT THE APP DOES, so you translate meaning and not words: the user sets a sleep
window on a 24-hour dial. During that window the phone turns on Do Not Disturb,
turns the screen greyscale and dims the wallpaper. A separate screen lists the
exceptions — who can still ring through at night.

VOICE. Calm, plain, second person, never breathless. No exclamation marks, no
marketing register, no diminutives. It talks to an adult at bedtime. Where the
English is deliberately plain ("What gets through", "Nothing scheduled"), keep it
plain; do not decorate it.

HARD RULES
1. Output ONLY a valid Android resources XML file. No prose, no code fences.
2. Keep every name= attribute EXACTLY as given. Do not add, drop or reorder entries.
3. Format placeholders — %1$s, %2$s, %d, %1$d, %2$02d — must appear in the
   translation exactly as in the source, same count, same numbering. You may move
   them within the sentence if the grammar needs it; you may not invent or drop one.
4. Escape for Android XML: an apostrophe is \\' , an ampersand is &amp;, a
   less-than is &lt;. Use the typographic quotes of the target language.
5. For <plurals>, emit ALL of these quantity values: {quantities}. {lang} needs
   more forms than English — supply each one correctly inflected, do not copy.
6. Anything marked translatable="false" is copied through untouched.
7. A comment above an entry that starts with BUDGET is a WIDTH LIMIT: that string
   shares a fixed-width row with its neighbours. Stay within the stated length,
   choosing a shorter natural word over a literal long one.
8. Items named item_* appear INSIDE a sentence built at runtime, joined by the
   locale's own list conjunction and then capitalised. Give them in the
   grammatical form that reads correctly in such a list ("alarms, calls and 2
   more"), lower case, no final period.
9. Translate from the English only. Do NOT translate via Russian into Ukrainian
   or the reverse — they are separate languages and a calque of one into the
   other is the single worst outcome here.

TERMINOLOGY — use these renderings consistently:
{glossary}

Where Android itself has established wording on a Ukrainian/Russian phone (Do Not
Disturb, starred contacts, conversations), match the system. The user is reading
our screen next to theirs.
"""


def load_key() -> str:
    f = ROOT / ".env.local"
    if not f.exists():
        sys.exit(".env.local not found — create it with GEMINI_API_KEY=...")
    for line in f.read_text().splitlines():
        if line.startswith("GEMINI_API_KEY=") and line.split("=", 1)[1].strip():
            return line.split("=", 1)[1].strip()
    sys.exit("GEMINI_API_KEY is empty in .env.local")


def call(model: str, key: str, system: str, user: str) -> str:
    body = {
        "systemInstruction": {"parts": [{"text": system}]},
        "contents": [{"parts": [{"text": user}]}],
        "generationConfig": {"temperature": 0.2, "maxOutputTokens": 32000},
    }
    url = (f"https://generativelanguage.googleapis.com/v1beta/models/"
           f"{model}:generateContent?key={key}")
    req = urllib.request.Request(url, data=json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json"})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=300) as r:
                d = json.load(r)
            parts = d["candidates"][0]["content"]["parts"]
            return "".join(p.get("text", "") for p in parts)
        except urllib.error.HTTPError as e:
            detail = e.read().decode()[:200]
            if e.code in (429, 500, 503) and attempt < 3:
                time.sleep(8 * (attempt + 1)); continue
            sys.exit(f"{model}: HTTP {e.code} {detail}")
        except Exception as e:
            if attempt < 3:
                time.sleep(8 * (attempt + 1)); continue
            sys.exit(f"{model}: {type(e).__name__}: {e}")


def main() -> None:
    if len(sys.argv) < 2 or sys.argv[1] not in LANGS:
        sys.exit(f"usage: translate.py <{'|'.join(LANGS)}> [--only=2.5|3.1]")
    lang = sys.argv[1]
    only = next((a.split("=")[1] for a in sys.argv[2:] if a.startswith("--only=")), None)
    name, quantities = LANGS[lang]

    system = SYSTEM.format(lang=name, quantities=", ".join(quantities),
                           glossary=GLOSSARY[lang].strip())
    source = SRC.read_text()
    user = ("Translate this file into " + name +
            ". Return the complete resources XML and nothing else.\n\n" + source)

    key = load_key()
    OUT.mkdir(parents=True, exist_ok=True)
    for model, label in MODELS:
        if only and only != label:
            continue
        print(f"→ {name} via {model} …", flush=True)
        text = call(model, key, system, user)
        m = re.search(r"<resources.*?</resources>", text, re.S)
        if not m:
            print(f"  ! {label}: no <resources> block in the reply", flush=True)
            (OUT / f"{lang}-{label}.raw.txt").write_text(text)
            continue
        xml = '<?xml version="1.0" encoding="utf-8"?>\n' + m.group(0) + "\n"
        p = OUT / f"{lang}-{label}.xml"
        p.write_text(xml)
        print(f"  wrote {p}  ({len(xml)} bytes)", flush=True)


if __name__ == "__main__":
    main()
