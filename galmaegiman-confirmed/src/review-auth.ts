// 별도 발급·승인 후에만 켜는 심사 로그인. DB 쓰기/계정 생성 없음.
import {randomBytes, scrypt as derive, timingSafeEqual} from 'node:crypto';
import type {PrismaClient} from '@prisma/client';
export const REVIEW_IDENTITY='review:play-store';
export const REVIEW_ERROR='심사 로그인 정보를 확인하거나 잠시 후 다시 시도해 주세요.';
export interface ReviewConfig {enabled:boolean;username:string;playerId:string;passwordHash:string;version:string}
export function reviewConfig(env:NodeJS.ProcessEnv):ReviewConfig|undefined {
  if(env.REVIEW_LOGIN_ENABLED!=='true')return undefined;
  const c={enabled:true,username:env.REVIEW_LOGIN_USERNAME??'',playerId:env.REVIEW_LOGIN_PLAYER_ID??'',passwordHash:env.REVIEW_LOGIN_PASSWORD_HASH??'',version:env.REVIEW_LOGIN_VERSION??''};
  // 고정 scrypt 비용(N=131072,r=8,p=1), 16바이트 이상 salt/32바이트 키. 평문 비밀번호는 받지 않음.
  return validConfig(c)?c:undefined;
}
function validConfig(c:ReviewConfig){return c.enabled&&/^[A-Za-z0-9_@-]{8,80}$/.test(c.username)&&/^[A-Za-z0-9-]{1,80}$/.test(c.playerId)&&/^[A-Za-z0-9_-]{8,80}$/.test(c.version)&&/^scrypt\$131072\$8\$1\$(?:[0-9a-f]{2}){16,32}\$[0-9a-f]{64}$/.test(c.passwordHash);}
const hash=(password:string,salt:string)=>new Promise<Buffer>((resolve,reject)=>derive(password,Buffer.from(salt,'hex'),32,{N:131072,r:8,p:1,maxmem:192*1024*1024},(e,key)=>e?reject(e):resolve(key)));
const equal=(a:string,b:string)=>{const x=Buffer.from(a),y=Buffer.from(b);return x.length===y.length&&timingSafeEqual(x,y);};
export class ReviewAuth {
  private sessions=new Map<string,{expires:number;version:string}>();
  private sources=new Set<string>();
  private waiting:Array<()=>void>=[];
  private working=false;
  constructor(private db:PrismaClient,private config?:ReviewConfig,private now=Date.now){}
  async account(){
    const c=this.config;if(!c||!validConfig(c))return null;
    const p=await this.db.player.findUnique({where:{id:c.playerId},include:{kakao:true,chatApp:true,adminSnapshot:true}}).catch(()=>null);
    return p&&p.identity===REVIEW_IDENTITY&&!p.isAdmin&&!p.adminMode&&!p.kakao&&!p.chatApp&&!p.adminSnapshot?p.id:null;
  }
  async login(username:unknown,password:unknown,source='local'){
    const c=this.config;if(!c||!validConfig(c)||typeof username!=='string'||typeof password!=='string'||username.length>80||password.length<16||password.length>256)return null;
    // 요청 출처별 rate limit은 HTTP 경계에서 적용. 출처마다 진행/대기 1개,
    // 전체 KDF 1개 + FIFO 대기 8개: 한 출처가 대기열 전체를 차지하지 못한다.
    if(this.sources.has(source)||this.waiting.length>=8)return null;
    this.sources.add(source);
    if(this.working)await new Promise<void>(resolve=>this.waiting.push(resolve));
    else this.working=true;
    try{
      if(this.config!==c||!validConfig(c))return null;
      const now=this.now();
      const parts=c.passwordHash.split('$'),key=await hash(password,parts[4]);
      if(!equal(username,c.username)||!timingSafeEqual(key,Buffer.from(parts[5],'hex'))||!await this.account()||this.config!==c||!validConfig(c))return null;
      for(const [k,s] of this.sessions)if(s.expires<=now)this.sessions.delete(k);
      if(this.sessions.size>=20)this.sessions.delete(this.sessions.keys().next().value!);
      const token='rv_'+randomBytes(32).toString('base64url');
      this.sessions.set(token,{expires:now+12*3600000,version:c.version});return token;
    }catch{return null;}finally{this.sources.delete(source);const next=this.waiting.shift();if(next)next();else this.working=false;}
  }
  async authenticate(token:string){const s=this.sessions.get(token);if(!s||s.expires<=this.now()||!this.config?.enabled||s.version!==this.config.version){this.sessions.delete(token);return null;}const id=await this.account();if(!id)this.sessions.clear();return id;}
  revoke(token:string){this.sessions.delete(token);}
  close(){this.sessions.clear();this.config=undefined;}
}
// 새 API는 명시적으로 검토하여 추가. 계정 연결/삭제, 관리자, 쿠폰 제외.
// 실시간 PvP(일반·랭크)는 일반 이용자와 같은 규칙·매칭으로 연다. 심사 계정에만 다른 게임 동작을 두지 않는다.
const reads=new Set(['native/menu-access','who','me','craft','draw','daily','tutorial','story','duel-key','native/news/notices','native/news/events','native/news/inbox','native/catalog','native/state','native/progression','native/battle/current',
  'pvp/queue/current','pvp/room/current','pvp/rank']);
const writes=new Set(['native/news/read','logout','pvp/queue/attempt','pvp/queue/join','pvp/queue/cancel','pvp/room/command','pvp/room/surrender','party','units/protect','expedition/start','expedition/claim','shop','items/buy','items/use-field','items/use','battle-team','craft','mix','claim','attend','draw','tutorial','story','story/battle','native/story/reset','native/team','native/growth','native/heal','native/battle/start','native/battle/turn','native/progression/pass/claim','native/progression/reroll/claim','native/progression/reincarnate','native/progression/reassess']);
// Equivalent new-roster gameplay routes; no account/admin/coupon capability added.
for(const path of ['state','home','collection','recruit','recruit/session','battle/options','battle/active','adventure/active','craft/options','reincarnation/preview','pvp/options','pvp/current'])reads.add('native/roster60/'+path);
for(const path of ['start','complete','preview','confirm','home/rebirth-intro/complete','battle/create','battle/read','battle/action','battle/settle','adventure/start','adventure/read','adventure/return','craft/preview','craft/receipt','craft/confirm','reincarnation/confirm','recruit','recruit/session/start','recruit/session/action','recruit/session/read','pvp/party','pvp/join','pvp/cancel','pvp/accept','pvp/action','pvp/settle'])writes.add('native/roster60/'+path);
export function reviewRouteAllowed(method:string,path:string,body:unknown){
  if(!(method==='GET'?reads:method==='POST'?writes:new Set<string>()).has(path))return false;
  if(path==='native/battle/start')return !!body&&typeof body==='object'&&['story','practice','normal'].includes(String((body as {mode?:unknown}).mode));
  return true;
}
