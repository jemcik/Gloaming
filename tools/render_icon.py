"""Render the launcher mark as a raster, for the README and for Google Play.

The launcher icon is a vector; GitHub needs a raster and so does Play.
Screenshotting the phone would bake the wallpaper into the mask's corners, so
this redraws the same geometry from the same numbers the drawables use, crops to
the central 72dp a launcher actually shows of the 108dp canvas, and - for the
README - masks it to a squircle with TRANSPARENT corners. The version that
replaced was an opaque square, which is exactly how it looked on the README: a
black tile.

TWO callers now, and they want opposite things at the corners, which is why
`mask` is a parameter rather than a constant:

  README      squircle, transparent corners. The page is white or near-black
              depending on the reader's theme, and a square would show its own
              edge against both.
  Google Play FULL BLEED, no alpha at all. Play requires a 512x512 32-bit PNG
              and applies its OWN corner mask, so a pre-masked icon would be
              rounded twice - visibly, as a thin light fringe inside Play's
              radius. Play also rejects an alpha channel outright, so the
              squircle's transparent corners are not merely wrong here, they
              fail upload.

The stops here are duplicated from ic_launcher_bg.xml and ic_launcher_foreground
.xml rather than parsed out of them. If either changes, change them here too and
re-run - the check is that the output matches a screenshot of the real launcher
icon, which is how a mirrored first attempt was caught.

Run from anywhere:  python3 tools/render_icon.py       (writes docs/icon.png)
Play's copy comes from tools/play_assets.py, which imports `render` from here.
"""
import math, os, numpy as np
from PIL import Image

S = 8                      # supersample per dp
N = 108 * S
def px(v): return v * S

def lerp(a, b, t):
    return tuple(a[i] + (b[i]-a[i])*t for i in range(3))
def hx(h):
    h=h.lstrip('#'); return tuple(int(h[i:i+2],16) for i in (0,2,4))
def ramp(stops, t):
    t = max(0.0, min(1.0, t))
    for i in range(len(stops)-1):
        o0,c0 = stops[i]; o1,c1 = stops[i+1]
        if o0 <= t <= o1:
            u = 0 if o1==o0 else (t-o0)/(o1-o0)
            return lerp(hx(c0), hx(c1), u)
    return hx(stops[-1][1])

ARC = [(0.00,'#2F4260'), (0.42,'#4E6480'), (0.76,'#A85C2A'), (1.00,'#D4814C')]
BG  = [(0.00,'#FFFFFF'), (1.00,'#FFFFFF')]


def render(size=512, mask=True):
    """The launcher mark at `size` px. `mask=False` leaves the corners opaque.

    Returns RGBA either way - the caller converts. Keeping the return type
    stable means the Play path differs from the README path in exactly one
    place, the alpha assignment below, rather than in two.
    """
    yy, xx = np.mgrid[0:N, 0:N]
    # The mark is scaled 0.90 and dropped 5.18dp to centre it vertically in the
    # 72dp the launcher shows - see ic_launcher_foreground.xml. Sampling inverts it.
    MARK, MARK_DY, CRES = 0.92, 2.51, 0.86
    X = (xx / S - 54.0) / MARK + 54.0
    Y = (yy / S - MARK_DY - 54.0) / MARK + 54.0
    img = np.zeros((N, N, 4), dtype=float)

    # ── background: linear gradient (0,68) -> (108,40)
    ax, ay, bx, by = 0.0, 68.0, 108.0, 40.0
    dx, dy = bx-ax, by-ay
    t = ((X-ax)*dx + (Y-ay)*dy) / (dx*dx + dy*dy)
    t = np.clip(t, 0, 1)
    lut = np.array([ramp(BG, i/255) for i in range(256)])
    idx = (t*255).astype(int)
    img[...,:3] = lut[idx]
    img[...,3] = 255

    def rot(px_, py_, deg, cx=54.0, cy=54.0):
        r = math.radians(deg)
        ox, oy = px_-cx, py_-cy
        return (cx + ox*math.cos(r) + oy*math.sin(r),
                cy - ox*math.sin(r) + oy*math.cos(r))

    # ── crescent: rotate the SAMPLE point by +32 to undo the group's -32
    rx, ry = rot(X, Y, -32.0)
    outer = (rx-54.0)**2 + (ry-54.0)**2 <= (21.0*CRES)**2
    bite  = (rx-(54.0+10.91*CRES))**2 + (ry-(54.0-8.41*CRES))**2 <= (18.48*CRES)**2
    cres  = outer & ~bite
    # gradient along the crescent's own axis: (54,75) night -> (54,33) dawn
    # the gradient lives INSIDE the scaled group, so its axis scales with it
    ct = np.clip(((54.0 + 21.0*CRES) - ry) / (42.0*CRES), 0, 1)
    clut = np.array([ramp(ARC, i/255) for i in range(256)])
    cidx = (ct*255).astype(int)
    img[cres, :3] = clut[cidx][cres]

    # ── arc: rotate by -90.95 to undo the group's +90.95
    ax_, ay_ = rot(X, Y, 90.95)
    r = np.hypot(ax_-54.0, ay_-54.0)
    ang = (np.degrees(np.arctan2(ay_-54.0, ax_-54.0)) + 360) % 360
    band = (r >= 30.6-2.0) & (r <= 30.6+2.0)
    span = (ang >= 56.65) & (ang <= 303.35)          # the gap is the other 113 deg
    arc = band & span
    at = np.clip((ang - 56.65) / (303.35 - 56.65), 0, 1)
    aidx = (at*255).astype(int)
    img[arc, :3] = clut[aidx][arc]

    # ── crop to the central 72dp the launcher shows, then squircle-mask
    lo, hi = px(18), px(90)
    img = img[lo:hi, lo:hi]
    if mask:
        M = hi - lo
        gy, gx = np.mgrid[0:M, 0:M]
        u = (gx - (M-1)/2) / ((M-1)/2); v = (gy - (M-1)/2) / ((M-1)/2)
        sq = (np.abs(u)**5 + np.abs(v)**5) <= 1.0   # superellipse ~ Android squircle
        img[...,3] = np.where(sq, 255, 0)

    out = Image.fromarray(np.clip(img,0,255).astype(np.uint8), 'RGBA')
    return out.resize((size, size), Image.LANCZOS)


if __name__ == '__main__':
    out = render(512, mask=True)
    dest = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'docs', 'icon.png')
    dest = os.path.normpath(dest)
    out.save(dest)
    print(dest, out.size, "· corner alpha", out.getpixel((3, 3))[3])
