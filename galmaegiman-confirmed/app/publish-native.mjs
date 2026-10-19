// apk 브랜치의 기존 파일·서명·이력을 보존하며 네이티브 APK를 공개한다.
import {execFileSync} from 'node:child_process';
import {copyFileSync,mkdirSync,readFileSync,writeFileSync,statSync,mkdtempSync,rmSync,readdirSync,existsSync} from 'node:fs';
import {resolve,dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {tmpdir} from 'node:os';
import {createHash} from 'node:crypto';

const here=dirname(fileURLToPath(import.meta.url));
const src=resolve(process.argv.find((v,i)=>i>1&&!v.startsWith('--'))??`${here}/android/app/build/outputs/apk/release/app-release.apk`);
const out=resolve(here,'../public/app');
const gradle=readFileSync(`${here}/android/app/build.gradle`,'utf8');
const versionCode=Number(gradle.match(/versionCode\s+(\d+)/)[1]);
const versionName=gradle.match(/versionName\s+"([^"]+)"/)[1];
const data=readFileSync(src);
const sdk=process.env.ANDROID_HOME??process.env.ANDROID_SDK_ROOT;
if(!sdk)throw Error('공개 전 서명 검사를 위해 ANDROID_HOME이 필요해요.');
const versions=readdirSync(join(sdk,'build-tools')).sort((a,b)=>b.localeCompare(a,undefined,{numeric:true}));
const buildTools=versions.map(v=>join(sdk,'build-tools',v)).find(p=>existsSync(join(p,'apksigner'))&&existsSync(join(p,'aapt')));
if(!buildTools)throw Error('Android build-tools의 apksigner와 aapt가 필요해요.');
const signer=path=>{
  const report=execFileSync(join(buildTools,'apksigner'),['verify','--print-certs',path],{encoding:'utf8'});
  const certificates=[...report.matchAll(/^Signer #\d+ certificate SHA-256 digest: ([a-f0-9]+)$/gm)].map(m=>m[1]).sort();
  if(!certificates.length)throw Error('APK 서명을 확인하지 못했어요.');
  return certificates.join(',');
};
const certificateSha256=signer(src);
const badging=execFileSync(join(buildTools,'aapt'),['dump','badging',src],{encoding:'utf8'});
if(/^application-debuggable$/m.test(badging))throw Error('공개 APK는 release 빌드여야 해요.');
const pkg=badging.match(/^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'/m);
if(!pkg||pkg[1]!==gradle.match(/applicationId\s+"([^"]+)"/)[1]||Number(pkg[2])!==versionCode||pkg[3]!==versionName)throw Error('APK 패키지·버전과 소스의 공개 정보가 달라요.');
const git=(args,input,env)=>execFileSync('git',args,{cwd:here,input,env:{...process.env,...env},encoding:'utf8'}).trim();
git(['fetch','origin','apk']);
const parent=git(['rev-parse','FETCH_HEAD']);
const dir=mkdtempSync(resolve(tmpdir(),'gmm-native-publish-'));
const env={GIT_INDEX_FILE:resolve(dir,'index')};
try{
  const previous=JSON.parse(git(['show',`${parent}:native.json`]));
  const oldApk=join(dir,'previous.apk');
  writeFileSync(oldApk,execFileSync('git',['show',`${parent}:galmaegiman-native.apk`],{cwd:here,maxBuffer:150*1024*1024}));
  const packageChanged=previous.applicationId!==pkg[1];
  const migration=packageChanged&&process.argv.includes('--new-package')&&previous.applicationId==='org.dpdns.gmm.native'&&pkg[1]==='org.dpdns.gmm.native.revised';
  if(packageChanged&&!migration)throw Error('별도 설치 패키지 전환은 --new-package를 지정해 주세요.');
  if(!migration&&signer(oldApk)!==certificateSha256)throw Error('기존 배포 APK와 서명키가 달라 업데이트할 수 없어요. 기존 서명키로 다시 서명해 주세요. 공개 파일은 변경하지 않았어요.');
  if(versionCode<=previous.versionCode)throw Error('기존 APK보다 큰 versionCode가 필요해요.');
  const meta={versionCode,versionName,size:statSync(src).size,date:new Date().toISOString().slice(0,10),test:true,native:true,applicationId:pkg[1],separateInstall:pkg[1]==='org.dpdns.gmm.native.revised',certificateSha256,sha256:createHash('sha256').update(data).digest('hex')};
  mkdirSync(out,{recursive:true});
  copyFileSync(src,`${out}/galmaegiman-native.apk`);
  writeFileSync(`${out}/native.json`,JSON.stringify(meta,null,2)+'\n');
  if(process.argv.includes('--no-push')){console.log(JSON.stringify(meta));process.exitCode=0;}
  else{
    git(['read-tree',parent],undefined,env);
    const names=['native.json','galmaegiman-native.apk'];
    if(migration){
      copyFileSync(oldApk,`${out}/galmaegiman-native-66.apk`);
      writeFileSync(`${out}/native-66.json`,JSON.stringify(previous,null,2)+'\n');
      names.push('native-66.json','galmaegiman-native-66.apk');
    }
    for(const name of names){
      const blob=git(['hash-object','-w',`${out}/${name}`]);
      git(['update-index','--add','--cacheinfo',`100644,${blob},${name}`],undefined,env);
    }
    const tree=git(['write-tree'],undefined,env);
    const commit=git(['commit-tree',tree,'-p',parent,'-m',`네이티브 앱 ${versionName} 공개`]);
    git(['push','origin',`${commit}:refs/heads/apk`]);
    console.log(JSON.stringify({...meta,artifactCommit:commit}));
  }
}finally{rmSync(dir,{recursive:true,force:true});}
