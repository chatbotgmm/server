import {it,expect} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {mkdtempSync,readFileSync,writeFileSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join,resolve} from 'node:path';
import {execFileSync} from 'node:child_process';
import {assertProgressionSchema} from '../src/progression.js';
it('기존 DB 추가 SQL은 계정·개체를 보존하고 미적용 기동을 차단한다',async()=>{
 const dir=mkdtempSync(join(tmpdir(),'progression-upgrade-')),url=`file:${join(dir,'old.db')}`;
 const old=readFileSync('prisma/schema.prisma','utf8').replace(/^  (reincarnation Int @default\(0\)|inheritedFrom String\?|statBonusPct Int @default\(0\))\n/gm,'').replace(/model Progression(?:State|Receipt|Milestone) \{[\s\S]*?\n\}/g,'');
 writeFileSync(join(dir,'old.prisma'),old);writeFileSync(join(dir,'old.db'),'');
 execFileSync(process.execPath,['node_modules/prisma/build/index.js','db','push','--skip-generate','--schema',join(dir,'old.prisma')],{env:{...process.env,DATABASE_URL:url},stdio:'pipe'});
 const db=new PrismaClient({datasources:{db:{url}}});
 try{
  await db.$executeRaw`INSERT INTO Player (id,identity,credits) VALUES ('legacy-player','legacy-identity',77)`;
  await db.$executeRaw`INSERT INTO CharacterDefinition (id,name,rarity,position,quote,imageUrl) VALUES ('D1','fixture','LIMITED',1,'','')`;
  await db.$executeRaw`INSERT INTO OwnedCharacter (id,playerId,characterId,obtainedVia) VALUES (42,'legacy-player','D1','GACHA')`;
  await expect(assertProgressionSchema(db)).rejects.toThrow('아직 적용되지');
  const sql=readFileSync('prisma/manual/20261019_progression_additive.sql','utf8').replace(/^--.*$/gm,'');
  await db.$transaction(async tx=>{for(const statement of sql.split(';').map(s=>s.trim()).filter(Boolean))await tx.$executeRawUnsafe(statement);});
  await expect(assertProgressionSchema(db)).resolves.toBeUndefined();
  expect(await db.player.findUniqueOrThrow({where:{id:'legacy-player'}})).toMatchObject({identity:'legacy-identity',credits:77});
  expect(await db.ownedCharacter.findUniqueOrThrow({where:{id:42}})).toMatchObject({characterId:'D1',obtainedVia:'GACHA',status:'AVAILABLE',reincarnation:0,inheritedFrom:null,statBonusPct:0});
  expect(await db.progressionReceipt.count()).toBe(0);
 }finally{await db.$disconnect();rmSync(dir,{recursive:true,force:true});}
},30000);
