// Single shared synthetic scenario source for Node reference and Java parity checks.
import {exampleBattle,exampleFighter,exampleBalance} from './fixture.mjs';
import {writeFileSync} from 'node:fs';
const a={playerId:'review-a'},b={playerId:'review-b'};
const scenarios=[];
function scenario(name,edit,steps,balanceOverrides={}){
 const fighters=[exampleFighter('u01'),exampleFighter('u02',2)];edit?.(fighters);
 const battle=exampleBattle({fighters,balance:{...exampleBalance,...balanceOverrides}});
 const config={fixtureOnly:true,balanceVersion:'synthetic-fixture-not-product',baseDamage:20,reflectionPolicy:'fixture-base-times-fit',initial:battle.snapshot(a)};
 const results=[];
 for(const step of steps){try{results.push({dto:battle.command(step.side===0?a:step.side===1?b:{playerId:'outsider'},step.command)});}catch(e){results.push({error:e.message,dto:battle.snapshot(a)});}}
 scenarios.push({name,config,steps,results});
}
const cmd=(side,rev,id,kind='attack',extra={})=>({side,command:{battleId:'review-1',requestId:id,expectedRevision:rev,kind,...extra}});
const sixth=cmd(0,0,'sixth','skill',{skillId:'fixture-5'});
scenario('sixth, receipts, conflict, stale, low MP, floor focus, cap',null,[sixth,sixth,cmd(0,0,'sixth','focus'),cmd(1,1,'foe','focus'),cmd(0,0,'stale'),cmd(0,2,'poor','skill',{skillId:'fixture-5'}),cmd(0,2,'focus','focus'),cmd(1,3,'foe2','focus'),cmd(0,4,'focus2','focus'),cmd(1,5,'foe3','focus'),cmd(0,6,'focus3','focus')]);
for(const defense of ['weak','normal','resist','immune','reflect'])scenario('normal attack + suitability + '+defense,f=>{f[0].suitability.NORMAL=3;f[1].defense.NORMAL=defense;f[0].defense.NORMAL='reflect';},[cmd(0,0,'hit')]);
scenario('negative cannot heal',f=>{f[0].suitability.NORMAL=-20;},[cmd(0,0,'hit')]);
scenario('immunity cannot be bypassed',f=>{f[0].suitability.NORMAL=100;f[1].defense.NORMAL='immune';},[cmd(0,0,'hit')]);
scenario('reflect lethal and no re-reflect',f=>{f[0].maxHp=10;f[0].defense.NORMAL='reflect';f[1].defense.NORMAL='reflect';},[cmd(0,0,'hit'),cmd(1,1,'after')]);
scenario('invalid commands atomic',null,[cmd(2,0,'outsider'),cmd(1,0,'turn'),cmd(0,0,'extra','focus',{bogus:1}),cmd(0,0,'missing','skill',{skillId:'absent'}),cmd(0,0,'item','item'),cmd(0,0,'valid')]);
scenario('focus below four MP stays zero',null,[cmd(0,0,'zero','focus')],{maxMp:()=>3});
scenario('immune extreme finite suitability',f=>{f[0].suitability.NORMAL=1e308;f[1].defense.NORMAL='immune';},[cmd(0,0,'immune')]);
scenario('large MP focus cannot overflow',null,[cmd(0,0,'large','focus')],{maxMp:()=>2000000000});
const runtime=exampleBattle({fighters:[exampleFighter('u01'),exampleFighter('u01')]});
const runtimeConfig={fixtureOnly:true,balanceVersion:'synthetic-fixture-not-product',baseDamage:20,reflectionPolicy:'fixture-base-times-fit',initial:runtime.snapshot(a)};
const commands=[sixth.command,{battleId:'review-1',expectedRevision:2,requestId:'focus',kind:'focus'},{battleId:'review-1',expectedRevision:4,requestId:'hit',kind:'attack'}];
const sessionResults=[];
for(const c of commands){let dto=runtime.command(a,c);if(dto.phase!=='over'){const foe=runtime.snapshot(b);runtime.command(b,{battleId:c.battleId,requestId:'opponent-'+c.requestId,expectedRevision:foe.revision,kind:foe.self.mp>=foe.self.attack.mpCost?'attack':'focus'});}sessionResults.push(runtime.snapshot(a));}
const invalidConfigs=[];
for(const [name,mutate] of [
 ['negative attack cost',f=>f.attack.mpCost=-2],
 ['fractional skill cost',f=>f.skills[0].mpCost=1.5],
 ['duplicate skill',f=>f.skills[1].id=f.skills[0].id],
 ['seventh skill',f=>f.skills.push({...f.skills[0],id:'seventh'})],
 ['missing defense',f=>delete f.defense.NORMAL],
 ['invalid element',f=>f.attack.element='ROCK']
]) {
 const config=structuredClone(runtimeConfig);mutate(config.initial.foe);
 let error;try{exampleBattle({fighters:[config.initial.self,config.initial.foe]});}catch(e){error=e.message;}
 if(!error)throw Error('Expected Node rejection: '+name);invalidConfigs.push({name,config,error});
}
writeFileSync(process.argv[2]??'/tmp/combat-offline-cases.json',JSON.stringify({scenarios,invalidConfigs,session:{config:runtimeConfig,commands,results:sessionResults}},null,2));
if(process.argv[3])writeFileSync(process.argv[3],JSON.stringify(runtimeConfig,null,2)+'\n');
console.log(`Generated ${scenarios.length} Node reference scenarios + complete-turn session cases; synthetic fixture only.`);
