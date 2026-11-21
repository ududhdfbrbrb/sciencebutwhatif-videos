# Parallel render helper: python3 par_render.py cfg.json out.mp4 K N  (run K=0..N-1 in background, then concat out.mp4.partK.mp4 with ffmpeg concat and mux out.mp4.wav). Full single-process render takes >15 min on 2 cores.
import sys, subprocess
cfg, out, k, n = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4])
sys.argv = ['geo_quiz.py', cfg, out]
import os; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import geo_quiz as g
nfr = int(g.TOTAL * g.FPS); a = nfr * k // n; b = nfr * (k + 1) // n
if k == 0: g.make_audio(out + '.wav')
p = subprocess.Popen(['ffmpeg','-y','-v','error','-f','rawvideo','-pix_fmt','bgr24','-s',f'{g.W}x{g.H}','-r',str(g.FPS),'-i','-',
 '-c:v','libx264','-preset','veryfast','-crf','20','-pix_fmt','yuv420p',f'{out}.part{k}.mp4'], stdin=subprocess.PIPE)
for f in range(a, b):
    p.stdin.write(g.frame_at(f / g.FPS).tobytes())
    if f % 90 == 0: print(k, f, b, flush=True)
p.stdin.close(); p.wait(); print('done', k)
