// 「갈매미맨 수비대」 소리: 음악 파일 없이 코드로 만든 8비트 배경음악·효과음(웹 오디오). 저작권 걱정 없음
// - 브라우저·앱은 사람이 한 번 누르기 전에는 소리를 못 내서, 첫 터치 때 켜짐
// - 🔊 음소거는 이 기기에 저장
// - 나중에 진짜 음악 파일로 바꾸려면 bgm()에서 <audio>로 틀면 됨
// - 음량: 배경음악·효과음 따로 0~100(이 기기에 저장, ⚙️ 설정에서 조절). v0.9.99부터 기본 음량을 키우고 소리가 깨지지 않게 압축기를 거침
const MUTE_KEY = 'gmm-mute', VOL_KEY = { music: 'gmm-vol-music', sfx: 'gmm-vol-sfx' }, VOL_DEF = { music: 70, sfx: 80 };
let ctx = null, master = null, music = null, sfxBus = null, cur = null, want = null, timer = null, step = 0, nextAt = 0;
let muted = (() => { try { return localStorage.getItem(MUTE_KEY) === '1'; } catch { return false; } })();
const vol = { music: VOL_DEF.music, sfx: VOL_DEF.sfx };
for (const k of ['music', 'sfx']) try { const v = localStorage.getItem(VOL_KEY[k]); if (v !== null && !isNaN(+v)) vol[k] = Math.max(0, Math.min(100, +v)); } catch { /* 무시 */ }
const gainOf = k => (k === 'music' ? 0.65 : 0.9) * (vol[k] / 100) ** 1.5;   // 귀에 고르게 들리도록 곡선

const hz = m => 440 * 2 ** ((m - 69) / 12);   // 미디 음 번호 → 주파수
function init() {
  if (ctx) return;
  const AC = window.AudioContext || window.webkitAudioContext;
  if (!AC) return;
  ctx = new AC();
  const comp = ctx.createDynamicsCompressor(); comp.threshold.value = -14; comp.knee.value = 10; comp.ratio.value = 4; comp.connect(ctx.destination);
  master = ctx.createGain(); master.gain.value = muted ? 0 : 1; master.connect(comp);
  music = ctx.createGain(); music.gain.value = gainOf('music'); music.connect(master);
  sfxBus = ctx.createGain(); sfxBus.gain.value = gainOf('sfx'); sfxBus.connect(master);
}
function tone(f, t, dur, { type = 'square', vol = 0.18, to = sfxBus, slide = 0, attack = 0.008 } = {}) {
  const o = ctx.createOscillator(), g = ctx.createGain();
  o.type = type; o.frequency.setValueAtTime(f, t);
  if (slide) o.frequency.exponentialRampToValueAtTime(Math.max(30, f * slide), t + dur);
  g.gain.setValueAtTime(0.0001, t); g.gain.linearRampToValueAtTime(vol, t + attack); g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  o.connect(g); g.connect(to); o.start(t); o.stop(t + dur + 0.03);
}
let noiseBuf = null;
function noise(t, dur, { vol = 0.2, freq = 1800, to = sfxBus } = {}) {
  if (!noiseBuf) { noiseBuf = ctx.createBuffer(1, ctx.sampleRate * 0.5, ctx.sampleRate); const d = noiseBuf.getChannelData(0); for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1; }
  const s = ctx.createBufferSource(), f = ctx.createBiquadFilter(), g = ctx.createGain();
  s.buffer = noiseBuf; f.type = 'bandpass'; f.frequency.value = freq; f.Q.value = 0.9;
  g.gain.setValueAtTime(vol, t); g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  s.connect(f); f.connect(g); g.connect(to); s.start(t); s.stop(t + dur + 0.02);
}

// ── 배경음악: 8분음표 칸마다 [멜로디, 베이스] (0 = 쉼). 칸 수만큼 돌고 다시 처음부터 ──
const SONGS = {
  // 첫 화면·스토리 목록: 밝은 항구(다장조)
  title: { bpm: 104, wave: 'square', bass: 'triangle', drum: false,
    lead: [76, 0, 79, 0, 81, 79, 76, 74, 72, 0, 74, 76, 79, 0, 0, 0, 77, 0, 76, 74, 72, 74, 76, 0, 74, 0, 72, 0, 67, 0, 0, 0],
    low:  [48, 0, 55, 0, 48, 0, 55, 0, 45, 0, 52, 0, 45, 0, 52, 0, 41, 0, 48, 0, 41, 0, 48, 0, 43, 0, 50, 0, 43, 0, 47, 0] },
  // 대사: 잔잔하게(바장조, 느리게)
  story: { bpm: 84, wave: 'triangle', bass: 'triangle', drum: false,
    lead: [72, 0, 0, 74, 76, 0, 72, 0, 69, 0, 0, 0, 70, 0, 72, 0, 74, 0, 0, 72, 70, 0, 69, 0, 67, 0, 0, 0, 0, 0, 0, 0],
    low:  [41, 0, 48, 0, 53, 0, 48, 0, 38, 0, 45, 0, 50, 0, 45, 0, 46, 0, 53, 0, 58, 0, 53, 0, 48, 0, 55, 0, 52, 0, 48, 0] },
  // 전투: 빠르고 힘차게(가단조)
  battle: { bpm: 148, wave: 'square', bass: 'sawtooth', drum: true,
    lead: [69, 0, 72, 69, 76, 0, 74, 72, 71, 0, 72, 74, 76, 0, 0, 0, 69, 0, 72, 69, 77, 0, 76, 74, 72, 0, 71, 69, 68, 0, 71, 0],
    low:  [45, 45, 57, 45, 45, 45, 57, 45, 41, 41, 53, 41, 41, 41, 53, 41, 43, 43, 55, 43, 43, 43, 55, 43, 40, 40, 52, 40, 40, 40, 52, 40] },
  // 보스: 낮고 무겁게(라단조 반음 내림)
  boss: { bpm: 132, wave: 'sawtooth', bass: 'square', drum: true,
    lead: [62, 0, 0, 63, 62, 0, 58, 0, 61, 0, 0, 62, 61, 0, 57, 0, 62, 0, 65, 0, 67, 66, 65, 63, 62, 0, 61, 0, 57, 0, 0, 0],
    low:  [38, 38, 50, 38, 38, 38, 50, 38, 37, 37, 49, 37, 37, 37, 49, 37, 34, 34, 46, 34, 34, 34, 46, 34, 33, 33, 45, 33, 36, 36, 45, 40] },
};
function tick() {
  const song = SONGS[cur];
  if (!song || !ctx) return;
  const eighth = 60 / song.bpm / 2;
  while (nextAt < ctx.currentTime + 0.12) {
    const i = step % song.lead.length, m = song.lead[i], b = song.low[i];
    if (m) tone(hz(m), nextAt, eighth * 1.6, { type: song.wave, vol: 0.12, to: music });
    if (b) tone(hz(b), nextAt, eighth * 0.95, { type: song.bass, vol: 0.16, to: music });
    if (song.drum) { if (i % 4 === 0) tone(110, nextAt, 0.12, { type: 'sine', vol: 0.35, to: music, slide: 0.35 }); if (i % 4 === 2) noise(nextAt, 0.08, { vol: 0.12, freq: 2600, to: music }); if (i % 2 === 1) noise(nextAt, 0.03, { vol: 0.05, freq: 7000, to: music }); }
    nextAt += eighth; step++;
  }
}
export function bgm(name) {
  want = name;
  if (!ctx || ctx.state !== 'running') return;   // 첫 터치 뒤에 시작
  if (name === cur) return;
  clearInterval(timer); timer = null; cur = name; step = 0; nextAt = ctx.currentTime + 0.05;
  if (name && SONGS[name]) timer = setInterval(tick, 30);
}

// ── 효과음 ──
const FX = {
  tap: t => tone(880, t, 0.05, { vol: 0.06 }),
  hit: t => { noise(t, 0.12, { vol: 0.28, freq: 1200 }); tone(220, t, 0.1, { vol: 0.12, slide: 0.5 }); },
  crit: t => { noise(t, 0.18, { vol: 0.35, freq: 900 }); tone(660, t, 0.08, { vol: 0.15 }); tone(990, t + 0.06, 0.12, { vol: 0.15 }); },
  big: t => { noise(t, 0.2, { vol: 0.32, freq: 700 }); tone(330, t, 0.16, { vol: 0.15, slide: 1.6 }); },
  weak: t => { noise(t, 0.08, { vol: 0.14, freq: 2400 }); },
  faint: t => { tone(523, t, 0.18, { type: 'triangle', vol: 0.2 }); tone(392, t + 0.16, 0.18, { type: 'triangle', vol: 0.2 }); tone(262, t + 0.32, 0.5, { type: 'triangle', vol: 0.22, slide: 0.6 }); },
  heal: t => [72, 76, 79, 84].forEach((m, i) => tone(hz(m), t + i * 0.07, 0.18, { type: 'triangle', vol: 0.16 })),
  aug: t => [67, 71, 74].forEach((m, i) => tone(hz(m), t + i * 0.06, 0.14, { vol: 0.1 })),
  combo: t => { [60, 64, 67, 72, 76].forEach((m, i) => tone(hz(m), t + i * 0.05, 0.3, { vol: 0.12 })); noise(t + 0.25, 0.3, { vol: 0.15, freq: 1500 }); },
  win: t => [[72, 0], [76, 0.12], [79, 0.24], [84, 0.36], [79, 0.5], [84, 0.62]].forEach(([m, d]) => tone(hz(m), t + d, d === 0.62 ? 0.6 : 0.14, { vol: 0.16 })),
  lose: t => [[67, 0], [63, 0.22], [60, 0.44], [55, 0.66]].forEach(([m, d]) => tone(hz(m), t + d, d === 0.66 ? 0.7 : 0.22, { type: 'triangle', vol: 0.18 })),
  levelup: t => [[72, 0], [79, 0.08], [84, 0.16]].forEach(([m, d]) => tone(hz(m), t + d, 0.2, { vol: 0.14 })),
};
export function sfx(name) {
  if (!ctx || ctx.state !== 'running' || muted || !FX[name]) return;
  FX[name](ctx.currentTime + 0.005);
}
export const isMuted = () => muted;
export const getVol = k => vol[k];
export function setVol(k, v) {
  vol[k] = Math.max(0, Math.min(100, Math.round(v)));
  try { localStorage.setItem(VOL_KEY[k], String(vol[k])); } catch { /* 무시 */ }
  init();
  const node = k === 'music' ? music : sfxBus;
  if (node) node.gain.setTargetAtTime(gainOf(k), ctx.currentTime, 0.03);
}
export function toggleMute() {
  muted = !muted;
  try { localStorage.setItem(MUTE_KEY, muted ? '1' : '0'); } catch { /* 무시 */ }
  init();
  if (master) master.gain.setTargetAtTime(muted ? 0 : 1, ctx.currentTime, 0.05);
  return muted;
}
// 첫 터치에 켜기, 앱을 내리면 멈춤
addEventListener('pointerdown', () => {
  init();
  if (!ctx) return;
  const go = () => { const w = want; cur = null; bgm(w); };
  if (ctx.state === 'suspended') ctx.resume().then(go); else if (cur !== want) go();
}, { passive: true });
document.addEventListener('visibilitychange', () => {
  if (!ctx) return;
  if (document.hidden) ctx.suspend(); else ctx.resume();
});
