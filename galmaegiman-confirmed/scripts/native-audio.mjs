// 웹 원곡을 APK 로컬 PCM으로 준비한다. 사용: node scripts/native-audio.mjs [--check]
// 기존 title/battle/boss.wav는 유지하고 누락된 story.wav만 생성·검증한다.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { runInNewContext } from 'node:vm';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const output = resolve(root, 'app/android/app/src/main/assets/native/story.wav');

export function storyScore() {
  const source = readFileSync(resolve(root, 'public/duel/sound.js'), 'utf8');
  const start = source.indexOf('const CH = '), end = source.indexOf('function tick()');
  if (start < 0 || end <= start) throw new Error('웹 음악의 원곡 구간을 찾지 못했어요.');
  // 로컬 저장소의 순수 악보 정의만 읽는다. DOM·네트워크·웹 AudioContext는 실행하지 않는다.
  const song = runInNewContext(`${source.slice(start, end)}\nSONGS.story`, {}, { timeout: 1000 });
  if (song.bpm !== 80 || song.lead.length !== 64 || song.low.length !== 64 || song.arp.length !== 64)
    throw new Error('스토리 악보 형식이 바뀌었어요. 네이티브 음악 생성을 확인해 주세요.');
  return song;
}

export function renderSong(song, sampleRate = 16000) {
  const eighth = 60 / song.bpm / 2, count = Math.round(eighth * song.lead.length * sampleRate);
  const samples = new Float64Array(count);
  const hz = note => 440 * 2 ** ((note - 69) / 12);
  function tone(note, step, duration, wave, volume) {
    if (!note) return;
    const frequency = hz(note), start = Math.round(step * eighth * sampleRate), length = Math.round(duration * sampleRate);
    const harmonics = Math.min(23, Math.floor(sampleRate / 2 / frequency));
    for (let i = 0; i < length; i++) {
      const time = i / sampleRate, phase = frequency * time * Math.PI * 2;
      const envelope = time < .008 ? .0001 + (volume - .0001) * time / .008
        : volume * (.0001 / volume) ** ((time - .008) / Math.max(.001, duration - .008));
      let value = 0;
      if (wave === 'sine') value = Math.sin(phase);
      else if (wave === 'sawtooth') {
        for (let harmonic = 1; harmonic <= harmonics; harmonic++) value += (harmonic % 2 ? 1 : -1) * Math.sin(phase * harmonic) / harmonic;
        value *= 2 / Math.PI;
      } else {
        for (let harmonic = 1; harmonic <= harmonics; harmonic += 2)
          value += Math.sin(phase * harmonic) * (wave === 'triangle'
            ? (harmonic % 4 === 1 ? 1 : -1) / (harmonic * harmonic) : 1 / harmonic);
        value *= wave === 'triangle' ? 8 / (Math.PI * Math.PI) : 4 / Math.PI;
      }
      samples[(start + i) % count] += value * envelope;
    }
  }
  for (let step = 0; step < song.lead.length; step++) {
    tone(song.lead[step], step, eighth * 1.6, song.wave, song.leadVol);
    tone(song.low[step], step, eighth * .95, song.bass, .16);
    tone(song.arp[step], step, eighth * .9, 'triangle', .05);
  }
  const wav = Buffer.alloc(44 + count * 2);
  wav.write('RIFF'); wav.writeUInt32LE(wav.length - 8, 4); wav.write('WAVEfmt ', 8);
  wav.writeUInt32LE(16, 16); wav.writeUInt16LE(1, 20); wav.writeUInt16LE(1, 22);
  wav.writeUInt32LE(sampleRate, 24); wav.writeUInt32LE(sampleRate * 2, 28);
  wav.writeUInt16LE(2, 32); wav.writeUInt16LE(16, 34); wav.write('data', 36); wav.writeUInt32LE(count * 2, 40);
  for (let i = 0; i < count; i++) {
    let value = samples[i], peak = Math.abs(value);
    if (peak > .6) value = Math.sign(value) * (.6 + .35 * (1 - Math.exp(-(peak - .6) / .35)));
    wav.writeInt16LE(Math.round(Math.max(-1, Math.min(1, value)) * 32767), 44 + i * 2);
  }
  return wav;
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  const wav = renderSong(storyScore());
  if (process.argv.includes('--check')) {
    if (!readFileSync(output).equals(wav)) throw new Error('story.wav가 웹 원곡과 달라요. node scripts/native-audio.mjs로 다시 생성해 주세요.');
    console.log(`스토리 음악 검증 완료 · 24초 · ${wav.length.toLocaleString('ko-KR')}바이트`);
  } else {
    mkdirSync(dirname(output), { recursive: true }); writeFileSync(output, wav);
    console.log(`스토리 음악 준비 완료 · ${output}`);
  }
}
