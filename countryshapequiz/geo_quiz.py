import json, math, subprocess, sys, wave
import numpy as np, cv2
from PIL import Image, ImageDraw, ImageFont, ImageFilter

W, H, FPS = 1080, 1920, 30
"""Usage: python3 geo_quiz.py config.json out.mp4 [--still t1 t2 ...]
config.json: {"countries": [{"iso": "fr", "name": "FRANCE", "q": "Which country ...?", "fact": "optional extra line after reveal"}, ...],
              "title": "GUESS THE COUNTRY", "subtitle": "by its shape only"}
Assets (run setup.sh first): $GEO_ASSETS (default /home/claude/geoassets)"""
import os
A = os.environ.get('GEO_ASSETS', '/home/claude/geoassets')
CFG = json.load(open(sys.argv[1]))
OUT = sys.argv[2]
ROOT = os.path.dirname(os.path.abspath(OUT))
FONT = '/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf'
WATERMARK = CFG.get('watermark', '@countryshapequiz')

tex = cv2.imread(f'{A}/tg/example/img/earth-blue-marble.jpg')
hsv = cv2.cvtColor(tex, cv2.COLOR_BGR2HSV).astype(np.float32)
hsv[..., 1] *= 1.25; hsv[..., 2] = np.clip(hsv[..., 2] * 1.1, 0, 255)
tex = cv2.cvtColor(np.clip(hsv, 0, 255).astype(np.uint8), cv2.COLOR_HSV2BGR)
TH, TW = tex.shape[:2]

sky = cv2.imread(f'{A}/tg/example/img/night-sky.png')
sky = cv2.resize(sky, (int(sky.shape[1] * H / sky.shape[0]), H))
sky = sky[:, :W] if sky.shape[1] >= W else cv2.resize(sky, (W, H))
sky = (sky * 0.8).astype(np.uint8)

geo = json.load(open(f'{A}/ne/geojson/ne_50m_admin_0_countries.geojson'))

def area(r):
    x, y = r[:, 0], r[:, 1]
    return abs(np.dot(x, np.roll(y, 1)) - np.dot(y, np.roll(x, 1))) / 2

def rings_for(iso):
    feats = [f for f in geo['features'] if str(f['properties'].get('ISO_A2_EH', '')).lower() == iso.lower()]
    if not feats:
        raise SystemExit(f'No country with ISO_A2_EH={iso}')
    polys = []
    for f in feats:
        g = f['geometry']
        polys += g['coordinates'] if g['type'] == 'MultiPolygon' else [g['coordinates']]
    rings = [np.array(p[0], float) for p in polys]
    big = max(rings, key=area)
    bc = big.mean(0)
    keep = [r for r in rings if abs(r[:, 0].mean() - bc[0]) < 12 and abs(r[:, 1].mean() - bc[1]) < 12]
    allp = np.concatenate(keep)
    c = ((allp[:, 1].min() + allp[:, 1].max()) / 2, (allp[:, 0].min() + allp[:, 0].max()) / 2)
    return [np.radians(r) for r in keep], c

def flag_png(iso):
    os.makedirs(f'{A}/flagpng', exist_ok=True)
    png = f'{A}/flagpng/{iso}.png'
    if not os.path.exists(png):
        html = f'{A}/flagpng/{iso}.html'
        open(html, 'w').write(f"<style>html,body{{margin:0}}img{{width:1500px;height:1000px;display:block}}</style><img src='../flags/svg/{iso}.svg'>")
        subprocess.run(['google-chrome', '--headless', '--no-sandbox', '--disable-gpu', '--hide-scrollbars',
                        '--allow-file-access-from-files', f'--screenshot={png}', '--window-size=1500,1000',
                        f'file://{html}'], capture_output=True)
    img = cv2.imread(png)
    if img is None: raise SystemExit(f'flag render failed for {iso}')
    return img

COUNTRIES = []
for k in CFG['countries']:
    k = dict(k); k['iso'] = k['iso'].lower()
    k['rings'], k['c'] = rings_for(k['iso'])
    k['flagimg'] = flag_png(k['iso'])
    COUNTRIES.append(k)

def fwd(rings, lat0, lon0, R, cx=W / 2, cy=H / 2):
    p0, l0 = math.radians(lat0), math.radians(lon0)
    pts = []
    for r in rings:
        lon, lat = r[:, 0], r[:, 1]
        x = np.cos(lat) * np.sin(lon - l0)
        y = math.cos(p0) * np.sin(lat) - math.sin(p0) * np.cos(lat) * np.cos(lon - l0)
        pts.append(np.stack([cx + R * x, cy - R * y], 1))
    return pts

# target zoom per country: fit into ~860 x 980 px box
for k in COUNTRIES:
    pts = np.concatenate(fwd(k['rings'], *k['c'], 1.0))
    w = np.ptp(pts[:, 0]); h = np.ptp(pts[:, 1])
    k['R'] = min(820 / w, 800 / h)
    mid = (pts.min(0) + pts.max(0)) / 2
    k['off'] = (mid - [W / 2, H / 2])  # recenter offset at R=1

ys, xs = np.mgrid[0:H, 0:W].astype(np.float32)

def render_globe(lat0, lon0, R, cx=W / 2, cy=H / 2):
    x = (xs - cx) / R; y = (cy - ys) / R
    rho2 = x * x + y * y
    inside = rho2 <= 1.0
    z = np.sqrt(np.clip(1 - rho2, 0, 1))
    p0, l0 = math.radians(lat0), math.radians(lon0)
    lat = np.arcsin(np.clip(z * math.sin(p0) + y * math.cos(p0), -1, 1))
    lon = l0 + np.arctan2(x, z * math.cos(p0) - y * math.sin(p0))
    mx = ((np.degrees(lon) + 180) % 360) / 360 * TW
    my = (90 - np.degrees(lat)) / 180 * TH
    img = cv2.remap(tex, mx.astype(np.float32), np.clip(my, 0, TH - 1).astype(np.float32),
                    cv2.INTER_CUBIC, borderMode=cv2.BORDER_WRAP)
    shade = (0.3 + 0.7 * np.sqrt(z))[..., None]
    img = img.astype(np.float32) * shade
    frame = sky.astype(np.float32).copy()
    if R < 3000:  # atmosphere glow
        glow = np.zeros((H, W, 3), np.float32)
        cv2.circle(glow, (int(cx), int(cy)), int(R * 1.02), (255, 170, 80), -1)
        glow = cv2.GaussianBlur(glow, (0, 0), max(8, R * 0.06))
        frame = frame + glow * 0.55
    aa = np.clip((1 - np.sqrt(rho2)) * R, 0, 1)[..., None]
    frame = frame * (1 - aa) + img * aa
    return np.clip(frame, 0, 255).astype(np.uint8), inside

# ---------- text ----------
_fonts = {}
FONT_FACT = next((p for p in ['/usr/share/fonts/opentype/inter/Inter-SemiBold.otf', '/usr/share/fonts/truetype/google-fonts/Inter-SemiBold.ttf'] if os.path.exists(p)), FONT)
def font(s, path=None):
    path = path or FONT
    if (path, s) not in _fonts: _fonts[(path, s)] = ImageFont.truetype(path, s)
    return _fonts[(path, s)]

def wrap(text, f, maxw):
    words, lines, cur = text.split(), [], ''
    for w_ in words:
        t = (cur + ' ' + w_).strip()
        if f.getlength(t) <= maxw: cur = t
        else: lines.append(cur); cur = w_
    lines.append(cur); return lines

def draw_text(pil, text, cy, size, scale=1.0, alpha=1.0, fill=(255, 255, 255), stroke=10, maxw=940, top=None, fpath=None, shadow=False):
    if alpha <= 0.01: return
    f = font(max(8, int(size * scale)), fpath)
    lines = wrap(text, f, maxw)
    lh = int(f.size * 1.18)
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    top = cy - lh * len(lines) / 2 if top is None else top
    for i, ln in enumerate(lines):
        tw = f.getlength(ln)
        d.text(((W - tw) / 2, top + i * lh), ln, font=f, fill=fill + (255,),
               stroke_width=int(stroke * scale), stroke_fill=(0, 0, 0, 255))
    if shadow:  # soft drop shadow under the text
        sh = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        sh.putalpha(layer.split()[3].filter(ImageFilter.GaussianBlur(6)))
        base = Image.new('RGBA', (W, H), (0, 0, 0, 0)); base.alpha_composite(sh, (4, 6)); base.alpha_composite(sh, (4, 6))
        base.alpha_composite(layer); layer = base
    if alpha < 1:
        a = layer.split()[3].point(lambda v: int(v * alpha)); layer.putalpha(a)
    pil.alpha_composite(layer)

_wm = None
def draw_watermark(pil):
    global _wm
    if _wm is None:
        f = font(36); tw = f.getlength(WATERMARK)
        _wm = Image.new('RGBA', (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(_wm)
        x0, x1 = (W - tw) / 2 - 24, (W + tw) / 2 + 24
        d.rounded_rectangle([x0, 196 - 30, x1, 196 + 30], 30, fill=(0, 0, 0, 110))
        d.text(((W - tw) / 2, 196 - 25), WATERMARK, font=f, fill=(255, 255, 255, 170))
    pil.alpha_composite(_wm)

def pop(t, dur=0.25):  # pop-in scale curve
    if t < 0: return 0
    if t > dur: return 1
    u = t / dur - 1; c = 2.2
    return 0.55 + 0.45 * (1 + (c + 1) * u ** 3 + c * u ** 2)

def ease(u): u = min(max(u, 0), 1); return u * u * (3 - 2 * u)
def ease_io(u): u = min(max(u, 0), 1); return 0.5 - 0.5 * math.cos(math.pi * u)

# ---------- timeline ----------
INTRO, SEG, OUTRO = 2.6, 6.6, 3.0
R_OUT = 430
TOTAL = INTRO + SEG * len(COUNTRIES) + OUTRO
events = []  # audio cues (time, kind)

def lerp_angle(a, b, u):
    d = (b - a + 180) % 360 - 180
    return a + d * u

def camera(t):
    """returns lat, lon, R, country index or None, local time"""
    if t < INTRO:
        return 20, -60 + t * 12, R_OUT, None, t
    tt = t - INTRO
    i = int(tt // SEG)
    if i >= len(COUNTRIES):
        lt = tt - SEG * len(COUNTRIES)
        k = COUNTRIES[-1]
        return k['c'][0], k['c'][1] + lt * 12, R_OUT, None, lt
    lt = tt - i * SEG
    k = COUNTRIES[i]
    prev = (20, -60 + INTRO * 12) if i == 0 else COUNTRIES[i - 1]['c']
    u = ease_io(lt / 1.5)
    lat = prev[0] + (k['c'][0] - prev[0]) * u
    lon = lerp_angle(prev[1], k['c'][1], u)
    zu = ease_io((lt - 0.7) / 1.4)
    R = math.exp(math.log(R_OUT) + (math.log(k['R']) - math.log(R_OUT)) * zu)
    if 2.1 <= lt < 5.3:
        R = k['R'] * (1 + 0.05 * (lt - 2.1) / 3.2)
    if lt >= 5.3:
        R5 = k['R'] * 1.05
        zo = ease_io((lt - 5.4) / 1.2)
        R = math.exp(math.log(R5) + (math.log(R_OUT) - math.log(R5)) * zo)
    return lat, lon, R, i, lt

for i in range(len(COUNTRIES)):
    b = INTRO + i * SEG
    events += [(b + 0.1, 'whoosh'), (b + 2.4, 'tick'), (b + 3.1, 'tick'), (b + 3.8, 'tick'),
               (b + 4.5, 'reveal'), (b + 5.4, 'whoosh')]
events += [(0.0, 'whoosh'), (INTRO + SEG * len(COUNTRIES) + 0.3, 'reveal')]

def frame_at(t):
    lat, lon, R, ci, lt = camera(t)
    cx, cy = W / 2, H / 2
    if ci is None:
        cy += 90
    if ci is not None:
        k = COUNTRIES[ci]
        # recenter country nicely (scales in as we zoom)
        s = min(1, (R - R_OUT) / max(1, k['R'] - R_OUT)) if k['R'] > R_OUT else 1
        cx -= k['off'][0] * R * s; cy -= k['off'][1] * R * s - 100 * s - 90 * (1 - s)
    img, _ = render_globe(lat, lon, R, cx, cy)
    if ci is not None:
        k = COUNTRIES[ci]
        pts = [p.astype(np.int32) for p in fwd(k['rings'], lat, lon, R, cx, cy)]
        ss = 4  # supersampled mask for smooth edges
        m = np.zeros((H, W), np.uint8)
        cv2.fillPoly(m, [p for p in pts], 255, lineType=cv2.LINE_AA)
        mask = m.astype(np.float32) / 255
        out_a = ease((lt - 2.0) / 0.4)
        if lt > 5.3: out_a *= 1 - ease((lt - 5.3) / 0.5)
        if out_a > 0:
            # darken surroundings slightly to focus the shape
            dim = 1 - 0.35 * out_a * (1 - mask)
            img = (img * dim[..., None]).astype(np.uint8)
            # flag reveal: wipe left -> right
            rv = ease((lt - 4.5) / 0.55)
            if rv > 0:
                allp = np.concatenate(pts)
                x0, y0 = allp.min(0); x1, y1 = allp.max(0)
                fw, fh = max(2, x1 - x0), max(2, y1 - y0)
                fl = cv2.resize(k['flagimg'], (int(fw), int(fh)), interpolation=cv2.INTER_AREA)
                canvas = np.zeros_like(img, np.float32)
                X0, Y0 = int(x0), int(y0)
                xa, ya = max(0, X0), max(0, Y0)
                xb, yb = min(W, X0 + fl.shape[1]), min(H, Y0 + fl.shape[0])
                canvas[ya:yb, xa:xb] = fl[ya - Y0:yb - Y0, xa - X0:xb - X0]
                lum = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY).astype(np.float32) / 255
                shaded = canvas * (0.62 + 0.55 * lum[..., None])
                wipe = np.clip((xs - (x0 + (fw + 80) * rv - 80)) / -80, 0, 1)
                a = (mask * wipe * 0.9 * out_a)[..., None]
                if lt > 5.3: a = a  # fades with out_a
                img = np.clip(img * (1 - a) + shaded * a, 0, 255).astype(np.uint8)
            # glowing outline
            line = np.zeros_like(img)
            cv2.polylines(line, pts, True, (255, 255, 255), 5, cv2.LINE_AA)
            glow = cv2.GaussianBlur(line, (0, 0), 9)
            img = np.clip(img.astype(np.float32) + (glow.astype(np.float32) * 1.6 + line) * out_a, 0, 255).astype(np.uint8)

    pil = Image.fromarray(cv2.cvtColor(img, cv2.COLOR_BGR2RGB)).convert('RGBA')
    TOP = 380
    if ci is None and t < INTRO:
        # full-size from frame 0 so the TikTok cover shows the title
        _tt = CFG.get('title', 'GUESS THE COUNTRY'); _tl = len(wrap(_tt, font(104), 940))
        draw_text(pil, _tt, TOP, 104, 1, stroke=12)
        # subtitle sits below the title, even when the title wraps to 2+ lines
        draw_text(pil, CFG.get('subtitle', 'by its shape only'), None, 64, 1, fill=(255, 214, 64), stroke=9,
                  top=TOP - int(104 * 1.18) * _tl / 2 + int(104 * 1.18) * _tl + 18)
    elif ci is None:
        draw_text(pil, 'How many did you get?', TOP + 20, 96, pop(lt - 0.2), stroke=12)
        draw_text(pil, 'Comment your score', 1560, 72, pop(lt - 0.9), fill=(255, 214, 64), stroke=10)
    else:
        k = COUNTRIES[ci]
        a = ease(lt / 0.3) * (1 - ease((lt - 5.9) / 0.3))
        draw_text(pil, f'{ci + 1}/{len(COUNTRIES)}', 268, 50, 1, a, stroke=7)
        qa = ease((lt - 1.6) / 0.3) * (1 - ease((lt - 5.9) / 0.3))
        draw_text(pil, k['q'], 0, 76, pop(lt - 1.6), qa, stroke=10, top=318)
        for j, n in enumerate(['3', '2', '1']):
            st = 2.4 + j * 0.7
            if st <= lt < st + 0.7:
                u = (lt - st) / 0.7
                draw_text(pil, n, 1590, 210, 1.35 - 0.35 * ease(u * 3), 1 - ease((u - 0.7) / 0.3),
                          fill=(255, 214, 64), stroke=16)
        if 4.5 <= lt:
            na = 1 - ease((lt - 5.9) / 0.3)
            ns = 150
            while ns > 70 and font(ns).getlength(k['name']) > 960: ns -= 6
            draw_text(pil, k['name'], 1590, ns, pop(lt - 4.5), na, stroke=16, maxw=2000)
        if k.get('fact') and lt >= 4.8:  # optional extra fun fact under the revealed name
            fa = ease((lt - 4.8) / 0.3) * (1 - ease((lt - 6.25) / 0.3))
            draw_text(pil, k['fact'], 1735, 48, 1, fa, stroke=4, maxw=920, fpath=FONT_FACT, shadow=True)
    draw_watermark(pil)
    return cv2.cvtColor(np.array(pil.convert('RGB')), cv2.COLOR_RGB2BGR)

# ---------- audio ----------
def make_audio(path):
    sr = 44100; n = int(TOTAL * sr); tt = np.arange(n) / sr
    a = np.zeros(n)
    # ambient pad
    for f_, g in [(55, .05), (82.4, .035), (110, .03), (164.8, .015)]:
        a += g * np.sin(2 * np.pi * f_ * tt) * (0.7 + 0.3 * np.sin(2 * np.pi * 0.1 * tt + f_))
    # soft beat: kick every 0.5s
    for bt in np.arange(INTRO, TOTAL - 1.0, 0.5):
        i0 = int(bt * sr); L = int(0.25 * sr)
        if i0 + L > n: break
        k_t = np.arange(L) / sr
        a[i0:i0 + L] += 0.22 * np.sin(2 * np.pi * (50 + 90 * np.exp(-k_t * 30)) * k_t) * np.exp(-k_t * 14)
    rng = np.random.default_rng(1)
    for et, kind in events:
        i0 = int(et * sr)
        if kind == 'whoosh':
            L = int(1.2 * sr); nz = rng.standard_normal(L)
            env = np.sin(np.linspace(0, np.pi, L)) ** 2
            nz = np.convolve(nz, np.ones(30) / 30, 'same')
            seg = 0.5 * nz * env
        elif kind == 'tick':
            L = int(0.12 * sr); k_t = np.arange(L) / sr
            seg = 0.35 * np.sin(2 * np.pi * 1400 * k_t) * np.exp(-k_t * 45)
        else:
            L = int(1.4 * sr); k_t = np.arange(L) / sr
            seg = sum(g * np.sin(2 * np.pi * f_ * k_t) for f_, g in [(880, .25), (1318.5, .18), (1760, .1)]) * np.exp(-k_t * 3.5)
        e = min(n, i0 + len(seg)); a[i0:e] += seg[:e - i0]
    a = a / max(1e-6, np.abs(a).max()) * 0.85
    fade = int(0.6 * sr); a[-fade:] *= np.linspace(1, 0, fade)
    pcm = (a * 32767).astype(np.int16)
    with wave.open(path, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr); w.writeframes(pcm.tobytes())

if __name__ == '__main__':
    if '--still' in sys.argv:
        for ts in map(float, sys.argv[sys.argv.index('--still') + 1:]):
            cv2.imwrite(f'{ROOT}/still_{ts:.1f}.jpg', frame_at(ts))
        sys.exit()
    make_audio(OUT + '.wav')
    nfr = int(TOTAL * FPS)
    p = subprocess.Popen(['ffmpeg', '-y', '-v', 'error', '-f', 'rawvideo', '-pix_fmt', 'bgr24', '-s', f'{W}x{H}',
                          '-r', str(FPS), '-i', '-', '-i', OUT + '.wav', '-c:v', 'libx264',
                          '-preset', 'veryfast', '-crf', '20', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '160k',
                          '-shortest', '-movflags', '+faststart', OUT], stdin=subprocess.PIPE)
    for f in range(nfr):
        p.stdin.write(frame_at(f / FPS).tobytes())
        if f % 60 == 0: print(f, '/', nfr, flush=True)
    p.stdin.close(); p.wait()
    print('done', OUT)
