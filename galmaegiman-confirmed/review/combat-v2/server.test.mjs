import {test} from 'node:test';
import assert from 'node:assert/strict';
import {reviewServer} from './server.mjs';
import {writeFile} from 'node:fs/promises';
test('real HTTP DTO: six skills, retry, concurrency, revision, focus and nonproduction boundary',async()=>{
 const server=reviewServer();await new Promise(r=>server.listen(0,'127.0.0.1',r));
 try {
 const url=`http://127.0.0.1:${server.address().port}`;
 const post=async(path,data,headers={})=>{const r=await fetch(url+path,{method:'POST',headers:{'Content-Type':'application/json',...headers},body:JSON.stringify(data)});return {status:r.status,body:await r.json()};};
 let {body:dto}=await post('/session',{});assert.equal(dto.skills.length,6);assert.equal(dto.self.mp,dto.self.maxMp);
 const command={battleId:dto.battleId,expectedRevision:0,requestId:'one',kind:'skill',skillId:dto.skills[5].id};
 const replies=await Promise.all([post('/command',command),post('/command',command)]);
 assert.deepEqual(replies[0],replies[1]);dto=replies[0].body;assert.equal(dto.revision,2);assert.equal(dto.self.mp,5);
 assert.equal((await post('/command',{...command,kind:'focus'})).body.error,'IDEMPOTENCY_CONFLICT');
 assert.equal((await post('/command',{...command,requestId:'stale'})).body.error,'STALE_REVISION');
 dto=(await post('/command',{battleId:dto.battleId,expectedRevision:2,requestId:'two',kind:'focus'})).body;
 assert.equal(dto.self.mp,8);assert.equal(dto.revision,4);assert.equal(dto.events.length,4);
 assert.equal((await post('/session',{}, {Origin:'https://example.test'})).status,403);
 if(process.env.REVIEW_DTO_OUTPUT)await writeFile(process.env.REVIEW_DTO_OUTPUT,JSON.stringify(dto));
 }finally{await new Promise(r=>server.close(r));}
});
