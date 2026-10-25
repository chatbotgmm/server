import {it,expect,beforeEach,afterEach} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {mkdtempSync,writeFileSync} from 'node:fs';
import {scryptSync} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {plan,applyIsolated,isolatedUrl,initial,erased} from '../scripts/manual/game-progress-reset/reset.js';
import {RosterOnboarding} from '../src/roster60-onboarding.js';
import {verifyReset,requireStopped} from '../scripts/manual/game-progress-reset/production.js';
import {ReviewAuth} from '../src/review-auth.js';
let db:PrismaClient,url:string;
const confirm='APPLY TO ISOLATED COPY ONLY';
beforeEach(async()=>{
 const dir=mkdtempSync('/tmp/gmm-progress-reset-');url='file:'+dir+'/copy.db';writeFileSync(dir+'/copy.db','');writeFileSync(dir+'/ISOLATED_COPY','test fixture only');
 const r=spawnSync(process.execPath,['node_modules/prisma/build/index.js','db','push','--skip-generate'],{env:{...process.env,DATABASE_URL:url},encoding:'utf8'});if(r.status!==0)throw Error('TEST_SCHEMA_FAILED '+r.stderr);
 db=new PrismaClient({datasourceUrl:url});
 await db.player.createMany({data:[{id:'normal',identity:'kakao:test',credits:999,snack:900,peanut:10,nickname:'preserved',gameTutorial:9},{id:'review',identity:'review:play-store'},{id:'admin',identity:'admin:test',isAdmin:true}]});
 await db.kakaoLink.create({data:{playerId:'normal',kakaoId:'fake-kakao'}});await db.chatAppUser.create({data:{playerId:'normal',appUserId:'fake-app'}});
 await db.webLogin.create({data:{playerId:'normal',code:'test-code',expiresAt:new Date(Date.now()+86400000)}});
 await db.auditEvent.create({data:{playerId:'normal',kind:'WELCOME',detail:{retained:true}}});
 await db.characterDefinition.create({data:{id:'C1',name:'legacy',rarity:'COMMON',position:1,quote:'',imageUrl:''}});
 await db.ownedCharacter.create({data:{playerId:'normal',characterId:'C1',obtainedVia:'GACHA'}});
 await db.achievement.create({data:{playerId:'normal',key:'COUPON_TEST'}});
 await db.roster60Owned.create({data:{playerId:'normal',speciesId:'u02',source:'old',totalXp:123n}});
 await db.roster60PvpParty.create({data:{playerId:'normal',ownedIds:'["old"]'}});
 await db.roster60PvpQueue.create({data:{playerId:'normal',mode:'casual',rating:1000,tierIndex:0,ownedIds:'[]',queuedAt:new Date(),leaseUntil:new Date()}});
 await db.roster60PvpMatch.create({data:{id:'old-match',player0Id:'normal',player1Id:'review',mode:'casual',seed:'fixture',snapshot:'{}',state:'{}',deadline:new Date()}});
});
afterEach(async()=>{await db?.$disconnect();});
it('dry run is read-only and rejects non-isolated destinations',async()=>{
 const a=await plan(db);expect(await plan(db)).toEqual(a);expect(a.initial.credits).toBe(200);expect(a.counts.Player).toBe(3);expect(()=>isolatedUrl('file:/workspace/server-recruit-policy/galmaegiman-confirmed/package.json')).toThrow();expect(await db.ownedCharacter.count()).toBe(1);
});
it('preserves login/admin/review/audit and creates exactly one fresh starter per account',async()=>{
 const select={id:true,identity:true,isAdmin:true,adminMode:true,nickname:true,createdAt:true,inviteCode:true} as const;
 const before=await db.player.findMany({select}),auth=await db.kakaoLink.findMany(),login=await db.webLogin.findMany();
 await applyIsolated(db,url,(await plan(db)).fingerprint,confirm);
 expect(await db.player.findMany({select})).toEqual(before);expect(await db.kakaoLink.findMany()).toEqual(auth);expect(await db.webLogin.findMany()).toEqual(login);expect(await db.chatAppUser.count()).toBe(1);
 expect(await db.auditEvent.count({where:{kind:'WELCOME'}})).toBe(1);expect(await db.auditEvent.count({where:{kind:'GAME_PROGRESS_RESET'}})).toBe(3);
 expect(await db.player.findUnique({where:{id:'normal'}})).toMatchObject(initial());
 for(const table of erased.filter(t=>!['Roster60Owned','Roster60State'].includes(t)))expect(Number((await db.$queryRawUnsafe<any[]>(`SELECT COUNT(*) n FROM "${table}"`))[0].n)).toBe(0);
 const start=new RosterOnboarding(db,()=>null);await start.start('normal');await start.start('normal');expect(await db.roster60Owned.count()).toBe(3);expect(await db.roster60State.count()).toBe(3);
 expect(await db.roster60Owned.findMany()).toEqual(expect.arrayContaining([expect.objectContaining({speciesId:'u01',totalXp:0n,reincarnation:0})]));await expect(start.complete('normal')).rejects.toThrow('TUTORIAL_NOT_VERIFIED');
 const password='isolated-fixture-password',salt='ab'.repeat(16),hash=scryptSync(password,Buffer.from(salt,'hex'),32,{N:131072,r:8,p:1,maxmem:192*1024*1024}).toString('hex');
 const review=new ReviewAuth(db,{enabled:true,username:'review_user',playerId:'review',passwordHash:'scrypt$131072$8$1$'+salt+'$'+hash,version:'fixture-version'});
 expect(await review.account()).toBe('review');const token=await review.login('review_user',password);expect(token).toBeTruthy();expect(await review.authenticate(token!)).toBe('review');review.close();
 expect(await start.state('normal')).toMatchObject({flow:'roster60-v1',tutorialComplete:false});
 await expect(applyIsolated(db,url,(await plan(db)).fingerprint,confirm)).rejects.toThrow('RESET_ALREADY_APPLIED');
});
it('refuses missing confirmation, stale plan, admin snapshot and billing without mutation',async()=>{
 const p=await plan(db);await expect(applyIsolated(db,url,p.fingerprint,'')).rejects.toThrow('CONFIRMATION_REQUIRED');
 await db.player.update({where:{id:'normal'},data:{credits:998}});await expect(applyIsolated(db,url,p.fingerprint,confirm)).rejects.toThrow('PLAN_CHANGED');
 await db.adminSnapshot.create({data:{playerId:'admin',data:{old:true}}});await expect(applyIsolated(db,url,(await plan(db)).fingerprint,confirm)).rejects.toThrow('ADMIN_SNAPSHOT');await db.adminSnapshot.deleteMany();
 await db.auditEvent.create({data:{playerId:'normal',kind:'PAYMENT',detail:{noSecrets:true}}});await expect(applyIsolated(db,url,(await plan(db)).fingerprint,confirm)).rejects.toThrow('BILLING_AUDIT');expect(await db.ownedCharacter.count()).toBe(1);
});
it('unknown entitlement table blocks; starter failure rolls back every reset change',async()=>{
 await db.$executeRawUnsafe('CREATE TABLE PaymentRights (id TEXT PRIMARY KEY)');expect((await plan(db)).blockers).toContain('UNREVIEWED_TABLE:PaymentRights');await expect(applyIsolated(db,url,(await plan(db)).fingerprint,confirm)).rejects.toThrow('UNREVIEWED_TABLE');await db.$executeRawUnsafe('DROP TABLE PaymentRights');
 await db.$executeRawUnsafe("CREATE TRIGGER fail_starter BEFORE INSERT ON Roster60Owned BEGIN SELECT RAISE(ABORT, 'test failure'); END");
 await expect(applyIsolated(db,url,(await plan(db)).fingerprint,confirm)).rejects.toThrow();expect(await db.ownedCharacter.count()).toBe(1);expect(await db.achievement.count()).toBe(1);expect(await db.roster60Owned.count()).toBe(1);expect(await db.appMeta.count()).toBe(0);
});

it('rejects mismatched Prisma connection even if supplied path is an isolated copy',async()=>{
 const dir=mkdtempSync('/tmp/gmm-progress-reset-');writeFileSync(dir+'/copy.db','');writeFileSync(dir+'/ISOLATED_COPY','test');
 await expect(applyIsolated(db,'file:'+dir+'/copy.db',(await plan(db)).fingerprint,confirm)).rejects.toThrow('DATABASE_CONNECTION_MISMATCH');
});
it('CLI defaults to dry-run and saves a verified restorable pre-reset copy before isolated apply',async()=>{
 const cli=['--import','tsx','scripts/manual/game-progress-reset/cli.ts',url];
 const preview=spawnSync(process.execPath,cli,{encoding:'utf8'});expect(preview.status).toBe(0);expect(JSON.parse(preview.stdout).counts.Player).toBe(3);expect(await db.ownedCharacter.count()).toBe(1);
 const applied=spawnSync(process.execPath,[...cli,'--apply-isolated',JSON.parse(preview.stdout).fingerprint,confirm],{encoding:'utf8'});expect(applied.stderr).toBe('');expect(applied.status).toBe(0);expect(JSON.parse(applied.stdout).productionReady).toBe(false);
 const backup=new PrismaClient({datasourceUrl:url+'.before-reset'});
 try{expect(await backup.player.count()).toBe(3);expect(await backup.ownedCharacter.count()).toBe(1);expect(await backup.achievement.count()).toBe(1);expect(await backup.roster60State.count()).toBe(0);expect(await verifyReset(db,backup)).toMatchObject({accounts:3,identityPreserved:true});}finally{await backup.$disconnect();}
});

it('requires stopped service and rehearses backup/restore without touching any other DB',async()=>{
 expect(()=>requireStopped('active','12')).toThrow('SERVICE_MUST_BE_STOPPED');expect(()=>requireStopped('inactive','12')).toThrow();expect(()=>requireStopped('inactive','0')).not.toThrow();
 const path=url.slice(5),backup=path.replace('/copy.db','/before.db'),helper='scripts/manual/game-progress-reset/db-ops.py';
 const saved=spawnSync('python3',[helper,'backup',path,backup,'--isolated'],{encoding:'utf8'});expect(saved.status,saved.stderr).toBe(0);
 await db.player.update({where:{id:'normal'},data:{credits:1}});await db.$disconnect();
 const restored=spawnSync('python3',[helper,'restore',path,backup,'--isolated'],{encoding:'utf8'});expect(restored.status,restored.stderr).toBe(0);
 db=new PrismaClient({datasourceUrl:url});expect((await db.player.findUniqueOrThrow({where:{id:'normal'}})).credits).toBe(999);expect(await db.ownedCharacter.count()).toBe(1);
});
