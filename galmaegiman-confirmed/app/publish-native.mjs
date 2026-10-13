// 네이티브 APK를 기존 apk 브랜치에 추가한다. 기존 APK와 이력을 보존하며 강제 push하지 않는다.
import {execFileSync} from 'node:child_process';
import {copyFileSync,mkdirSync,readFileSync,writeFileSync,statSync,mkdtempSync,rmSync} from 'node:fs';
import {resolve,dirname} from 'node:path';import {fileURLToPath} from 'node:url';import {tmpdir} from 'node:os';import{createHash}from'node:crypto';
const here=dirname(fileURLToPath(import.meta.url)),src=resolve(process.argv.find((v,i)=>i>1&&!v.startsWith('--'))??`${here}/android/app/build/outputs/apk/debug/app-debug.apk`),out=resolve(here,'../public/app');
const gradle=readFileSync(`${here}/android/app/build.gradle`,'utf8'),versionCode=Number(gradle.match(/versionCode\s+(\d+)/)[1]),versionName=gradle.match(/versionName\s+"([^"]+)"/)[1],data=readFileSync(src);
const meta={versionCode,versionName,size:statSync(src).size,date:new Date().toISOString().slice(0,10),test:true,native:true,applicationId:'org.dpdns.gmm.native',sha256:createHash('sha256').update(data).digest('hex')};
mkdirSync(out,{recursive:true});copyFileSync(src,`${out}/galmaegiman-native.apk`);writeFileSync(`${out}/native.json`,JSON.stringify(meta,null,2)+'\n');
if(process.argv.includes('--no-push')){console.log(JSON.stringify(meta));process.exit(0);}
const git=(args,input,env)=>execFileSync('git',args,{cwd:here,input,env:{...process.env,...env},encoding:'utf8'}).trim();
git(['fetch','origin','apk']);const parent=git(['rev-parse','FETCH_HEAD']);const dir=mkdtempSync(resolve(tmpdir(),'gmm-native-publish-')),env={GIT_INDEX_FILE:resolve(dir,'index')};
try{git(['read-tree',parent],undefined,env);for(const name of ['native.json','galmaegiman-native.apk']){const blob=git(['hash-object','-w',`${out}/${name}`]);git(['update-index','--add','--cacheinfo',`100644,${blob},${name}`],undefined,env);}const tree=git(['write-tree'],undefined,env),commit=git(['commit-tree',tree,'-p',parent,'-m',`네이티브 앱 ${versionName} 공개`]);git(['push','origin',`${commit}:refs/heads/apk`]);console.log(JSON.stringify({...meta,artifactCommit:commit}));}finally{rmSync(dir,{recursive:true,force:true});}
