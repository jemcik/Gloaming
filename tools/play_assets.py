"""Build the Google Play store assets into docs/play/.

Play wants three things the README's assets are not, and each difference is a
rejection rather than a matter of taste:

  icon-512.png        512x512, 32-bit PNG, NO ALPHA. docs/icon.png is RGBA with
                      transparent squircle corners - correct for a README that
                      renders on both a white and a near-black page, and refused
                      by Play. Play applies its own corner mask, so this one is
                      full bleed and gets rounded exactly once.

  feature-graphic.png 1024x500, no alpha. Required, and there was nothing to
                      reuse. Composed here rather than drawn by hand so it
                      tracks the palette: change Theme.kt's Arc stops and re-run.

  screenshots/        the captures in docs/screenshots are 1256x2808, which is
                      about 9:20. Both sides are inside Play's 320-3840 range,
                      but that is TALLER than the 9:16 Play's phone-screenshot
                      guidance asks for. These are padded, not cropped - the
                      original framing is what the README shows and what the app
                      actually looks like, and cropping to fit a store would
                      make the two disagree. Padding colour is sampled from each
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
TAGLINE = 'Bedtime that keeps its own schedule'


def hx(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))


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
    rgb.paste(rgba, mask=None)          # mask=None: no alpha anywhere to honour
    dest = os.path.join(OUT, 'icon-512.png')
    rgb.save(dest)
    print(dest, rgb.size, rgb.mode)
    return dest


def build_feature():
    W, H = 1024, 500
    # Ground: a shallow vertical gradient rather than a flat fill, so the tile
    # and the rule below have something to sit against.
    top, bot = np.array(hx(DEEP), float), np.array(hx(SURFACE), float)
    col = top[None, :] + (bot - top)[None, :] * (np.arange(H)[:, None] / (H - 1))
    img = Image.fromarray(
        np.repeat(col[:, None, :], W, axis=1).clip(0, 255).astype(np.uint8), 'RGB')

    d = ImageDraw.Draw(img)
    mark_f, tag_f = figtree(78, 700), figtree(29, 400)

    # Centre the whole lockup rather than pinning it left. Play crops the
    # feature graphic from the SIDES on some surfaces, so anything with a left
    # margin and 200px of dead air on the right loses the wrong end of itself.
    # Measured, not guessed - the tagline is wider than the wordmark here, but
    # that is a fact about this string and would flip with a different one.
    TILE, GAP = 196, 56
    text_w = max(d.textlength(WORDMARK, font=mark_f), d.textlength(TAGLINE, font=tag_f))
    left = int((W - (TILE + GAP + text_w)) / 2)

    tile = render(TILE, mask=True)
    img.paste(tile, (left, (H - TILE) // 2), tile)

    x = left + TILE + GAP
    d.text((x, 186), WORDMARK, font=mark_f, fill=hx(ON_SURFACE))
    d.text((x, 286), TAGLINE, font=tag_f, fill=hx(ON_SURFACE_LOW))

    # The dial's own sweep, as a rule under the tagline. Night to dawn, left to
    # right, from the same stops the arc and the crescent use.
    rw, rh, ry = 300, 7, 340
    lut = np.array([ramp(ARC, i / (rw - 1)) for i in range(rw)])
    rule = Image.fromarray(
        np.repeat(lut[None, :, :], rh, axis=0).clip(0, 255).astype(np.uint8), 'RGB')
    round_ = Image.new('L', (rw, rh), 0)
    ImageDraw.Draw(round_).rounded_rectangle([0, 0, rw - 1, rh - 1], radius=rh // 2, fill=255)
    img.paste(rule, (x, ry), round_)

    dest = os.path.join(OUT, 'feature-graphic.png')
    img.save(dest)
    print(dest, img.size, img.mode)
    return dest


def build_screenshots():
    """Pad each capture out to 9:16. Never crop - see the module docstring."""
    src = os.path.join(ROOT, 'docs', 'screenshots')
    dst = os.path.join(OUT, 'screenshots')
    os.makedirs(dst, exist_ok=True)
    # The README's order, which is also the order the store should tell it in:
    # what it looks like, then what it does, then what you can change.
    order = ['home.png', 'home-dark.png', 'effects.png', 'allowed.png', 'settings.png']
    for i, name in enumerate(order, start=1):
        im = Image.open(os.path.join(src, name)).convert('RGB')
        w, h = im.size
        want = int(round(h * 9 / 16))
        if want <= w:
            out = im                       # already 9:16 or wider; leave it alone
        else:
            out = Image.new('RGB', (want, h), im.getpixel((0, 0)))
            out.paste(im, ((want - w) // 2, 0))
        p = os.path.join(dst, f'{i}.png')
        out.save(p)
        print(p, out.size, f'({name})')


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    build_icon()
    build_feature()
    build_screenshots()
