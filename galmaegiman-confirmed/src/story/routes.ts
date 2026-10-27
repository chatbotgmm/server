import type {CommanderPolicy} from '../progression/commander.js';
import type {PrismaClient} from '@prisma/client';
import type {FastifyRequest} from 'fastify';
import {existsSync,readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {z} from 'zod';
import {RosterError,type RosterCatalog} from '../roster60-onboarding.js';
import type {BattlePolicy} from '../roster60-battle.js';
import {RosterStory} from './service.js';
import {storyContent} from './content.js';
import type {StoryContent} from './types.js';
import {storyUnitXpPolicyReader} from '../progression/story-unit-xp.js';

type Route=(method:'GET'|'POST',path:string,run:(id:string,r:FastifyRequest)=>Promise<unknown>)=>unknown;
/** Reuses the native authenticated/no-store/strict-body route boundary. */
export function registerStoryRoutes(route:Route,db:PrismaClient,root:string,catalog:()=>RosterCatalog|null,policy:()=>BattlePolicy|null,commanderPolicy:()=>CommanderPolicy|null=()=>null){
 const flag=process.env.ROSTER60_STORY_PROFILE;
 if(flag&&flag!=='story-device-test-v1')throw Error('UNKNOWN_ROSTER60_STORY_PROFILE');
 const file=resolve(root,'data/story/story-mode-v1.json');
 const service=flag==='story-device-test-v1'?new RosterStory(db,catalog,policy,existsSync(file)?JSON.parse(readFileSync(file,'utf8')) as StoryContent:storyContent,commanderPolicy,storyUnitXpPolicyReader(root,process.env.ROSTER60_PROFILE)):null;
 const enabled=<T>(run:(service:RosterStory)=>Promise<T>)=>{if(!service)throw new RosterError(503,'STORY_PROFILE_PENDING');return run(service);};
 const requestId=z.string().min(1).max(100),revision=z.number().int().nonnegative(),questId=z.string().min(1).max(100),ownedIds=z.array(z.string().min(1).max(100)).min(1).max(6).refine(ids=>new Set(ids).size===ids.length),adventureId=z.string().uuid().optional();
 route('GET','story/state',id=>enabled(service=>service.state(id)));
 route('POST','story/open',(id,r)=>enabled(service=>service.open(id,z.object({requestId,revision,questId,phase:z.enum(['intro','return','replay'])}).strict().parse(r.body))));
 route('POST','story/advance',(id,r)=>enabled(service=>service.advance(id,z.object({requestId,revision,cursor:z.number().int().nonnegative()}).strict().parse(r.body))));
 route('POST','story/accept',(id,r)=>enabled(service=>service.accept(id,z.object({requestId,revision,questId}).strict().parse(r.body))));
 route('POST','story/close',(id,r)=>enabled(service=>service.close(id,z.object({requestId,revision}).strict().parse(r.body))));
 route('POST','story/battle/create',(id,r)=>enabled(service=>service.createBattle(id,z.object({requestId,questId,ownedIds,adventureId}).strict().parse(r.body))));
 route('POST','story/adventure/create',(id,r)=>enabled(service=>service.createBattle(id,z.object({requestId,encounterId:z.string().min(1).max(100),ownedIds,adventureId}).strict().parse(r.body))));
 // Material balances are shown only in this explicit usage interface, never the home/story HUD.
 route('GET','story/materials',id=>enabled(service=>service.materials(id)));
}
