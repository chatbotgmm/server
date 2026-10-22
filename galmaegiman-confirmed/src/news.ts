import {createHash} from 'node:crypto';
export type NewsKind='notices'|'events'|'inbox';
export interface NewsItem {id:string;title:string;bodyText:string;publishedAt:string;expiresAt:string|null;startsAt?:string}
export interface NewsStore {
  inbox(playerId:string,now:Date,selection:{limit:number;after?:[string,string];id?:string}):Promise<NewsItem[]>;
  reads(playerId:string,kind:NewsKind,ids:string[]):Promise<Record<string,string>>;
  // Atomically bind (playerId, requestId) to (kind,id); reject a mismatched replay.
  // First-read timestamp must not change, including across different request IDs.
  mark(playerId:string,kind:NewsKind,id:string,requestId:string,now:Date):Promise<string>;
}
export class NewsError extends Error {constructor(public status:number,public code:string){super(code);}}
const idPattern=/^[A-Za-z0-9:_-]{1,100}$/;
// Slice by Unicode scalar, never leave half an emoji or an unpaired surrogate.
export const plain=(s:unknown,max:number)=>typeof s==='string'?Array.from(s.slice(0,max*2)).slice(0,max).map(c=>c.length===1&&/[\uD800-\uDFFF]/.test(c)?'\uFFFD':c).join(''):'';
const iso=(s:unknown)=>typeof s==='string'&&Number.isFinite(Date.parse(s))?new Date(s).toISOString():null;
const key=(value:string)=>createHash('sha256').update(value).digest('hex').slice(0,32);
export function eventItems(events:readonly {label:string;from:string;to:string;claimBonus:number;snackMultiplier:number}[]):NewsItem[]{
  return events.flatMap(e=>{const start=iso(e.from),end=iso(e.to);if(!start||!end||start>=end)return [];
    return [{id:'event:'+key(JSON.stringify([e.label,start,end])),title:plain(e.label,160),bodyText:[e.claimBonus>0?`뽑기권 받기 +${e.claimBonus}장`:'',e.snackMultiplier>1?`새우gang ×${e.snackMultiplier}`:''].filter(Boolean).join(' · ')||'특별 이벤트',publishedAt:start,startsAt:start,expiresAt:end}];});
}
export class NewsService {
  constructor(private store:NewsStore,private sources:()=>{notices:NewsItem[];events:NewsItem[]},private now=()=>new Date()){}
  private async visible(player:string,kind:NewsKind,now:Date,selection:{limit:number;after?:[string,string];id?:string}={limit:51}){
    const rows=kind==='inbox'?await this.store.inbox(player,now,selection):this.sources()[kind];
    const seen=new Set<string>();
    return rows.filter(r=>{const p=iso(r.publishedAt),e=r.expiresAt===null?null:iso(r.expiresAt);if(!idPattern.test(r.id)||seen.has(r.id)||!p||(r.expiresAt!==null&&!e)|| (e!==null&&e<=now.toISOString())||(kind!=='events'&&p>now.toISOString()))return false;seen.add(r.id);return true;})
      .map(r=>({id:r.id,title:plain(r.title,160),bodyText:plain(r.bodyText,12000),publishedAt:iso(r.publishedAt)!,expiresAt:r.expiresAt?iso(r.expiresAt):null,...(kind==='events'?{startsAt:iso(r.startsAt??r.publishedAt)!}:{})}))
      .sort((a,b)=>a.publishedAt===b.publishedAt?(a.id===b.id?0:a.id<b.id?1:-1):a.publishedAt<b.publishedAt?1:-1);
  }
  async list(player:string,kind:NewsKind,query:Record<string,unknown>={}){
    if(Object.keys(query).some(k=>!['limit','cursor'].includes(k)))throw new NewsError(400,'INVALID_QUERY');
    if(query.limit!==undefined&&(typeof query.limit!=='string'&&typeof query.limit!=='number'||typeof query.limit==='string'&&!/^[1-9][0-9]?$/.test(query.limit)))throw new NewsError(400,'INVALID_LIMIT');
    const n=query.limit===undefined?20:Number(query.limit);if(!Number.isInteger(n)||n<1||n>50)throw new NewsError(400,'INVALID_LIMIT');
    const now=this.now();let after:[string,string]|undefined;
    if(query.cursor!==undefined){if(typeof query.cursor!=='string'||query.cursor.length>400||! /^[A-Za-z0-9_-]+$/.test(query.cursor))throw new NewsError(400,'INVALID_CURSOR');let c;try{c=JSON.parse(Buffer.from(query.cursor,'base64url').toString());}catch{throw new NewsError(400,'INVALID_CURSOR');}
      if(!Array.isArray(c)||c.length!==3||c[0]!==kind||typeof c[1]!=='string'||iso(c[1])!==c[1]||typeof c[2]!=='string'||!idPattern.test(c[2]))throw new NewsError(400,'INVALID_CURSOR');after=[c[1],c[2]];}
    let rows=await this.visible(player,kind,now,{limit:n+1,after});if(after)rows=rows.filter(r=>r.publishedAt<after![0]||(r.publishedAt===after![0]&&r.id<after![1]));
    const page=rows.slice(0,n),reads=await this.store.reads(player,kind,page.map(r=>r.id));const last=page.at(-1);
    return {items:page.map(r=>({...r,readAt:reads[r.id]??null,unread:!reads[r.id],reward:{state:'none' as const,claimable:false}})),nextCursor:rows.length>n&&last?Buffer.from(JSON.stringify([kind,last.publishedAt,last.id])).toString('base64url'):null,serverNow:now.toISOString()};
  }
  async read(player:string,body:unknown){
    if(!body||typeof body!=='object'||Array.isArray(body))throw new NewsError(400,'INVALID_BODY');const b=body as Record<string,unknown>;
    if(Object.keys(b).sort().join(',')!=='id,kind,requestId'||typeof b.kind!=='string'||!['notices','events','inbox'].includes(b.kind)||typeof b.id!=='string'||!idPattern.test(b.id)||typeof b.requestId!=='string'||! /^[A-Za-z0-9:_-]{8,80}$/.test(b.requestId))throw new NewsError(400,'INVALID_BODY');
    const kind=b.kind as NewsKind,now=this.now();if(!(await this.visible(player,kind,now,{limit:1,id:b.id})).some(r=>r.id===b.id))throw new NewsError(404,'NOT_FOUND');
    return {ok:true,kind,id:b.id,readAt:await this.store.mark(player,kind,b.id,b.requestId,now)};
  }
}
