#!/usr/bin/env python3
"""Original music bed for IN THIS TOWN stories: music-box arpeggio + soft pad + light beat,
whoosh on day cards, low boom at the start of the last beat. Usage:
python3 townmusic.py <video.mp4.json timing> out.wav [seed]"""
import sys, json, wave, numpy as np
info = json.load(open(sys.argv[1])); out = sys.argv[2]; seed = int(sys.argv[3]) if len(sys.argv) > 3 else 1
rng = np.random.default_rng(seed)
sr = 44100; D = info['total']; n = int(sr * D); x = np.zeros(n); fx = np.zeros(n)
bpm = int(rng.choice([84, 88, 92, 96])); b = 60 / bpm
root = 220 * 2 ** (int(rng.integers(-3, 4)) / 12)
progs = [[[0, 3, 7], [8, 12, 15], [3, 7, 10], [10, 14, 17]], [[0, 3, 7], [5, 8, 12], [8, 12, 15], [7, 11, 14]], [[0, 3, 7], [10, 14, 17], [8, 12, 15], [7, 10, 14]]]
prog = progs[int(rng.integers(len(progs)))]
def add(buf, s, start):
    i = int(start * sr)
    if i >= n: return
    e = min(n, i + len(s)); buf[i:e] += s[:e - i]
def bell(f, dur, a):
    t = np.arange(int(dur * sr)) / sr
    return a * (np.sin(2 * np.pi * f * t) + 0.3 * np.sin(2 * np.pi * 2.01 * f * t) + 0.12 * np.sin(2 * np.pi * 3.98 * f * t)) * np.exp(-4 * t)
def pad(fs, dur, a):
    t = np.arange(int(dur * sr)) / sr
    s = sum(np.sin(2 * np.pi * f * t) + 0.5 * np.sin(2 * np.pi * f * 1.003 * t) for f in fs)
    env = np.minimum(1, t / 0.6) * np.minimum(1, (dur - t) / 0.6)
    return a * s * env / len(fs)
beat = 0; pattern = [0, 1, 2, 1, 3, 2, 1, 2]
while beat * b < D:
    ch = prog[(beat // 8) % 4]; st = beat * b
    if beat % 8 == 0: add(x, pad([root / 2 * 2 ** (c / 12) for c in ch], 8 * b, 0.10), st)
    for k in range(2):
        idx = pattern[(beat * 2 + k) % 8]; note = ch[idx % 3] + (12 if idx == 3 else 0)
        add(x, bell(root * 2 * 2 ** (note / 12), 1.2, 0.06), st + k * b / 2)
    if beat >= 8:
        if beat % 2 == 0:
            kt = np.arange(int(0.2 * sr)) / sr; add(x, 0.35 * np.sin(2 * np.pi * (45 + 60 * np.exp(-30 * kt)) * kt) * np.exp(-14 * kt), st)
        add(x, 0.025 * rng.standard_normal(int(0.03 * sr)) * np.exp(-90 * np.arange(int(0.03 * sr)) / sr), st + b / 2)
    beat += 1
for e in info['events']:
    if e.get('day'):
        L = int(0.5 * sr); t = np.arange(L) / sr
        noise = rng.standard_normal(L); noise = np.convolve(noise, np.ones(20) / 20, 'same')
        add(fx, 0.5 * noise * np.sin(np.pi * t / 0.5) ** 2, e['start'] - 0.1)
last = max(e['beat'] for e in info['events'])
lt = min(e['start'] for e in info['events'] if e['beat'] == last)
t = np.arange(int(2.5 * sr)) / sr
add(fx, 0.8 * np.sin(2 * np.pi * (38 + 30 * np.exp(-6 * t)) * t) * np.exp(-1.6 * t), lt)
x = x / (np.abs(x).max() + 1e-9) * 0.45; fx = fx / (np.abs(fx).max() + 1e-9) * 0.55
y = x + fx
f = int(1.0 * sr); y[:f] *= np.linspace(0, 1, f); y[-2 * f:] *= np.linspace(1, 0, 2 * f)
y = np.tanh(y * 1.3) * 0.9
w = wave.open(out, 'w'); w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr); w.writeframes((y * 32767).astype(np.int16).tobytes()); w.close()
print('bpm', bpm)
