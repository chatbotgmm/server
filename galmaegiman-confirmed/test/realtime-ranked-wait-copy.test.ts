// Focused presentation tests. Install beside the existing realtime client tests.
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
const harness=vi.hoisted(()=>({client:null as any}));
vi.mock('../public/duel/realtime-ranked-client.js',()=>({RealtimeRankedClient:class {
  startedAt=Date.now();state={status:'waiting',error:null,room:null,mode:'normal'};generation=0;
  constructor(public options:any){harness.client=this;}
  start(){this.options.onChange(this.state);} queueTimeVisible(){return false;}
  queueElapsed(){return Math.floor((Date.now()-this.startedAt)/1000);} seconds(){return null;} destroy(){this.generation++;}
  stop(){} startPolling(){} tick(){} dismiss(){return true;}
}}));
// @ts-ignore Browser source deliberately remains JavaScript.
import {createRealtimeScreen} from '../public/duel/realtime-ranked-screen.js';
class ElementStub {
  className='';hidden=false;textContent='';onclick:any=null;html='';copy:any=null;
  set innerHTML(value:string){this.html=value;const match=value.match(/<button[^>]*data-wait-copy[^>]*>([^<]*)<\/button>/);this.copy=match?{textContent:match[1],onclick:null,attributes:{'aria-label':match[1]+' · 다음 대기 문구 보기'} as Record<string,string>,setAttribute(name:string,value:string){this.attributes[name]=value;}}:null;}
  get innerHTML(){return this.html;}
  querySelector(selector:string){return selector==='[data-wait-copy]'?this.copy:null;}
  querySelectorAll(){return [];}
  append(){}remove(){}
}
let root:ElementStub,screen:any,api:any;
beforeEach(()=>{
  vi.useFakeTimers();vi.stubGlobal('document',{hidden:false,head:{append(){}},createElement:()=>new ElementStub(),addEventListener(){},removeEventListener(){}});
  vi.stubGlobal('addEventListener',vi.fn());vi.stubGlobal('removeEventListener',vi.fn());
  api=vi.fn();screen=createRealtimeScreen({api,host:{append(el:ElementStub){root=el;}},confirmLeave:vi.fn(),onExit:vi.fn()});screen.start('normal');
});
afterEach(()=>{screen.destroy();vi.clearAllTimers();vi.useRealTimers();vi.unstubAllGlobals();});
describe('tap-only waiting copy',()=>{
  it('does not rotate with elapsed time or polling redraws',async()=>{
    const first=root.copy.textContent;await vi.advanceTimersByTimeAsync(8000);expect(root.copy.textContent).toBe(first);await vi.advanceTimersByTimeAsync(52000);
    expect(root.copy.textContent).toBe(first);harness.client.options.onChange(harness.client.state);
    expect(root.copy.textContent).toBe(first);
  });
  it('advances once per click and preserves choice across redraws without API calls',async()=>{
    root.copy.onclick();expect(root.copy.textContent).toBe('갈매기들이 상대를 부르고 있어요.');
    expect(root.copy.attributes['aria-label']).toBe('갈매기들이 상대를 부르고 있어요. · 다음 대기 문구 보기');
    await vi.advanceTimersByTimeAsync(16000);expect(root.copy.textContent).toBe('갈매기들이 상대를 부르고 있어요.');
    harness.client.options.onChange(harness.client.state);expect(root.copy.textContent).toBe('갈매기들이 상대를 부르고 있어요.');
    root.copy.onclick();root.copy.onclick();root.copy.onclick();expect(root.copy.textContent).toBe('어울리는 대전 상대를 찾아요.');
    expect(api).not.toHaveBeenCalled();expect(root.html).toContain('type="button"');expect(root.html).toContain('aria-label="갈매기들이 상대를 부르고 있어요. · 다음 대기 문구 보기"');
  });
  it('ignores stale copy clicks after destruction',()=>{
    const copy=root.copy;screen.destroy();copy.onclick();expect(copy.textContent).toBe('어울리는 대전 상대를 찾아요.');
  });
});
