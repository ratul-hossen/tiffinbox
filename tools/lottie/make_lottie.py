"""Generates TiffinBox's original Lottie animations (no third-party assets).

Run with the scratchpad venv:  venv/bin/python make_lottie.py <out_dir>
Each animation is 512x512 @ 30fps, flat vector, brand colours.
"""
import math
import sys
import os

from lottie import objects, Point, Color
from lottie.objects import easing
from lottie.exporters.core import export_lottie

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)

FPS = 30
S = 512
C = S / 2

RED = Color(0.898, 0.224, 0.208)       # #E53935
RED_DARK = Color(0.70, 0.14, 0.13)
ORANGE = Color(1.0, 0.54, 0.24)        # #FF8A3D
GOLD = Color(1.0, 0.784, 0.239)        # #FFC83D
GREEN = Color(0.18, 0.72, 0.45)        # #2EB872
CREAM = Color(1.0, 0.953, 0.878)       # #FFF3E0
WHITE = Color(1, 1, 1)
CHAR = Color(0.17, 0.17, 0.17)
STEAM = Color(0.85, 0.85, 0.85)

EO = easing.EaseOut
EI = easing.EaseIn
EIO = easing.Sigmoid
LIN = easing.Linear


def anim(seconds):
    a = objects.Animation(int(round(seconds * FPS)), FPS)
    a.width = S
    a.height = S
    return a


def layer(a, name):
    lay = objects.ShapeLayer()
    lay.name = name
    lay.in_point = 0
    lay.out_point = a.out_point
    a.insert_layer(0, lay)  # first layer = top, so newer layers go above
    return lay


def group(parent, anchor=(C, C)):
    # Lottie draws earlier items on top, so insert each new group first:
    # anything created later in this script ends up visually above.
    g = objects.Group()
    parent.shapes.insert(0, g)
    g.transform.anchor_point.value = Point(*anchor)
    g.transform.position.value = Point(*anchor)
    return g


def kf(prop, frames):
    """frames: list of (frame, value, easing-or-None)."""
    for f, v, e in frames:
        if isinstance(v, (tuple, list)):
            v = Point(*v)
        prop.add_keyframe(f, v, e() if e else LIN())


def ellipse(g, cx, cy, w, h):
    e = g.add_shape(objects.Ellipse())
    e.position.value = Point(cx, cy)
    e.size.value = Point(w, h)
    return e


def rect(g, cx, cy, w, h, r=0):
    rc = g.add_shape(objects.Rect())
    rc.position.value = Point(cx, cy)
    rc.size.value = Point(w, h)
    rc.rounded.value = r
    return rc


def path(g, pts, closed=False, smooth=None):
    """pts: list of (x, y); smooth: list of (in_tangent, out_tangent) per point or None."""
    p = g.add_shape(objects.Path())
    b = objects.Bezier()
    for i, (x, y) in enumerate(pts):
        if smooth:
            ti, to = smooth[i]
            b.add_point(Point(x, y), Point(*ti), Point(*to))
        else:
            b.add_point(Point(x, y))
    b.closed = closed
    p.shape.value = b
    return p


def fill(g, color, opacity=100):
    f = g.add_shape(objects.Fill(color))
    f.opacity.value = opacity
    return f


def stroke(g, color, width, cap=objects.LineCap.Round):
    s = g.add_shape(objects.Stroke(color, width))
    s.line_cap = cap
    s.line_join = objects.LineJoin.Round
    return s


def save(a, name):
    export_lottie(a, os.path.join(OUT, name + ".json"))


# ---------------------------------------------------------------- success
def success_check():
    a = anim(1.8)
    # confetti burst (bottom layer)
    lay = layer(a, "confetti")
    colors = [RED, GOLD, ORANGE, GREEN, RED, GOLD, ORANGE, GREEN, GOLD, RED]
    for i in range(10):
        ang = i / 10 * math.tau + 0.3
        g = group(lay)
        ellipse(g, C, C, 18 if i % 2 else 12, 18 if i % 2 else 12)
        fill(g, colors[i])
        dx, dy = math.cos(ang) * 190, math.sin(ang) * 190
        kf(g.transform.position, [(12, (C, C), EO), (36, (C + dx, C + dy), None)])
        kf(g.transform.opacity, [(0, 0, None), (12, 100, None), (30, 100, None), (42, 0, None)])
        kf(g.transform.scale, [(12, (60, 60), EO), (24, (120, 120), None), (42, (40, 40), None)])
    # soft halo ring
    lay = layer(a, "halo")
    g = group(lay)
    ellipse(g, C, C, 300, 300)
    stroke(g, GREEN, 10)
    kf(g.transform.scale, [(6, (70, 70), EO), (30, (125, 125), None)])
    kf(g.transform.opacity, [(0, 0, None), (5, 0, None), (6, 70, None), (30, 0, None)])
    # disc
    lay = layer(a, "disc")
    g = group(lay)
    ellipse(g, C, C, 300, 300)
    fill(g, GREEN)
    kf(g.transform.scale, [(0, (0, 0), EO), (12, (112, 112), EIO), (20, (100, 100), None)])
    # check mark drawn on
    lay = layer(a, "check")
    g = group(lay)
    path(g, [(C - 72, C + 4), (C - 22, C + 54), (C + 78, C - 50)])
    trim = g.add_shape(objects.Trim())
    kf(trim.end, [(14, 0, EIO), (30, 100, None)])
    stroke(g, WHITE, 30)
    kf(g.transform.opacity, [(0, 0, None), (13, 0, None), (14, 100, None)])
    save(a, "success_check")


# ---------------------------------------------------------------- cooking pot
def cooking_pot():
    a = anim(2.4)
    n = a.out_point
    # steam wisps (behind lid)
    lay = layer(a, "steam")
    for i, x in enumerate([C - 60, C, C + 60]):
        g = group(lay, (x, 200))
        path(g, [(x, 230), (x + 18, 190), (x - 14, 150), (x + 10, 112)],
             smooth=[((0, 0), (14, -10)), ((0, 14), (0, -14)), ((0, 14), (0, -14)), ((-10, 10), (0, 0))])
        stroke(g, STEAM, 14)
        # Seamless loop: sample the phase-shifted rise/fade every 3 frames.
        pos, op = [], []
        for t in range(0, n + 1, 3):
            ph = (t / n + i / 3) % 1.0
            pos.append((t, (x, 236 - 90 * ph), None))
            op.append((t, 85 * math.sin(math.pi * ph), None))
        kf(g.transform.position, pos)
        kf(g.transform.opacity, op)
    # pot body (shadow first so it sits underneath)
    lay = layer(a, "pot")
    sh = group(lay)
    ellipse(sh, C, 418, 330, 28)
    fill(sh, CHAR, 22)
    for side in (-1, 1):
        h = group(lay)
        rect(h, C + side * 172, 300, 56, 24, 12)
        fill(h, RED_DARK)
    g = group(lay, (C, 330))
    rect(g, C, 335, 300, 160, 44)
    fill(g, RED)
    hl = group(lay)
    rect(hl, C - 100, 330, 26, 96, 13)
    fill(hl, WHITE, 28)
    band = group(lay)
    rect(band, C, 300, 300, 18, 6)
    fill(band, RED_DARK)
    # domed lid that jiggles as the pot boils
    lay = layer(a, "lid")
    g = group(lay, (C, 262))
    path(g, [(C - 158, 268), (C, 214), (C + 158, 268)], closed=True,
         smooth=[((0, 0), (20, -40)), ((-110, 0), (110, 0)), ((-20, -40), (0, 0))])
    fill(g, RED_DARK)
    knob = group(g, (C, 214))
    rect(knob, C, 208, 52, 26, 13)
    fill(knob, CHAR)
    kf(g.transform.position, [(0, (C, 262), EIO), (6, (C, 252), EIO), (12, (C, 262), EIO),
                               (18, (C, 255), EIO), (24, (C, 262), None), (n, (C, 262), None)])
    kf(g.transform.rotation, [(0, 0, EIO), (6, -4, EIO), (12, 3, EIO), (18, -1, EIO), (24, 0, None), (n, 0, None)])
    save(a, "cooking_pot")


# ---------------------------------------------------------------- delivery pin
def delivery_pin():
    a = anim(2.0)
    n = a.out_point
    # dashed route
    lay = layer(a, "route")
    g = group(lay)
    path(g, [(40, 470), (160, 420), (250, 440), (330, 390)],
         smooth=[((0, 0), (50, -20)), ((-40, 0), (40, 0)), ((-40, -10), (40, 10)), ((-30, 10), (0, 0))])
    st = stroke(g, ORANGE, 12)
    st.dashes = [objects.StrokeDash(18, objects.StrokeDashType.Dash), objects.StrokeDash(16, objects.StrokeDashType.Gap)]
    off = objects.StrokeDash(0, objects.StrokeDashType.Offset)
    kf(off.length, [(0, 0, None), (n, -68, None)])
    st.dashes.append(off)
    # ripples at pin base
    lay = layer(a, "ripples")
    for i in range(2):
        g = group(lay, (C + 80, 380))
        ellipse(g, C + 80, 380, 120, 40)
        stroke(g, RED, 6)
        start = i * n / 2
        kf(g.transform.scale, [(start, (30, 30), EO), (start + n / 2, (160, 160), None)])
        kf(g.transform.opacity, [(start, 90, None), (start + n / 2, 0, None)])
    # map pin: round head + pointed tip, bouncing with a squash on landing
    lay = layer(a, "pin")
    px = C + 80
    g = group(lay, (px, 372))
    ellipse(g, px, 236, 156, 156)
    path(g, [(px - 66, 272), (px + 66, 272), (px, 372)], closed=True)
    fill(g, RED)
    dot = group(g, (px, 236))
    ellipse(dot, px, 236, 64, 64)
    fill(dot, WHITE)
    kf(g.transform.position, [(0, (px, 372), EO), (n * 0.3, (px, 330), EI), (n * 0.6, (px, 372), EIO),
                               (n * 0.7, (px, 372), None), (n, (px, 372), None)])
    kf(g.transform.scale, [(0, (100, 100), None), (n * 0.58, (100, 100), EO), (n * 0.64, (112, 90), EO),
                            (n * 0.74, (100, 100), None), (n, (100, 100), None)])
    save(a, "delivery_pin")


# ---------------------------------------------------------------- star burst
def star_burst():
    a = anim(1.6)
    lay = layer(a, "rays")
    for i in range(12):
        ang = i / 12 * math.tau
        g = group(lay)
        x1, y1 = C + math.cos(ang) * 120, C + math.sin(ang) * 120
        x2, y2 = C + math.cos(ang) * 180, C + math.sin(ang) * 180
        path(g, [(x1, y1), (x2, y2)])
        trim = g.add_shape(objects.Trim())
        kf(trim.end, [(8, 0, EO), (18, 100, None)])
        kf(trim.start, [(16, 0, EI), (30, 100, None)])
        stroke(g, GOLD if i % 2 == 0 else ORANGE, 12)
        # round caps would leave a dot behind - fade the ray out with its tail
        kf(g.transform.opacity, [(0, 0, None), (8, 100, None), (24, 100, None), (30, 0, None)])
    lay = layer(a, "star")
    g = group(lay)
    pts = []
    for i in range(10):
        r = 130 if i % 2 == 0 else 58
        ang = -math.pi / 2 + i * math.pi / 5
        pts.append((C + math.cos(ang) * r, C + math.sin(ang) * r))
    path(g, pts, closed=True)
    fill(g, GOLD)
    stroke(g, ORANGE, 10)
    kf(g.transform.scale, [(0, (0, 0), EO), (12, (120, 120), EIO), (20, (100, 100), None)])
    kf(g.transform.rotation, [(0, -90, EO), (20, 0, None)])
    save(a, "star_burst")


# ---------------------------------------------------------------- empty bowl
def empty_bowl():
    a = anim(3.0)
    n = a.out_point
    lay = layer(a, "shadow")
    g = group(lay, (C, 400))
    ellipse(g, C, 400, 260, 26)
    fill(g, CHAR, 16)
    kf(g.transform.scale, [(0, (100, 100), EIO), (n / 2, (78, 78), EIO), (n, (100, 100), None)])
    lay = layer(a, "bowl")
    g = group(lay, (C, 300))
    # bowl: half disc
    path(g, [(C - 150, 260), (C + 150, 260), (C, 380)], closed=True,
         smooth=[((0, 0), (0, 0)), ((0, 0), (0, 70)), ((90, 0), (-90, 0))])
    fill(g, ORANGE)
    rim = group(g, (C, 260))
    ellipse(rim, C, 260, 300, 44)
    fill(rim, CREAM)
    inner = group(g, (C, 262))
    ellipse(inner, C, 262, 250, 26)
    fill(inner, Color(0.94, 0.86, 0.74))
    # chopsticks resting
    for k, (dx, rot) in enumerate([(-10, -18), (14, -24)]):
        cs = group(g, (C + 60 + dx, 220))
        rect(cs, C + 60 + dx, 220, 12, 190, 6)
        fill(cs, CHAR)
        cs.transform.rotation.value = rot
    kf(g.transform.position, [(0, (C, 300), EIO), (n / 2, (C, 280), EIO), (n, (C, 300), None)])
    kf(g.transform.rotation, [(0, -2, EIO), (n / 2, 2, EIO), (n, -2, None)])
    save(a, "empty_bowl")


# ---------------------------------------------------------------- hourglass
def hourglass():
    a = anim(2.8)
    n = a.out_point
    flip = int(n * 0.8)
    lay = layer(a, "glass")
    g = group(lay)
    glass = group(g)
    path(glass, [(C - 90, 146), (C + 90, 146), (C + 14, C), (C + 90, 366), (C - 90, 366), (C - 14, C)], closed=True)
    fill(glass, CREAM)
    stroke(glass, RED_DARK, 8)
    # top sand shrinking
    top = group(g, (C, 160))
    path(top, [(C - 70, 160), (C + 70, 160), (C, 240)], closed=True)
    fill(top, GOLD)
    kf(top.transform.scale, [(0, (100, 100), LIN), (flip, (0, 0), None), (n, (100, 100), None)])
    kf(top.transform.position, [(0, (C, 160), LIN), (flip, (C, 240), None), (n, (C, 160), None)])
    # bottom sand growing
    bot = group(g, (C, 352))
    path(bot, [(C - 72, 352), (C + 72, 352), (C, 290)], closed=True)
    fill(bot, GOLD)
    kf(bot.transform.scale, [(0, (0, 0), LIN), (flip, (100, 100), None), (n, (0, 0), None)])
    # falling stream
    st = group(g)
    rect(st, C, 296, 8, 96, 4)
    fill(st, GOLD)
    kf(st.transform.opacity, [(0, 100, None), (flip - 4, 100, None), (flip, 0, None), (n, 0, None)])
    # frame bars on top of everything else
    for y in (130, 382):
        bar = group(g)
        rect(bar, C, y, 220, 28, 14)
        fill(bar, RED_DARK)
    # flip the whole glass at the end
    g.transform.anchor_point.value = Point(C, C)
    g.transform.position.value = Point(C, C)
    kf(g.transform.rotation, [(0, 0, None), (flip, 0, EIO), (n, 180, None)])
    save(a, "hourglass")


# ---------------------------------------------------------------- splash ripple
def splash_ripple():
    a = anim(1.6)
    n = a.out_point
    lay = layer(a, "rings")
    for i, col in enumerate([RED, ORANGE, GOLD]):
        g = group(lay)
        ellipse(g, C, C, 200, 200)
        stroke(g, col, 8)
        start = i * 6
        kf(g.transform.scale, [(start, (60, 60), EO), (start + 30, (230, 230), None)])
        kf(g.transform.opacity, [(start, 0, None), (start + 4, 90, None), (start + 30, 0, None)])
    lay = layer(a, "sparkles")
    for i in range(8):
        ang = i / 8 * math.tau + 0.2
        g = group(lay)
        r = 150 + (i % 3) * 30
        x, y = C + math.cos(ang) * r, C + math.sin(ang) * r
        # four-point sparkle
        path(g, [(x, y - 16), (x + 4, y - 4), (x + 16, y), (x + 4, y + 4), (x, y + 16), (x - 4, y + 4), (x - 16, y), (x - 4, y - 4)], closed=True)
        fill(g, GOLD if i % 2 else WHITE)
        g.transform.anchor_point.value = Point(x, y)
        g.transform.position.value = Point(x, y)
        s0 = 10 + (i % 4) * 4
        kf(g.transform.scale, [(s0, (0, 0), EO), (s0 + 8, (130, 130), EIO), (s0 + 20, (0, 0), None)])
    save(a, "splash_ripple")


if __name__ == "__main__":
    success_check()
    cooking_pot()
    delivery_pin()
    star_burst()
    empty_bowl()
    hourglass()
    splash_ripple()
    print("done")
