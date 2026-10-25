import {it,expect} from 'vitest';import {readFileSync} from 'node:fs';
import {recruitmentGrowth} from '../src/roster60-library.js';
const catalog=JSON.parse(readFileSync('data/test-profiles/five-stat-device-test-v1/catalog.json','utf8'));
it('all eight epics receive their own level15 threshold, ordinary low grades level1',()=>{
 const epics=catalog.characters.filter((u:any)=>u.rarity==='EPIC');expect(epics).toHaveLength(8);
 for(const u of epics)expect(recruitmentGrowth(u)).toMatchObject({level:15,totalXp:u.growth[14].totalXp,xp:0});
 for(const u of catalog.characters.filter((u:any)=>['COMMON','UNCOMMON'].includes(u.rarity)))expect(recruitmentGrowth(u)).toMatchObject({level:1,totalXp:0});
});
it('uses changed curve rather than copying6400; rejects missing/invalid curve and excluded top grades',()=>{
 const epic=structuredClone(catalog.characters.find((u:any)=>u.rarity==='EPIC'));epic.growth.forEach((p:any)=>p.totalXp*=2);expect(recruitmentGrowth(epic)).toMatchObject({level:15,totalXp:epic.growth[14].totalXp});
 expect(()=>recruitmentGrowth({...epic,growth:epic.growth.slice(0,14)})).toThrow('RECRUIT_GROWTH_INVALID');
 expect(()=>recruitmentGrowth({...epic,rarity:'LIMITED'})).toThrow('RECRUIT_LEVEL_POLICY_PENDING');
});

it('all rare/legend/hidden definitions use approved levels and their own XP curves',()=>{
 const levels:Record<string,number>={RARE:10,LEGEND:20,HIDDEN:20};
 const units=catalog.characters.filter((u:any)=>u.rarity in levels);expect(units).toHaveLength(20);
 for(const u of units){const level=levels[u.rarity];expect(recruitmentGrowth(u)).toMatchObject({level,totalXp:u.growth[level-1].totalXp,xp:0});const changed=structuredClone(u);changed.growth.forEach((p:any)=>p.totalXp*=2);expect(recruitmentGrowth(changed).totalXp).toBe(changed.growth[level-1].totalXp);}
 for(const rarity of ['LIMITED','TRANSCEND','IMMORTAL','ETERNAL'])expect(()=>recruitmentGrowth({...units[0],rarity})).toThrow('RECRUIT_LEVEL_POLICY_PENDING');
});
