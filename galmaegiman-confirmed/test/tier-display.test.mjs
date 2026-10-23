import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,writeFileSync,mkdtempSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
import vm from 'node:vm';
import {stripTypeScriptTypes} from 'node:module';
const root=fileURLToPath(new URL('../',import.meta.url));
const read=p=>readFileSync(join(root,p),'utf8');
const cases=[['COMMON','흔함','커먼'],['UNCOMMON','안흔함','레어'],['RARE','희귀','에픽'],['SPECIAL','특별','특별'],['LEGEND','전설','전설'],['HIDDEN','히든','히든'],['LIMITED','제한','제한'],['TRANSCEND','초월','초월'],['ETERNAL','영원','영원'],['IMMORTAL','불멸','불멸'],['','안흔함','레어'],['','희귀도','희귀도'],['UNKNOWN','안흔함','안흔함'],['EPIC','기존 별도 등급','기존 별도 등급']];
const context=vm.createContext({});vm.runInContext(read('public/my/tier-display.js'),context);
test('browser tier labels are exact, preserve identity and do not mutate cached JSON',()=>{
 for(const [tier,old,expected] of cases){const unit=Object.freeze({tier,tierName:old,id:'CACHED',have:2});assert.equal(context.gmmTierName(unit),expected);assert.equal(unit.tierName,old);assert.equal(unit.tier,tier)}
 assert.equal(context.gmmTierName(null),'');
});
test('server labels and both legacy/new command aliases resolve same internal tiers',()=>{
 const source=read('src/content.ts');
 const part=source.slice(source.indexOf('export const tierOrder'),source.indexOf('// 조합 재료'));
 const executable=stripTypeScriptTypes(part).replace(/export /g,'')+';globalThis.labels=rarityNames;globalThis.resolve=tierFromLabel;globalThis.order=tierOrder';
 const server=vm.createContext({});vm.runInContext(executable,server);
 for(const [tier,old,expected] of cases.slice(0,10)){assert.equal(server.labels[tier],expected);assert.equal(server.resolve(old),tier);assert.equal(server.resolve(expected),tier)}
 assert.equal(server.resolve('안커먼'),undefined);assert.equal(server.resolve('희귀도'),undefined);
 assert.equal(server.order.join(','),'COMMON,UNCOMMON,SPECIAL,RARE,LEGEND,HIDDEN,LIMITED,TRANSCEND,ETERNAL,IMMORTAL,NEUTRAL');
});
test('Android display adapter executes the same cases on JVM without Android dependencies',()=>{
 const dir=mkdtempSync(join(tmpdir(),'tier-display-'));
 try{
  const checks=cases.map(([tier,old,expected])=>`if(!${JSON.stringify(expected)}.equals(TierDisplay.name(${JSON.stringify(tier)},${JSON.stringify(old)})))throw new AssertionError(${JSON.stringify(tier+':'+old)});`).join('\n');
  const harness=join(dir,'TierCheck.java');writeFileSync(harness,'package org.dpdns.gmm; public class TierCheck {public static void main(String[] args){'+checks+'}}');
  for(const args of [['com.sun.tools.javac.Main','-d',dir,join(root,'app/android/app/src/main/java/org/dpdns/gmm/TierDisplay.java'),harness],['-cp',dir,'org.dpdns.gmm.TierCheck']]){const r=spawnSync('java',args,{encoding:'utf8'});assert.equal(r.status,0,r.stderr||String(r.error))}
 }finally{rmSync(dir,{recursive:true,force:true})}
});
test('browser entry points load display adapter before callers',()=>{
 for(const file of ['public/my/index.html','public/web/index.html','public/game/index.html']){
  const html=read(file);assert.ok(html.indexOf('/my/tier-display.js')>=0);
  const caller=file.includes('/game/')?'{{game.js}}':file.includes('/web/')?'/my/dash.js':'/my/my.js';assert.ok(html.indexOf('/my/tier-display.js')<html.indexOf(caller));
 }
});
test('legacy command payload and hidden search restriction remain',()=>{
 const game=read('src/game.ts');assert.ok(game.includes("choice('고르기','교환 흔함')"));assert.ok(game.includes("choice('고르기','교환 안흔함')"));assert.ok(game.includes("'조합목록 희귀'"));
 assert.ok(read('src/web.ts').includes("parsedTier==='HIDDEN'?undefined:parsedTier"));
});
