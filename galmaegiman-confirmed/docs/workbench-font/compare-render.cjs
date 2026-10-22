const fs=require('fs'), crypto=require('crypto');
const {GlobalFonts,createCanvas}=require('@napi-rs/canvas');
// Recreate face fixtures rather than relying on a prior shell session.
require('child_process').execFileSync('python', ['-c', `
from fontTools.ttLib import TTFont
p='/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc'
TTFont(p,fontNumber=0).save('/tmp/noto-jp.otf')
TTFont(p,fontNumber=1).save('/tmp/noto-kr.otf')
open('/tmp/noto-variant-text.txt','w').write('丫乳伴侮侵倂倜偉偏偠偰傑僊僧僭免兎全公关具兼再冒冓冬凞刃分判')
`]);
GlobalFonts.registerFromPath('/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc','MockNoto');
GlobalFonts.registerFromPath('/tmp/noto-jp.otf','FaceJP');
GlobalFonts.registerFromPath('/tmp/noto-kr.otf','FaceKR');
const sample=fs.readFileSync('/tmp/noto-variant-text.txt','utf8');
function draw(family,text){const c=createCanvas(1800,120),q=c.getContext('2d');q.font=`40px ${family}`;q.fillStyle='#ffffff';q.fillText(text,10,60);return c.toBuffer('image/png');}
const fonts=['MockNoto','FaceJP','FaceKR']; const report={sample,hashes:{}};
for(const family of fonts){const b=draw(family,sample);report.hashes[family]=crypto.createHash('sha256').update(b).digest('hex');fs.writeFileSync(`/tmp/${family}.png`,b);}
report.families=GlobalFonts.families.filter(x=>/MockNoto|FaceJP|FaceKR/.test(x.family));
console.log(JSON.stringify(report,null,2)); fs.writeFileSync(__dirname+'/render-face-verification.json',JSON.stringify(report,null,2)+'\n');
GlobalFonts.registerFromPath(__dirname+'/workbench-noto-regular.otf','BundledSubset');
const strings=['조합','카드를 고르고, 재료를 모아 조합하세요','결과로 찾기','재료로 찾기','선택한 재료','재료에 추가','빈 재료칸','선택 해제','가능한 결과','재료를 더 선택하세요','선택한 재료에 맞는 결과를','이곳에서 확인할 수 있어요','결과를 확인한 뒤 조합하세요','조합하기','1 / 14','1 / 6','＋ − ‹ ›'];
const checks=[];
for(const size of [10,11,12,14,16,21])for(const weight of [400,700])for(const text of strings){
 const hashes=[];
 for(const font of ['MockNoto','BundledSubset']){const c=createCanvas(1600,100),q=c.getContext('2d');q.font=`${weight} ${size}px ${font}`;q.fillStyle='#F5EAD0';q.fillText(text,10,55);hashes.push(crypto.createHash('sha256').update(c.toBuffer('image/png')).digest('hex'));}
 if(hashes[0]!==hashes[1]) throw Error(JSON.stringify({size,weight,text,hashes}));
 checks.push({size,weight,text,identical:true});
}
fs.writeFileSync(__dirname+'/mock-raster-verification.json',JSON.stringify({renderer:'@napi-rs/canvas',checks},null,2)+'\n');
console.log('Actual mock string raster matches:',checks.length);
