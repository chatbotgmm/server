// 서버 전용: data/battle.yaml 읽기(검사 포함). 앱에는 서버가 검사한 결과를 JSON으로 보냄
import { readFileSync } from 'node:fs';
import { parse } from 'yaml';
import { ZodError } from 'zod';
import { loadContent } from '../content.js';
import { buildRules, type Rules } from './rules.js';

// 잘못 고쳤을 때 "어디가 왜" 틀렸는지 한국어로
function explain(e:unknown):string{
  if(e instanceof ZodError)return e.issues.slice(0,8).map(i=>{
    const where=i.path.join('.')||'(파일 전체)';
    const why=i.code==='invalid_value'||i.code==='invalid_union'?'쓸 수 없는 값이에요(철자·코드 확인)'
      :i.code==='invalid_type'?`종류가 달라요(${i.expected} 필요)`:i.code==='too_small'?'값이 너무 작아요':i.code==='too_big'?'값이 너무 커요'
      :i.code==='unrecognized_keys'?`모르는 항목: ${i.keys.join(', ')}`:i.message;
    return `  - ${where}: ${why}`;
  }).join('\n');
  return `  - ${(e as Error).message}`;
}
export function loadBattleRules(root=process.cwd()):Rules{
  const file=`${root}/data/battle.yaml`;
  let raw:unknown;
  try{raw=parse(readFileSync(file,'utf8'));}catch(e){throw new Error(`data/battle.yaml을 읽지 못했어요(들여쓰기·콜론·괄호 확인):\n  - ${(e as Error).message}`);}
  try{return buildRules(raw,raw&&typeof raw==='object'&&'recipePower' in raw?loadContent(root):undefined);}catch(e){throw new Error(`data/battle.yaml 오류:\n${explain(e)}`);}
}
