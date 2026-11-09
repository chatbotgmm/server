import type {PrismaClient} from '@prisma/client';
import {z} from 'zod';

// 랜덤 디펜스 계정 저장(2026-11-09): 로그인한 사람의 모드별 최고 웨이브와 이어하기 판 하나.
// 게임 계산은 브라우저에서 하므로 서버는 모양·크기만 확인하고 그대로 보관한다(기록 조작을 막는 장치는 아님).
export const DEFENSE_MODES=['normal','hard','noupg','nostory'] as const;   // public/defense/engine.js의 MODES와 같아야 함(시험으로 확인)
export const DEFENSE_DIFFS=['intro','mid','high','master','artisan'] as const;   // engine.js의 DIFF_ORDER와 같아야 함. 달인(master)이 예전 규칙
// 최고 기록 키: 모드(달인) 또는 「모드.난이도」(예: normal.intro) — 화면의 recKey와 같음
export const DEFENSE_REC_KEYS=DEFENSE_MODES.flatMap(m=>DEFENSE_DIFFS.map(d=>d==='master'?m:`${m}.${d}`)) as [string,...string[]];
export const DEFENSE_CLEAR=61;                                            // 60웨이브 클리어 = 61
export const DEFENSE_SAVE_MAX=120_000;                                    // 이어하기 판 글자 수 상한
export const DEFENSE_BODY_LIMIT=160_000;

const mode=z.enum(DEFENSE_MODES),recKey=z.enum(DEFENSE_REC_KEYS);
const wave=z.number().int().min(0).max(DEFENSE_CLEAR);
export const defenseBody=z.object({
  best:z.partialRecord(recKey,wave).optional(),
  save:z.union([z.null(),z.object({
    data:z.string().min(2).max(DEFENSE_SAVE_MAX).refine(s=>{try{const v=JSON.parse(s);return !!v&&typeof v==='object'&&!Array.isArray(v);}catch{return false;}},'판 저장 모양이 아니에요'),
    wave:wave.min(1),mode,at:z.number().int().positive(),
  }).strict()]).optional(),
}).strict();
export type DefenseBody=z.infer<typeof defenseBody>;

type Best=Partial<Record<string,number>>;
const readBest=(raw:string|undefined):Best=>{
  try{const v=JSON.parse(raw??'{}');const r=z.partialRecord(recKey,wave).safeParse(v);return r.success?r.data as Best:{};}catch{return {};}
};
const view=(row:{best:string;save:string|null;saveWave:number|null;saveMode:string|null;saveAt:Date|null}|null)=>({
  best:readBest(row?.best),
  save:row?.save&&row.saveAt?{data:row.save,wave:row.saveWave??1,mode:row.saveMode??'normal',at:row.saveAt.getTime()}:null,
});

export async function defenseLoad(db:PrismaClient,playerId:string){
  return view(await db.defenseSave.findUnique({where:{playerId}}));
}

// 최고 기록은 모드마다 큰 값으로 합치고, 이어하기 판은 더 나중(at)에 저장한 것만 받는다(오래된 탭이 덮어쓰지 않게).
export async function defenseStore(db:PrismaClient,playerId:string,body:DefenseBody){
  return db.$transaction(async tx=>{
    const row=await tx.defenseSave.findUnique({where:{playerId}});
    const best=readBest(row?.best);
    for(const [k,v] of Object.entries(body.best??{}) as [keyof Best,number][])if(v>(best[k]??0))best[k]=v;
    const data:{best:string;save?:string|null;saveWave?:number|null;saveMode?:string|null;saveAt?:Date|null}={best:JSON.stringify(best)};
    let saved=false;
    if(body.save===null){Object.assign(data,{save:null,saveWave:null,saveMode:null,saveAt:null});saved=true;}
    else if(body.save&&!(row?.saveAt&&row.saveAt.getTime()>body.save.at)){
      Object.assign(data,{save:body.save.data,saveWave:body.save.wave,saveMode:body.save.mode,saveAt:new Date(body.save.at)});saved=true;
    }
    const next=await tx.defenseSave.upsert({where:{playerId},create:{playerId,...data},update:data});
    return {...view(next),saved};
  });
}
