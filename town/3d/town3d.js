// 3D world for IN THIS TOWN stories (three.js). window.setup(story, beats) then window.renderAt(t).
import * as THREE from './three.module.js';

const W = 720, H = 1280;
const renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true });
renderer.setSize(W, H); renderer.setPixelRatio(1);
renderer.shadowMap.enabled = true; renderer.shadowMap.type = THREE.PCFShadowMap;
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.toneMapping = THREE.ACESFilmicToneMapping; renderer.toneMappingExposure = 1.05;
document.body.appendChild(renderer.domElement);
const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(42, W / H, 0.1, 600);

// ---------- helpers
let seed = 7; const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
const mat = (c, o = {}) => new THREE.MeshStandardMaterial({ color: c, roughness: 0.85, ...o });
function box(w, h, d, c, x, y, z, parent = scene, o = {}) {
  const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), c.isMaterial ? c : mat(c, o));
  m.position.set(x, y, z); m.castShadow = true; m.receiveShadow = true; parent.add(m); return m;
}
function cyl(rt, rb, h, c, x, y, z, parent = scene, seg = 16) {
  const m = new THREE.Mesh(new THREE.CylinderGeometry(rt, rb, h, seg), c.isMaterial ? c : mat(c));
  m.position.set(x, y, z); m.castShadow = true; m.receiveShadow = true; parent.add(m); return m;
}
function canvasTex(w, h, draw) {
  const c = document.createElement('canvas'); c.width = w; c.height = h; draw(c.getContext('2d'), w, h);
  const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = 4; return t;
}

// ---------- lights / sky
const hemi = new THREE.HemisphereLight(0xdcefff, 0x8aa070, 1.6); scene.add(hemi);
const sun = new THREE.DirectionalLight(0xfff1d8, 2.4); sun.position.set(40, 60, 30); sun.castShadow = true;
sun.shadow.mapSize.set(1024, 1024); const sc = sun.shadow.camera; sc.left = -40; sc.right = 40; sc.top = 40; sc.bottom = -40; sc.far = 200;
sun.shadow.bias = -0.0005; scene.add(sun); scene.add(sun.target);
const lamp = new THREE.PointLight(0xffd9a0, 0, 30); scene.add(lamp);
const DAY = new THREE.Color(0x8ec9f5), NIGHT = new THREE.Color(0x0b1230);
scene.fog = new THREE.Fog(0xbfe0f7, 60, 220);

// ---------- outdoor town (origin area)
const grass = new THREE.Mesh(new THREE.PlaneGeometry(500, 500), mat(0x6fb35a)); grass.rotation.x = -Math.PI / 2; grass.receiveShadow = true; scene.add(grass);
// lake: centred at (0,?, -40)
const lakeGeo = new THREE.PlaneGeometry(120, 60, 60, 30);
const lake = new THREE.Mesh(lakeGeo, new THREE.MeshStandardMaterial({ color: 0x3d8fd6, roughness: 0.25, metalness: 0.1 }));
lake.rotation.x = -Math.PI / 2; lake.position.set(0, 0.05, -40); lake.receiveShadow = true; scene.add(lake);
const lakeBase = lakeGeo.attributes.position.array.slice();
// promenade path + railing + benches + lamps along lake front z=-8
box(120, 0.12, 6, 0xb9b9bd, 0, 0.06, -6);
for (let x = -58; x <= 58; x += 2.5) box(0.12, 1.1, 0.12, 0x222428, x, 0.6, -9);
box(120, 0.1, 0.1, 0x222428, 0, 1.1, -9); box(120, 0.08, 0.08, 0x222428, 0, 0.7, -9);
function bench(x, z, ry = 0, p = scene) {
  const g = new THREE.Group(); g.position.set(x, 0, z); g.rotation.y = ry; p.add(g);
  box(2.4, 0.12, 0.6, 0xa0683a, 0, 0.55, 0, g); box(2.4, 0.5, 0.1, 0xa0683a, 0, 0.95, -0.3, g);
  box(0.1, 0.55, 0.6, 0x2a2a2a, -1.1, 0.28, 0, g); box(0.1, 0.55, 0.6, 0x2a2a2a, 1.1, 0.28, 0, g); return g;
}
bench(-6, -7.5); bench(8, -7.5); bench(22, -7.5);
function lampPost(x, z) { cyl(0.08, 0.1, 3.4, 0x222428, x, 1.7, z); box(0.4, 0.5, 0.4, 0xfff3c4, x, 3.5, z, scene, { emissive: 0xffe9a8, emissiveIntensity: 0.4 }); }
for (let x = -30; x <= 30; x += 15) lampPost(x, -8.6);
// jetty into the lake + boat
for (let i = 0; i < 10; i++) box(3, 0.15, 1.1, 0xa47648, 14, 0.4, -10 - i * 1.15);
for (let i = 0; i < 4; i++) { cyl(0.12, 0.12, 1.4, 0x6b4a2e, 12.6, 0.3, -11 - i * 3); cyl(0.12, 0.12, 1.4, 0x6b4a2e, 15.4, 0.3, -11 - i * 3); }
const boat = new THREE.Group(); boat.position.set(18, 0.2, -18); scene.add(boat);
box(1.4, 0.5, 3.4, 0xd23a3a, 0, 0, 0, boat); box(1.2, 0.1, 0.4, 0xe6d5b0, 0, 0.25, 0.4, boat);
// trees
function tree(x, z, s = 1) { cyl(0.2 * s, 0.25 * s, 2 * s, 0x6b4a2e, x, s, z); const t = new THREE.Mesh(new THREE.IcosahedronGeometry(1.4 * s, 0), mat(0x3f9a48)); t.position.set(x, 2.6 * s, z); t.castShadow = true; scene.add(t); }
// houses
const houseCols = [0x86b6c9, 0xd59a7a, 0x9cc79a, 0xe8d3a0, 0xb4a3d3, 0xe0a8a8, 0x9aa9d6, 0xf0e6d2];
function house(x, z, ry, w = 5, h = 6, col) {
  const g = new THREE.Group(); g.position.set(x, 0, z); g.rotation.y = ry; scene.add(g);
  col = col ?? houseCols[Math.floor(rnd() * houseCols.length)];
  box(w, h, 5, col, 0, h / 2, 0, g);
  const roof = new THREE.Mesh(new THREE.CylinderGeometry(0.01, 3.4, w + 0.4, 3), mat(0x7a4636)); roof.rotation.z = Math.PI / 2; roof.rotation.y = 0;
  roof.scale.set(1, 1, 0.9); roof.position.set(0, h + 0.9, 0); roof.castShadow = true; g.add(roof);
  box(0.6, 1.6, 0.6, 0x8a4a38, w * 0.3, h + 1.6, 0.5, g);
  const win = mat(0xfff3c8, { emissive: 0xffe2a0, emissiveIntensity: 0.15 });
  for (const wx of [-w * 0.25, w * 0.25]) { box(1, 1.2, 0.1, win, wx, h * 0.7, 2.52, g); box(1, 1.2, 0.1, win, wx, h * 0.3 + 0.4, 2.52, g); }
  box(1, 2, 0.1, 0x5a3b28, 0, 1, 2.53, g); return g;
}
// far shore row
for (let x = -60; x < 60; x += 5.4) house(x, -76, 0, 5, 5 + Math.floor(rnd() * 3));
for (let i = 0; i < 18; i++) tree(-60 + rnd() * 120, -71 + rnd() * 2, 0.9 + rnd() * 0.5);
// church spire far shore
box(3, 9, 3, 0xe9e4da, 22, 4.5, -73); const sp = new THREE.Mesh(new THREE.ConeGeometry(1.8, 6, 4), mat(0x5d6c80)); sp.position.set(22, 12, -73); scene.add(sp);
// near side: houses behind promenade (z=+14) facing lake
for (let x = -55; x < 55; x += 5.4) house(x, 16, Math.PI, 5, 6 + Math.floor(rnd() * 2));
for (let i = 0; i < 10; i++) tree(-50 + rnd() * 100, 9 + rnd() * 2, 1);
// street: road at x 80, along z
const road = new THREE.Mesh(new THREE.PlaneGeometry(10, 200), mat(0x45474d)); road.rotation.x = -Math.PI / 2; road.position.set(90, 0.03, 0); road.receiveShadow = true; scene.add(road);
for (let z = -95; z < 100; z += 8) box(0.3, 0.02, 3, 0xf2f2f2, 90, 0.05, z);
box(3, 0.2, 200, 0xbdbdbd, 83.5, 0.1, 0); box(3, 0.2, 200, 0xbdbdbd, 96.5, 0.1, 0);
for (let z = -90; z < 90; z += 5.4) { house(78, z, Math.PI / 2, 5, 6 + Math.floor(rnd() * 3)); house(102, z, -Math.PI / 2, 5, 6 + Math.floor(rnd() * 3)); }
// town square with clock tower at (0,0,60)
const plaza = new THREE.Mesh(new THREE.PlaneGeometry(40, 40), mat(0xc9c2b6)); plaza.rotation.x = -Math.PI / 2; plaza.position.set(0, 0.04, 62); plaza.receiveShadow = true; scene.add(plaza);
box(5, 15, 5, 0xc8b28e, 0, 7.5, 70); const troof = new THREE.Mesh(new THREE.ConeGeometry(4.2, 6, 4), mat(0x7a4636)); troof.rotation.y = Math.PI / 4; troof.position.set(0, 18, 70); troof.castShadow = true; scene.add(troof);
box(1.6, 3, 0.2, 0x5a3b28, 0, 1.5, 67.45);
const faceTex = canvasTex(256, 256, (g, w, h) => { g.fillStyle = '#f7f3e6'; g.beginPath(); g.arc(128, 128, 120, 0, 7); g.fill(); g.lineWidth = 14; g.strokeStyle = '#3a3026'; g.stroke();
  g.fillStyle = '#3a3026'; for (let k = 0; k < 12; k++) { const a = k / 12 * Math.PI * 2; g.fillRect(128 + 95 * Math.sin(a) - 5, 128 - 95 * Math.cos(a) - 5, 10, 10); } });
const clockFace = new THREE.Mesh(new THREE.CircleGeometry(2, 40), new THREE.MeshStandardMaterial({ map: faceTex, roughness: 0.6 })); clockFace.position.set(0, 11.5, 67.46); clockFace.rotation.y = Math.PI; scene.add(clockFace);
const hands = new THREE.Group(); hands.position.set(0, 11.5, 67.4); scene.add(hands);
const hourH = box(0.14, 1.1, 0.05, 0x1d1d1d, 0, 0.5, 0, hands); const minH = box(0.1, 1.6, 0.05, 0x1d1d1d, 0, 0.75, 0, hands);
const hourG = new THREE.Group(); const minG = new THREE.Group(); hands.add(hourG, minG); hourG.add(hourH); minG.add(minH); hands.rotation.y = Math.PI;
for (let x = -20; x <= 20; x += 5.4) house(x, 88, Math.PI, 5, 7);
for (let z = 48; z < 85; z += 5.4) { house(-24, z, Math.PI / 2, 5, 7); house(24, z, -Math.PI / 2, 5, 7); }
const fountain = cyl(2.2, 2.4, 0.7, 0xd8d2c6, -8, 0.35, 55); cyl(1.9, 1.9, 0.05, 0x58a6e6, -8, 0.72, 55);

// ---------- interiors (far away at x=-200 .. ) each room 12x12
function room(ox, oz, wall, floor, ceilingLight = true) {
  const g = new THREE.Group(); g.position.set(ox, 0, oz); scene.add(g);
  const f = new THREE.Mesh(new THREE.PlaneGeometry(14, 14), mat(floor)); f.rotation.x = -Math.PI / 2; f.receiveShadow = true; g.add(f);
  box(14, 7, 0.3, wall, 0, 3.5, -7, g); box(0.3, 7, 14, wall, -7, 3.5, 0, g); box(0.3, 7, 14, wall, 7, 3.5, 0, g);
  box(14, 0.3, 14, 0xf2f2f2, 0, 7, 0, g); return g;
}
const classroom = room(-200, 0, 0xe9e2cf, 0xa98463);
box(8, 3, 0.1, 0x2f5a46, 0, 3.6, -6.8, classroom); box(8.4, 0.2, 0.3, 0x8a6a48, 0, 2, -6.7, classroom);
const chalk = canvasTex(512, 192, (g) => { g.fillStyle = '#2f5a46'; g.fillRect(0, 0, 512, 192); g.fillStyle = '#e8eee6'; g.font = 'bold 60px sans-serif'; g.fillText('2 + 2 = 4', 40, 110); });
const board = new THREE.Mesh(new THREE.PlaneGeometry(7.6, 2.8), new THREE.MeshStandardMaterial({ map: chalk })); board.position.set(0, 3.6, -6.73); classroom.add(board);
for (let r = 0; r < 3; r++) for (let c = 0; c < 3; c++) { const x = -4 + c * 4, z = -1 + r * 3; box(2, 0.12, 1.2, 0xc79a5e, x, 1.4, z, classroom); for (const dx of [-0.9, 0.9]) box(0.1, 1.4, 0.1, 0x555555, x + dx, 0.7, z, classroom); }
for (let k = 0; k < 3; k++) box(0.1, 4, 3, 0x9fd0ff, -6.84, 3.5, -3 + k * 4, classroom, { emissive: 0xbfe3ff, emissiveIntensity: 0.6 });
const kitchen = room(-240, 0, 0xf1e3c8, 0xb98c64);
box(12, 2, 1.4, 0xffffff, 0, 1, -6, kitchen); box(12, 0.15, 1.5, 0x8e8e8e, 0, 2.05, -6, kitchen); box(4, 2, 0.1, 0xcfe9ff, 3, 4.5, -6.84, kitchen, { emissive: 0xcfe9ff, emissiveIntensity: 0.5 });
box(3.2, 0.15, 2, 0xa0683a, 0, 1.6, -1, kitchen); for (const dx of [-1.4, 1.4]) for (const dz of [-0.8, 0.8]) box(0.12, 1.6, 0.12, 0x6b4a2e, dx, 0.8, -1 + dz, kitchen);
const bedroom = room(-280, 0, 0x2e3a6e, 0x4a3a33);
box(3, 0.9, 5, 0x5c5ca0, -4, 0.45, -3.5, bedroom); box(3, 0.3, 1, 0xe8e8f0, -4, 1.05, -5.4, bedroom);
const winM = new THREE.Mesh(new THREE.PlaneGeometry(3.2, 2.6), new THREE.MeshBasicMaterial({ map: canvasTex(256, 208, (g) => { g.fillStyle = '#0d1840'; g.fillRect(0, 0, 256, 208); for (let i = 0; i < 40; i++) { g.fillStyle = '#fff'; g.fillRect(Math.random() * 256, Math.random() * 208, 2, 2); } g.fillStyle = '#f6f1cf'; g.beginPath(); g.arc(180, 60, 30, 0, 7); g.fill(); g.strokeStyle = '#5a4a3a'; g.lineWidth = 10; g.strokeRect(0, 0, 256, 208); g.beginPath(); g.moveTo(128, 0); g.lineTo(128, 208); g.moveTo(0, 104); g.lineTo(256, 104); g.stroke(); }) }));
winM.position.set(2, 4, -6.84); bedroom.add(winM);
const hallway = room(-320, 0, 0xd8d8dc, 0x9a9a9e);
for (const sx of [-6.8, 6.8]) for (let k = 0; k < 6; k++) box(0.4, 3.6, 1.1, 0x2e5f78, sx, 1.8, -5.5 + k * 1.2, hallway);

// ---------- characters
const CH = {
  me: [0x3c6ec8, 0x2a1e1e, 'short'], ivy: [0x2c5a46, 0x2d7a85, 'long'], mum: [0xc85070, 0x6e3c28, 'long'], dad: [0x5a785a, 0x3c2a1e, 'short'],
  grandad: [0x96785a, 0xeeeeee, 'short'], nan: [0xaa6eaa, 0xe4e4ea, 'bun'], mayor: [0x24243a, 0x50382f, 'short', 'tie'], teacher: [0x78503c, 0x1e1e1e, 'bun'],
  kid: [0xe6a028, 0x5a3c28, 'short'], girl: [0xdc5a5a, 0xc89640, 'long'], man: [0x50508c, 0x1e1e1e, 'short'], woman: [0x3c96a0, 0x8c3228, 'long'],
};
function faceTexture(mood = 'smile') {
  return canvasTex(128, 128, (g) => { g.fillStyle = '#f6cfa6'; g.fillRect(0, 0, 128, 128);
    const eye = (x, y, rx, ry) => { g.fillStyle = '#1b1b1b'; g.beginPath(); g.ellipse(x, y, rx, ry, 0, 0, 7); g.fill(); };
    const shine = (x, y, r) => { g.fillStyle = '#fff'; g.beginPath(); g.arc(x, y, r, 0, 7); g.fill(); };
    g.strokeStyle = '#3a2418'; g.lineCap = 'round'; g.lineWidth = 5;
    const line = (pts) => { g.beginPath(); g.moveTo(...pts[0]); for (const q of pts.slice(1)) g.lineTo(...q); g.stroke(); };
    if (mood === 'scared') {
      g.fillStyle = '#fff'; g.beginPath(); g.ellipse(42, 56, 14, 17, 0, 0, 7); g.ellipse(86, 56, 14, 17, 0, 0, 7); g.fill();
      g.lineWidth = 3; g.strokeStyle = '#1b1b1b'; g.stroke(); eye(42, 58, 4, 4); eye(86, 58, 4, 4);
      g.lineWidth = 5; g.strokeStyle = '#3a2418'; line([[28, 30], [54, 36]]); line([[100, 30], [74, 36]]);
      g.fillStyle = '#5a2020'; g.beginPath(); g.ellipse(64, 98, 10, 14, 0, 0, 7); g.fill();
      g.fillStyle = '#8fd0ff'; g.beginPath(); g.moveTo(108, 70); g.quadraticCurveTo(118, 88, 108, 92); g.quadraticCurveTo(98, 88, 108, 70); g.fill();
    } else if (mood === 'surprised') {
      eye(42, 56, 9, 12); eye(86, 56, 9, 12); shine(45, 51, 3); shine(89, 51, 3);
      line([[30, 30], [52, 28]]); line([[76, 28], [98, 30]]);
      g.fillStyle = '#5a2020'; g.beginPath(); g.ellipse(64, 94, 8, 10, 0, 0, 7); g.fill();
    } else if (mood === 'sad') {
      eye(42, 60, 7, 9); eye(86, 60, 7, 9); shine(44, 56, 3); shine(88, 56, 3);
      line([[30, 40], [52, 34]]); line([[76, 34], [98, 40]]);
      g.strokeStyle = '#5a2a2a'; g.beginPath(); g.arc(64, 104, 14, Math.PI + 0.4, -0.4); g.stroke();
      g.fillStyle = '#8fd0ff'; g.beginPath(); g.ellipse(40, 80, 4, 7, 0, 0, 7); g.fill();
    } else if (mood === 'laugh') {
      g.lineWidth = 6; g.beginPath(); g.arc(42, 60, 9, Math.PI + 0.3, -0.3); g.stroke(); g.beginPath(); g.arc(86, 60, 9, Math.PI + 0.3, -0.3); g.stroke();
      g.fillStyle = '#5a2020'; g.beginPath(); g.moveTo(42, 78); g.lineTo(86, 78); g.arc(64, 78, 22, 0, Math.PI); g.fill();
      g.fillStyle = '#e87a8a'; g.beginPath(); g.ellipse(64, 92, 10, 5, 0, 0, 7); g.fill();
    } else if (mood === 'creepy') {
      eye(42, 58, 7, 5); eye(86, 58, 7, 5); line([[30, 48], [54, 52]]); line([[98, 48], [74, 52]]);
      g.strokeStyle = '#5a2a2a'; g.beginPath(); g.arc(64, 66, 30, 0.35, Math.PI - 0.35); g.stroke();
    } else if (mood === 'neutral') {
      eye(42, 56, 7, 10); eye(86, 56, 7, 10); shine(44, 52, 3); shine(88, 52, 3); line([[52, 92], [76, 92]]);
    } else {
      eye(42, 56, 7, 11); eye(86, 56, 7, 11); shine(44, 52, 3); shine(88, 52, 3);
      g.strokeStyle = '#5a2a2a'; g.beginPath(); g.arc(64, 74, 16, 0.2, Math.PI - 0.2); g.stroke();
    } });
}
const FACES = {}; const face = (m) => FACES[m] || (FACES[m] = faceTexture(m));
const skinM = mat(0xf6cfa6), legM = mat(0x2c3050);
const people = {};
function makePerson(key) {
  const [shirt, hair, style, extra] = CH[key] || CH.man;
  const g = new THREE.Group(); scene.add(g);
  const sM = mat(shirt), hM = mat(hair);
  const hip = new THREE.Group(); hip.position.y = 1.0; g.add(hip);
  const legL = new THREE.Group(), legR = new THREE.Group(); legL.position.set(-0.25, 1.0, 0); legR.position.set(0.25, 1.0, 0); g.add(legL, legR);
  box(0.46, 1.0, 0.5, legM, 0, -0.5, 0, legL); box(0.46, 1.0, 0.5, legM, 0, -0.5, 0, legR);
  const torso = box(1.0, 1.0, 0.55, sM, 0, 0.5, 0, hip);
  if (extra === 'tie') box(0.14, 0.7, 0.02, 0xb02020, 0, 0.55, 0.29, hip);
  const armL = new THREE.Group(), armR = new THREE.Group(); armL.position.set(-0.74, 0.95, 0); armR.position.set(0.74, 0.95, 0); hip.add(armL, armR);
  box(0.46, 1.0, 0.5, sM, 0, -0.45, 0, armL); box(0.46, 1.0, 0.5, sM, 0, -0.45, 0, armR);
  box(0.42, 0.2, 0.44, skinM, 0, -1.02, 0, armL); box(0.42, 0.2, 0.44, skinM, 0, -1.02, 0, armR);
  const head = new THREE.Group(); head.position.y = 1.0; hip.add(head);
  const fm = new THREE.MeshStandardMaterial({ map: face('smile'), roughness: 0.8 });
  const hm = [skinM, skinM, skinM, skinM, fm, skinM];
  const hd = new THREE.Mesh(new THREE.BoxGeometry(0.8, 0.8, 0.8), hm); hd.position.y = 0.42; hd.castShadow = true; head.add(hd);
  box(0.86, 0.26, 0.86, hM, 0, 0.88, -0.02, head); box(0.86, 0.5, 0.2, hM, 0, 0.6, -0.36, head);
  if (style === 'long') { box(0.9, 1.1, 0.25, hM, 0, 0.25, -0.4, head); box(0.12, 0.9, 0.7, hM, -0.45, 0.35, -0.08, head); box(0.12, 0.9, 0.7, hM, 0.45, 0.35, -0.08, head); }
  if (style === 'bun') { const b = new THREE.Mesh(new THREE.SphereGeometry(0.22, 12, 10), hM); b.position.set(0, 1.08, -0.2); head.add(b); }
  return { g, hip, legL, legR, armL, armR, head, fm };
}
function person(key) { return people[key] || (people[key] = makePerson(key)); }

// ---------- props
const props = {};
function makeProp(kind) {
  const g = new THREE.Group(); scene.add(g);
  if (kind === 'cat') { const o = 0xf0963c; box(0.9, 0.5, 0.45, o, 0, 0.35, 0, g); box(0.45, 0.42, 0.42, o, 0.55, 0.65, 0, g); for (const dz of [-0.13, 0.13]) { const e = new THREE.Mesh(new THREE.ConeGeometry(0.09, 0.2, 4), mat(o)); e.position.set(0.55, 0.95, dz); g.add(e); }
    box(0.08, 0.08, 0.02, 0x111111, 0.78, 0.7, -0.1, g); box(0.08, 0.08, 0.02, 0x111111, 0.78, 0.7, 0.1, g).rotation.y = 0; box(0.12, 0.5, 0.12, o, -0.5, 0.65, 0, g).rotation.z = 0.5;
    for (const dx of [-0.3, 0.3]) for (const dz of [-0.15, 0.15]) box(0.12, 0.25, 0.12, o, dx, 0.1, dz, g); }
  else if (kind === 'cake') { cyl(0.6, 0.6, 0.5, 0xf2c8dc, 0, 0.25, 0, g); cyl(0.62, 0.62, 0.08, 0xffffff, 0, 0.52, 0, g); cyl(0.03, 0.03, 0.3, 0x78aaff, 0, 0.7, 0, g); const fl = new THREE.Mesh(new THREE.SphereGeometry(0.06, 8, 8), new THREE.MeshBasicMaterial({ color: 0xffc040 })); fl.position.y = 0.9; g.add(fl); }
  else if (kind === 'letter') { box(1.0, 0.65, 0.04, 0xf7f1df, 0, 0.5, 0, g); box(0.18, 0.18, 0.05, 0xc0283c, 0, 0.5, 0.02, g); }
  else if (kind === 'fish') { const b = new THREE.Mesh(new THREE.SphereGeometry(0.3, 14, 10), mat(0xff962a)); b.scale.set(1.6, 1, 0.6); b.position.y = 0.5; g.add(b); const t = new THREE.Mesh(new THREE.ConeGeometry(0.25, 0.4, 3), mat(0xff962a)); t.rotation.z = Math.PI / 2; t.position.set(-0.6, 0.5, 0); g.add(t); }
  else if (kind === 'clock') { const f = new THREE.Mesh(new THREE.CircleGeometry(0.6, 32), new THREE.MeshStandardMaterial({ map: faceTex })); f.position.y = 1; g.add(f); cyl(0.65, 0.65, 0.1, 0x6b4a2e, 0, 1, -0.06, g).rotation.x = Math.PI / 2; }
  else if (kind === 'key') { const r = new THREE.Mesh(new THREE.TorusGeometry(0.2, 0.06, 8, 20), mat(0xe6be3c, { metalness: 0.7, roughness: 0.3 })); r.position.y = 0.8; g.add(r); box(0.6, 0.08, 0.08, 0xe6be3c, 0.48, 0.8, 0, g, { metalness: 0.7, roughness: 0.3 }); }
  else if (kind === 'book') { box(0.8, 0.15, 1.0, 0x963232, 0, 0.4, 0, g); box(0.76, 0.12, 0.96, 0xf4f0e0, 0.02, 0.4, 0, g); }
  else if (kind === 'phone') { box(0.4, 0.8, 0.06, 0x1e1e22, 0, 0.8, 0, g); box(0.34, 0.68, 0.01, 0x78c8ff, 0, 0.8, 0.04, g, { emissive: 0x78c8ff, emissiveIntensity: 0.8 }); }
  else if (kind === 'ring') { const r = new THREE.Mesh(new THREE.TorusGeometry(0.25, 0.06, 12, 24), mat(0xf0c846, { metalness: 0.8, roughness: 0.25 })); r.position.y = 0.8; g.add(r); }
  else if (kind === 'bike') { for (const dx of [-0.7, 0.7]) { const w = new THREE.Mesh(new THREE.TorusGeometry(0.45, 0.05, 8, 24), mat(0x1e1e1e)); w.position.set(dx, 0.5, 0); g.add(w); } box(1.4, 0.08, 0.08, 0x2878dc, 0, 0.95, 0, g); }
  else if (kind === 'moon') { const m = new THREE.Mesh(new THREE.SphereGeometry(0.6, 20, 16), new THREE.MeshBasicMaterial({ color: 0xf7f1cf })); m.position.y = 1.2; g.add(m); }
  else if (kind === 'umbrella') { const u = new THREE.Mesh(new THREE.SphereGeometry(0.9, 16, 8, 0, Math.PI * 2, 0, Math.PI / 2), mat(0xdc3c3c)); u.position.y = 1.8; g.add(u); cyl(0.03, 0.03, 1.8, 0x333333, 0, 0.9, 0, g); }
  else if (kind === 'door') { box(1.3, 2.4, 0.15, 0x7a5032, 0, 1.2, 0, g); }
  else { const s = new THREE.Mesh(new THREE.SphereGeometry(0.4, 16, 12), new THREE.MeshBasicMaterial({ color: 0xfff0b0 })); s.position.y = 1; g.add(s); const l = new THREE.PointLight(0xffe8a0, 6, 6); l.position.y = 1; g.add(l); }
  return g;
}
function prop(kind) { return props[kind] || (props[kind] = makeProp(kind)); }

const birds = [];
for (let k = 0; k < 4; k++) { const bd = new THREE.Group(); scene.add(bd); box(0.5, 0.18, 0.2, 0x333340, 0, 0, 0, bd);
  const wl = new THREE.Group(); wl.position.set(0, 0.05, 0.1); bd.add(wl); box(0.3, 0.04, 0.6, 0x444455, 0, 0, 0.3, wl);
  const wr = new THREE.Group(); wr.position.set(0, 0.05, -0.1); bd.add(wr); box(0.3, 0.04, 0.6, 0x444455, 0, 0, -0.3, wr); birds.push(bd); }
// ---------- stages: where characters stand + camera base (pos, look)
const STAGES = {
  lake:      { at: [0, 0, -5.2], face: 0, cam: [0, 2.6, 7.5], look: [0, 1.5, -6], out: true },
  jetty:     { at: [14, 0.45, -14], face: 0, cam: [14, 2.9, -3.5], look: [14, 1.6, -16], out: true },
  square:    { at: [0, 0, 57], face: Math.PI, cam: [0, 2.4, 45], look: [0, 4.6, 62], out: true },
  street:    { at: [90, 0, 0], face: -Math.PI / 2, cam: [80.5, 2.6, 0], look: [92, 1.6, 0], out: true },
  classroom: { at: [-200, 0, 1.5], face: 0, cam: [-200, 2.6, 10.5], look: [-200, 2.0, -2] },
  kitchen:   { at: [-240, 0, 1], face: 0, cam: [-240, 2.6, 10], look: [-240, 1.9, -2] },
  bedroom:   { at: [-280, 0, 1], face: 0, cam: [-279, 2.5, 10], look: [-280, 2.0, -2], night: true },
  hallway:   { at: [-320, 0, 0], face: 0, cam: [-320, 2.4, 9.5], look: [-320, 1.9, -4] },
};

let STORY = null, BEATS = null, PROPS_ON = [];
window.setup = (story, beats) => { STORY = story; BEATS = beats; for (const b of story.beats) { (b.who || []).forEach(person); if (b.prop) prop(b.prop); } return true; };

const ease = (x) => x < 0 ? 0 : x > 1 ? 1 : 0.5 - 0.5 * Math.cos(Math.PI * x);
window.renderAt = (t) => {
  let bi = BEATS.length - 1; for (let i = 0; i < BEATS.length; i++) if (t >= BEATS[i].start && t < BEATS[i].end) { bi = i; break; }
  const B = STORY.beats[bi], bt = BEATS[bi]; const lt = t - bt.start, p = ease(Math.min(1, lt / Math.max(0.1, bt.end - bt.start)));
  const st = STAGES[B.scene] || STAGES.lake;
  // sky / light
  const night = !!st.night || !!B.night;
  scene.background = night ? NIGHT : DAY; scene.fog.color.copy(night ? NIGHT : new THREE.Color(0xbfe0f7));
  hemi.intensity = night ? 0.35 : 1.6; sun.intensity = night ? 0.25 : 2.8; sun.color.set(night ? 0x8090ff : 0xfff1d8);
  const [ax, ay, az] = st.at; { const cb = new THREE.Vector3(...st.cam).sub(new THREE.Vector3(...st.look)).setY(0).normalize();
    sun.position.set(ax + cb.x * 30 + cb.z * 18, 55, az + cb.z * 30 - cb.x * 18); sun.target.position.set(ax, 0, az); }
  lamp.intensity = st.out ? 0 : (night ? 25 : 60); lamp.position.set(ax + 1, 6, az + 2);
  // people: mood + pose (beat keys or guessed from the caption), freeze when time stops
  const freeze = !!(B.clock_stopped || B.freeze); const tf = freeze ? bt.start + 0.37 : t;
  const txt = (B.text || '').toLowerCase();
  const auto = (k, i) => {
    if (/scared|afraid|terrified|scream|shaking|couldn't sleep|breathing|run!|ran /.test(txt)) return i === 0 ? ['scared', 'scared'] : ['surprised', 'idle'];
    if (/laugh/.test(txt)) return ['laugh', 'laugh'];
    if (/cried|crying|tears|sad|alone/.test(txt)) return ['sad', 'idle'];
    if (/whisper|creepy|stared|smiled\./.test(txt)) return i === 0 && n > 1 ? ['surprised', 'idle'] : ['creepy', 'idle'];
    if (/suddenly|gasp|shock|\?|!$|vanish|gone/.test(txt)) return ['surprised', i === 0 ? 'point' : 'idle'];
    if (/smile|happy|love|hug/.test(txt)) return ['smile', 'wave'];
    return ['smile', 'idle'];
  };
  const pick = (v, k, i) => v == null ? null : (typeof v === 'string' ? v : (v[k] ?? null));
  for (const k in people) people[k].g.visible = false;
  const who = B.who || []; const n = who.length; const sp = n > 2 ? 1.7 : 2.0;
  const right = new THREE.Vector3(Math.cos(st.face), 0, -Math.sin(st.face));
  const fwd0 = new THREE.Vector3(Math.sin(st.face), 0, Math.cos(st.face));
  const shift = ((bi * 37) % 3 - 1) * 0.35, depth = ((bi * 53) % 3 - 1) * 0.6;   // vary positions per beat
  who.forEach((k, i) => {
    const P = person(k); P.g.visible = true;
    const still = freeze && !(B.freeze_except || []).includes(k);
    const at = still ? tf : t;
    const [am, ap] = auto(k, i);
    const mood = pick(B.mood, k, i) || am, pose = pick(B.pose, k, i) || ap;
    P.fm.map = face(mood);
    let off = (i - (n - 1) / 2) * sp + (n < 3 ? shift : 0);
    let dz = depth + (n === 2 ? (i ? 0.4 : -0.3) : 0);
    let rot = st.face + (n === 2 ? (i === 0 ? 0.5 : -0.5) : ((bi + i) % 3 - 1) * 0.25);
    const ph = at * 2 + i * 1.7;
    P.hip.position.y = 1.0 + Math.sin(ph * 1.1) * 0.02; P.hip.rotation.set(0, 0, 0);
    P.armL.rotation.set(Math.sin(ph) * 0.08, 0, -0.05); P.armR.rotation.set(-Math.sin(ph) * 0.08, 0, 0.05);
    P.legL.rotation.set(0, 0, 0); P.legR.rotation.set(0, 0, 0);
    P.head.rotation.set(Math.sin(at * 0.7 + i) * 0.05, Math.sin(at * 0.9 + i * 2) * 0.18, 0);
    const talk = (i === (bi % Math.max(1, n)));
    if (pose === 'idle' && talk) P.armR.rotation.x = -0.35 + Math.sin(at * 5) * 0.25;
    if (pose === 'walk') {
      const dir = (bi % 2) ? 1 : -1; const wp = still ? 0.5 : (at - bt.start) / Math.max(0.5, bt.end - bt.start); off = off * (n > 2 ? 0.8 : 1) + dir * (-1.5 + 3 * wp);
      rot = st.face + dir * Math.PI / 2 * 0.8;
      const sw = Math.sin(at * 7 + i) * 0.6; P.legL.rotation.x = sw; P.legR.rotation.x = -sw; P.armL.rotation.x = -sw * 0.8; P.armR.rotation.x = sw * 0.8;
      P.hip.position.y = 1.0 + Math.abs(Math.cos(at * 7 + i)) * 0.06;
    } else if (pose === 'scared') {
      const back = Math.min(1, (at - bt.start) / 0.5);
      dz -= 0.8 * back; P.hip.rotation.x = -0.18 * back;
      P.armL.rotation.set(-2.3, 0, -0.35); P.armR.rotation.set(-2.3, 0, 0.35);
      off += Math.sin(at * 45) * 0.03; P.head.rotation.x = -0.12; P.head.rotation.z = Math.sin(at * 40) * 0.03;
    } else if (pose === 'laugh') {
      P.hip.position.y = 1.0 + Math.abs(Math.sin(at * 9)) * 0.08; P.head.rotation.x = -0.3 + Math.sin(at * 9) * 0.08;
      P.armL.rotation.set(-0.5, 0, -0.6); P.armR.rotation.set(-0.5, 0, 0.6);
    } else if (pose === 'point') {
      P.armR.rotation.set(-1.55, 0, 0.1);
    } else if (pose === 'wave') {
      P.armR.rotation.set(0, 0, 2.6 + Math.sin(at * 8) * 0.35);
    } else if (pose === 'shrug') {
      P.armL.rotation.set(-0.4, 0, -0.7); P.armR.rotation.set(-0.4, 0, 0.7); P.head.rotation.z = 0.2;
    } else if (pose === 'hug' && n === 2) {
      off *= 0.45; P.armL.rotation.set(-1.4, 0, 0.5); P.armR.rotation.set(-1.4, 0, -0.5);
    }
    if (pose !== 'walk') rot += still ? 0 : Math.sin(at * 0.6 + i) * 0.05;
    P.g.position.set(ax + right.x * off + fwd0.x * dz, ay, az + right.z * off + fwd0.z * dz);
    P.g.rotation.y = rot;
  });
  // birds (outdoors): freeze mid-air when time stops
  birds.forEach((bd, k) => { bd.visible = !!st.out && !night;
    const u = ((tf * 1.6 + k * 4.3) % 22) - 11; bd.position.set(ax + right.x * u + fwd0.x * (-4 - k), 7 + k * 0.9 + Math.sin(tf * 2 + k) * 0.3, az + right.z * u + fwd0.z * (-4 - k));
    bd.rotation.y = st.face + Math.PI / 2; const fl = Math.sin(tf * 14 + k) * 0.7; bd.children[1].rotation.x = fl; bd.children[2].rotation.x = -fl; });
  // props
  for (const k in props) props[k].visible = false;
  if (B.prop) { const G = prop(B.prop); G.visible = true;
    const off = n ? (n - 1) / 2 * sp + 1.6 : 0; const fw = new THREE.Vector3(Math.sin(st.face), 0, Math.cos(st.face)); G.position.set(ax + right.x * off + fw.x * (n ? 0.8 : 1.2), ay + (n ? 0.6 : 0.4) + Math.sin(tf * 2) * 0.08, az + right.z * off + fw.z * (n ? 0.8 : 1.2));
    G.rotation.y = st.face + Math.sin(tf) * 0.3; G.scale.setScalar(n ? 1.0 : 1.5); }
  // clock: runs unless beat says stopped
  const ct = freeze ? 2.0 : t;
  minG.rotation.z = -ct * 0.6; hourG.rotation.z = -ct * 0.05 - 1.2;
  // lake waves
  const pa = lakeGeo.attributes.position.array;
  for (let i = 0; i < pa.length; i += 3) pa[i + 2] = Math.sin(lakeBase[i] * 0.6 + tf * 1.6) * 0.06 + Math.cos(lakeBase[i + 1] * 0.8 + tf * 1.2) * 0.05;
  lakeGeo.attributes.position.needsUpdate = true; boat.position.y = 0.2 + Math.sin(tf * 1.3) * 0.08; boat.rotation.z = Math.sin(tf) * 0.05;
  // camera move
  const cam = new THREE.Vector3(...st.cam), look = new THREE.Vector3(...st.look);
  const fwd = look.clone().sub(cam).normalize(); const side = new THREE.Vector3().crossVectors(fwd, new THREE.Vector3(0, 1, 0)).normalize();
  const mode = B.cam || ['in', 'out', 'left', 'right'][bi % 4];
  let c = cam.clone(), lk = look.clone();
  if (n === 0 && B.scene === 'square') { lk.set(0, 9, 67); }
  if (mode === 'in') c.addScaledVector(fwd, 2.2 * p);
  else if (mode === 'out') c.addScaledVector(fwd, 2.2 * (1 - p));
  else if (mode === 'left') { c.addScaledVector(side, -1.6 + 3.2 * p); }
  else if (mode === 'right') { c.addScaledVector(side, 1.6 - 3.2 * p); }
  else if (mode === 'up') { lk.y += 8 * p; }
  if (mode === 'close' && n) { const P = person(B.focus || who[0]); const hp = new THREE.Vector3(); P.head.getWorldPosition(hp); c = hp.clone().addScaledVector(new THREE.Vector3(Math.sin(st.face), 0, Math.cos(st.face)), 4.2 - 0.8 * p); c.y += 0.3; lk = hp.clone(); lk.y += 0.3; }
  if (t === 0) { /* cover frame */ }
  camera.position.copy(c); camera.lookAt(lk);
  renderer.render(scene, camera);
  return bi;
};
window.ready = true;
