import {spawn,spawnSync} from 'node:child_process';
import {mkdtempSync,mkdirSync,openSync,closeSync,appendFileSync,readFileSync,writeFileSync,existsSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

// 어떤 실제 DB 주소가 환경에 있어도 상속하지 않는다. 모든 준비·시험은 새 임시 SQLite만 사용한다.
const root=fileURLToPath(new URL('../',import.meta.url));
const stamp=new Date().toISOString().replace(/[:.]/g,'-');
const output=process.argv[2]?resolve(process.argv[2]):join(root,'reports','story-and-user-reports-'+stamp);
mkdirSync(output,{recursive:true});
const disposable=mkdtempSync(join(tmpdir(),'galmaegiman-tests-'));
const database=join(disposable,'test.db');closeSync(openSync(database,'wx',0o600));
const env={...process.env,DATABASE_URL:`file:${database}`,TEST_DB_URL:`file:${database}`};
delete env.ROSTER60_PROFILE;delete env.ROSTER60_STORY_PROFILE;

const groups={
 story:[
  'test/story-content-invalid.test.ts',
  'test/story-mode-content.test.ts',
  'test/story-mode-villains.test.ts',
  'test/story-mode-api.integration.test.ts',
 ],
 battleRegression:[
  'test/roster60-battle.integration.test.ts',
  'test/roster60-adventure.integration.test.ts',
  'test/roster60-party-actions.integration.test.ts',
  'test/roster60-observed-defense.integration.test.ts',
  'test/roster60-rebirth-transaction.integration.test.ts',
  'test/roster60-play-modes.integration.test.ts',
 ],
 normalXp:[
  'test/normal-xp-policy.test.ts',
  'test/normal-xp.integration.test.ts',
 ],
 userReports:[
  'test/roster60-casual-ai.integration.test.ts',
  'test/roster60-craft-client-capability.test.ts',
  'test/roster60-craft.integration.test.ts',
  'test/roster60-pvp.integration.test.ts',
  'test/roster60-library.integration.test.ts',
  'test/roster60-recruit-session.integration.test.ts',
  'test/roster60-recruit-batch.integration.test.ts',
  'test/roster60-recruit-policy-file.integration.test.ts',
  'test/roster60-recruit-level.test.ts',
 ],
};
const tests=Object.values(groups).flat();
if(new Set(tests).size!==tests.length||tests.some(file=>!existsSync(join(root,file))))throw Error('시험 파일 목록 오류');
const log=join(output,'run.log'),results=join(output,'vitest-results.json');
const base=spawnSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).stdout.trim();
const meta={baseCommit:base,startedAt:new Date().toISOString(),database:`file:${database}`,output,groups,steps:[]};
writeFileSync(join(output,'run-metadata.json'),JSON.stringify(meta,null,2)+'\n');
console.log(`기준 커밋: ${base}\n새 임시 DB: ${database}\n검증 증거: ${output}\n선정 시험 파일: ${tests.length}`);
appendFileSync(log,`기준 커밋: ${base}\n임시 DB: ${database}\n`);

async function run(label,args){
 const start=new Date().toISOString();const heading=`\n[${label}] ${args.join(' ')}\n`;
 process.stdout.write(heading);appendFileSync(log,heading);
 const code=await new Promise((done,reject)=>{
  const child=spawn(process.execPath,args,{cwd:root,env,stdio:['ignore','pipe','pipe']});
  child.stdout.on('data',chunk=>{process.stdout.write(chunk);appendFileSync(log,chunk);});
  child.stderr.on('data',chunk=>{process.stderr.write(chunk);appendFileSync(log,chunk);});
  child.on('error',reject);child.on('close',status=>done(status??1));
 });
 meta.steps.push({label,startedAt:start,endedAt:new Date().toISOString(),exitCode:code});
 writeFileSync(join(output,'run-metadata.json'),JSON.stringify(meta,null,2)+'\n');
 return code;
}

let exitCode=0;
for(const [label,args] of [
 ['client-generate',['node_modules/prisma/build/index.js','generate']],
 ['typescript',['node_modules/typescript/bin/tsc','--noEmit']],
 ['temporary-schema',['node_modules/prisma/build/index.js','db','push','--skip-generate']],
 ['temporary-seed',['--import','tsx','prisma/seed.ts']],
 ['combined-tests',['node_modules/vitest/vitest.mjs','run','--maxWorkers=1','--reporter=default','--reporter=json',`--outputFile=${results}`,...tests]],
]){
 exitCode=await run(label,args);if(exitCode!==0)break;
}

const summary={baseCommit:base,database:meta.database,exitCode,finishedAt:new Date().toISOString(),groups:{},failures:[],unhandledErrors:[]};
if(existsSync(results)){
 const report=JSON.parse(readFileSync(results,'utf8'));
 Object.assign(summary,{totalTests:report.numTotalTests,passedTests:report.numPassedTests,failedTests:report.numFailedTests,pendingTests:report.numPendingTests,totalFiles:report.testResults?.length});
 for(const [group,files] of Object.entries(groups)){
  const selected=(report.testResults??[]).filter(file=>files.some(relative=>file.name===join(root,relative)));
  const assertions=selected.flatMap(file=>file.assertionResults??[]);
  summary.groups[group]={files:selected.length,tests:assertions.length,passed:assertions.filter(test=>test.status==='passed').length,failed:assertions.filter(test=>test.status==='failed').length,pending:assertions.filter(test=>['pending','skipped','todo'].includes(test.status)).length};
 }
 for(const file of report.testResults??[]){
  const failed=(file.assertionResults??[]).filter(test=>test.status==='failed').map(test=>({name:test.fullName??test.title,messages:test.failureMessages??[]}));
  if(file.status==='failed'||failed.length)summary.failures.push({file:file.name,classification:'검토 필요: 기준/버그 원본/통합 회귀를 증거와 대조',message:file.message??'',tests:failed});
 }
 summary.unhandledErrors=report.unhandledErrors??[];
}
writeFileSync(join(output,'summary.json'),JSON.stringify(summary,null,2)+'\n');
const display={...summary,failures:summary.failures.map(failure=>({file:failure.file,classification:failure.classification,failedTests:failure.tests.length,names:failure.tests.map(test=>test.name)}))};
console.log('\n집중 검증 요약\n'+JSON.stringify(display,null,2));
console.log('운영 DB·원격 서비스·APK는 사용하지 않았습니다.');
process.exitCode=exitCode;
