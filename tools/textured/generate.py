"""Draws the images of the `textured` variant (land menus for players with the resource pack).

    python3 tools/textured/generate.py

Writes src/main/resources/artisan-system/lands/assets/{textures,glyphs}/lands_*.png and their .yml
descriptors. Pillow only. Vanilla palette (generic_54 / generic_27); no text in the images: the texts
are title texts and button labels, translated.

The drawing functions down to `card_badge` are those of ArtisanGuilds' tools/textured/generate.py,
unchanged: the buttons, arrows, back, close, toggles and boxes come out identical to the pixel to
guilds_* (and to daily_* where those match), so that they move to Artisan's shared UI kit
(button.3x1, arrow.next.1x2, close.1x1…) without a visible change. The lands' own: the icons of the
cards and boxes, and the windows, one row shorter (a 5-row `window.5`, the links in a `window.3`)
because the lands' titles hold on one line.
"""
from pathlib import Path

import math

from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/artisan-system/lands/assets"

CLEAR = (0, 0, 0, 0)
OUTLINE = (0, 0, 0, 255)
VBG = (198, 198, 198, 255)
VHI = (255, 255, 255, 255)
VSH = (85, 85, 85, 255)
VSLOT = (139, 139, 139, 255)
VSLOT_SH = (55, 55, 55, 255)
GOLD = (244, 200, 66, 255)
GOLD_D = (190, 132, 28, 255)
GOLD_H = (255, 238, 150, 255)
W = 176


def px(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c)


def rect(img, x0, y0, x1, y1, c):  # inclusive
    ImageDraw.Draw(img).rectangle([x0, y0, x1, y1], fill=c)


def bevel(img, x0, y0, x1, y1, fill, hi, sh):
    rect(img, x0, y0, x1, y1, fill)
    rect(img, x0, y0, x1 - 1, y0, hi)
    rect(img, x0, y0, x0, y1 - 1, hi)
    rect(img, x0 + 1, y1, x1, y1, sh)
    rect(img, x1, y0 + 1, x1, y1, sh)


def slot_xy(slot):
    return 7 + (slot % 9) * 18, 17 + (slot // 9) * 18


def vslot(img, x, y):
    """An 18x18 vanilla slot whose top-left pixel is (x, y)."""
    rect(img, x, y, x + 17, y + 17, VSLOT)
    rect(img, x, y, x + 16, y, VSLOT_SH)
    rect(img, x, y, x, y + 16, VSLOT_SH)
    rect(img, x + 1, y + 17, x + 17, y + 17, VHI)
    rect(img, x + 17, y + 1, x + 17, y + 17, VHI)


def sprite(rows, colors):
    t = Image.new("RGBA", (len(rows[0]), len(rows)), CLEAR)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in colors:
                t.putpixel((x, y), colors[ch])
    return t


# ------------------------------------------------------------------ windows

def panel(h):
    """The vanilla window panel, 176 wide, with the player's inventory at its usual place."""
    img = Image.new("RGBA", (W, h), CLEAR)
    rect(img, 3, 0, W - 4, h - 1, OUTLINE); rect(img, 0, 3, W - 1, h - 4, OUTLINE)
    rect(img, 1, 1, W - 2, h - 2, OUTLINE)
    rect(img, 2, 1, W - 3, h - 2, VBG); rect(img, 1, 2, W - 2, h - 3, VBG)
    rect(img, 3, 1, W - 5, 2, VHI); rect(img, 1, 3, 2, h - 5, VHI); px(img, 3, 3, VHI)
    rect(img, 4, h - 3, W - 3, h - 2, VSH); rect(img, W - 3, 4, W - 2, h - 4, VSH); px(img, W - 4, h - 4, VSH)
    inv = h - 83  # top of the inventory's slot frames: 139 in a 6-row window, 83 in a 3-row one
    for r in range(3):
        for c in range(9):
            vslot(img, 7 + c * 18, inv + r * 18)
    for c in range(9):
        vslot(img, 7 + c * 18, h - 25)
    return img


def groove(img, x0, x1, y):
    """An engraved line: dark, then light under it."""
    rect(img, x0, y, x1, y, VSH)
    rect(img, x0 + 1, y + 1, x1 + 1, y + 1, VHI)


# The lands' titles fit on one line at the vanilla place, so their windows start the content at the
# first row, right under the title: 5 rows instead of the guilds' 6 (whose first row is left to a
# two-line title), and no groove under the title.
ROWS5 = 204  # = 222 - 18, the guilds' 6-row window one row shorter


def main_background():
    """A land's menu and its chunks' menu: the cards (images of their own) over rows 0-3, a groove,
    the bottom row."""
    img = panel(ROWS5)
    groove(img, 7, 167, 88)
    return img


def links_background():
    """The links: the three cards over rows 0-1, a groove, the bottom row; 3 rows (166, like the
    confirmations)."""
    img = panel(166)
    groove(img, 7, 167, 52)
    return img


def list_background():
    """The lists: the 28 slots (1-7 ... 28-34) from the first row."""
    img = panel(ROWS5)
    gx0, gy0 = slot_xy(1)
    for r in range(4):
        for c in range(7):
            vslot(img, gx0 + c * 18, gy0 + r * 18)
    return img


def confirm_background():
    """The 3-row confirmations: one slot in the middle (13) for what is about to go, the two buttons
    under it (19-21, 23-25)."""
    img = panel(166)
    x, y = slot_xy(13)
    vslot(img, x, y)
    return img


# ------------------------------------------------------------------ icons (16x16)

def icon_members():  # two members, one behind the other
    return sprite([
        "................",
        ".......kkkkkkkk.",
        ".......kKKKKKKk.",
        ".......kKKKKKKk.",
        "..hhhhhhhhssssk.",
        "..hhhhhhhhseseK.",
        "..hhhhhhhhssssk.",
        "..hsssssshssssk.",
        "..hweswesh.ssk..",
        "..hssssssh.ccc..",
        "..hssmmssh.cccc.",
        "...ssssss..cccc.",
        ".tttttttttttccc.",
        ".tttttttttttccc.",
        ".tTTTTTTTTTtccc.",
        "................",
    ], {"h": (74, 52, 34, 255), "s": (228, 178, 140, 255), "w": VHI, "e": (60, 60, 120, 255),
        "m": (170, 110, 90, 255), "t": (60, 150, 170, 255), "T": (40, 110, 130, 255),
        "k": (40, 34, 30, 255), "K": (60, 50, 44, 255), "c": (60, 80, 150, 255)})


def icon_ranks():  # a medal on its ribbon
    return sprite([
        "................",
        "..RRR......BBB..",
        "..RrRR....BBbB..",
        "...RrRR..BBbB...",
        "....RrRRBBbB....",
        ".....RRRBBB.....",
        "......ddddd.....",
        ".....dGGGGGd....",
        "....dGHHGGGGd...",
        "....dGHGGGGGd...",
        "....dGGGSGGGd...",
        "....dGGSSSGGd...",
        "....dGGGSGGGd...",
        ".....dGGGGGd....",
        "......ddddd.....",
        "................",
    ], {"R": (200, 50, 46, 255), "r": (240, 110, 90, 255), "B": (52, 92, 170, 255), "b": (110, 150, 230, 255),
        "d": GOLD_D, "G": GOLD, "H": GOLD_H, "S": (150, 96, 20, 255)})


def icon_chest():
    return sprite([
        "................",
        "................",
        ".oooooooooooooo.",
        ".oWwwwwwwwwwwWo.",
        ".oWwwwwwwwwwwWo.",
        ".oWwwwwwwwwwwWo.",
        ".oddddddggddddo.",
        ".oWwwwwwgGwwwWo.",
        ".oWwwwwwggwwwWo.",
        ".oWwwwwwwwwwwWo.",
        ".oWwwwwwwwwwwWo.",
        ".oWwwwwwwwwwwWo.",
        ".oWWWWWWWWWWWWo.",
        ".oooooooooooooo.",
        "................",
        "................",
    ], {"o": (40, 26, 12, 255), "W": (120, 78, 36, 255), "w": (164, 112, 56, 255), "d": (70, 44, 18, 255),
        "g": (210, 210, 210, 255), "G": (110, 110, 110, 255)})


def icon_home():  # a house
    return sprite([
        "................",
        ".......oo.......",
        "......oRRo..oo..",
        ".....oRrrRo.oo..",
        "....oRrrrrRooo..",
        "...oRrrrrrrRoo..",
        "..oRrrrrrrrrRo..",
        ".oRRRRRRRRRRRRo.",
        "..oWWWWWWWWWWo..",
        "..oWBBWWWWddWo..",
        "..oWBBWWWWddWo..",
        "..oWWWWWWWddWo..",
        "..oWWWWWWWdgWo..",
        "..oWWWWWWWddWo..",
        "..oooooooooooo..",
        "................",
    ], {"o": (50, 30, 24, 255), "R": (160, 40, 36, 255), "r": (210, 70, 56, 255), "W": (226, 208, 168, 255),
        "B": (110, 170, 220, 255), "d": (110, 70, 34, 255), "g": GOLD})


def icon_log_small():  # 8x10: the log's book, small enough for half an entry
    return sprite([
        "......f.",
        "oooooFfo",
        "oPPPPfPo",
        "oPllqllo",
        "oPPqPPPo",
        "oPllllPo",
        "oPPPPPPo",
        "oPlllPPo",
        "oBBBBBBo",
        "oooooooo",
    ], {"o": (60, 34, 20, 255), "P": (236, 226, 196, 255), "l": (150, 140, 120, 255), "B": (140, 60, 40, 255),
        "f": (220, 220, 220, 255), "F": (255, 255, 255, 255), "q": (60, 60, 60, 255)})


def icon_lock():
    return sprite([
        "..oooo..",
        ".o....o.",
        ".o....o.",
        "oooooooo",
        "oGGGGGGo",
        "oGGddGGo",
        "oGGddGGo",
        "oooooooo",
    ], {"o": (60, 60, 60, 255), "G": (170, 170, 170, 255), "d": (60, 60, 60, 255)})


def icon_alliances():  # two shields side by side, each with a white band
    return sprite([
        "................",
        "................",
        ".ooooooo.ooooooo",
        ".oGGGGGo.oBBBBBo",
        ".oGGGGGo.oBBBBBo",
        ".oWWWWWo.oWWWWWo",
        ".oGGGGGo.oBBBBBo",
        ".oGGGGGo.oBBBBBo",
        ".oGGGGGo.oBBBBBo",
        "..oGGGo...oBBBo.",
        "...oGo.....oBo..",
        "....o.......o...",
        "................",
        "................",
        "................",
        "................",
    ], {"o": (40, 40, 40, 255), "G": (70, 150, 60, 255), "B": (60, 110, 200, 255), "W": (236, 236, 236, 255)})


def icon_coin():  # 8x8
    return sprite([
        "..dddd..",
        ".dHGGGd.",
        "dHGddGGd",
        "dGGdGGGd",
        "dGGdGGGd",
        "dGGddGGd",
        ".dGGGGd.",
        "..dddd..",
    ], {"d": GOLD_D, "G": GOLD, "H": GOLD_H})


def icon_heads():  # 8x8: two small heads
    return sprite([
        "....kkkk",
        "hhhhkSSk",
        "hsssSSSk",
        "hese.SS.",
        "hsss.cc.",
        ".ss.cccc",
        "tttttccc",
        "tttttccc",
    ], {"h": (74, 52, 34, 255), "s": (228, 178, 140, 255), "e": (60, 60, 120, 255), "t": (60, 150, 170, 255),
        "k": (40, 34, 30, 255), "S": (170, 122, 90, 255), "c": (60, 80, 150, 255)})


def icon_bubble(color):  # 10x9 chat bubble
    c = color
    d = tuple(max(0, v - 70) for v in c[:3]) + (255,)
    return sprite([
        ".oooooooo.",
        "occcccccco",
        "occwcwcwco",
        "occcccccco",
        ".oooooooo.",
        "..oco.....",
        "..oo......",
        "..........",
        "..........",
    ], {"o": d, "c": c, "w": VHI})


# ------------------------------------------------------------------ buttons

def card(icon):
    """An entry of the guild menu over 3x2 slots (54x36): a raised plate, its icon on top. The
    entry's name is the button's label, under the icon."""
    t = Image.new("RGBA", (54, 36), CLEAR)
    d = ImageDraw.Draw(t)
    d.rectangle([2, 1, 51, 34], fill=OUTLINE)
    d.rectangle([1, 2, 52, 33], fill=OUTLINE)
    d.rectangle([2, 2, 51, 33], fill=(214, 214, 214, 255))
    d.rectangle([2, 2, 50, 3], fill=VHI); d.rectangle([2, 2, 3, 32], fill=VHI)
    d.rectangle([3, 32, 51, 33], fill=(150, 150, 150, 255)); d.rectangle([50, 3, 51, 33], fill=(150, 150, 150, 255))
    t.alpha_composite(icon, (19, 5))
    return t


def button(kind):
    """A vanilla-style button over 3 slots (54x18). green: the main action; red: what cannot be
    undone; plain: a vanilla button (cancel); off: greyed, nothing to do. Its label is a title text."""
    fill, hi, sh = {
        "green": ((60, 133, 39, 255), (108, 192, 76, 255), (30, 82, 20, 255)),
        "red": ((160, 40, 36, 255), (214, 86, 74, 255), (96, 20, 18, 255)),
        "plain": ((128, 128, 128, 255), (176, 176, 176, 255), (84, 84, 84, 255)),
        "off": ((84, 84, 84, 255), (108, 108, 108, 255), (60, 60, 60, 255)),
    }[kind]
    t = Image.new("RGBA", (54, 18), CLEAR)
    d = ImageDraw.Draw(t)
    d.rectangle([0, 1, 53, 16], fill=OUTLINE)
    d.rectangle([1, 2, 52, 15], fill=fill)
    d.rectangle([1, 2, 52, 3], fill=hi); d.rectangle([1, 2, 1, 14], fill=hi)
    d.rectangle([1, 14, 52, 15], fill=sh); d.rectangle([52, 3, 52, 15], fill=sh)
    return t


def button_locked():
    t = button("off")
    t.alpha_composite(icon_lock(), (5, 5))
    return t


def tile(base, rim, rim_hi, rim_sh):
    t = Image.new("RGBA", (16, 16), CLEAR)
    bevel(t, 1, 1, 14, 14, base, rim_hi, rim_sh)
    ImageDraw.Draw(t).rectangle([0, 0, 15, 15], outline=rim)
    for c in ((0, 0), (15, 0), (0, 15), (15, 15)):
        t.putpixel(c, CLEAR)
    return t


def rank_tile(level):
    """A rank: gold with a crown for the highest, steel with two chevrons in between, wood with one for
    the lowest (that of new members)."""
    if level == "top":
        t = tile((150, 92, 200, 255), (70, 30, 110, 255), (200, 160, 236, 255), (110, 60, 160, 255))
        crown = sprite([
            "d...d...d.",
            "Gd.dGd.dG.",
            "GGdGGGdGG.",
            "GGGGGGGGG.",
            "GRGGBGGRG.",
            "ddddddddd.",
        ], {"d": GOLD_D, "G": GOLD, "R": (220, 50, 50, 255), "B": (60, 140, 240, 255)})
        t.alpha_composite(crown, (3, 5))
        return t
    if level == "middle":
        t = tile((176, 196, 214, 255), (70, 90, 120, 255), (226, 236, 246, 255), (130, 150, 176, 255))
        ink = (50, 70, 110, 255)
        n = 2
    else:
        t = tile((196, 160, 110, 255), (110, 70, 34, 255), (230, 206, 160, 255), (160, 122, 76, 255))
        ink = (100, 60, 26, 255)
        n = 1
    for k in range(n):
        yb = 11 - k * 4  # the chevron's lowest row (its two ends)
        for i in range(5):
            for dy in (0, 1):
                px(t, 3 + i, yb - i + dy, ink)
                px(t, 12 - i, yb - i + dy, ink)
    return t


def perm_on():  # granted: the green check
    t = tile((176, 200, 140, 255), (88, 112, 64, 255), (214, 230, 186, 255), (130, 156, 100, 255))
    for (x, y) in ((4, 8), (5, 9), (6, 10), (7, 9), (8, 8), (9, 7), (10, 6), (11, 5)):
        for dy in (0, 1):
            t.putpixel((x, y + dy), (40, 120, 50, 255))
    return t


def perm_off():  # not granted: a grey plate with an empty round
    t = tile((150, 150, 150, 255), (80, 80, 80, 255), (184, 184, 184, 255), (118, 118, 118, 255))
    d = ImageDraw.Draw(t)
    d.ellipse([4, 4, 11, 11], outline=(96, 96, 96, 255))
    return t


def arrow(left, enabled, w=18, h=36):
    t = Image.new("RGBA", (w, h), CLEAR)
    d = ImageDraw.Draw(t)
    cx, cy = w // 2, h // 2
    hw, hh = min(w, h * 2) // 2 - 3, h // 2 - 3
    if left:
        pts = [(cx + hw // 2, cy - hh), (cx - hw // 2 - 2, cy), (cx + hw // 2, cy + hh)]
    else:
        pts = [(cx - hw // 2, cy - hh), (cx + hw // 2 + 2, cy), (cx - hw // 2, cy + hh)]
    d.polygon(pts, fill=GOLD if enabled else (150, 150, 150, 255), outline=OUTLINE if enabled else (110, 110, 110, 255))
    return t


def shaded(m, light, mid, dark, under):
    """A pixel mask drawn like the vanilla icons: light top edges, dark bottom edges, a darker underside
    one pixel below, outlined in black."""
    n = len(m)
    t = Image.new("RGBA", (n, n), CLEAR)
    for y in range(n):
        for x in range(n):
            if m[y][x]:
                top = y == 0 or not m[y - 1][x]
                bottom = y == n - 1 or not m[y + 1][x]
                t.putpixel((x, y), light if top else dark if bottom else mid)
    solid = [row[:] for row in m]
    for y in range(n - 1, 0, -1):
        for x in range(n):
            if m[y - 1][x] and not m[y][x]:
                t.putpixel((x, y), under)
                solid[y][x] = True
    for y in range(n):
        for x in range(n):
            if not solid[y][x] and any(0 <= x + dx < n and 0 <= y + dy < n and solid[y + dy][x + dx]
                                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                t.putpixel((x, y), OUTLINE)
    return t


def close():
    """Close: a thick red cross, shaded and outlined like the back arrow."""
    n = 16
    m = [[2 <= x <= 13 and 2 <= y <= 12 and (abs(x - y) <= 1 or abs(x + y - 14) <= 1)
          for x in range(n)] for y in range(n)]
    light, mid, dark = (255, 130, 110, 255), (220, 60, 50, 255), (160, 36, 30, 255)
    t = shaded(m, light, mid, dark, (110, 24, 20, 255))
    for y in range(n):
        for x in range(n):
            if m[y][x]:
                # each bar lit along its upper side, dark along its lower side, one shade per diagonal
                sides = [v for v, on in ((x - y, abs(x - y) <= 1), (14 - x - y, abs(x + y - 14) <= 1)) if on]
                v = 0 if len(sides) > 1 else sides[0]
                t.putpixel((x, y), light if v == 1 else dark if v == -1 else mid)
    return t


def box(w, icon):
    """A vanilla text box (w x 16) with its icon on the left; the value is the element's label."""
    t = Image.new("RGBA", (w, 16), CLEAR)
    rect(t, 0, 0, w - 1, 15, (160, 160, 160, 255))
    rect(t, 1, 1, w - 2, 14, OUTLINE)
    t.alpha_composite(icon, (4, 4))
    return t


def back():
    """Back to the menu before (one slot): a thick gold arrow, its head pointing left at the top, the
    stroke turning back round the right and ending along the bottom, 4 pixels thick all along."""
    n = 16
    m = [[False] * n for _ in range(n)]
    cx, cy = 10.0, 9.0
    for y in range(n):
        for x in range(n):
            r = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if x + 0.5 >= cx and 1.0 <= r < 5.1:
                m[y][x] = True  # the turn, on the right
    for x in range(5, 10):
        for y in range(4, 8):
            m[y][x] = True  # the shaft, along the top
    for x in range(7, 10):
        for y in range(10, 14):
            m[y][x] = True  # the tail, along the bottom
    for x in range(1, 6):
        for y in range(5 - (x - 1), 6 + (x - 1) + 1):
            m[y][x] = True  # the head, pointing left
    return shaded(m, GOLD_H, GOLD, GOLD_D, (150, 96, 20, 255))


def half_card(icon, x=2):
    """Half an entry (54x18, one row of 3 slots): the plate of a card, its icon on the left, its name the
    button's label."""
    t = Image.new("RGBA", (54, 18), CLEAR)
    d = ImageDraw.Draw(t)
    d.rectangle([1, 0, 52, 17], fill=OUTLINE)
    d.rectangle([0, 1, 53, 16], fill=OUTLINE)
    d.rectangle([1, 1, 52, 16], fill=(214, 214, 214, 255))
    d.rectangle([1, 1, 51, 1], fill=VHI); d.rectangle([1, 1, 1, 15], fill=VHI)
    d.rectangle([2, 16, 52, 16], fill=(150, 150, 150, 255)); d.rectangle([52, 2, 52, 16], fill=(150, 150, 150, 255))
    t.alpha_composite(icon, (x, (18 - icon.height) // 2))
    return t


def chat_card(mode):
    """The chat mode, half an entry: a bubble in the mode's colour (white, green, blue); the mode is the label."""
    color = {"public": (236, 236, 236, 255), "guild": (90, 210, 80, 255), "ally": (90, 200, 230, 255)}[mode]
    return half_card(icon_bubble(color), 4)


def alliance_tile(state):
    """An allied guild (green, its shield), an offer to answer (gold, a letter), an offer sent (grey, an hourglass)."""
    if state == "ally":
        t = tile((176, 200, 140, 255), (88, 112, 64, 255), (214, 230, 186, 255), (130, 156, 100, 255))
        mark = sprite([
            "oooooooo",
            "oGGGGGGo",
            "oWWWWWWo",
            "oGGGGGGo",
            "oGGGGGGo",
            ".oGGGGo.",
            "..oGGo..",
            "...oo...",
        ], {"o": (40, 70, 30, 255), "G": (70, 150, 60, 255), "W": (236, 236, 236, 255)})
    elif state == "offer":
        t = tile((255, 222, 110, 255), GOLD_D, GOLD_H, (214, 150, 40, 255))
        mark = sprite([
            "oooooooo",
            "oWoWWoWo",
            "oWWooWWo",
            "oWWWWWWo",
            "oWWWWWWo",
            "oooooooo",
            "........",
            "........",
        ], {"o": (120, 80, 20, 255), "W": (255, 250, 236, 255)})
    else:
        t = tile((150, 150, 150, 255), (80, 80, 80, 255), (184, 184, 184, 255), (118, 118, 118, 255))
        mark = sprite([
            "oooooo..",
            ".oSSo...",
            "..oo....",
            "..oo....",
            ".oSSo...",
            "oSSSSo..",
            "oooooo..",
            "........",
        ], {"o": (70, 70, 70, 255), "S": (226, 196, 120, 255)})
    t.alpha_composite(mark, (4, 4))
    return t


def card_badge(base):
    """A card with a gold "!" badge in its top right corner: something waits there."""
    t = base.copy()
    d = ImageDraw.Draw(t)
    d.rectangle([42, 4, 48, 14], fill=OUTLINE)
    d.rectangle([43, 5, 47, 13], fill=(220, 50, 44, 255))
    d.rectangle([45, 6, 45, 10], fill=VHI)
    d.rectangle([45, 12, 45, 12], fill=VHI)
    return t




# ------------------------------------------------------------------ the lands' own icons (16x16)

GRASS = (96, 160, 56, 255)
GRASS_H = (130, 196, 80, 255)
GRASS_D = (66, 120, 40, 255)
DIRT = (134, 96, 67, 255)
DIRT_D = (98, 68, 46, 255)
DIRT_H = (160, 120, 86, 255)


def icon_chunk():  # a block of grass seen from the front: the land's chunks
    return sprite([
        "................",
        ".oooooooooooooo.",
        ".oHGGHGGGHGGHGo.",
        ".oGGgGGHGGGgGGo.",
        ".oGgGGgGGgGGgGo.",
        ".odgddGdgdGddgo.",
        ".oddDddgddDdddo.",
        ".odhdddDdddhddo.",
        ".oddddhdddDdddo.",
        ".oDdddddhdddddo.",
        ".odddDdddddhddo.",
        ".odhddddDddddDo.",
        ".odddhdddddhddo.",
        ".oddDddddDddddo.",
        ".oooooooooooooo.",
        "................",
    ], {"o": OUTLINE, "G": GRASS, "H": GRASS_H, "g": GRASS_D, "d": DIRT, "D": DIRT_D, "h": DIRT_H})


def icon_key():  # permissions: a gold key
    return sprite([
        "................",
        "....dddd........",
        "...dGHHGd.......",
        "..dGHddGGd......",
        "..dHd..dGd......",
        "..dGd..dGd......",
        "..dGGddGGd......",
        "...dGGGGd.......",
        "....ddGGdddddd..",
        "......dGGGGGGGd.",
        "......dGddGdGGd.",
        ".......d..dd.dd.",
        "................",
        "................",
        "................",
        "................",
    ], {"d": GOLD_D, "G": GOLD, "H": GOLD_H})


def icon_gear():  # settings: a steel gear
    return sprite([
        "................",
        "......oooo......",
        "...oo.oSSo.oo...",
        "..oSSooSSooSSo..",
        "..oSHSSSSSSSSo..",
        "...oSHSooSSSo...",
        ".oooSSo..oSSooo.",
        ".oSSSo....oSSSo.",
        ".oSSSo....oSDSo.",
        ".oooSSo..oSDooo.",
        "...oSSSooSDDo...",
        "..oSSSSSSSDDDo..",
        "..oSSooDSooDDo..",
        "...oo.oDDo.oo...",
        "......oooo......",
        "................",
    ], {"o": (50, 50, 56, 255), "S": (170, 174, 182, 255), "H": (224, 228, 236, 255), "D": (110, 114, 124, 255)})


def icon_ban():  # bans: a red no-entry circle
    return sprite([
        "................",
        ".....oooooo.....",
        "...ooRRRRRRoo...",
        "..oRRrrrrrrRRo..",
        "..oRrrrrrrrrRo..",
        ".oRrrrrrrrrrrRo.",
        ".oRrWWWWWWWWrRo.",
        ".oRrWWWWWWWWrRo.",
        ".oRrrrrrrrrrrRo.",
        ".oRrrrrrrrrrrRo.",
        "..oRrrrrrrrrRo..",
        "..oRRrrrrrrRRo..",
        "...ooRRRRRRoo...",
        ".....oooooo.....",
        "................",
        "................",
    ], {"o": (70, 14, 12, 255), "R": (150, 30, 26, 255), "r": (214, 52, 44, 255), "W": (250, 240, 236, 255)})


def icon_sublands():  # sub-lands: a plot and, inside it, a smaller one outlined in gold
    return sprite([
        "................",
        ".oooooooooooooo.",
        ".oGGGHGGGGHGGGo.",
        ".oGHGGGGGGGGHGo.",
        ".oGGyyyyyyyyGGo.",
        ".oGGyGGGHGGyGGo.",
        ".oGHyGHGGGGyGGo.",
        ".oGGyGGGGHGyGHo.",
        ".oGGyGGHGGGyGGo.",
        ".oHGyGGGGGGyGGo.",
        ".oGGyyyyyyyyGGo.",
        ".oGGGGHGGGGGHGo.",
        ".oGHGGGGGHGGGGo.",
        ".oggggggggggggo.",
        ".oooooooooooooo.",
        "................",
    ], {"o": OUTLINE, "G": GRASS, "H": GRASS_H, "g": GRASS_D, "y": GOLD})


def icon_link():  # links: two chain links, one through the other
    n = 16

    def ring(x0, y0, x1, y1):
        def inside(x, y):
            outer = x0 <= x <= x1 and y0 <= y <= y1 and (x, y) not in {(x0, y0), (x1, y0), (x0, y1), (x1, y1)}
            inner = x0 + 2 <= x <= x1 - 2 and y0 + 2 <= y <= y1 - 2
            return outer and not inner
        return inside

    a, b = ring(1, 3, 9, 9), ring(6, 6, 14, 12)
    m = [[a(x, y) or b(x, y) for x in range(n)] for y in range(n)]
    return shaded(m, (226, 230, 238, 255), (176, 180, 190, 255), (120, 124, 134, 255), (80, 84, 92, 255))


def icon_flag():  # the chunk you stand in: a red flag on its pole
    return sprite([
        "................",
        "...oo...........",
        "...oWoooooooo...",
        "...oWRRRRRRRRo..",
        "...oWRrRRRRRRo..",
        "...oWRRRRRRRo...",
        "...oWRRRRRRRRo..",
        "...oWoooooooo...",
        "...oWo..........",
        "...oWo..........",
        "...oWo..........",
        "...oWo..........",
        ".ooGGGGooo......",
        ".oGGHGGGGo......",
        "..oooooooo......",
        "................",
    ], {"o": OUTLINE, "W": (150, 104, 60, 255), "R": (210, 50, 44, 255), "r": (250, 120, 100, 255),
        "G": GRASS, "H": GRASS_H})


def icon_selection():  # an area selected corner to corner: a dashed square, gold corners
    return sprite([
        "................",
        ".YYYY.oo.oo.YYYY",
        ".Y..............",
        ".Y............Y.",
        ".Y............Y.",
        "..............Y.",
        ".o............o.",
        ".o..............",
        "..............o.",
        ".o............o.",
        ".o..............",
        ".Y............Y.",
        ".Y............Y.",
        "..............Y.",
        ".YYYY.oo.oo.YYYY",
        "................",
    ], {"o": (40, 40, 40, 255), "Y": GOLD})


def icon_map():  # the chunk map: a vanilla-looking map
    return sprite([
        "................",
        ".oooooooooooooo.",
        ".oPPPPPPPPPPPPo.",
        ".oPBBBBGGGGGGPo.",
        ".oPBBBGGGgGGGPo.",
        ".oPBBGGGGGGgGPo.",
        ".oPBGGgGGGGGGPo.",
        ".oPGGGGGrrGGGPo.",
        ".oPGGgGGrrGGGPo.",
        ".oPGGGGGGGGBBPo.",
        ".oPGGGGGgGBBBPo.",
        ".oPGgGGGGBBBBPo.",
        ".oPPPPPPPPPPPPo.",
        ".oooooooooooooo.",
        "................",
        "................",
    ], {"o": (70, 50, 30, 255), "P": (226, 210, 170, 255), "B": (90, 130, 210, 255), "G": (120, 170, 80, 255),
        "g": (90, 140, 60, 255), "r": (210, 50, 44, 255)})


def icon_list():  # the list of chunks: a sheet of paper, its lines
    return sprite([
        "................",
        "...oooooooooo...",
        "...oPPPPPPPPo...",
        "...oPddPllllo...",
        "...oPddPPPPPo...",
        "...oPPPPPPPPo...",
        "...oPddPllllo...",
        "...oPddPPPPPo...",
        "...oPPPPPPPPo...",
        "...oPddPllllo...",
        "...oPddPPPPPo...",
        "...oPPPPPPPPo...",
        "...oPPPPPPPPo...",
        "...oooooooooo...",
        "................",
        "................",
    ], {"o": (60, 50, 40, 255), "P": (240, 236, 222, 255), "l": (150, 140, 120, 255), "d": DIRT})


def icon_globe():  # the global permissions: everyone
    return sprite([
        "................",
        ".....oooooo.....",
        "...ooBBGGBBoo...",
        "..oBBGGGGBBBBo..",
        "..oBGGGGGBBBBo..",
        ".oBBGGGGBBBGGBo.",
        ".oBBBGGBBBGGGGo.",
        ".oBBBBGBBBGGGBo.",
        ".oBBBBBBBBBGBBo.",
        ".oBBGGBBBBBBBBo.",
        "..oBGGGGBBBBBo..",
        "..oBBGGGBBBBBo..",
        "...ooBGGBBBoo...",
        ".....oooooo.....",
        "................",
        "................",
    ], {"o": (20, 40, 80, 255), "B": (70, 120, 210, 255), "G": (90, 170, 70, 255)})


def icon_chunk_small():  # 8x8, for the boxes
    return sprite([
        "oooooooo",
        "oGHGGHGo",
        "ogGgGgGo",
        "oddDddho",
        "odhdddDo",
        "oDddhddo",
        "odddDddo",
        "oooooooo",
    ], {"o": OUTLINE, "G": GRASS, "H": GRASS_H, "g": GRASS_D, "d": DIRT, "D": DIRT_D, "h": DIRT_H})


def add():
    """Add (one slot): a thick green plus, shaded and outlined like close and back."""
    n = 16
    m = [[(6 <= x <= 9 and 2 <= y <= 13) or (2 <= x <= 13 and 6 <= y <= 9) for x in range(n)] for y in range(n)]
    return shaded(m, (150, 230, 110, 255), (70, 170, 50, 255), (40, 110, 30, 255), (24, 80, 20, 255))


# Title glyphs (assets/glyphs): the windows, drawn by a `background` title layer from the window's top
# left (ascent 13 puts the top row at chest-y 0). Codepoints are only hints: the pack moves a glyph
# whose codepoint another project already uses.
GLYPHS = {
    # guilds_main_background one row shorter, without the groove under the title
    "lands_hub_background": (main_background(), 0xE7D0),
    # guilds_list_background (= daily_background) one row shorter: the 28 slots of a list
    "lands_list_background": (list_background(), 0xE7D1),
    # the links' three cards and the bottom row, 3 rows
    "lands_links_background": (links_background(), 0xE7D3),
    # = guilds_confirm_background
    "lands_confirm_background": (confirm_background(), 0xE7D2),
}

# Image buttons (assets/textures). "=" marks a copy of another module's image, to the pixel.
IMAGES = {
    # The cards (3x2 slots) of the land menu, of its chunks menu and of its links.
    "lands_card_chunks": card(icon_chunk()),
    "lands_card_permissions": card(icon_key()),
    "lands_card_settings": card(icon_gear()),
    "lands_card_bans": card(icon_ban()),
    "lands_card_sublands": card(icon_sublands()),
    "lands_card_links": card(icon_link()),
    "lands_card_here": card(icon_flag()),
    "lands_card_selection": card(icon_selection()),
    "lands_card_map": card(icon_map()),
    "lands_card_list": card(icon_list()),
    "lands_card_global": card(icon_globe()),
    "lands_card_players": card(icon_members()),  # = guilds_card_members
    "lands_card_linked_global": card_badge(card(icon_globe())),
    "lands_card_linked_players": card_badge(card(icon_members())),
    "lands_card_linked_bans": card_badge(card(icon_ban())),
    # Boxes (3x1) whose value is the label.
    "lands_chunks_count": box(54, icon_chunk_small()),
    "lands_trusts_count": box(54, icon_heads()),  # = guilds_members_count
    # Buttons (3x1).
    "lands_button": button("green"),  # = guilds_button
    "lands_button_danger": button("red"),  # = guilds_button_danger
    "lands_button_plain": button("plain"),  # = guilds_button_plain
    # A flag or a permission: granted or not.
    "lands_toggle_on": perm_on(),  # = guilds_permission_on = daily_day_claimed
    "lands_toggle_off": perm_off(),  # = guilds_permission_off
    # The arrows on both sides of a list, the slots of the bottom row.
    "lands_prev": arrow(True, True),  # = guilds_prev = daily_prev
    "lands_prev_off": arrow(True, False),  # = guilds_prev_off = daily_prev_off
    "lands_next": arrow(False, True),  # = guilds_next = daily_next
    "lands_next_off": arrow(False, False),  # = guilds_next_off = daily_next_off
    "lands_back": back(),  # = guilds_back = daily_back
    "lands_close": close(),  # = guilds_close = daily_close
    "lands_add": add(),
}


def main():
    textures, glyphs = OUT / "textures", OUT / "glyphs"
    textures.mkdir(parents=True, exist_ok=True)
    glyphs.mkdir(parents=True, exist_ok=True)
    for name, img in IMAGES.items():
        img.save(textures / f"{name}.png", optimize=True)
        (textures / f"{name}.yml").write_text(f"width: {img.width}\nheight: {img.height}\nusages: [button]\n")
    for name, (img, codepoint) in GLYPHS.items():
        img.save(glyphs / f"{name}.png", optimize=True)
        (glyphs / f"{name}.yml").write_text(
            f"codepoint: {codepoint}\nascent: 13\nwidth: {img.width}\nheight: {img.height}\nusages: [background]\n")
    print(len(IMAGES) + len(GLYPHS), "images in", OUT)


if __name__ == "__main__":
    main()
