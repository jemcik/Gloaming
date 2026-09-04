"""Build the Google Play store assets into docs/play/.

Play wants three things the README's assets are not, and each difference is a
rejection rather than a matter of taste:

  icon-512.png        512x512, 32-bit PNG, NO ALPHA. docs/icon.png is RGBA with
                      transparent squircle corners - correct for a README that
                      renders on both a white and a near-black page, and refused
                      by Play. Play applies its own corner mask, so this one is
                      full bleed and gets rounded exactly once.

  feature-graphic{,-uk,-ru}.png 1024x500, no alpha. Required, and nothing to
                      reuse. Composed here rather than drawn by hand so it
                      tracks the palette: change Theme.kt's Arc stops and re-run.

  screenshots/        the captures in docs/screenshots are 1256x2808, which is
                      about 9:20. Both sides are inside Play's 320-3840 range,
                      but that is TALLER than the 9:16 Play's phone-screenshot
                      guidance asks for. These are padded, not cropped - the
                      original framing is what the README shows and what the app
                      actually looks like, and cropping to fit a store would
                      make the two disagree. Padding color is sampled from each
                      capture's own top-left pixel, so a Dusk shot pads dark and
                      a Dawn shot pads light.

Nothing here is committed as a source of truth: docs/screenshots and the
drawables are. This is a build step, and re-running it is always safe.

Run from anywhere:  python3 tools/play_assets.py
"""
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFont

from render_icon import render, ARC, ramp

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
OUT = os.path.join(ROOT, 'docs', 'play')
FONTS = os.path.join(ROOT, 'app', 'src', 'main', 'res', 'font')

# Dusk, from Theme.kt. The feature graphic is the dark theme because that is the
# one the app is designed for - see the palette note in Theme.kt.
SURFACE = '#20242A'
DEEP = '#171A1F'
ON_SURFACE = '#EAEBED'
ON_SURFACE_LOW = '#BCC1CC'

WORDMARK = 'Gloaming'
TAGLINES = {
    'en': 'Bedtime that keeps its own schedule',
    # Not translations of the English but of the SITE's taglines, which were
    # read and corrected by a native speaker - «спрацьовує вчасно» /
    # «срабатывает вовремя» is his wording, not a fresh guess at it.
    'uk': 'Режим сну, який спрацьовує вчасно',
    'ru': 'Режим сна, который срабатывает вовремя',
}


def hx(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))


# FIGTREE HAS NO CYRILLIC - 391 codepoints, Latin only - and unlike a browser,
# PIL cannot fall back per glyph: it draws a tofu box for every letter, which is
# exactly what the first Ukrainian caption came out as. So the Slavic captions
# are set in a face that covers them. San Francisco ships on every Mac and is
# only ever used HERE, to draw a picture; nothing is redistributed.
CYRILLIC_CANDIDATES = [
    '/System/Library/Fonts/SFNS.ttf',
    '/System/Library/Fonts/Supplemental/Arial Unicode.ttf',
    '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',
]


def cyrillic_font(size, grad=620):
    for path in CYRILLIC_CANDIDATES:
        if os.path.exists(path):
            f = ImageFont.truetype(path, size)
            # San Francisco has no Weight axis, but it has GRAD (400-1000),
            # which darkens the strokes WITHOUT changing the metrics - so the
            # Cyrillic band carries the same visual weight as Figtree 600 on the
            # English one, and the line still wraps where it measured. Nobody
            # sees two languages side by side, but the sets should not look like
            # they came from different projects.
            try:
                f.set_variation_by_axes([100, float(size), grad])
            except Exception:
                pass
            return f
    raise SystemExit(
        'No Cyrillic font found for the uk/ru captions. Add one to '
        'CYRILLIC_CANDIDATES; Figtree cannot draw them and PIL will not '
        'substitute, so the band would come out as boxes.')


def caption_font(lang, size, weight):
    """Figtree where it can read the words, something that can where it cannot.

    The GRADE tracks the weight asked for. Grade 620 matches Figtree 600 on the
    caption bands, but the feature graphic's tagline is a 400 - drawing that at
    620 too would make the Slavic graphics heavier than the English one in the
    one place all three sit side by side in a listing switcher.
    """
    if lang == 'en':
        return figtree(size, weight)
    return cyrillic_font(size, grad=620 if weight >= 600 else 400)


def figtree(size, weight):
    """Figtree at a weight. It is a VARIABLE font, so the weight is an axis.

    Theme.kt uses 700 for the brand lockup and 400 for running text; the same
    two are used here so the graphic and the app agree. If Pillow cannot set the
    axis it still renders, at the font's default weight - visibly lighter, but a
    usable asset rather than a crash on someone else's machine.
    """
    f = ImageFont.truetype(os.path.join(FONTS, 'figtree.ttf'), size)
    try:
        f.set_variation_by_axes([weight])
    except Exception:
        pass
    return f


def build_icon():
    """Full-bleed 512, alpha dropped. Play rounds it itself."""
    rgba = render(512, mask=False)
    rgb = Image.new('RGB', rgba.size, hx('#FFFFFF'))
    rgb.paste(rgba, mask=None)          # mask=None: no alpha anywhere to honor
    dest = os.path.join(OUT, 'icon-512.png')
    rgb.save(dest)
    print(dest, rgb.size, rgb.mode)
    return dest


def build_feature(lang='en'):
    W, H = 1024, 500
    tagline = TAGLINES[lang]
    # Ground: a shallow vertical gradient rather than a flat fill, so the tile
    # and the rule below have something to sit against.
    top, bot = np.array(hx(DEEP), float), np.array(hx(SURFACE), float)
    col = top[None, :] + (bot - top)[None, :] * (np.arange(H)[:, None] / (H - 1))
    img = Image.fromarray(
        np.repeat(col[:, None, :], W, axis=1).clip(0, 255).astype(np.uint8), 'RGB')

    d = ImageDraw.Draw(img)
    # The WORDMARK is Latin in every language - it is a name - so it keeps
    # Figtree throughout. Only the tagline needs a face with Cyrillic.
    mark_f, tag_f = figtree(78, 700), caption_font(lang, 29, 400)

    # Centre the whole lockup rather than pinning it left. Play crops the
    # feature graphic from the SIDES on some surfaces, so anything with a left
    # margin and 200px of dead air on the right loses the wrong end of itself.
    # Measured, not guessed - the tagline is wider than the wordmark here, but
    # that is a fact about this string and would flip with a different one.
    TILE, GAP = 196, 56
    text_w = max(d.textlength(WORDMARK, font=mark_f), d.textlength(tagline, font=tag_f))
    left = int((W - (TILE + GAP + text_w)) / 2)

    tile = render(TILE, mask=True)
    img.paste(tile, (left, (H - TILE) // 2), tile)

    x = left + TILE + GAP
    d.text((x, 186), WORDMARK, font=mark_f, fill=hx(ON_SURFACE))
    d.text((x, 286), tagline, font=tag_f, fill=hx(ON_SURFACE_LOW))

    # The dial's own sweep, as a rule under the tagline. Night to dawn, left to
    # right, from the same stops the arc and the crescent use.
    rw, rh, ry = 300, 7, 340
    lut = np.array([ramp(ARC, i / (rw - 1)) for i in range(rw)])
    rule = Image.fromarray(
        np.repeat(lut[None, :, :], rh, axis=0).clip(0, 255).astype(np.uint8), 'RGB')
    round_ = Image.new('L', (rw, rh), 0)
    ImageDraw.Draw(round_).rounded_rectangle([0, 0, rw - 1, rh - 1], radius=rh // 2, fill=255)
    img.paste(rule, (x, ry), round_)

    name = 'feature-graphic.png' if lang == 'en' else f'feature-graphic-{lang}.png'
    dest = os.path.join(OUT, name)
    img.save(dest)
    print(dest, img.size, img.mode)
    return dest


# The README's order, which is also the order the store should tell it in: what
# the night looks like, then what it does, then what you can change. The caption
# says what the SCREEN is, because that is the question a carousel leaves open -
# nobody scrolling a listing is wondering whether it is a phone.
#
# No device frame, deliberately. A bezel encodes nothing true and costs pixels,
# and these screens are dense: a dial, a countdown and a stack of rows. Same
# reason the app has no decorative structure - see the design note in CLAUDE.md.
# The five, and where each is shot from. The arrangement mirrors what the set
# has always shown: the dial in daylight, then the same screen at night, then
# the three that only make sense dark.
SHOTS = [
    ('light', 'home'), ('dark', 'home'), ('dark', 'effects'),
    ('dark', 'allowed'), ('dark', 'settings'),
]

# Play carries a screenshot set per LANGUAGE, and a Ukrainian reader being shown
# English captions over Ukrainian screens is worse than no caption at all.
CAPTIONS = {
    'en': [
        'One window — drag either end, or tap a time to set it',
        'Two themes, and it follows your phone',
        'Gray screen and dimmed wallpaper, where your phone allows it',
        'Choose exactly what still gets through',
        'Theme, language, and the vendor screens that matter',
    ],
    'uk': [
        'Один проміжок: тягніть будь-який кінець або торкніться часу',
        'Дві теми — світла й темна, як на вашому телефоні',
        'Чорно-білий екран і притемнені шпалери, де телефон це дозволяє',
        'Оберіть, що все одно зможе вас потурбувати',
        'Тема, мова і потрібні екрани вашого телефона',
    ],
    'ru': [
        'Один промежуток: тяните любой конец или коснитесь времени',
        'Две темы — светлая и тёмная, как на вашем телефоне',
        'Чёрно-белый экран и приглушённые обои, где телефон это позволяет',
        'Выберите, что всё равно сможет вас побеспокоить',
        'Тема, язык и нужные экраны вашего телефона',
    ],
}

BAND = 300          # caption band MINIMUM height, px - the real one is whatever
                    # the 9:16 canvas leaves above the screenshot


def nine_by_sixteen(w, h, extra=0):
    """The smallest EXACT 9:16 canvas holding a w x h shot plus `extra` height.

    Play's phone screenshots are 16:9 or 9:16 and it enforces it - a 1256x3108
    captioned shot is 1:2.47 and comes back "needs cropping", which for a phone
    screenshot means losing either the caption or the top of the screen. Padding
    the SIDES costs nothing instead: the shot keeps every pixel and the margins
    read as a deliberate frame in the same colour as the band.

    Snapped to a multiple of 9 so the height is exact rather than 0.5624 - the
    ratio is checked, and a rounding error is still not 9:16.
    """
    need = h + extra
    width = max(w, -(-need * 9 // 16))
    width = -(-width // 9) * 9
    return width, width * 16 // 9
CAP_SIZE = 52


def _wrap(d, text, font, width):
    words, lines, line = text.split(), [], ''
    for w in words:
        t = f'{line} {w}'.strip()
        if d.textlength(t, font=font) <= width:
            line = t
        else:
            lines.append(line); line = w
    if line:
        lines.append(line)
    return lines


def build_screenshots():
    """Caption each capture. Also keep a plain 9:16 set as a fallback.

    Two sets because the aspect ratio is unsettled. Play's hard rule is only
    that each side is 320-3840px, which the captures already satisfy at
    1256x2808; the 9:16 figure is guidance tied to promotional placement. So the
    captioned set keeps the phone's own aspect and the plain padded set stands
    by in case Console objects - which is cheaper than guessing wrong and
    re-shooting.

    Neither crops. The README's framing is what the app looks like.
    """
    src = os.path.join(ROOT, 'docs', 'screenshots')

    for lang, captions in CAPTIONS.items():
        cap_dir = os.path.join(OUT, 'screenshots', lang)
        plain_dir = os.path.join(OUT, 'screenshots-plain-9x16', lang)
        os.makedirs(cap_dir, exist_ok=True)
        os.makedirs(plain_dir, exist_ok=True)
        font = caption_font(lang, CAP_SIZE, 600)

        for i, ((theme, name), caption) in enumerate(zip(SHOTS, captions), start=1):
            im = Image.open(os.path.join(src, lang, theme, f'{name}.png')).convert('RGB')
            w, h = im.size

            # A Dusk band above the shot, the same band on all five so they read
            # as one set rather than following each screenshot's own theme. The
            # canvas is a true 9:16, so the band is whatever height is left over
            # once the shot is placed - never less than BAND.
            cw, ch = nine_by_sixteen(w, h, BAND)
            band = ch - h
            out = Image.new('RGB', (cw, ch), hx(SURFACE))
            out.paste(im, ((cw - w) // 2, band))
            d = ImageDraw.Draw(out)
            lines = _wrap(d, caption, font, cw - 260)
            lh = CAP_SIZE * 1.32
            y = (band - lh * len(lines)) / 2
            for ln in lines:
                d.text(((cw - d.textlength(ln, font=font)) / 2, y), ln,
                       font=font, fill=hx(ON_SURFACE))
                y += lh
            out.save(os.path.join(cap_dir, f'{i}.png'))

            # Plain, padded to an exact 9:16 from each capture's own corner
            # colour, so a Dusk shot pads dark and a Dawn shot pads light.
            pw, ph = nine_by_sixteen(w, h)
            pl = Image.new('RGB', (pw, ph), im.getpixel((0, 0)))
            pl.paste(im, ((pw - w) // 2, (ph - h) // 2))
            pl.save(os.path.join(plain_dir, f'{i}.png'))
        print(f'  {lang}: 5 captioned + 5 plain')


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    build_icon()
    for _lang in TAGLINES:
        build_feature(_lang)
    build_screenshots()
