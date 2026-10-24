// Loopback-only developer fixture. No account, production API or database access.
import http from 'node:http';
import {randomUUID} from 'node:crypto';
import {pathToFileURL} from 'node:url';
import {exampleBattle,exampleFighter} from './fixture.mjs';
export function reviewServer() {
  const sessions=new Map();
  return http.createServer(async(req,res)=>{
    const reply=(code,body)=>{res.writeHead(code,{'Content-Type':'application/json','Cache-Control':'no-store'});res.end(JSON.stringify(body));};
    if(req.headers.origin || !/^127\.0\.0\.1:\d+$/.test(req.headers.host||''))return reply(403,{error:'LOOPBACK_ONLY'});
    try {
      if(req.method!=='POST'||req.headers['content-type']!=='application/json')return reply(400,{error:'JSON_POST_REQUIRED'});
      let raw='';for await(const chunk of req){raw+=chunk;if(raw.length>8192)throw Error('BODY_TOO_LARGE');}
      const input=JSON.parse(raw);
      if(req.url==='/session') {
        if(sessions.size>=32)throw Error('RESTART_FIXTURE_SERVER');
        const id=randomUUID(),battle=exampleBattle({battleId:id,fighters:[exampleFighter('u01'),exampleFighter('u01')]});
        sessions.set(id,{battle,receipts:new Map()});return reply(200,battle.snapshot({playerId:'review-a'}));
      }
      if(req.url!=='/command')return reply(404,{error:'NOT_FOUND'});
      const session=sessions.get(input.battleId);if(!session)throw Error('SESSION_NOT_FOUND');
      // Entire player + fixture opponent turn is synchronous and cached, including retries after response loss.
      const signature=JSON.stringify(Object.keys(input).sort().map(k=>[k,input[k]]));
      const old=session.receipts.get(input.requestId);
      if(old){if(old.signature!==signature)throw Error('IDEMPOTENCY_CONFLICT');return reply(200,old.dto);}
      if(session.receipts.size>=1000)throw Error('RESTART_FIXTURE_SERVER');
      let dto=session.battle.command({playerId:'review-a'},input);
      if(dto.phase!=='over') {
        const foe=session.battle.snapshot({playerId:'review-b'});
        session.battle.command({playerId:'review-b'},{battleId:input.battleId,requestId:'opponent-'+input.requestId,
          expectedRevision:foe.revision,kind:foe.self.mp>=foe.self.attack.mpCost?'attack':'focus'});
        dto=session.battle.snapshot({playerId:'review-a'});
      }
      session.receipts.set(input.requestId,{signature,dto});reply(200,dto);
    }catch(error){reply(409,{error:error.message});}
  });
}
if(process.argv[1] && import.meta.url===pathToFileURL(process.argv[1]).href)
  reviewServer().listen(18765,'127.0.0.1',()=>console.log('LOCAL FIXTURE ONLY http://127.0.0.1:18765; balance is not final'));
