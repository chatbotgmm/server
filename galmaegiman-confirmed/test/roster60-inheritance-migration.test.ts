import {it,expect} from 'vitest';
import {PrismaClient} from '@prisma/client';
import {mkdtempSync,openSync,closeSync,readFileSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';

it('the additive inheritance migration preserves existing units and defaults their override to null',async()=>{
 const directory=mkdtempSync(join(tmpdir(),'galmaegiman-tests-inherit-migration-')),file=join(directory,'test.db');closeSync(openSync(file,'wx',0o600));
 const db=new PrismaClient({datasources:{db:{url:'file:'+file}}});
 try{
  await db.$executeRawUnsafe('CREATE TABLE "Roster60Owned" ("id" TEXT PRIMARY KEY,"playerId" TEXT NOT NULL,"speciesId" TEXT NOT NULL,"totalXp" BIGINT NOT NULL,"reincarnation" INTEGER NOT NULL,"status" TEXT NOT NULL,"locked" BOOLEAN NOT NULL,"source" TEXT NOT NULL,"createdAt" TEXT NOT NULL)');
  await db.$executeRawUnsafe("INSERT INTO Roster60Owned VALUES ('old-owned','old-player','u01',12345,2,'BATTLE',1,'legacy-source','2026-10-28T00:00:00Z')");
  const before=await db.$queryRawUnsafe<Record<string,unknown>[]>('SELECT * FROM Roster60Owned');
  const migration=readFileSync('prisma/migrations/20261028_roster60_skill_inheritance/migration.sql','utf8');
  expect(migration).not.toMatch(/DROP|DELETE|UPDATE|REPLACE/i);
  expect(readFileSync('prisma/manual/20261028_roster60_skill_inheritance_additive.sql','utf8')).toBe(migration);
  await db.$executeRawUnsafe(migration);
  const columns=await db.$queryRawUnsafe<{name:string;notnull:bigint}[]>('PRAGMA table_info(Roster60Owned)');
  expect(columns.some(column=>column.name==='skillInheritanceJson'&&Number(column.notnull)===0)).toBe(true);
  // Use a fresh projection: SQLite may cache SELECT * metadata from before ALTER on this connection.
  const after=await db.$queryRawUnsafe<Record<string,unknown>[]>('SELECT id,playerId,speciesId,totalXp,reincarnation,status,locked,source,createdAt,skillInheritanceJson FROM Roster60Owned');
  expect(after).toHaveLength(1);expect(after[0].skillInheritanceJson).toBeNull();
  const {skillInheritanceJson,...legacy}=after[0];expect(legacy).toEqual(before[0]);
 }finally{await db.$disconnect();rmSync(directory,{recursive:true,force:true});}
});
