import {readdirSync,readFileSync,writeFileSync,mkdirSync,existsSync} from 'node:fs';
import {resolve,join,relative,basename} from 'node:path';
import {createHash} from 'node:crypto';
import {spawnSync} from 'node:child_process';
const root=process.cwd();
if(basename(root)!=='galmaegiman-confirmed')throw new Error('프로젝트 폴더에서 실행하세요.');
const dirs=['src','data','prisma','public','scripts','test','docs'];
const files=['package.json','package-lock.json','tsconfig.json','.env.example','.gitignore','README.md'];
function walk(path){
  for(const entry of readdirSync(path,{withFileTypes:true})){
    const file=join(path,entry.name);
    if(entry.isSymbolicLink())throw new Error('심볼릭 링크는 배포하지 않습니다.');
    if(entry.isDirectory())walk(file);else files.push(relative(root,file));
  }
}
dirs.forEach(d=>walk(join(root,d)));
if(files.some(f=>/\.db(?:-|$)|\.env$|(?:^|\/)(node_modules|backups|storage|config)\//.test(f)))throw new Error('비공개 파일 포함 차단');
const manifest=files.filter(f=>f!=='docs/package-checksums.sha256').sort().map(f=>`${createHash('sha256').update(readFileSync(f)).digest('hex')}  ${f}`).join('\n')+'\n';
writeFileSync('docs/package-checksums.sha256',manifest);
if(!files.includes('docs/package-checksums.sha256'))files.push('docs/package-checksums.sha256');
const out=resolve(root,'../outputs');mkdirSync(out,{recursive:true});
const version=JSON.parse(readFileSync('package.json','utf8')).version;
const archive=join(out,`galmaegiman-random-75-tablet-v${version}.zip`);
if(existsSync(archive))throw new Error('기존 ZIP을 덮어쓰지 않습니다. 버전/출력 경로를 확인하세요.');
const result=spawnSync('zip',['-q','-1',archive,'-@'],{cwd:resolve(root,'..'),input:files.map(f=>`galmaegiman-confirmed/${f}`).join('\n')+'\n',encoding:'utf8'});
if(result.status!==0)throw new Error(result.stderr);
const check=spawnSync('unzip',['-tq',archive],{encoding:'utf8'});
if(check.status!==0)throw new Error(check.stdout+check.stderr);
const bytes=readFileSync(archive);
console.log(JSON.stringify({archive,files:files.length,images:files.filter(f=>/^public\/images\/.+\.png$/.test(f)).length,bytes:bytes.length,sha256:createHash('sha256').update(bytes).digest('hex'),zipCheck:check.stdout.trim()}));
