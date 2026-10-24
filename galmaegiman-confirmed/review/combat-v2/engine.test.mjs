import test from 'node:test';
import assert from 'node:assert/strict';
import {EPOCH, ELEMENTS} from './engine.mjs';
import {exampleBattle, exampleFighter, exampleBalance, profile} from './fixture.mjs';
const a={playerId:'review-a'}, b={playerId:'review-b'};
const cmd=(battle,auth,kind='attack',more={})=>battle.command(auth,{battleId:'review-1',requestId:crypto.randomUUID(),
  expectedRevision:battle.snapshot(auth).revision,kind,...more});
test('8 skill elements, full per-unit level-grown MP, six commands and six skills',()=>{
  const x=exampleBattle(),s=x.snapshot(a);
  assert.equal(ELEMENTS.length,8);assert.equal(s.self.mp,13);assert.equal(s.foe.mp,21);
  assert.equal('element' in s.self,false);assert.equal(s.skills.length,6);
  assert.deepEqual(s.commands.map(c=>c.kind),['attack','skills','focus','item','switch','escape']);
  assert.ok(s.commands.slice(3).every(c=>!c.enabled&&c.reason));
});
test('focus consumes a turn, floors 25% recovery, caps at max, no bonus action',()=>{
  const x=exampleBattle();cmd(x,a,'skill',{skillId:'fixture-5'});cmd(x,b,'focus');
  const s=cmd(x,a,'focus');assert.equal(s.self.mp,8);assert.equal(s.activeSide,1);
  cmd(x,b,'focus');cmd(x,a,'focus');cmd(x,b,'focus');assert.equal(cmd(x,a,'focus').self.mp,13);
  assert.throws(()=>cmd(x,a,'focus'),/NOT_YOUR_TURN/);
});
test('MP cost checked server-side including sixth skill; rejection leaves state intact',()=>{
  const x=exampleBattle();cmd(x,a,'skill',{skillId:'fixture-5'});cmd(x,b,'focus');
  const before=x.snapshot(a);assert.equal(before.skills[5].enabled,false);
  assert.throws(()=>cmd(x,a,'skill',{skillId:'fixture-5'}),/INSUFFICIENT_MP/);
  assert.deepEqual(x.snapshot(a),before);
});
test('normal attacks obey every defense and suitability multiplies independently of cost',()=>{
  for(const [defense,damage] of [['weak',36],['normal',24],['resist',12],['immune',0]]){
    const f=exampleFighter('u01'),d=exampleFighter('u02');f.suitability.NORMAL=2;d.defense.NORMAL=defense;
    const x=exampleBattle({fighters:[f,d]}),s=cmd(x,a);
    assert.equal(s.events[0].damage,damage);assert.equal(s.self.mp,11);assert.equal(s.activeSide,1);
  }
});
test('negative suitability cannot heal; immunity cannot be pierced by positive suitability',()=>{
  for(const fit of [-100,-10,-1,100]){
    const f=exampleFighter('u01'),d=exampleFighter('u02');f.suitability.NORMAL=fit;
    if(fit>0)d.defense.NORMAL='immune';
    const s=cmd(exampleBattle({fighters:[f,d]}),a);
    assert.ok(s.foe.hp<=s.foe.maxHp);assert.ok(s.events[0].damage>=0);
    assert.equal(s.events[0].damage,fit===-1?18:0);
  }
});
test('reflection affects attacker only, cannot re-reflect, and can end battle',()=>{
  const f=exampleFighter('u01'),d=exampleFighter('u02');f.maxHp=9;
  f.defense.NORMAL='reflect';d.defense.NORMAL='reflect';
  const x=exampleBattle({fighters:[f,d]}),s=cmd(x,a);
  assert.equal(s.self.hp,0);assert.equal(s.foe.hp,d.maxHp);assert.equal(s.events.length,1);
  assert.equal(s.phase,'over');assert.equal(s.winner,1);
  assert.throws(()=>cmd(x,b),/BATTLE_OVER/);
});
test('authenticated ownership, stale revision and idempotent retries',()=>{
  const x=exampleBattle(),q={battleId:'review-1',requestId:'same',expectedRevision:0,kind:'attack'};
  assert.throws(()=>x.command({playerId:'stranger'},q),/UNAUTHORIZED/);
  const first=x.command(a,q);assert.deepEqual(x.command(a,q),first);
  assert.throws(()=>x.command(a,{...q,kind:'focus'}),/IDEMPOTENCY_CONFLICT/);
  assert.throws(()=>x.command(b,{...q,requestId:'new'}),/STALE_REVISION/);
  assert.equal(x.replay(a).frames.length,2);
});
test('invalid balance policy and unsupported actions do not partially consume MP',()=>{
  const x=exampleBattle({balance:{...exampleBalance,baseDamage:()=>NaN}}),before=x.snapshot(a);
  assert.throws(()=>cmd(x,a),/INVALID_DAMAGE_POLICY/);assert.deepEqual(x.snapshot(a),before);
  for(const kind of ['item','switch','escape'])assert.throws(()=>cmd(x,a,kind),/ACTION_NOT_CONNECTED/);
  assert.deepEqual(x.snapshot(a),before);
});
test('reject more than six skills and incomplete profiles, never truncate',()=>{
  const f=exampleFighter('u01');f.skills.push({...f.skills[0],id:'seventh'});
  assert.throws(()=>exampleBattle({fighters:[f,exampleFighter('u02')]}),/INVALID_FIGHTER/);
  f.skills.pop();delete f.defense.HOLY;
  assert.throws(()=>exampleBattle({fighters:[f,exampleFighter('u02')]}),/PROFILE_REQUIRED/);
});
test('review replay frame version and immutable snapshots retain MP/HP events',()=>{
  const x=exampleBattle();cmd(x,a);const replay=x.replay(a);
  assert.equal(replay.epoch,EPOCH);assert.equal(replay.frames[1].events[0].mpBefore,13);
  assert.equal(replay.frames[1].events[0].mpAfter,11);
  replay.frames[1].fighters[0].mp=999;
  assert.equal(x.snapshot(a).self.mp,11);
});

// Regression from independent review: caller mutation cannot alter identity/policy version.
test('creation captures owners and policy hooks',()=>{
 const players=['review-a','review-b'],balance={...exampleBalance};
 const battle=exampleBattle({players,balance});players[0]='outsider';balance.baseDamage=()=>999;balance.version='changed';
 assert.throws(()=>battle.snapshot({playerId:'outsider'}),/UNAUTHORIZED/);
 const result=battle.command({playerId:'review-a'},{battleId:'review-1',expectedRevision:0,requestId:'capture',kind:'attack'});
 assert.equal(result.events[0].damage,20);assert.equal(battle.replay({playerId:'review-a'}).balanceVersion,'synthetic-fixture-not-product');
});
