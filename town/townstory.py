#!/usr/bin/env python3
"""IN THIS TOWN story renderer (@sciencebutwhatif).
Usage: python3 townstory.py story.json out.mp4 [--still t1 t2 ...] [--sheet sheet.jpg]
story.json: {"beats":[{"scene":"lake","who":["me","ivy"],"prop":"cat","text":"In this town, ..."}...]}
Caption markup inside text: *yellow*  ~cyan~  !red!  ^pink^  (wrap single words or short phrases).
A beat whose text is "#MONDAY" (any word after #) shows a big day card ("ON" + word).
Scenes: lake, square, street, classroom, hallway, bedroom, jetty, kitchen.
Characters (who): me, ivy, mum, dad, grandad, nan, mayor, teacher, kid, girl, man, woman.
Props: cat, cake, letter, fish, clock, key, book, phone, ring, bike, moon, umbrella, glow, door.
Writes video (no audio) and timing json (<out>.json: chunk start times, used for music hits).
"""
import sys, json, math, re, subprocess, random
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

W, H, FPS = 1080, 1920, 30
FD = '/usr/share/fonts/opentype/inter/'
def F(n, s): return ImageFont.truetype(FD + n, s)
CAP = F('Inter-Black.otf', 92); WM = F('Inter-Bold.otf', 36)
DAY_ON = F('Inter-Black.otf', 70); DAY = F('Inter-Black.otf', 210)
COL = {'*': (255, 214, 64), '~': (90, 220, 235), '!': (255, 70, 70), '^': (255, 120, 190), '': (255, 255, 255)}
SW, SH = int(W * 1.12), int(H * 1.12)   # scene canvas (camera crops from it)

# ---------------------------------------------------------------- captions
def tokenize(text):
    """-> list of (word, colorkey)."""
    out = []
    for m in re.finditer(r'([*~!^])(.+?)\1|(\S+)', text):
        if m.group(3):
            out.append((m.group(3), ''))
        else:
            for w in m.group(2).split(): out.append((w, m.group(1)))
    # attach punctuation following markup e.g. *word*,
    fixed = []
    for w, c in out:
        if fixed and re.fullmatch(r'[.,!?"\'):;]+', w): fixed[-1] = (fixed[-1][0] + w, fixed[-1][1])
        else: fixed.append((w, c))
    return fixed

def chunks(words):
    cs, cur = [], []
    for w, c in words:
        cur.append((w, c))
        n = sum(len(x[0]) for x in cur)
        if re.search(r'[.,?!:;"]$', w) or len(cur) >= 3 or n >= 14:
            cs.append(cur); cur = []
    if cur: cs.append(cur)
    out = []
    for c in cs:  # merge a lonely word into the previous unfinished chunk
        if out and len(c) == 1 and not re.search(r'[.,?!:;"]$', out[-1][-1][0]) and len(out[-1]) <= 3 and sum(len(w) for w, _ in out[-1] + c) <= 22:
            out[-1] = out[-1] + c
        else: out.append(c)
    return out

def chunk_dur(ch):
    d = 0.15 + 0.24 * len(ch) + 0.03 * sum(len(w) for w, _ in ch)
    last = ch[-1][0]
    if last.endswith(('.', '?', '!', '."', '?"', '!"')): d += 0.35
    elif last.endswith((',', ',"', ':')): d += 0.15
    return d

def draw_caption(img, ch, age, y=1290):
    d = ImageDraw.Draw(img)
    words = [(w.upper(), COL[c]) for w, c in ch]
    sp = d.textlength(' ', font=CAP)
    widths = [d.textlength(w, font=CAP) for w, _ in words]
    total = sum(widths) + sp * (len(words) - 1)
    f = CAP
    if total > 880:
        f = F('Inter-Black.otf', int(92 * 880 / total)); sp = d.textlength(' ', font=f)
        widths = [d.textlength(w, font=f) for w, _ in words]; total = sum(widths) + sp * (len(words) - 1)
    pop = 1.0 if age > 0.08 else 0.9 + age / 0.08 * 0.1
    x = W / 2 - 40 - total / 2
    yy = y + (1 - pop) * 30
    for (w, c), wd in zip(words, widths):
        d.text((x + 4, yy + 6), w, font=f, fill=(0, 0, 0), anchor='lm', stroke_width=9, stroke_fill=(0, 0, 0))
        d.text((x, yy), w, font=f, fill=c, anchor='lm', stroke_width=7, stroke_fill=(10, 10, 14))
        x += wd + sp

def draw_daycard(img, word, age):
    d = ImageDraw.Draw(img)
    s = min(1.0, 0.75 + age / 0.12 * 0.25)
    f = F('Inter-Black.otf', int(210 * s))
    tw = d.textlength(word, font=f)
    if tw > 900: f = F('Inter-Black.otf', int(210 * s * 900 / tw))
    d.text((W / 2 - 40, 1060), 'ON', font=DAY_ON, fill=(255, 255, 255), anchor='mm', stroke_width=6, stroke_fill=(10, 10, 14))
    d.text((W / 2 - 40 + 6, 1240 + 8), word, font=f, fill=(0, 0, 0), anchor='mm', stroke_width=12, stroke_fill=(0, 0, 0))
    d.text((W / 2 - 40, 1240), word, font=f, fill=(255, 245, 230), anchor='mm', stroke_width=10, stroke_fill=(20, 20, 40))

def watermark(img):
    o = Image.new('RGBA', img.size, (0, 0, 0, 0)); d = ImageDraw.Draw(o)
    t = '@sciencebutwhatif'; w = d.textlength(t, font=WM)
    d.rounded_rectangle((W / 2 - w / 2 - 26, 164, W / 2 + w / 2 + 26, 228), 32, fill=(0, 0, 0, 140))
    d.text((W / 2, 196), t, font=WM, fill=(255, 255, 255, 166), anchor='mm')
    img.alpha_composite(o)

# ---------------------------------------------------------------- scenes
def grad(top, bot, w=SW, h=SH):
    y = np.linspace(0, 1, h)[:, None, None]
    a = np.array(top) * (1 - y) + np.array(bot) * y
    return Image.fromarray((a * np.ones((1, w, 1))).astype(np.uint8)).convert('RGBA')

def houses(d, y, rnd, scale=1.0, n=9):
    cols = [(120, 170, 190), (200, 140, 110), (150, 190, 150), (230, 210, 160), (170, 150, 200), (220, 170, 170), (140, 160, 200)]
    x = -40
    while x < SW:
        w = int(rnd.randint(110, 170) * scale); h = int(rnd.randint(120, 180) * scale)
        c = rnd.choice(cols)
        d.rectangle((x, y - h, x + w, y), fill=c)
        d.polygon([(x - 6, y - h), (x + w + 6, y - h), (x + w // 2, y - h - int(60 * scale))], fill=(110, 70, 60))
        d.rectangle((x + w * 0.65, y - h - 70 * scale, x + w * 0.75, y - h - 25 * scale), fill=(150, 80, 60))
        for wx in (0.18, 0.58):
            d.rectangle((x + w * wx, y - h * 0.75, x + w * wx + 26 * scale, y - h * 0.75 + 32 * scale), fill=(250, 240, 200))
        x += w + 4

def trees(d, y, rnd, n=8, scale=1.0):
    for i in range(n):
        x = rnd.randint(0, SW); s = rnd.uniform(0.7, 1.2) * scale
        d.rectangle((x - 8 * s, y - 60 * s, x + 8 * s, y), fill=(100, 70, 50))
        d.ellipse((x - 55 * s, y - 160 * s, x + 55 * s, y - 50 * s), fill=(60, 140, 70))

def scene_bg(name, seed=1):
    rnd = random.Random(seed)
    if name in ('lake', 'jetty'):
        im = grad((110, 180, 240), (200, 230, 250)); d = ImageDraw.Draw(im)
        for i in range(4):
            cx, cy = rnd.randint(0, SW), rnd.randint(100, 500)
            for k in range(4): d.ellipse((cx + k * 50 - 60, cy - 30 - (k % 2) * 20, cx + k * 50 + 60, cy + 30), fill=(255, 255, 255))
        houses(d, 880, rnd, 0.8); trees(d, 900, rnd, 10, 0.8)
        d.rectangle((0, 880, SW, 1500), fill=(50, 130, 200))
        for i in range(60):
            x, y = rnd.randint(0, SW), rnd.randint(900, 1480)
            d.line((x, y, x + rnd.randint(30, 90), y), fill=(120, 190, 240), width=4)
        if name == 'lake':
            d.rectangle((0, 1500, SW, SH), fill=(150, 150, 155))
            d.rectangle((0, 1740, SW, SH), fill=(90, 170, 80))
            for x in range(0, SW, 120):
                d.rectangle((x, 1380, x + 14, 1560), fill=(25, 25, 30))
            d.rectangle((0, 1390, SW, 1404), fill=(25, 25, 30)); d.rectangle((0, 1450, SW, 1460), fill=(25, 25, 30))
        else:
            d.polygon([(250, SH), (830, SH), (700, 1250), (380, 1250)], fill=(170, 120, 80))
            for y in range(1270, SH, 60): d.line((200, y, 880, y), fill=(130, 90, 60), width=5)
            d.polygon([(860, 1200), (1080, 1180), (1050, 1260), (880, 1270)], fill=(210, 50, 50))
        return im
    if name == 'square':
        im = grad((120, 180, 235), (210, 230, 250)); d = ImageDraw.Draw(im)
        houses(d, 1250, rnd, 1.3)
        cx = SW // 2
        d.rectangle((cx - 120, 300, cx + 120, 1250), fill=(190, 170, 140))
        d.polygon([(cx - 150, 300), (cx + 150, 300), (cx, 120)], fill=(110, 70, 60))
        d.ellipse((cx - 95, 380, cx + 95, 570), fill=(250, 248, 235), outline=(60, 50, 40), width=10)
        for k in range(12):
            a = k / 12 * 2 * math.pi
            d.line((cx + 75 * math.sin(a), 475 - 75 * math.cos(a), cx + 85 * math.sin(a), 475 - 85 * math.cos(a)), fill=(60, 50, 40), width=6)
        d.rectangle((cx - 40, 1100, cx + 40, 1250), fill=(90, 60, 40))
        d.rectangle((0, 1250, SW, SH), fill=(175, 170, 165))
        for y in range(1280, SH, 70): d.line((0, y, SW, y), fill=(150, 145, 140), width=3)
        return im
    if name == 'street':
        im = grad((130, 190, 240), (220, 235, 250)); d = ImageDraw.Draw(im)
        houses(d, 1150, rnd, 1.8)
        d.rectangle((0, 1150, SW, 1300), fill=(170, 170, 170))
        d.rectangle((0, 1300, SW, SH), fill=(70, 72, 78))
        for x in range(40, SW, 260): d.rectangle((x, 1600, x + 140, 1620), fill=(240, 240, 240))
        return im
    if name in ('hallway', 'classroom'):
        im = grad((215, 215, 220), (190, 190, 195)); d = ImageDraw.Draw(im)
        if name == 'hallway':
            vx, vy = SW // 2, 900
            d.polygon([(0, SH), (SW, SH), (vx + 150, vy + 120), (vx - 150, vy + 120)], fill=(150, 150, 150))
            for i in range(300):
                x, y = rnd.randint(0, SW), rnd.randint(1100, SH)
                d.ellipse((x, y, x + 10, y + 8), fill=rnd.choice([(40, 40, 40), (230, 230, 230), (100, 100, 100)]))
            for side in (-1, 1):
                for k in range(7):
                    x0 = vx + side * (170 + k * 70); x1 = vx + side * (170 + (k + 1) * 70)
                    t0 = 300 + k * 60
                    d.polygon([(x0, vy - t0 * 0.9), (x1, vy - (t0 + 60) * 0.9), (x1, vy + 100 + k * 120), (x0, vy + 100 + (k - 1) * 120)], fill=(40, 80, 100), outline=(20, 40, 50))
            for k in range(3): d.rectangle((vx - 120 + k * 10, 320 + k * 160, vx + 120 - k * 10, 345 + k * 160), fill=(255, 255, 250))
        else:
            d.rectangle((90, 380, SW - 90, 900), fill=(40, 70, 55), outline=(120, 90, 60), width=18)
            d.text((180, 480), '2 + 2 = 4', font=F('Inter-Bold.otf', 70), fill=(230, 230, 220))
            d.rectangle((0, 1150, SW, SH), fill=(140, 110, 85))
            for r in range(2):
                for c in range(3):
                    x = 60 + c * 360; y = 1250 + r * 330
                    d.rectangle((x, y, x + 280, y + 60), fill=(190, 150, 100)); d.rectangle((x + 20, y + 60, x + 40, y + 200), fill=(90, 70, 50)); d.rectangle((x + 240, y + 60, x + 260, y + 200), fill=(90, 70, 50))
        return im
    if name == 'bedroom':
        im = grad((25, 25, 50), (10, 10, 25)); d = ImageDraw.Draw(im)
        d.rectangle((560, 380, 960, 820), fill=(30, 40, 80), outline=(90, 80, 70), width=16)
        d.ellipse((780, 450, 880, 550), fill=(240, 240, 210))
        d.line((760, 380, 760, 820), fill=(90, 80, 70), width=10); d.line((560, 600, 960, 600), fill=(90, 80, 70), width=10)
        d.rectangle((0, 1300, SW, SH), fill=(40, 30, 30))
        d.rectangle((60, 1150, 620, 1450), fill=(70, 70, 110)); d.rectangle((60, 1100, 220, 1180), fill=(220, 220, 230))
        return im
    if name == 'kitchen':
        im = grad((240, 225, 200), (225, 205, 175)); d = ImageDraw.Draw(im)
        for x in range(0, SW, 90):
            for y in range(380, 1000, 90): d.rectangle((x + 4, y + 4, x + 86, y + 86), outline=(210, 190, 160), width=3)
        d.rectangle((0, 1150, SW, SH), fill=(160, 120, 90)); d.rectangle((0, 1100, SW, 1160), fill=(230, 230, 230))
        return im
    return grad((60, 60, 90), (20, 20, 40))

CHARS = {
    'me': ((60, 110, 200), (40, 30, 30), 'short'), 'ivy': ((40, 90, 70), (40, 110, 120), 'long'),
    'mum': ((200, 80, 110), (110, 60, 40), 'long'), 'dad': ((90, 120, 90), (60, 40, 30), 'short'),
    'grandad': ((150, 120, 90), (235, 235, 235), 'short'), 'nan': ((170, 110, 170), (225, 225, 230), 'bun'),
    'mayor': ((40, 40, 60), (80, 60, 50), 'short'), 'teacher': ((120, 80, 60), (30, 30, 30), 'bun'),
    'kid': ((230, 160, 40), (90, 60, 40), 'short'), 'girl': ((220, 90, 90), (200, 150, 60), 'long'),
    'man': ((80, 80, 140), (30, 30, 30), 'short'), 'woman': ((60, 150, 160), (140, 50, 40), 'long'),
}
def draw_char(d, x, y, s, key, t, phase=0.0, face=1):
    shirt, hair, style = CHARS.get(key, CHARS['man'])
    skin = (250, 210, 170)
    bob = math.sin(t * 2.2 + phase) * 4 * s
    sw = math.sin(t * 4 + phase) * 10 * s
    # legs
    d.rectangle((x - 34 * s, y - 120 * s, x - 4 * s, y), fill=(40, 45, 70))
    d.rectangle((x + 4 * s, y - 120 * s, x + 34 * s, y), fill=(40, 45, 70))
    ty = y - 120 * s + bob
    d.rectangle((x - 40 * s, ty - 130 * s, x + 40 * s, ty), fill=shirt)
    d.rectangle((x - 66 * s, ty - 126 * s + sw * 0.2, x - 42 * s, ty - 20 * s + sw * 0.2), fill=shirt)
    d.rectangle((x + 42 * s, ty - 126 * s - sw * 0.2, x + 66 * s, ty - 20 * s - sw * 0.2), fill=shirt)
    hy = ty - 130 * s
    if style == 'long': d.rectangle((x - 42 * s, hy - 80 * s, x + 42 * s, hy + 40 * s), fill=hair)
    d.rounded_rectangle((x - 36 * s, hy - 76 * s, x + 36 * s, hy), int(10 * s), fill=skin)
    d.rectangle((x - 40 * s, hy - 90 * s, x + 40 * s, hy - 58 * s), fill=hair)
    if style == 'bun': d.ellipse((x - 20 * s, hy - 120 * s, x + 20 * s, hy - 82 * s), fill=hair)
    ex = 8 * s * face
    blink = (t * 0.7 + phase) % 3 < 0.08
    for e in (-14, 14):
        if blink: d.line((x + ex + e * s - 5 * s, hy - 40 * s, x + ex + e * s + 5 * s, hy - 40 * s), fill=(20, 20, 20), width=max(2, int(3 * s)))
        else: d.ellipse((x + ex + e * s - 5 * s, hy - 46 * s, x + ex + e * s + 5 * s, hy - 34 * s), fill=(20, 20, 20))
    d.arc((x + ex - 12 * s, hy - 30 * s, x + ex + 12 * s, hy - 14 * s), 20, 160, fill=(80, 40, 40), width=max(2, int(3 * s)))

def draw_prop(d, kind, cx, cy, t, s=1.0):
    b = math.sin(t * 3) * 10
    cy = cy + b
    if kind == 'cat':
        d.ellipse((cx - 70 * s, cy - 40 * s, cx + 70 * s, cy + 50 * s), fill=(240, 150, 60))
        d.ellipse((cx + 30 * s, cy - 100 * s, cx + 110 * s, cy - 20 * s), fill=(240, 150, 60))
        d.polygon([(cx + 35 * s, cy - 80 * s), (cx + 50 * s, cy - 125 * s), (cx + 65 * s, cy - 90 * s)], fill=(240, 150, 60))
        d.polygon([(cx + 80 * s, cy - 90 * s), (cx + 95 * s, cy - 125 * s), (cx + 108 * s, cy - 80 * s)], fill=(240, 150, 60))
        d.ellipse((cx + 55 * s, cy - 70 * s, cx + 65 * s, cy - 60 * s), fill=(20, 20, 20)); d.ellipse((cx + 82 * s, cy - 70 * s, cx + 92 * s, cy - 60 * s), fill=(20, 20, 20))
        d.line((cx - 70 * s, cy, cx - 120 * s, cy - 60 * s), fill=(240, 150, 60), width=int(14 * s))
    elif kind == 'cake':
        d.rectangle((cx - 90 * s, cy - 40 * s, cx + 90 * s, cy + 60 * s), fill=(240, 200, 220)); d.rectangle((cx - 90 * s, cy - 50 * s, cx + 90 * s, cy - 25 * s), fill=(255, 250, 250))
        d.rectangle((cx - 6 * s, cy - 100 * s, cx + 6 * s, cy - 50 * s), fill=(120, 170, 255)); d.ellipse((cx - 10 * s, cy - 125 * s, cx + 10 * s, cy - 98 * s), fill=(255, 200, 60))
    elif kind == 'letter':
        d.rectangle((cx - 100 * s, cy - 65 * s, cx + 100 * s, cy + 65 * s), fill=(250, 245, 230), outline=(150, 130, 100), width=4)
        d.line((cx - 100 * s, cy - 65 * s, cx, cy + 10 * s, cx + 100 * s, cy - 65 * s), fill=(150, 130, 100), width=4)
        d.ellipse((cx - 16 * s, cy - 5 * s, cx + 16 * s, cy + 27 * s), fill=(200, 40, 60))
    elif kind == 'fish':
        d.ellipse((cx - 70 * s, cy - 35 * s, cx + 50 * s, cy + 35 * s), fill=(255, 150, 30)); d.polygon([(cx + 40 * s, cy), (cx + 100 * s, cy - 40 * s), (cx + 100 * s, cy + 40 * s)], fill=(255, 150, 30))
        d.ellipse((cx - 45 * s, cy - 12 * s, cx - 30 * s, cy + 3 * s), fill=(20, 20, 20))
    elif kind == 'clock':
        d.ellipse((cx - 90 * s, cy - 90 * s, cx + 90 * s, cy + 90 * s), fill=(250, 248, 235), outline=(60, 50, 40), width=10)
        a = t * 1.5
        d.line((cx, cy, cx + 60 * s * math.sin(a), cy - 60 * s * math.cos(a)), fill=(30, 30, 30), width=8); d.line((cx, cy, cx, cy - 40 * s), fill=(30, 30, 30), width=10)
    elif kind == 'key':
        d.ellipse((cx - 80 * s, cy - 35 * s, cx - 10 * s, cy + 35 * s), outline=(230, 190, 60), width=int(16 * s)); d.rectangle((cx - 15 * s, cy - 8 * s, cx + 90 * s, cy + 8 * s), fill=(230, 190, 60)); d.rectangle((cx + 60 * s, cy, cx + 74 * s, cy + 30 * s), fill=(230, 190, 60))
    elif kind == 'book':
        d.rectangle((cx - 90 * s, cy - 60 * s, cx + 90 * s, cy + 60 * s), fill=(150, 50, 50)); d.rectangle((cx - 80 * s, cy - 50 * s, cx + 80 * s, cy + 50 * s), outline=(230, 200, 120), width=4)
    elif kind == 'phone':
        d.rounded_rectangle((cx - 50 * s, cy - 95 * s, cx + 50 * s, cy + 95 * s), int(16 * s), fill=(30, 30, 35)); d.rectangle((cx - 40 * s, cy - 80 * s, cx + 40 * s, cy + 70 * s), fill=(120, 200, 255))
    elif kind == 'ring':
        d.ellipse((cx - 55 * s, cy - 40 * s, cx + 55 * s, cy + 70 * s), outline=(240, 200, 70), width=int(16 * s)); d.polygon([(cx - 20 * s, cy - 40 * s), (cx + 20 * s, cy - 40 * s), (cx, cy - 75 * s)], fill=(180, 230, 255))
    elif kind == 'bike':
        for ox in (-70, 70): d.ellipse((cx + ox * s - 50 * s, cy - 10 * s, cx + ox * s + 50 * s, cy + 90 * s), outline=(30, 30, 30), width=8)
        d.line((cx - 70 * s, cy + 40 * s, cx, cy - 30 * s, cx + 70 * s, cy + 40 * s), fill=(40, 120, 220), width=10)
    elif kind == 'moon':
        d.ellipse((cx - 70 * s, cy - 70 * s, cx + 70 * s, cy + 70 * s), fill=(250, 245, 210)); d.ellipse((cx - 20 * s, cy - 80 * s, cx + 100 * s, cy + 60 * s), fill=(25, 25, 50))
    elif kind == 'umbrella':
        d.pieslice((cx - 110 * s, cy - 90 * s, cx + 110 * s, cy + 90 * s), 180, 360, fill=(220, 60, 60)); d.line((cx, cy, cx, cy + 110 * s), fill=(40, 40, 40), width=8)
    elif kind == 'door':
        d.rectangle((cx - 80 * s, cy - 150 * s, cx + 80 * s, cy + 150 * s), fill=(120, 80, 50), outline=(70, 45, 30), width=8); d.ellipse((cx + 40 * s, cy, cx + 58 * s, cy + 18 * s), fill=(230, 190, 60))
    else:  # glow
        for k in range(6, 0, -1):
            a = 40 + k * 18
            d.ellipse((cx - a * s, cy - a * s, cx + a * s, cy + a * s), fill=(255, 240, 180 - k * 10))

GROUND = {'lake': 1700, 'jetty': 1560, 'square': 1650, 'street': 1560, 'hallway': 1680, 'classroom': 1620, 'bedroom': 1640, 'kitchen': 1620}

# ---------------------------------------------------------------- timeline
def build(story):
    tl = []; t = 0.0
    for bi, b in enumerate(story['beats']):
        txt = b['text'].strip()
        if txt.startswith('#'):
            d = 1.4
            tl.append(dict(beat=bi, start=t, dur=d, day=txt[1:].upper(), chunk=None)); t += d; continue
        for ch in chunks(tokenize(txt)):
            d = chunk_dur(ch)
            tl.append(dict(beat=bi, start=t, dur=d, day=None, chunk=ch)); t += d
    return tl, t + 0.6

def render_frame(story, tl, total, t, bgs):
    cur = tl[-1]
    for e in tl:
        if e['start'] <= t < e['start'] + e['dur']: cur = e; break
    bi = cur['beat']; b = story['beats'][bi]
    # beat time range
    bstart = min(e['start'] for e in tl if e['beat'] == bi)
    bend = max(e['start'] + e['dur'] for e in tl if e['beat'] == bi)
    lt = t - bstart; p = min(1, lt / max(0.1, bend - bstart))
    scene = b.get('scene', 'lake')
    cam = b.get('cam', ['in', 'out', 'left', 'right'][bi % 4])
    z = {'in': 1.0 + 0.10 * p, 'out': 1.10 - 0.10 * p}.get(cam, 1.05)
    px = {'left': 0.5 - 0.35 * p, 'right': 0.5 + 0.35 * p}.get(cam, 0.5) if cam in ('left', 'right') else 0.5
    if t < 1 / FPS: z, px = 1.0, 0.5
    frame = bgs[scene].copy()
    d = ImageDraw.Draw(frame)
    who = b.get('who', [])
    gy = GROUND.get(scene, 1650)
    n = len(who)
    for i, k in enumerate(who):
        x = SW / 2 + (i - (n - 1) / 2) * 300
        draw_char(d, x, gy, 2.5 if n <= 2 else 1.9, k, t, phase=i * 1.7, face=1 if x < SW / 2 else -1)
    if b.get('prop'):
        if n: draw_prop(d, b['prop'], SW / 2 + 330, 640, t, 1.3)
        else: draw_prop(d, b['prop'], SW / 2, 820 if scene != 'square' else 900, t, 1.8)
    if scene == 'lake' or scene == 'jetty':
        for i in range(10):
            x = (i * 157 + t * 40) % SW; y = 950 + (i * 53) % 450
            d.line((x, y, x + 60, y), fill=(160, 210, 250), width=4)
    cw, chh = int(SW / z / 1.12 * 1.0), int(SH / z / 1.12)
    cw = int(W * 1.12 / (1.12 * z) * 1.0); chh = int(H / z)
    cw = int(W / z * 1.0); chh = int(H / z)
    ox = int((SW - cw) * px); oy = int((SH - chh) * 0.5)
    img = frame.crop((ox, oy, ox + cw, oy + chh)).resize((W, H), Image.BILINEAR)
    if b.get('tint'):
        ov = Image.new('RGBA', (W, H), tuple(b['tint']) + (90,)); img.alpha_composite(ov)
    if b.get('gray'):
        img = Image.merge('RGBA', (*[img.convert('L')] * 3, img.split()[3]))
    if cur['day']:
        dark = Image.new('RGBA', (W, H), (0, 0, 0, 90)); img.alpha_composite(dark)
        draw_daycard(img, cur['day'], t - cur['start'])
    elif cur['chunk'] and t < total - 0.6:
        draw_caption(img, cur['chunk'], t - cur['start'])
    watermark(img)
    return img.convert('RGB')

def main():
    story = json.load(open(sys.argv[1])); out = sys.argv[2]
    tl, total = build(story)
    bgs = {}
    for i, b in enumerate(story['beats']):
        s = b.get('scene', 'lake')
        if s not in bgs: bgs[s] = scene_bg(s, seed=len(bgs) + 3)
    if '--still' in sys.argv:
        ts = [float(x) for x in sys.argv[sys.argv.index('--still') + 1:] if not x.endswith('.jpg') and x != '--sheet']
        sheet = sys.argv[sys.argv.index('--sheet') + 1] if '--sheet' in sys.argv else 'sheet.jpg'
        ims = [render_frame(story, tl, total, t, bgs).resize((W // 4, H // 4)) for t in ts]
        cols = 6; rows = math.ceil(len(ims) / cols)
        sh = Image.new('RGB', (cols * W // 4, rows * H // 4))
        for i, im in enumerate(ims): sh.paste(im, ((i % cols) * W // 4, (i // cols) * H // 4))
        sh.save(sheet); print('total', round(total, 2)); return
    json.dump({'total': total, 'events': [dict(start=e['start'], day=e['day'], beat=e['beat']) for e in tl]}, open(out + '.json', 'w'))
    p = subprocess.Popen(['ffmpeg', '-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{W}x{H}', '-r', str(FPS), '-i', '-',
                          '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '19', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', out], stdin=subprocess.PIPE)
    for f in range(int(total * FPS)):
        p.stdin.write(render_frame(story, tl, total, f / FPS, bgs).tobytes())
    p.stdin.close(); p.wait(); print('total', round(total, 2))

if __name__ == '__main__':
    main()
