// 2.5D 시험판(public/play/game3d.js)을 Three.js와 함께 한 파일로 묶어 줄입니다 → dist/play/game3d.js
import { build } from 'esbuild';
await build({
  entryPoints: ['public/play/game3d.js'],
  outfile: 'dist/play/game3d.js',
  bundle: true, minify: true, format: 'esm', target: 'es2020',
  legalComments: 'eof', logLevel: 'warning',
});
