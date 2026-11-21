#!/usr/bin/env python3
"""3D version of the IN THIS TOWN renderer (three.js in headless Chromium + PIL captions).
Usage: python3 town3d.py story.json out.mp4 [--still t1 t2 ... --sheet sheet.jpg]
Same story.json as townstory.py (see its docstring). Extra beat keys: "cam": in|out|left|right|up|close,
"clock_stopped": true (tower clock hands freeze), "night": true. Scenes: lake, jetty, square, street,
classroom, kitchen, bedroom, hallway. Render speed is roughly 3-5 frames/s, so an 80 s story takes ~10 min:
run it in the background with nohup and poll the log."""
import sys, os, json, io, struct, subprocess, math
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import townstory as ts
W, H, FPS = ts.W, ts.H, ts.FPS
D3 = os.path.join(os.path.dirname(os.path.abspath(__file__)), '3d')

def main():
    sp, out = sys.argv[1], sys.argv[2]
    story = json.load(open(sp)); tl, total = ts.build(story)
    beats = []
    for bi in range(len(story['beats'])):
        es = [e for e in tl if e['beat'] == bi]
        beats.append(dict(start=min(e['start'] for e in es), end=max(e['start'] + e['dur'] for e in es)))
    beats[-1]['end'] = total + 1
    bj = out + '.beats.json'; json.dump({'total': total, 'beats': beats}, open(bj, 'w'))
    stills = []
    if '--still' in sys.argv:
        a = sys.argv[sys.argv.index('--still') + 1:]; stills = [x for x in a if not x.startswith('--') and not x.endswith('.jpg')]
    env = dict(os.environ)
    node = subprocess.Popen(['node', os.path.join(D3, 'drive.js'), sp, bj, str(FPS)] + stills, stdout=subprocess.PIPE, env=env)
    def frames():
        while True:
            h = node.stdout.read(4)
            if len(h) < 4: return
            n = struct.unpack('>I', h)[0]; yield Image.open(io.BytesIO(node.stdout.read(n))).convert('RGBA')
    def comp(img3, t):
        cur = tl[-1]
        for e in tl:
            if e['start'] <= t < e['start'] + e['dur']: cur = e; break
        b = story['beats'][cur['beat']]
        img = img3.resize((W, H), Image.LANCZOS)
        if b.get('gray'): img = Image.merge('RGBA', (*[img.convert('L')] * 3, img.split()[3]))
        if b.get('tint'): img.alpha_composite(Image.new('RGBA', (W, H), tuple(b['tint']) + (90,)))
        if cur['day']:
            img.alpha_composite(Image.new('RGBA', (W, H), (0, 0, 0, 90))); ts.draw_daycard(img, cur['day'], t - cur['start'])
        elif cur['chunk'] and t < total - 0.6:
            ts.draw_caption(img, cur['chunk'], t - cur['start'])
        ts.watermark(img); return img.convert('RGB')
    if stills:
        sheet = sys.argv[sys.argv.index('--sheet') + 1] if '--sheet' in sys.argv else 'sheet.jpg'
        ims = [comp(f, float(t)).resize((W // 4, H // 4)) for f, t in zip(frames(), stills)]
        cols = 6; rows = math.ceil(len(ims) / cols); sh = Image.new('RGB', (cols * W // 4, rows * H // 4))
        for i, im in enumerate(ims): sh.paste(im, ((i % cols) * W // 4, (i // cols) * H // 4))
        sh.save(sheet); print('total', round(total, 2)); return
    json.dump({'total': total, 'events': [dict(start=e['start'], day=e['day'], beat=e['beat']) for e in tl]}, open(out + '.json', 'w'))
    ff = subprocess.Popen(['ffmpeg', '-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{W}x{H}', '-r', str(FPS), '-i', '-',
                           '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '19', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', out], stdin=subprocess.PIPE)
    for i, f in enumerate(frames()):
        ff.stdin.write(comp(f, i / FPS).tobytes())
        if i % 150 == 0: print('frame', i, flush=True)
    ff.stdin.close(); ff.wait(); node.wait(); print('total', round(total, 2))

if __name__ == '__main__':
    main()
