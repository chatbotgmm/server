import {openSync,readSync,closeSync} from 'node:fs';
import {resolve} from 'node:path';
import {eventItems,plain,type NewsItem} from './news.js';

// Only the existing public /notices fields are accepted. Markdown stays literal
// text: consumers MUST use textContent/native text, never innerHTML or a webview.
export function noticeItems(input:unknown):NewsItem[]{
  if(!Array.isArray(input))return [];
  return input.slice(0,10000).flatMap(n=>{
    if(!n||typeof n!=='object'||typeof n.id!=='string'||!/^[A-Za-z0-9_-]{1,40}$/.test(n.id)||typeof n.title!=='string'||!n.title.trim()||typeof n.body!=='string'||typeof n.date!=='string')return [];
    // Existing calendar dates mean midnight UTC, independent of server locale.
    const date=/^\d{4}-\d{2}-\d{2}$/.test(n.date)?n.date+'T00:00:00.000Z':n.date;
    if(!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?(?:Z|[+-]\d{2}:\d{2})$/.test(date)||!Number.isFinite(Date.parse(date)))return [];
    if(n.date.length===10&&new Date(date).toISOString().slice(0,10)!==n.date)return [];
    return [{id:n.id,title:plain(n.title,160),bodyText:plain(n.body,12000),publishedAt:new Date(date).toISOString(),expiresAt:null}];
  });
}
// Bounded reads, including if an operator accidentally replaces the file with a
// huge one. Missing/malformed files safely yield no invented announcements.
export function loadNoticeItems(root:string):NewsItem[]{
  let fd:number|undefined;
  try{fd=openSync(resolve(root,'data/notices.json'),'r');const cap=2*1024*1024,buffer=Buffer.alloc(cap+1);let size=0;
    while(size<buffer.length){const n=readSync(fd,buffer,size,buffer.length-size,null);if(!n)break;size+=n;}
    return size>cap?[]:noticeItems(JSON.parse(buffer.subarray(0,size).toString('utf8')));
  }catch{return [];}finally{if(fd!==undefined)closeSync(fd);}
}
export function newsSources(root:string,events:()=>Parameters<typeof eventItems>[0]){
  // Deliberately accept events only, not economy/coupons or private player data.
  return ()=>({notices:loadNoticeItems(root),events:eventItems(events())});
}
