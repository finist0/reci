#!/usr/bin/env python3
"""Izbushka on chicken legs saying "Ђ": the launcher icon of «НЕРодная речь».

Writes the adaptive-icon layers into the app resources and web/icon.svg for the web app.
Usage: python3 art/izba.py [--preview DIR]  (DIR gets SVG previews of both layers)
"""
import os
import sys

sys.dont_write_bytecode = True
from svg2vd import svg2vd  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app/app/src/main/res/drawable")

ICON_BG = "#4F7F34"

CLASSIC = dict(
    outline="#2a1a10", log="#9a6234", logend="#dcaa6a", ring="#6b3f1f",
    roof="#b8322a", gable="#e8bd78", window="#ffd34d", trim="#fbf3e0",
    shutter="#3f7d4f", beam="#6b3f1f", leg="#f0a12b", feather="#f4e8d0",
    smoke="#b9c0c4", brick="#a3452f", dots="#fbf3e0", shadow="#000000",
    bubble="#fffdf6", letter="#b8322a",
)


def pal(**kw):
    p = dict(CLASSIC)
    p.update(kw)
    return p


def line(ink):
    # Outlines only: the monochrome layer is tinted by alpha, so fills must stay empty.
    p = {k: "none" for k in CLASSIC}
    p.update(outline=ink, leg=ink, smoke=ink, letter=ink)
    return p


def leg(path, p, w):
    return (f'<path d="{path}" fill="none" stroke="{p["outline"]}" stroke-width="{w + 4}" '
            f'stroke-linecap="round" stroke-linejoin="round"/>'
            f'<path d="{path}" fill="none" stroke="{p["leg"]}" stroke-width="{w}" '
            f'stroke-linecap="round" stroke-linejoin="round"/>')


def toes(x, y, d, p, w):
    s = f"M{x} {y} l{12*d} 5 M{x} {y} l{2*d} 9 M{x} {y} l{-9*d} 6 M{x} {y} l{-6*d} -3"
    return leg(s, p, w - 1.5)


def izba(p, detail=True, smoke=True, bubble=False, sw=2.2):
    o = p["outline"]
    g = []
    lw = 5.5 if detail else 7
    if p["shadow"] != "none":
        g.append(f'<ellipse cx="100" cy="190" rx="42" ry="4.5" fill="{p["shadow"]}" opacity="0.18"/>')
    g.append(leg("M88 140 L78 160 L89 181", p, lw))
    g.append(leg("M112 140 L122 160 L111 181", p, lw))
    g.append(toes(89, 181, -1, p, lw))
    g.append(toes(111, 181, 1, p, lw))
    for cx, cy, r in ((86, 141, 7), (100, 143, 8), (114, 141, 7)):
        g.append(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{p["feather"]}" stroke="{o}" stroke-width="{sw}"/>')
    # chimney and smoke go first: the roof covers their base
    g.append(f'<rect x="124" y="36" width="13" height="30" fill="{p["brick"]}" stroke="{o}" stroke-width="{sw}"/>')
    if smoke:
        g.append(f'<path d="M131 30 c-7 -6 7 -10 0 -16 c-6 -5 5 -9 1 -14" fill="none" stroke="{p["smoke"]}" '
                 f'stroke-width="{4.5 if detail else 6}" stroke-linecap="round"/>')
    n = 5 if detail else 4
    h = 55 / n
    for i in range(n):
        y = 78 + i * h
        g.append(f'<rect x="52" y="{y:.1f}" width="96" height="{h:.1f}" rx="{h/2:.1f}" fill="{p["log"]}" '
                 f'stroke="{o}" stroke-width="{sw}"/>')
    for i in range(n):
        y = 78 + i * h + h / 2
        for x in (50, 150):
            g.append(f'<circle cx="{x}" cy="{y:.1f}" r="{h/2+0.6:.1f}" fill="{p["logend"]}" stroke="{o}" stroke-width="{sw}"/>')
            if detail:
                g.append(f'<circle cx="{x}" cy="{y:.1f}" r="{h/5:.1f}" fill="none" stroke="{p["ring"]}" stroke-width="1.4"/>')
    g.append(f'<rect x="46" y="133" width="108" height="8" rx="4" fill="{p["beam"]}" stroke="{o}" stroke-width="{sw}"/>')
    g.append(f'<polygon points="56,80 100,44 144,80" fill="{p["gable"]}" stroke="{o}" stroke-width="{sw}" stroke-linejoin="round"/>')
    g.append(f'<circle cx="100" cy="66" r="7" fill="{p["window"]}" stroke="{o}" stroke-width="{sw}"/>')
    if detail:
        g.append(f'<path d="M93 66 H107 M100 59 V73" stroke="{o}" stroke-width="1.6"/>')
    g.append(f'<polyline points="40,86 100,34 160,86" fill="none" stroke="{o}" stroke-width="{17 if detail else 19}" '
             f'stroke-linecap="round" stroke-linejoin="round"/>')
    if p["roof"] != "none":
        g.append(f'<polyline points="40,86 100,34 160,86" fill="none" stroke="{p["roof"]}" '
                 f'stroke-width="{12.5 if detail else 14}" stroke-linecap="round" stroke-linejoin="round"/>')
    g.append(f'<circle cx="100" cy="28" r="{5.5 if detail else 7}" fill="{p["window"]}" stroke="{o}" stroke-width="{sw}"/>')
    for x in (73, 115):
        g.append(f'<rect x="{x}" y="92" width="12" height="28" fill="{p["shutter"]}" stroke="{o}" stroke-width="{sw}"/>')
    g.append(f'<rect x="85" y="92" width="30" height="28" fill="{p["window"]}" stroke="{o}" stroke-width="{sw}"/>')
    g.append(f'<path d="M100 92 V120 M85 106 H115" fill="none" stroke="{o}" stroke-width="{2 if detail else 2.6}"/>')
    g.append(f'<path d="M80 92 L100 79 L120 92 Z" fill="{p["trim"]}" stroke="{o}" stroke-width="{sw}" stroke-linejoin="round"/>')
    g.append(f'<rect x="81" y="120" width="38" height="5" fill="{p["trim"]}" stroke="{o}" stroke-width="{sw}"/>')
    if bubble:
        g.append(f'<path d="M16 14 h48 a10 10 0 0 1 10 10 v20 a10 10 0 0 1 -10 10 h-14 l12 18 l-26 -18 h-20 '
                 f'a10 10 0 0 1 -10 -10 v-20 a10 10 0 0 1 10 -10 z" fill="{p["bubble"]}" stroke="{o}" '
                 f'stroke-width="{sw + 0.8}" stroke-linejoin="round"/>')
        # Serbian Dje: a T with a hook hanging off the stem
        g.append(f'<path d="M27 24 H55 M37 24 V47 M37 34 C45 29 55 31 53 41 L51 50" fill="none" stroke="{p["letter"]}" '
                 f'stroke-width="5.5" stroke-linecap="round" stroke-linejoin="round"/>')
    return "".join(g)


def layer(drawing, bg=None, s=0.31):
    # 108x108 adaptive canvas; the drawing's centre (87,97) goes to the canvas centre
    back = f'<rect width="108" height="108" fill="{bg}"/>' if bg else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">{back}'
            f'<g transform="translate({54 - 87 * s:.2f} {54 - 97 * s:.2f}) scale({s})">{drawing}</g></svg>\n')


def write(path, text):
    with open(path, "w") as f:
        f.write(text)


def main():
    fg = izba(pal(outline="#1b2a12"), detail=False, bubble=True, sw=3.2)
    mono = izba(line("#000000"), detail=False, bubble=True, sw=3.2)
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        os.makedirs(out, exist_ok=True)
        write(os.path.join(out, "icon.svg"), layer(fg, ICON_BG))
        write(os.path.join(out, "icon-mono.svg"), layer(mono, "#ffffff"))
    write(os.path.join(RES, "ic_launcher_fg.xml"), svg2vd(layer(fg)))
    write(os.path.join(RES, "ic_launcher_mono.xml"), svg2vd(layer(mono)))
    # iOS only rounds the corners instead of cutting a circle, so the drawing can be larger.
    write(os.path.join(ROOT, "web/icon.svg"), layer(fg, ICON_BG, s=0.42))
    write(os.path.join(ROOT, "web/icon-maskable.svg"), layer(fg, ICON_BG))


if __name__ == "__main__":
    main()
