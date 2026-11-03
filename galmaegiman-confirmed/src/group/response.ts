// 단체방 챗봇 응답 빌더. 허용 말풍선만 만들고(덱 32·33), 길이 상한(ResponseGuard, 덱 24·43)과
// 금지 문구 검사(ContentPolicy, 덱 40·43·47)를 모든 응답에 적용해요.
// 응답 JSON 필드는 카카오 공개 문서 「응답 타입별 JSON 포맷」 기준:
// https://kakaobusiness.gitbook.io/main/tool/chatbot/skill_guide/answer_json_format
import type { GroupBotConfig } from './config.js';
import type { ContentPolicy } from './policy.js';

// 버튼: 메시지 전송(버튼명 자동 전송, 덱 32·33)과 웹 링크만.
// TODO(verify): share·block·operator 및 그룹 전용 버튼(도움말 guide·챗봇 멘션하기 mention)은 그룹챗봇 개발 가이드로 확인 후 추가
export type GroupButton =
  | {action:'message';label:string;messageText:string}
  | {action:'webLink';label:string;webLinkUrl:string};
// TODO(verify): 바로가기 block 동작은 blockId 확인 후 추가
export interface GroupQuickReply {action:'message';label:string;messageText:string}
export interface ListItem {title:string;description?:string;imageUrl?:string;action?:'message';messageText?:string}
export interface ItemRow {title:string;description:string}
export type GroupOutput =
  | {simpleText:{text:string}}
  | {simpleImage:{imageUrl:string;altText:string}}
  | {listCard:{header:{title:string};items:ListItem[];buttons?:GroupButton[]}}
  | {itemCard:{title?:string;description?:string;thumbnail?:{imageUrl:string};itemList:ItemRow[];buttons?:GroupButton[]}};
export interface GroupSkillResponse {version:'2.0';template:{outputs:GroupOutput[];quickReplies:GroupQuickReply[]}}

// 덱이 허용한 말풍선: 텍스트·이미지·리스트·피드형·ItemCard.
// TODO(verify): '피드형'이 어느 JSON 타입(basicCard·carousel 등)인지 공개 문서에 없음. 확인 전에는 만들지 않아요.
export const ALLOWED_OUTPUTS=['simpleText','simpleImage','listCard','itemCard'] as const;
const ITEM_TITLE_MAX=6; // 카카오: itemList의 title 최대 6자

export class GroupResponseError extends Error {constructor(message:string){super(message);this.name='GroupResponseError';}}
const fail=(message:string):never=>{throw new GroupResponseError(message);};
const len=(s:string)=>[...s].length;
const lines=(s:string)=>s.split('\n').length;
const isHttps=(u:string)=>{try{return new URL(u).protocol==='https:';}catch{return false;}};

// 만들기 도우미(타입으로 허용 말풍선만 만들 수 있게)
export const text=(t:string):GroupOutput=>({simpleText:{text:t}});
export const image=(imageUrl:string,altText:string):GroupOutput=>({simpleImage:{imageUrl,altText}});
export const list=(title:string,items:ListItem[],buttons?:GroupButton[]):GroupOutput=>({listCard:{header:{title},items,...(buttons?.length?{buttons}:{})}});
export const itemCard=(card:Extract<GroupOutput,{itemCard:unknown}>['itemCard']):GroupOutput=>({itemCard:card});
export const say=(label:string,messageText=label):GroupButton=>({action:'message',label,messageText});
export const quick=(label:string,messageText=label):GroupQuickReply=>({action:'message',label,messageText});

// 런타임 검사: 타입을 우회해 들어온 말풍선(basicCard·carousel·commerceCard 등)도 막아요
export function assertAllowedOutput(o:unknown):asserts o is GroupOutput{
  if(!o||typeof o!=='object'||Array.isArray(o))fail('말풍선 형식 오류');
  const keys=Object.keys(o as object);
  if(keys.length!==1||!(ALLOWED_OUTPUTS as readonly string[]).includes(keys[0]))fail(`허용되지 않은 말풍선: ${keys.join(',')||'(없음)'}`);
}
function checkButtons(buttons:GroupButton[]|undefined,limit:number,where:string){
  for(const b of buttons??[]){
    if(b.action!=='message'&&b.action!=='webLink')fail(`${where}: 허용되지 않은 버튼 동작`);
    if(!b.label||len(b.label)>limit)fail(`${where}: 버튼 문구는 1~${limit}자`);
    if(b.action==='message'&&!b.messageText)fail(`${where}: 버튼 전송 문구가 비었어요`);
    if(b.action==='webLink'&&!isHttps(b.webLinkUrl))fail(`${where}: 웹 링크는 https만`);
  }
  if((buttons?.length??0)>3)fail(`${where}: 버튼은 3개까지`);
}
// ResponseGuard: 응답 하나가 한 화면을 가리지 않게 개수·길이 상한을 검사해요. 상한은 data/group-bot.yaml
export function guardResponse(r:GroupSkillResponse,limits:GroupBotConfig['limits']){
  const {outputs,quickReplies}=r.template;
  if(outputs.length<1)fail('말풍선이 없어요');
  if(outputs.length>limits.outputs)fail(`말풍선은 ${limits.outputs}개까지`);
  if(quickReplies.length>limits.quickReplies)fail(`바로가기는 ${limits.quickReplies}개까지`);
  for(const q of quickReplies){
    if(q.action!=='message'||!q.messageText)fail('바로가기 형식 오류');
    if(!q.label||len(q.label)>limits.buttonLabel)fail(`바로가기 문구는 1~${limits.buttonLabel}자`);
  }
  for(const o of outputs){
    assertAllowedOutput(o);
    if('simpleText' in o){
      const t=o.simpleText.text;
      if(!t.trim())fail('빈 글');
      if(len(t)>limits.textChars)fail(`글은 ${limits.textChars}자까지(${len(t)}자)`);
      if(lines(t)>limits.textLines)fail(`글은 ${limits.textLines}줄까지(${lines(t)}줄)`);
    }else if('simpleImage' in o){
      if(!isHttps(o.simpleImage.imageUrl))fail('그림 주소는 https만');
      if(!o.simpleImage.altText||len(o.simpleImage.altText)>50)fail('그림 대체 글은 1~50자');
    }else if('listCard' in o){
      const c=o.listCard;
      if(!c.header.title)fail('리스트 제목이 비었어요');
      if(c.items.length<1||c.items.length>limits.listItems)fail(`리스트 항목은 1~${limits.listItems}개`);
      for(const i of c.items){if(!i.title)fail('리스트 항목 제목이 비었어요');if(i.imageUrl&&!isHttps(i.imageUrl))fail('그림 주소는 https만');if(i.action&&!i.messageText)fail('리스트 항목 전송 문구가 비었어요');}
      checkButtons(c.buttons,limits.buttonLabel,'리스트');
    }else{
      const c=o.itemCard;
      if(c.itemList.length<1||c.itemList.length>limits.itemCardItems)fail(`아이템 카드 목록은 1~${limits.itemCardItems}개`);
      for(const row of c.itemList)if(!row.title||len(row.title)>ITEM_TITLE_MAX)fail(`아이템 카드 목록 제목은 1~${ITEM_TITLE_MAX}자`);
      if(len((c.title??'')+(c.description??''))>200)fail('아이템 카드 제목+설명은 200자까지');
      if(c.thumbnail&&!isHttps(c.thumbnail.imageUrl))fail('그림 주소는 https만');
      checkButtons(c.buttons,limits.buttonLabel,'아이템 카드');
    }
  }
}
// 사용자에게 보이는 글 전부(버튼 전송 문구는 채팅방에 사용자 발화로 보이므로 포함)
export function visibleTexts(r:GroupSkillResponse):string[]{
  const out:string[]=[];
  const btn=(bs?:GroupButton[])=>{for(const b of bs??[])out.push(b.label,...(b.action==='message'?[b.messageText]:[]));};
  for(const o of r.template.outputs){
    if('simpleText' in o)out.push(o.simpleText.text);
    else if('simpleImage' in o)out.push(o.simpleImage.altText);
    else if('listCard' in o){out.push(o.listCard.header.title);for(const i of o.listCard.items)out.push(i.title,i.description??'',i.messageText??'');btn(o.listCard.buttons);}
    else{const c=o.itemCard;out.push(c.title??'',c.description??'');for(const row of c.itemList)out.push(row.title,row.description);btn(c.buttons);}
  }
  for(const q of r.template.quickReplies)out.push(q.label,q.messageText);
  return out.filter(Boolean);
}
export const SAFE_FALLBACK='지금은 답하기 어려워요. 잠시 뒤 다시 불러 주세요.';
// 모든 단체방 응답은 이 함수로만 만들어요: 상한 검사 → 금지 문구 검사(운영에서 걸리면 안전한 기본 응답으로)
export function buildResponse(outputs:GroupOutput[],quickReplies:GroupQuickReply[],deps:{config:GroupBotConfig;policy:ContentPolicy}):GroupSkillResponse{
  const r:GroupSkillResponse={version:'2.0',template:{outputs,quickReplies}};
  guardResponse(r,deps.config.limits);
  if(deps.policy.allow(visibleTexts(r)))return r;
  return {version:'2.0',template:{outputs:[text(SAFE_FALLBACK)],quickReplies:[]}};
}
// 봇 입장 인사(덱 24·43: 채팅창 절반 이하). 일반 상한보다 짧게 따로 검사해요.
// TODO(verify): '챗봇 멘션하기' 버튼의 JSON 형식은 그룹챗봇 개발 가이드 확인 후 추가
export function buildWelcome(message:string,quickReplies:GroupQuickReply[],deps:{config:GroupBotConfig;policy:ContentPolicy}):GroupSkillResponse{
  const {welcomeChars,welcomeLines}=deps.config.limits;
  if(len(message)>welcomeChars)fail(`입장 인사는 ${welcomeChars}자까지(${len(message)}자)`);
  if(lines(message)>welcomeLines)fail(`입장 인사는 ${welcomeLines}줄까지`);
  return buildResponse([text(message)],quickReplies,deps);
}
