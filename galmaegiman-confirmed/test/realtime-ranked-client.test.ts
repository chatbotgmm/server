// MANUAL DRAFT: not executed. Run in the project's Vitest environment after review.
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
// @ts-ignore The browser controller intentionally remains an unbundled JS module.
import {RealtimeRankedClient} from '../public/duel/realtime-ranked-client.js';

const deferred=()=>{let resolve!:(v:any)=>void,reject!:(e:any)=>void;const promise=new Promise<any>((a,b)=>{resolve=a;reject=b;});return {promise,resolve,reject};};
const room=(patch:any={})=>({roomId:'r1',mode:'rank',phaseId:'r1:1',revision:1,deadline:31000,serverNow:1000,canSubmit:true,mySubmitted:false,opponentReady:false,result:null,battle:{phase:'action'},...patch});
function fixture(api:any){let seq=0;const changes:any[]=[];const client=new RealtimeRankedClient({api,onChange:(s:any)=>changes.push(s),now:()=>1000,uuid:()=>`request-${++seq}`,storage:{getItem:()=>null,setItem:()=>{},removeItem:()=>{}}});return {client,changes};}
beforeEach(()=>vi.useFakeTimers());
afterEach(()=>{vi.clearAllTimers();vi.useRealTimers();});

describe('realtime human matchmaking client',()=>{
  it('cancels while attempt creation is pending and never joins afterward',async()=>{
    const attempt=deferred();const api=vi.fn(async(path:string,_body?:any)=>{
      if(path==='pvp/queue/current')return {status:'idle'};
      if(path==='pvp/queue/attempt')return attempt.promise;
      if(path==='pvp/queue/cancel')return {status:'cancelled'};
      throw new Error(path);
    });const {client}=fixture(api);const start=client.start('rank');await vi.advanceTimersByTimeAsync(0);client.cancel();
    expect(client.state.status).toBe('cancelling');
    attempt.resolve({attemptId:'a',token:'t'});await start;
    expect(api.mock.calls.map(x=>x[0])).toEqual(['pvp/queue/current','pvp/queue/attempt','pvp/queue/cancel']);
    expect(client.state.status).toBe('cancelled');
  });
  it('does not report cancellation before server acknowledgement',async()=>{
    const cancel=deferred();const api=vi.fn(async(path:string,_body?:any)=>path==='pvp/queue/current'?{status:'idle'}:path==='pvp/queue/attempt'?{attemptId:'a',token:'t'}:path==='pvp/queue/join'?{status:'waiting'}:cancel.promise);
    const {client}=fixture(api);await client.start();client.cancel();
    expect(client.state.status).toBe('cancelling');expect(client.dismiss()).toBe(false);
    cancel.resolve({status:'cancelled'});await vi.advanceTimersByTimeAsync(0);
    expect(client.state.status).toBe('cancelled');
  });
  it('enters a matched room when cancel loses the race, without surrendering',async()=>{
    const api=vi.fn(async(path:string,_body?:any)=>{
      if(path==='pvp/queue/current')return {status:'idle'};
      if(path==='pvp/queue/attempt')return {attemptId:'a',token:'t'};
      if(path==='pvp/queue/join')return {status:'waiting'};
      if(path==='pvp/queue/cancel')return {status:'matched',roomId:'r1'};
      if(path.startsWith('pvp/room/current'))return room();throw new Error(path);
    });const {client}=fixture(api);await client.start();client.cancelWanted=true;await client.cancelRequest();
    expect(client.state.room.roomId).toBe('r1');expect(api.mock.calls.some(x=>x[0].includes('surrender'))).toBe(false);
  });
  it('does not let a delayed waiting response resurrect cancelled queue state',async()=>{
    const {client}=fixture(vi.fn());client.state.status='cancelled';await client.acceptQueue({status:'waiting'});
    expect(client.state.status).toBe('cancelled');
  });
  it('retries an uncertain action with exactly the same request ID and payload',async()=>{
    let fail=true;const api=vi.fn(async(path:string,_body?:any)=>{
      if(path==='pvp/room/command'){if(fail){fail=false;throw new Error('offline');}return room({mySubmitted:true,canSubmit:false,revision:2});}
      return room();
    });const {client}=fixture(api);client.acceptRoom(room());
    await client.submit({action:{kind:'skill',skillId:'basic'}});const pending=client.pendingCommand;
    expect(pending).not.toBeNull();await client.retryCommand();
    expect(api.mock.calls.filter(x=>x[0]==='pvp/room/command').map(x=>x[1])).toEqual([pending,pending]);
    expect(client.state.pending).toBe(false);
  });
  it('clears a successful same-phase reroll without extending server deadline',async()=>{
    const api=vi.fn(async(_path:string,_body?:any)=>room({revision:2}));const {client}=fixture(api);client.acceptRoom(room());
    await client.submit({reroll:0});expect(client.pendingCommand).toBeNull();
    expect(client.state.room.deadline).toBe(31000);expect(client.seconds()).toBe(30);
  });
  it('discards old revisions and never performs local timeout actions',async()=>{
    const api=vi.fn();const {client}=fixture(api);client.acceptRoom(room({revision:7,deadline:500}));
    client.acceptRoom(room({revision:6,deadline:99000}));
    expect(client.state.room.revision).toBe(7);expect(client.seconds()).toBe(0);expect(api).not.toHaveBeenCalled();
  });
  it('rejects duplicate click submissions while a request is in flight',async()=>{
    const response=deferred(),api=vi.fn((_path:string,_body?:any)=>response.promise);const {client}=fixture(api);client.acceptRoom(room());
    const a=client.submit({action:{kind:'skill',skillId:'first'}});await client.submit({action:{kind:'skill',skillId:'second'}});
    expect(api).toHaveBeenCalledTimes(1);response.resolve(room({mySubmitted:true,canSubmit:false,revision:2}));await a;
  });
  it('restores a matched room through authoritative queue/current after reload',async()=>{
    const api=vi.fn(async(path:string,_body?:any)=>path==='pvp/queue/current'?{status:'matched',roomId:'r1'}:room());
    const {client}=fixture(api);await client.recover();expect(client.state.room.roomId).toBe('r1');
    expect(api.mock.calls.some(x=>x[0]==='pvp/queue/join')).toBe(false);
  });
  it('tombstones a known uncertain join before accepting idle',async()=>{
    const cancelled=deferred();const api=vi.fn((_path:string,_body?:any)=>cancelled.promise);const {client}=fixture(api);
    client.attempt={attemptId:'known',token:'token'};client.cancelId='cancel-once';client.state.status='joining';
    const reconcile=client.acceptQueue({status:'idle'});
    expect(client.state.status).toBe('cancelling');expect(client.attempt.attemptId).toBe('known');
    expect(api).toHaveBeenCalledWith('pvp/queue/cancel',{attemptId:'known',token:'token',requestId:'cancel-once'});
    cancelled.resolve({status:'cancelled',attemptId:'known'});await reconcile;
    expect(client.attempt).toBeNull();expect(client.state.status).toBe('cancelled');
  });
  it('honors cancellation during preflight without creating an attempt',async()=>{
    const current=deferred(),api=vi.fn((_path:string,_body?:any)=>current.promise);const {client}=fixture(api);
    const start=client.start();client.cancel();current.resolve({status:'idle'});await start;
    expect(api.mock.calls.map(x=>x[0])).toEqual(['pvp/queue/current']);expect(client.state.status).toBe('cancelled');
  });
  it('start resumes an existing room instead of creating an attempt',async()=>{
    const api=vi.fn(async(path:string,_body?:any)=>path==='pvp/queue/current'?{status:'matched',roomId:'r1'}:room());
    const {client}=fixture(api);await client.start();expect(client.state.room.roomId).toBe('r1');
    expect(api.mock.calls.some(x=>x[0]==='pvp/queue/attempt')).toBe(false);
  });
  it('does not apply another attempt cancellation marker after account switch',async()=>{
    const api=vi.fn(async(_path:string,_body?:any)=>({status:'waiting',attemptId:'account-B-attempt',token:'B'}));
    const {client}=fixture(api);client.storage={getItem:()=> 'account-A-attempt',setItem:()=>{},removeItem:()=>{}};
    await client.recover();expect(client.cancelWanted).toBe(false);
    expect(api.mock.calls.some(x=>x[0]==='pvp/queue/cancel')).toBe(false);
  });
  it.each(['command','surrender'])('ignores stale %s responses after generation change',async(kind)=>{
    const response=deferred(),api=vi.fn((_path:string,_body?:any)=>response.promise);const {client}=fixture(api);client.acceptRoom(room());
    const pending=kind==='command'?client.submit({action:{kind:'skill',skillId:'basic'}}):client.surrender();
    client.generation++;client.state={status:'preparing',room:null,error:null,pending:false};
    response.resolve(room({revision:2,result:{winner:0,settlement:'settled'}}));await pending;
    expect(client.state.status).toBe('preparing');expect(client.state.room).toBeNull();
  });
  it('keeps preparation lineup authoritative until the server acknowledges draft save',async()=>{
    const response=deferred(),api=vi.fn((_path:string,_body?:any)=>response.promise);const {client}=fixture(api);
    const preparation={selectedIds:['D1','D2','D3'],ready:false,opponentReady:false,available:[]};
    client.acceptRoom(room({phase:'prepare',battle:null,preparation}));
    const save=client.submit({prepare:{team:['D1','D2','D4'],ready:false}});
    expect(client.state.room.preparation.selectedIds).toEqual(['D1','D2','D3']);
    expect(client.state.pending).toBe(true);
    response.resolve(room({phase:'prepare',battle:null,revision:2,preparation:{...preparation,selectedIds:['D1','D2','D4']}}));await save;
    expect(client.state.room.preparation.selectedIds).toEqual(['D1','D2','D4']);expect(client.state.pending).toBe(false);
  });
  it('prevents simultaneous draft and ready requests from overwriting each other',async()=>{
    const response=deferred(),api=vi.fn((_path:string,_body?:any)=>response.promise);const {client}=fixture(api);
    client.acceptRoom(room({phase:'prepare',battle:null,preparation:{selectedIds:['D1','D2','D3'],ready:false}}));
    const save=client.submit({prepare:{team:['D1','D2','D4'],ready:false}});
    await client.submit({prepare:{team:['D1','D2','D4'],ready:true}});
    expect(api).toHaveBeenCalledTimes(1);
    response.resolve(room({phase:'prepare',battle:null,revision:2,preparation:{selectedIds:['D1','D2','D4'],ready:false}}));await save;
  });
  it('waits for the server to advance preparation and adopts the new augment deadline',async()=>{
    const api=vi.fn(async(_path:string,_body?:any)=>room({phase:'choose',phaseId:'r1:choose1',revision:3,deadline:61000,battle:{phase:'choose'},preparation:null}));
    const {client}=fixture(api);client.acceptRoom(room({phase:'prepare',battle:null,preparation:{selectedIds:['D1','D2','D3'],ready:false}}));
    await client.submit({prepare:{team:['D1','D2','D3'],ready:true}});
    expect(client.state.room.phase).toBe('choose');expect(client.state.room.phaseId).toBe('r1:choose1');expect(client.state.room.deadline).toBe(61000);
  });
  it('preparation timeout never chooses a local lineup or advances the phase',()=>{
    const api=vi.fn(),{client}=fixture(api);client.acceptRoom(room({phase:'prepare',deadline:500,battle:null,preparation:{selectedIds:['D1','D2','D3'],ready:false}}));
    expect(client.seconds()).toBe(0);expect(client.state.room.phase).toBe('prepare');expect(api).not.toHaveBeenCalled();
  });
  it('never optimistically decrements item stock before authoritative escrow acknowledgement',async()=>{
    const response=deferred(),api=vi.fn((_path:string,_body?:any)=>response.promise);const {client}=fixture(api);
    client.acceptRoom(room({phase:'action',battle:{phase:'action',inventory:[{id:'potion',count:2}]}}));
    const use=client.submit({action:{kind:'item',item:'potion'}});
    expect(client.state.room.battle.inventory[0].count).toBe(2);
    response.resolve(room({phase:'action',revision:2,mySubmitted:true,canSubmit:false,battle:{phase:'action',inventory:[{id:'potion',count:1}]}}));await use;
    expect(client.state.room.battle.inventory[0].count).toBe(1);
    expect(api.mock.calls[0][1].command).toEqual({action:{kind:'item',item:'potion'}});
  });
  it('retries feather with the same request ID and explicit dead-slot target',async()=>{
    let first=true;const api=vi.fn(async(_path:string,_body?:any)=>{if(first){first=false;throw new Error('offline');}return room({revision:2,mySubmitted:true,canSubmit:false});});
    const {client}=fixture(api);client.acceptRoom(room());await client.submit({action:{kind:'item',item:'feather',target:2}});
    await client.retryCommand();expect(api).toHaveBeenCalledTimes(2);expect(api.mock.calls[0][1]).toEqual(api.mock.calls[1][1]);
    expect(api.mock.calls[1][1].command.action.target).toBe(2);
  });
});

// queue-ui-acceptance-v3-tests
// Draft regression tests, not executed. Apply after web-queue-ui.py transform.
const queueUiDeferred=()=>{let resolve!:(v:any)=>void;const promise=new Promise<any>(r=>resolve=r);return {promise,resolve};};
const queueUiMake=(api:any,now=()=>1000)=>new RealtimeRankedClient({api,onChange:()=>{},now,elapsedNow:now,uuid:()=> 'request',storage:{getItem:()=>null,setItem:()=>{},removeItem:()=>{}}});
describe('queue presentation state',()=>{
  it('measures elapsed wait rather than counting down toward AI',()=>{
    let now=1000;const api=vi.fn(),c=queueUiMake(api,()=>now);c.state.waitStartedAt=1000;
    now=46000;expect(c.queueElapsed()).toBe(45);expect(api).not.toHaveBeenCalled();
    now=500;expect(c.queueElapsed()).toBe(0);
  });
  it('shows requested mode while preflight is pending and preserves cancellation',async()=>{
    const pending=queueUiDeferred(),api=vi.fn(()=>pending.promise),c=queueUiMake(api);
    const start=c.start('normal');expect(c.state.mode).toBe('normal');expect(c.queueElapsed()).toBe(0);
    c.cancel();pending.resolve({status:'idle'});await start;
    expect(c.state.status).toBe('cancelled');expect(api).toHaveBeenCalledTimes(1);
  });
  it('uses authoritative recovered mode instead of guessing from the clicked mode',async()=>{
    const api=vi.fn(async()=>({status:'waiting',mode:'rank',attemptId:'a',token:'t'})),c=queueUiMake(api);
    await c.start('normal');expect(c.state.mode).toBe('rank');
  });
  it('serializes explicit status retries and resets pending state',async()=>{
    const pending=queueUiDeferred(),api=vi.fn(()=>pending.promise),c=queueUiMake(api);
    const first=c.retryQueueStatus();expect(c.state.queueRetrying).toBe(true);
    await c.retryQueueStatus();expect(api).toHaveBeenCalledTimes(1);
    pending.resolve({status:'idle'});await first;expect(c.state.queueRetrying).toBe(false);
  });
  it('does not start a duplicate retry during an in-flight join request',async()=>{
    const api=vi.fn(),c=queueUiMake(api);c.creating=true;
    await c.retryQueueStatus();expect(api).not.toHaveBeenCalled();
  });
  it('restores elapsed time from server queuedAt without making an AI request',async()=>{
    let mono=1000;const api=vi.fn(),c=queueUiMake(api,()=>mono);c.state.status='recovering';
    await c.acceptQueue({status:'waiting',mode:'normal',queuedAt:10000,serverNow:55000});
    expect(c.queueElapsed()).toBe(45);expect(c.state.waitObserved).toBe(false);
    mono=6000;expect(c.queueElapsed()).toBe(50);expect(api).not.toHaveBeenCalled();
  });
  it('disables duplicate cancellation writes while acknowledgement is pending',async()=>{
    const d=queueUiDeferred(),api=vi.fn(()=>d.promise),c=queueUiMake(api);c.state.status='waiting';c.attempt={attemptId:'a',token:'t'};c.cancelId='cancel';
    c.cancel();c.cancel();expect(api).toHaveBeenCalledTimes(1);expect(c.state.status).toBe('cancelling');expect(c.dismiss()).toBe(false);
    d.resolve({status:'cancelled'});await vi.advanceTimersByTimeAsync(0);expect(c.state.status).toBe('cancelled');
  });
  it('sends protocol3 with queue attempt',async()=>{
    const api=vi.fn(async(path:string)=>path==='pvp/queue/current'?{status:'idle'}:path==='pvp/queue/attempt'?{attemptId:'a',token:'t'}:{status:'waiting'}),c=queueUiMake(api);
    await c.start('rank');expect(api).toHaveBeenCalledWith('pvp/queue/attempt',{mode:'rank',protocol:3});
  });
  it('waits for authoritative acceptance acknowledgement and never locally advances preparation',async()=>{
    const d=queueUiDeferred(),api=vi.fn(()=>d.promise),c=queueUiMake(api);
    const r={roomId:'r',phase:'accept',phaseId:'r:accept',revision:1,mode:'normal',battle:null,acceptance:{accepted:false,opponentAccepted:false},canSubmit:true,mySubmitted:false,result:null,deadline:11000,serverNow:1000};
    c.acceptRoom(r);const send=c.submit({accept:true});expect(c.state.room.acceptance.accepted).toBe(false);
    d.resolve({...r,revision:2,canSubmit:false,acceptance:{accepted:true,opponentAccepted:false}});await send;
    expect(c.state.room.phase).toBe('accept');expect(c.state.room.acceptance.accepted).toBe(true);
  });
  it('reconciles requeued acceptance timeout through queue/current without showing draw results',async()=>{
    const api=vi.fn(async()=>({status:'waiting',mode:'rank',attemptId:'again',token:'t',queuedAt:500,serverNow:1000})),c=queueUiMake(api);
    c.acceptRoom({roomId:'r',mode:'rank',revision:3,phase:'over',battle:null,result:{winner:null,reason:'MATCH_TIMEOUT',settlement:'not_applicable',requeued:true}});
    await vi.advanceTimersByTimeAsync(0);expect(api).toHaveBeenCalledWith('pvp/queue/current');expect(c.state.room).toBeNull();expect(c.state.status).toBe('waiting');
  });
  it('returns a declining user to lobby without treating decline as surrender or defeat',()=>{
    const api=vi.fn(),c=queueUiMake(api);c.acceptRoom({roomId:'r',revision:3,phase:'over',battle:null,result:{winner:null,reason:'MATCH_DECLINED',settlement:'not_applicable',requeued:false}});
    expect(c.state.matchDismissed).toBe(true);expect(c.state.room).toBeNull();expect(c.state.status).toBe('idle');expect(api).not.toHaveBeenCalled();
  });
  it('ignores acceptance response from a stale request generation',async()=>{
    const d=queueUiDeferred(),api=vi.fn(()=>d.promise),c=queueUiMake(api);
    const r={roomId:'old',phase:'accept',phaseId:'old:accept',revision:1,canSubmit:true,mySubmitted:false,result:null,deadline:11000,serverNow:1000,battle:null,acceptance:{accepted:false,opponentAccepted:false}};
    c.acceptRoom(r);const send=c.submit({accept:true});c.generation++;c.state.room=null;c.state.status='waiting';
    d.resolve({...r,revision:2,acceptance:{accepted:true,opponentAccepted:true}});await send;
    expect(c.state.room).toBeNull();expect(c.state.status).toBe('waiting');
  });
  it('retries an uncertain acceptance with the exact request ID and choice',async()=>{
    let fail=true;const r={roomId:'r',phase:'accept',phaseId:'r:accept',revision:1,canSubmit:true,mySubmitted:false,result:null,deadline:11000,serverNow:1000,battle:null,acceptance:{accepted:false,opponentAccepted:false}};
    const api=vi.fn(async(_path:string,_body?:unknown)=>{if(fail){fail=false;throw new Error('offline');}return {...r,revision:2,canSubmit:false,acceptance:{accepted:true,opponentAccepted:false}};}),c=queueUiMake(api);
    c.acceptRoom(r);await c.submit({accept:true});const body=api.mock.calls[0][1];await c.retryCommand();
    expect(api.mock.calls[1][1]).toEqual(body);expect((body as any).command).toEqual({accept:true});
  });
});

// V3.3 queue recovery regression drafts. No live network or battle engine.
describe('clean queue failure recovery',()=>{
  const http=(status:number,message='request rejected')=>Object.assign(new Error(message),{status});
  it('stops on version rejection before attempt creation, without joining',async()=>{
    const api=vi.fn(async(path:string,_body?:any)=>{if(path==='pvp/queue/current')return {status:'idle'};throw http(400,'새로고침해 주세요.');});
    const {client:c}=fixture(api);await c.start('normal');
    expect(c.state.status).toBe('failed');expect(c.running).toBe(false);expect(c.state.error).toBe('새로고침해 주세요.');
    expect(api.mock.calls.map(x=>x[0])).toEqual(['pvp/queue/current','pvp/queue/attempt']);
    await vi.advanceTimersByTimeAsync(60000);expect(api).toHaveBeenCalledTimes(2);
  });
  it('tombstones a rejected join then reports failure, never successful waiting',async()=>{
    const api=vi.fn(async(path:string,_body?:any)=>{
      if(path==='pvp/queue/current')return {status:'idle'};
      if(path==='pvp/queue/attempt')return {attemptId:'a',token:'t'};
      if(path==='pvp/queue/join')throw http(400,'출전 조건을 확인해 주세요.');
      if(path==='pvp/queue/cancel')return {status:'cancelled'};throw Error(path);
    });const {client:c,changes}=fixture(api);await c.start('rank');
    expect(c.state.status).toBe('failed');expect(c.attempt).toBeNull();expect(c.running).toBe(false);
    expect(changes.some(s=>s.status==='waiting')).toBe(false);
    expect(api.mock.calls.filter(x=>x[0]==='pvp/queue/cancel')).toHaveLength(1);
    expect(api.mock.calls.some(x=>x[0].includes('surrender'))).toBe(false);
  });
  it('retains account-scoped cancellation intent on authentication loss',async()=>{
    const {client:c}=fixture(vi.fn()),save=vi.fn();c.storage={getItem:()=>null,setItem:save,removeItem:vi.fn()};
    c.attempt={attemptId:'only-this-attempt',token:'secret'};c.startPolling();await c.queueFailure(http(401),'join');
    expect(c.state.status).toBe('failed');expect(c.running).toBe(false);expect(c.cancelWanted).toBe(true);
    expect(save).toHaveBeenCalledWith('gmm-ranked-cancel-attempt','only-this-attempt');expect(c.api).not.toHaveBeenCalled();
    expect(c.dismiss()).toBe(true);expect(c.storage.removeItem).not.toHaveBeenCalled();
  });
  it('caps transient recovery and stops its clock state instead of searching forever',async()=>{
    const api=vi.fn(async()=>{throw new TypeError('Failed to fetch');}),{client:c}=fixture(api);
    c.state.status='recovering';c.startPolling();
    for(let i=0;i<6;i++)await c.tick();
    expect(api).toHaveBeenCalledTimes(6);expect(c.state.status).toBe('disconnected');expect(c.running).toBe(false);
    await vi.advanceTimersByTimeAsync(120000);expect(api).toHaveBeenCalledTimes(6);
  });
  it('uses a conservative rate-limit backoff',async()=>{
    const api=vi.fn(async()=>{throw http(429);}),{client:c}=fixture(api);c.startPolling();await c.tick();
    expect(c.retryDelay).toBe(30000);await vi.advanceTimersByTimeAsync(29999);expect(api).toHaveBeenCalledTimes(1);
  });
  it('does not reset consecutive failure budget when current succeeds but join fails',async()=>{
    const api=vi.fn(async(path:string,_body?:any)=>{if(path==='pvp/queue/current')return {status:'preparing',attemptId:'a',token:'t'};throw http(500);});
    const {client:c}=fixture(api);c.state.status='recovering';c.startPolling();for(let i=0;i<6;i++)await c.tick();
    expect(c.queueFailures).toBe(6);expect(c.state.status).toBe('disconnected');expect(c.running).toBe(false);
  });
  it('lets explicit cancel resume exhausted recovery with the same cancellation id',async()=>{
    const api=vi.fn(async(_path:string,_body?:any)=>({status:'cancelled'})),{client:c}=fixture(api);
    c.attempt={attemptId:'a',token:'t'};c.cancelId='stable-cancel';c.state.status='disconnected';c.queueFailures=6;
    c.cancel();await vi.advanceTimersByTimeAsync(0);
    expect(api).toHaveBeenCalledWith('pvp/queue/cancel',{attemptId:'a',token:'t',requestId:'stable-cancel'});
    expect(c.state.status).toBe('cancelled');expect(c.running).toBe(false);
  });
  it('ignores stale failure completion after generation change',async()=>{
    const {client:c}=fixture(vi.fn());const old=c.generation;c.generation++;c.state.status='waiting';
    await c.queueFailure(http(401),'join',old);expect(c.state.status).toBe('waiting');expect(c.cancelWanted).toBe(false);
  });
});

it('does not count setup time as observed waiting when queuedAt is unavailable',async()=>{
  let mono=1000;const c=queueUiMake(vi.fn(),()=>mono);c.state.status='joining';c.state.waitStartedAt=mono;
  mono=21000;await c.acceptQueue({status:'waiting'});expect(c.queueElapsed()).toBe(0);
  mono=26000;expect(c.queueElapsed()).toBe(5);
  c.state.status='reconnecting';await c.acceptQueue({status:'waiting'});expect(c.queueElapsed()).toBe(5);
});

it('shows elapsed only with authoritative queue timestamps',async()=>{
  const {client:c}=fixture(vi.fn());c.state.status='recovering';
  await c.acceptQueue({status:'waiting'});expect(c.queueTimeVisible()).toBe(false);
  await c.acceptQueue({status:'waiting',queuedAt:500,serverNow:1000});expect(c.queueTimeVisible()).toBe(true);
  c.state.error='offline';expect(c.queueTimeVisible()).toBe(false);
});
it('destroy invalidates delayed responses and permanently prevents lifecycle restart',async()=>{
  const d=deferred(),api=vi.fn(()=>d.promise),{client:c,changes}=fixture(api);
  const start=c.start('normal');const generation=c.generation;c.destroy();const changeCount=changes.length;
  d.resolve({status:'waiting',attemptId:'a',token:'t'});await start;
  expect(c.generation).toBe(generation+1);expect(changes).toHaveLength(changeCount);
  c.startPolling();await c.tick();await c.recover();await c.start('rank');c.cancel();
  expect(c.running).toBe(false);expect(api).toHaveBeenCalledTimes(1);
  await vi.advanceTimersByTimeAsync(60000);expect(api).toHaveBeenCalledTimes(1);
});
