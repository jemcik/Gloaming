#!/usr/bin/env python3
"""
Mechanical check of a translated strings.xml against the English source.

Catches the classes a translator cannot be trusted on and a human will not spot
by eye: a dropped format placeholder (crashes at runtime), a missing plural form
(wrong word for half the numbers), a key that was silently dropped or renamed,
and a string left in English.

    python3 tools/check_translation.py /tmp/gloaming-i18n/ru-2.5.xml ru
"""
import re, sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/res/values/strings.xml"
NEEDED = {"ru": {"one", "few", "many", "other"}, "uk": {"one", "few", "many", "other"}}
PLACEHOLDER = re.compile(r"%(?:\d+\$)?0?\d*[sd]")


def load(path):
    root = ET.parse(path).getroot()
    strings, plurals, notrans = {}, {}, set()
    for el in root:
        name = el.get("name")
        if el.tag == "string":
            strings[name] = (el.text or "")
            if el.get("translatable") == "false":
                notrans.add(name)
        elif el.tag == "plurals":
            plurals[name] = {i.get("quantity"): (i.text or "") for i in el}
    return strings, plurals, notrans


def budgets(path):
    """`BUDGET <total> key1 key2 ...` — a limit on the SUM of those strings,
    because the real constraint is a row of fixed width shared between them."""
    text = Path(path).read_text()
    out = []
    for m in re.finditer(r"BUDGET\s+(\d+)\s+([a-z0-9_ ]+)", text):
        out.append(("sum", int(m.group(1)), m.group(2).split()))
    for m in re.finditer(r"BUDGET-EACH\s+(\d+)\s+([a-z0-9_ ]+)", text):
        out.append(("each", int(m.group(1)), m.group(2).split()))
    return out


def main():
    if len(sys.argv) < 3:
        sys.exit("usage: check_translation.py <file.xml> <lang>")
    path, lang = sys.argv[1], sys.argv[2]
    es, ep, notrans = load(SRC)
    ts, tp, _ = load(path)
    budget = budgets(SRC)
    problems = []

    for k in es:
        # translatable="false" means the string is deliberately NOT in the
        # locale files - absent is correct, present is what lint complains about.
        if k not in ts and k not in notrans:
            problems.append(f"MISSING string  {k}")
    for k in ts:
        if k not in es:
            problems.append(f"EXTRA string    {k}")
    for k in ep:
        if k not in tp:
            problems.append(f"MISSING plurals {k}")

    for k, v in ts.items():
        if k not in es:
            continue
        want = sorted(PLACEHOLDER.findall(es[k]))
        got = sorted(PLACEHOLDER.findall(v))
        if want != got:
            problems.append(f"PLACEHOLDER     {k}: source {want} -> translation {got}")
        bare = PLACEHOLDER.sub("", v).strip()
        if (k not in notrans and bare and v.strip() == es[k].strip() and len(v) > 3):
            problems.append(f"UNTRANSLATED    {k}: {v!r}")


    for k, forms in tp.items():
        missing = NEEDED[lang] - set(forms)
        if missing:
            problems.append(f"PLURAL FORMS    {k}: missing {sorted(missing)}")
        # A target form may legitimately USE a placeholder that English omits -
        # Russian's "one" covers 21 and 31, so it needs the number where English
        # can write "One". What it must never do is invent one we do not pass.
        available = set()
        for sv in ep.get(k, {}).values():
            available |= set(PLACEHOLDER.findall(sv))
        for q, v in forms.items():
            extra = set(PLACEHOLDER.findall(v)) - available
            if extra:
                problems.append(f"PLACEHOLDER     {k}[{q}]: uses {sorted(extra)}, not passed")
            # In ru and uk "one" also covers 21, 31, 41... so a form that spells
            # the number out as a word is wrong for every one of those. If the
            # source passes a number, every form has to be able to show it.
            if available and not PLACEHOLDER.findall(v):
                problems.append(
                    f"IMPLIED QUANTITY {k}[{q}]: {v!r} has no placeholder, but "
                    f"'{q}' covers more than one number in {lang}")

    # ListFormatter inserts the locale's own conjunction between the last two
    # items, so an item that starts with one produces "a, b и и ещё 6".
    CONJ = ("и ", "і ", "та ", "and ", "й ")
    for k, forms in tp.items():
        if not k.startswith("item_"):
            continue
        for q, v in forms.items():
            if v.lstrip().lower().startswith(CONJ):
                problems.append(
                    f"DOUBLE CONJUNCTION {k}[{q}]: {v!r} - the list joiner adds one")
    for k, v in ts.items():
        if k.startswith("item_") and v.lstrip().lower().startswith(CONJ):
            problems.append(f"DOUBLE CONJUNCTION {k}: {v!r} - the list joiner adds one")

    for kind, total, keys in budget:
        if kind == "sum":
            n = sum(len(ts.get(k, "")) for k in keys)
            if n > total:
                got = ", ".join(f"{k}={len(ts.get(k,''))}" for k in keys)
                problems.append(f"ROW TOO WIDE    {n} chars vs {total} allowed ({got})")
        else:
            for k in keys:
                n = len(ts.get(k, ""))
                if n > total:
                    problems.append(
                        f"TOO LONG        {k}: {n} chars vs {total} allowed "
                        f"- it is capped at one line, so this truncates")

    print(f"{path}  ({len(ts)} strings, {len(tp)} plurals)")
    if not problems:
        print("  clean")
    for p in problems:
        print("  " + p)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
