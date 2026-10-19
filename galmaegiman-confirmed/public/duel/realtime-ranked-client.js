// MANUAL DRAFT. Pure network controller: never imports or advances the battle engine.
export class RealtimeRankedClient {
  constructor({api, onChange, now = () => Date.now(), uuid = () => crypto.randomUUID(), storage = globalThis.sessionStorage, elapsedNow = () => globalThis.performance?.now() ?? Date.now()}) {
    Object.assign(this, {api, onChange, now, uuid, storage, elapsedNow});
    this.state = {status:'idle', error:null, room:null, pending:false, mode:null, waitStartedAt:null, waitElapsedBase:0, queuedAt:null, waitObserved:true, queueRetrying:false,matchDismissed:false};
    this.attempt = null; this.cancelWanted = false; this.cancelId = null;
    this.destroyed = false; this.running = false; this.polling = false; this.timer = null; this.offset = 0;
    this.generation = 0; this.pendingCommand = null; this.pendingSurrender = null;
    this.queueFailures=0;this.retryDelay=1000;this.failureCause=null;this.waitConfirmed=false;
  }
  emit(patch = {}) { if(this.destroyed)return; Object.assign(this.state, patch); this.onChange({...this.state}); }
  // Store only the globally unique attempt ID, never a global flag or queue credential.
  // Another authenticated account cannot have this attempt ID in its current response.
  saveCancel(value) { try { if(value && this.attempt) this.storage?.setItem('gmm-ranked-cancel-attempt',this.attempt.attemptId);else if(!value)this.storage?.removeItem('gmm-ranked-cancel-attempt'); } catch {} }
  loadCancel() { try {this.cancelAttempt=this.storage?.getItem('gmm-ranked-cancel-attempt')||null;} catch {this.cancelAttempt=null;} }
  // Presentation-only elapsed time. It never triggers AI, matchmaking, or a deadline.
  queueElapsed() { return this.state.waitStartedAt==null?0:Math.max(0,Math.floor((this.state.waitElapsedBase+this.elapsedNow()-this.state.waitStartedAt)/1000)); }
  queueTimeVisible() { return this.state.status==='waiting'&&!this.state.error&&!this.state.waitObserved&&Number.isFinite(this.state.queuedAt); }
  async retryQueueStatus() {
    if(this.state.status==='failed'||this.state.queueRetrying||this.creating||this.polling||this.cancelling)return;
    const g=this.generation;this.emit({queueRetrying:true});this.startPolling();
    try {await this.tick();} finally {if(g===this.generation)this.emit({queueRetrying:false});}
  }
  queueHealthy(){this.queueFailures=0;this.retryDelay=1000;}
  async queueFailure(error,stage='read',g=this.generation){
    if(g!==this.generation)return;
    const status=Number(error.status)||0;
    const fatal=status>=400&&status<500&&![408,429].includes(status);
    if(fatal){
      this.failureCause=status===401?'로그인이 필요해요. 다시 로그인해 주세요.':error.message||'매칭을 시작하지 못했어요.';
      // A rejected join must never look like a live queue. Tombstone its known
      // attempt first, without ever converting a room into a cancellation.
      if(this.attempt&&!this.roomId){this.cancelWanted=true;this.saveCancel(true);}
      if(this.attempt&&!this.roomId&&stage!=='cancel'&&![401,403].includes(status)){
        this.emit({status:'cancelling',error:this.failureCause});
        try{await this.cancelRequest();}catch(cleanup){await this.queueFailure(cleanup,'cancel',g);}
      }else{
        this.stop();this.emit({status:'failed',error:this.failureCause,pending:false});
      }
      return;
    }
    this.queueFailures++;
    this.retryDelay=status===429?30000:Math.min(30000,1000*2**(this.queueFailures-1));
    if(this.queueFailures>=6){
      this.stop();this.emit({status:'disconnected',error:'서버 연결을 확인하지 못했어요. 매칭이나 취소가 완료된 상태는 아니에요.',pending:false});
    }else {this.emit({status:this.cancelWanted?'cancelling':'reconnecting',error:'연결이 잠시 끊겼어요. 자동으로 다시 연결하고 있어요.'});this.schedule();}
  }
  seconds() { const d=this.state.room?.deadline; return d == null ? null : Math.max(0,Math.ceil((d-(this.now()+this.offset))/1000)); }
  acceptRoom(room) {
    if (!room?.roomId) throw new Error('대전 정보를 확인하지 못했어요.');
    const old=this.state.room;
    if(old && (old.roomId!==room.roomId || room.revision<old.revision)) return;
    if(room.result && ['MATCH_DECLINED','MATCH_TIMEOUT'].includes(room.result.reason)){
      // A prebattle cancellation is not a draw, surrender or rank result.
      this.generation++;this.stop();this.roomId=null;this.attempt=null;
      this.pendingCommand=null;this.pendingSurrender=null;this.polling=false;this.creating=false;this.sending=false;this.cancelling=false;
      this.cancelWanted=false;this.saveCancel(false);
      if(room.result.requeued){
        this.waitConfirmed=false;
        this.emit({status:'recovering',room:null,error:null,pending:false,mode:room.mode,matchDismissed:false,waitObserved:true,waitStartedAt:this.elapsedNow(),waitElapsedBase:0,queuedAt:null});
        this.startPolling();void this.tick();
      }else this.emit({status:'idle',room:null,error:null,pending:false,matchDismissed:true});
      return;
    }
    this.offset=room.serverNow-this.now();
    if(this.pendingCommand && (room.phaseId!==this.pendingCommand.phaseId || room.mySubmitted || room.result)) this.pendingCommand=null;
    if(room.result) this.pendingSurrender=null;
    this.cancelWanted=false; this.saveCancel(false);
    this.emit({status:'matched',room,error:null,pending:!!(this.pendingCommand||this.pendingSurrender)});
    if(room.result && room.result.settlement!=='pending') this.stop();
  }
  async acceptQueue(q,g=this.generation) {
    if(g!==this.generation)return;
    if(q.mode==='normal'||q.mode==='rank')this.state.mode=q.mode;
    if(q.status==='waiting'&&!this.waitConfirmed){
      this.waitConfirmed=true;
      Object.assign(this.state,{waitStartedAt:this.elapsedNow(),waitElapsedBase:0});
    }
    if(Number.isFinite(q.queuedAt)&&Number.isFinite(q.serverNow)){
      const previous=this.state.queuedAt===q.queuedAt?this.queueElapsed()*1000:0;
      Object.assign(this.state,{queuedAt:q.queuedAt,waitElapsedBase:Math.max(previous,q.serverNow-q.queuedAt,0),waitStartedAt:this.elapsedNow(),waitObserved:false});
    }
    if(['idle','cancelled','expired'].includes(this.state.status) && ['preparing','waiting'].includes(q.status)) return;
    if(q.attemptId && q.token) this.attempt={attemptId:q.attemptId,token:q.token};
    if(q.attemptId && q.attemptId===this.cancelAttempt)this.cancelWanted=true;
    if(q.status==='matched') {
      this.emit({status:'matched',error:null});
      this.roomId=q.roomId;
      const room=await this.api(`pvp/room/current?roomId=${encodeURIComponent(q.roomId)}`);
      if(g===this.generation)this.acceptRoom(room);
      return;
    }
    if(this.state.status==='matched') return; // Late join/cancel/queue response cannot replace a room.
    if(q.status==='idle' && this.attempt) {
      // An uncertain join may commit after this read. Tombstone its known ID first.
      this.cancelWanted=true;this.saveCancel(true);this.emit({status:'cancelling'});
      await this.cancelRequest();return;
    }
    if(['cancelled','expired','idle'].includes(q.status)) {
      this.cancelWanted=false; this.saveCancel(false); this.attempt=null;
      this.emit({status:this.failureCause?'failed':q.status,error:this.failureCause});this.stop();return;
    }
    this.emit({status:this.cancelWanted?'cancelling':q.status,error:null});
  }
  startPolling() { if(this.destroyed)return; this.running=true; this.schedule(); }
  schedule() { clearTimeout(this.timer); if(this.running) this.timer=setTimeout(()=>this.tick(),this.state.room?1000:this.retryDelay); }
  async recover() {
    if(this.destroyed)return;
    if(this.running) return;
    this.cancelWanted=false;this.waitConfirmed=false;this.loadCancel();
    this.cancelId=this.uuid(); this.emit({status:'recovering',mode:null,waitStartedAt:this.elapsedNow(),waitElapsedBase:0,queuedAt:null,waitObserved:true,queueRetrying:false}); this.startPolling();
    await this.tick();
  }
  async start(mode='rank') {
    if(this.destroyed)return;
    if(this.running && !['idle','cancelled','expired'].includes(this.state.status)) return;
    this.stop(); this.generation++; const g=this.generation;
    this.queueHealthy();this.failureCause=null;this.waitConfirmed=false;
    this.attempt=null; this.roomId=null; this.cancelWanted=false; this.cancelId=this.uuid();this.loadCancel();
    this.pendingCommand=null;this.pendingSurrender=null;this.sending=false;this.polling=false;this.cancelling=false;
    this.emit({status:'preparing',room:null,error:null,pending:false,mode,waitStartedAt:this.elapsedNow(),waitElapsedBase:0,queuedAt:null,waitObserved:true,queueRetrying:false,matchDismissed:false}); this.startPolling();
    // No join is possible before this credential has returned. Cancel is usable now.
    this.creating=true;
    let stage='read';
    try {
      // Recover an account's existing queue/room before creating any new attempt.
      const current=await this.api('pvp/queue/current');
      if(g!==this.generation)return;
      if(['preparing','waiting','matched'].includes(current.status)) {
        if(!current.mode)this.emit({mode:null,waitObserved:true});
        await this.acceptQueue(current,g);
        if(this.cancelWanted && this.attempt)await this.cancelRequest();
        else if(current.status==='preparing' && this.attempt){stage='join';const q=await this.api('pvp/queue/join',this.attempt);await this.acceptQueue(q,g);}
        this.queueHealthy();
        return;
      }
      if(this.cancelWanted){this.emit({status:'cancelled'});this.stop();return;}
      this.saveCancel(false);this.cancelAttempt=null;
      stage='attempt';const a=await this.api('pvp/queue/attempt',{mode,protocol:3});
      if(g!==this.generation) return;
      this.attempt={attemptId:a.attemptId,token:a.token};
      if(this.cancelWanted)this.saveCancel(true);
      if(this.cancelWanted) await this.cancelRequest();
      else {stage='join'; this.emit({status:'joining'}); const q=await this.api('pvp/queue/join',this.attempt);if(g===this.generation)await this.acceptQueue(q); }
      this.queueHealthy();
    } catch(e) { await this.queueFailure(e,stage,g); }
    finally { if(g===this.generation){this.creating=false;this.schedule();} }
  }
  cancel() {
    if(this.destroyed)return;
    if(this.roomId||this.state.room||this.state.status==='matched') return; // Cancellation is never a surrender.
    if(this.state.status==='failed')return;
    if(this.state.status==='disconnected'){this.queueHealthy();this.startPolling();}
    this.cancelWanted=true; this.saveCancel(true); this.emit({status:'cancelling'});
    const g=this.generation;
    if(this.attempt) void this.cancelRequest().catch(e=>this.queueFailure(e,'cancel',g));
    else if(!this.creating){this.startPolling();void this.tick();}
  }
  async cancelRequest() {
    if(this.cancelling || !this.attempt) return;
    this.cancelling=true;
    const g=this.generation;
    try { const q=await this.api('pvp/queue/cancel',{...this.attempt,requestId:this.cancelId});if(g===this.generation)await this.acceptQueue(q); }
    finally { if(g===this.generation)this.cancelling=false; }
  }
  async tick() {
    if(!this.running || this.polling || this.creating) {this.schedule();return;}
    this.polling=true;
    const g=this.generation;
    let stage='read';
    try {
      if(this.roomId) {
        const r=await this.api(`pvp/room/current?roomId=${encodeURIComponent(this.roomId)}`);if(g===this.generation)this.acceptRoom(r);
        if(g===this.generation && this.pendingCommand) await this.retryCommand();
        if(g===this.generation && this.pendingSurrender) await this.surrender();
      } else {
        const q=await this.api('pvp/queue/current');if(g!==this.generation)return;await this.acceptQueue(q);
        if(this.cancelWanted && this.attempt){stage='cancel';await this.cancelRequest();}
        else if(this.state.status==='preparing' && this.attempt){stage='join';await this.acceptQueue(await this.api('pvp/queue/join',this.attempt),g);}
      }
      if(g===this.generation)this.queueHealthy();
    } catch(e) { if(this.state.room){if(g===this.generation)this.emit({error:e.message});}else await this.queueFailure(e,stage,g); }
    finally {if(g===this.generation){this.polling=false;this.schedule();}}
  }
  async submit(command) {
    if(this.destroyed)return;
    const r=this.state.room;
    if(!r || !r.canSubmit || r.mySubmitted || r.result || this.pendingCommand || this.pendingSurrender) return;
    this.pendingCommand={roomId:r.roomId,phaseId:r.phaseId,requestId:this.uuid(),command};
    return this.retryCommand();
  }
  async retryCommand() {
    if(!this.pendingCommand || this.sending) return;
    this.sending=true; this.emit({pending:true,error:null});
    const g=this.generation;
    try {const response=await this.api('pvp/room/command',this.pendingCommand);if(g!==this.generation)return;this.pendingCommand=null;this.acceptRoom(response);}
    catch(e) {
      if(g!==this.generation)return;
      // On a definite validation error, reconcile before allowing a new choice.
      if(e.status>=400 && e.status<500 && ![408,409,429].includes(e.status)) this.pendingCommand=null;
      this.emit({error:e.message,pending:!!this.pendingCommand});
      await this.tick();
    } finally {if(g===this.generation)this.sending=false;}
  }
  async surrender() {
    if(this.destroyed)return;
    if(!this.state.room || this.state.room.result || this.sending) return;
    this.pendingSurrender??={roomId:this.state.room.roomId,requestId:this.uuid()};
    this.sending=true;this.emit({pending:true,error:null});
    const g=this.generation;
    try {const r=await this.api('pvp/room/surrender',this.pendingSurrender);if(g===this.generation)this.acceptRoom(r);}
    catch(e) {if(g===this.generation)this.emit({error:e.message});}
    finally {if(g===this.generation)this.sending=false;}
  }
  destroy() { if(this.destroyed)return;this.destroyed=true;this.generation++;this.stop();this.pendingCommand=null;this.pendingSurrender=null; }
  stop() { this.running=false; clearTimeout(this.timer); }
  dismiss() {
    if(!['idle','cancelled','expired','failed'].includes(this.state.status) && !(this.state.status==='disconnected'&&this.roomId) && !this.state.room?.result) return false;
    this.stop();this.generation++;return true;
  }
}
