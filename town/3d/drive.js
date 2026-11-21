// node drive.js story.json beats.json fps [t1 t2 ...]  -> writes length-prefixed JPEG frames to stdout
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const [story, beats, fps] = [JSON.parse(fs.readFileSync(process.argv[2])), JSON.parse(fs.readFileSync(process.argv[3])), parseFloat(process.argv[4])];
const stills = process.argv.slice(5).map(Number);
(async () => {
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await b.newPage({ viewport: { width: 720, height: 1280 } });
  p.on('console', m => process.stderr.write('[page] ' + m.text() + '\n')); p.on('pageerror', e => process.stderr.write('[err] ' + e + '\n'));
  await p.route('http://town.local/**', r => { const f = path.join(__dirname, new URL(r.request().url()).pathname);
    r.fulfill({ body: fs.readFileSync(f), contentType: f.endsWith('.js') ? 'application/javascript' : 'text/html' }); });
  await p.goto('http://town.local/index.html'); await p.waitForFunction('window.ready === true', null, { timeout: 60000 });
  await p.evaluate(([s, bt]) => window.setup(s, bt), [story, beats.beats]);
  const times = stills.length ? stills : Array.from({ length: Math.floor(beats.total * fps) }, (_, i) => i / fps);
  for (const t of times) {
    await p.evaluate(t => window.renderAt(t), t);
    const buf = await p.screenshot({ type: 'jpeg', quality: 92 });
    const len = Buffer.alloc(4); len.writeUInt32BE(buf.length);
    if (!process.stdout.write(Buffer.concat([len, buf]))) await new Promise(r => process.stdout.once('drain', r));
  }
  await b.close();
})();
