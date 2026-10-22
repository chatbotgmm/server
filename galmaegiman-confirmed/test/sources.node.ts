import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,mkdirSync,writeFileSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {noticeItems,loadNoticeItems,newsSources} from '../src/news-sources.js';
test('notice allowlist and explicit date interpretation; malformed rows fail closed',()=>{
 const rows=noticeItems([{id:'n',title:'공지',date:'2026-10-20',body:'**literal** <img src=x>',couponCode:'SECRET'},null,{id:'bad',title:'x',date:'2026-02-30',body:'x'}]);
 assert.deepEqual(rows,[{id:'n',title:'공지',bodyText:'**literal** <img src=x>',publishedAt:'2026-10-20T00:00:00.000Z',expiresAt:null}]);
 assert.deepEqual(noticeItems({}),[]);
});
test('missing, invalid, oversized file empty; hot reads pick up actual notices',()=>{
 const root=mkdtempSync(join(tmpdir(),'news-'));try{
 assert.deepEqual(loadNoticeItems(root),[]);mkdirSync(join(root,'data'));const file=join(root,'data/notices.json');
 for(const content of ['{','{}','x'.repeat(2*1024*1024+1),'[]']){writeFileSync(file,content);assert.deepEqual(loadNoticeItems(root),[]);}
 writeFileSync(file,JSON.stringify([{id:'live',title:'실제',date:'2026-10-21',body:'게시'}]));assert.equal(loadNoticeItems(root)[0].id,'live');
 const get=newsSources(root,()=>[{label:'event',from:'2026-10-22T00:00:00Z',to:'2026-10-23T00:00:00Z',claimBonus:1,snackMultiplier:1,couponCode:'SECRET'} as any]);
 assert.equal(JSON.stringify(get()).includes('SECRET'),false);
 }finally{rmSync(root,{recursive:true,force:true});}
});
