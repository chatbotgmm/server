package org.dpdns.gmm;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** 표현 전용 순차 재생. 피해·순서·턴은 계산하지 않고 서버 이벤트의 전후 상태만 그린다. */
final class NativeRoster60Playback {
  final NativeRoster60Battle host;
  final Roster60PlaybackCursor cursor=new Roster60PlaybackCursor();
  final ArrayDeque<JSONObject> queue=new ArrayDeque<>();
  JSONObject latest;
  ValueAnimator animator;
  Runnable advancePending;
  boolean playing;
  int generation;

  NativeRoster60Playback(NativeRoster60Battle host){this.host=host;}
  boolean playing(){return playing;}

  void accept(JSONObject received) {
    if(received==null||!host.current())return;
    JSONObject next=copy(received),state=next.optJSONObject("state");
    if(state==null){if(!playing)host.renderSnapshot(next);return;}
    JSONArray history=state.optJSONArray("events");
    if(history==null)history=new JSONArray();
    List<String> signatures=new ArrayList<>();
    for(int i=0;i<history.length();i++)signatures.add(canonical(history.opt(i)));
    Roster60PlaybackCursor.Decision decision=cursor.accept(
        next.optString("battleId"),next.optInt("revision",-1),signatures);
    if(decision.kind==Roster60PlaybackCursor.Kind.IGNORE)return;
    JSONObject previous=latest;
    // PvP submit/accept/settle can update flags without increasing revision.
    // A poll started before that acknowledgement must not reopen submitted controls or settlement.
    if(previous!=null&&previous.optInt("revision")==next.optInt("revision")){
      try{
        if(previous.optBoolean("waitingForOpponent"))next.put("waitingForOpponent",true);
        if(previous.optBoolean("accepted"))next.put("accepted",true);
        if("SETTLED".equals(previous.optString("status"))&&"FINISHED".equals(next.optString("status")))
          next.put("status","SETTLED");
      }catch(JSONException invalid){throw new IllegalArgumentException(invalid);}
    }
    latest=next;
    if(host.presentationPaused){suspend();return;}
    if(decision.kind==Roster60PlaybackCursor.Kind.BASELINE||previous==null) {
      synchronize();return;
    }
    if(decision.kind==Roster60PlaybackCursor.Kind.UPDATE) {
      // Waiting/receipt/poll refreshes can have the same revision. They must never unlock a playing turn.
      if(!samePair(previous.optJSONObject("state").optJSONArray("fighters"),state.optJSONArray("fighters"))) {
        synchronize();return;
      }
      if(playing)host.lockPresentation();else host.renderSnapshot(latest);
      return;
    }
    ArrayList<JSONObject> raw=new ArrayList<>(),added=new ArrayList<>();
    JSONArray baseline=previous.optJSONObject("state").optJSONArray("fighters");
    JSONArray expected=baseline;
    boolean valid=validPair(baseline)&&validPair(state.optJSONArray("fighters")),legacy=false;
    for(int group=decision.firstGroup;valid&&group<history.length();group++) {
      JSONObject outer=history.optJSONObject(group);
      JSONArray events=outer==null?null:outer.optJSONArray("events");
      if(events==null){valid=false;break;}
      for(int i=0;i<events.length();i++) {
        JSONObject event=events.optJSONObject(i);
        if(event==null||!supported(event)){valid=false;break;}
        JSONObject presentation=event.optJSONObject("presentation");
        if(presentation==null){
          if(event.has("presentation")&&!event.isNull("presentation")){valid=false;break;}
          legacy=true;
        }else if(presentation.optInt("version",-1)==0)legacy=true;
        else if(presentation.optInt("version",-1)!=1
            ||!validPair(presentation.optJSONArray("before"))
            ||!validPair(presentation.optJSONArray("after"))){valid=false;break;}
        raw.add(event);
      }
    }
    boolean[] uncertain={false,false};
    for(JSONObject event:raw){
      if(!valid)break;
      if(legacy){
        // 구서버는 정확한 중간 HP/MP를 주지 않는다. 피해를 역산하지 않고 보고된 행동만 재생한다.
        if(!legacyEvent(event)){valid=false;break;}
        int side="self".equals(event.optString("actor"))?0:1;
        if("switch".equals(event.optString("kind"))||"forced-switch".equals(event.optString("kind")))
          uncertain[side]=true;
        added.add(legacyFrame(event,baseline,uncertain));
      }else{
        JSONObject presentation=event.optJSONObject("presentation");
        if(!samePair(expected,presentation.optJSONArray("before"))){valid=false;break;}
        added.add(copy(event));expected=presentation.optJSONArray("after");
      }
    }
    // 정말 불완전한 이벤트만 동기화로 끝낸다. 정상 구계약은 중립 FX 후 최종 HP/MP를 적용한다.
    if(!valid||(!legacy&&!samePair(expected,state.optJSONArray("fighters")))){synchronize();return;}
    queue.addAll(added);
    if(queue.isEmpty()&&!playing){host.renderSnapshot(latest);return;}
    host.lockPresentation();
    if(!playing){playing=true;advance();}
  }

  private boolean supported(JSONObject event) {
    String actor=event.optString("actor"),kind=event.optString("kind");
    return ("self".equals(actor)||"enemy".equals(actor))
        &&java.util.Arrays.asList("skill","focus","item","switch","forced-switch",
           "escape-failed","escape-success","surrender").contains(kind);
  }

  private static boolean nonnegativeInteger(JSONObject event,String key){
    Object value=event.opt(key);
    if(!(value instanceof Number))return false;
    double n=((Number)value).doubleValue();
    return Double.isFinite(n)&&n>=0&&n<=Integer.MAX_VALUE&&n==Math.floor(n);
  }
  private boolean legacyEvent(JSONObject event){
    String kind=event.optString("kind");
    if("skill".equals(kind)){
      String target=event.optString("target"),defense=event.optString("defense");
      if(!nonnegativeInteger(event,"damage")
          ||!("attacker".equals(target)||"defender".equals(target))
          ||!java.util.Arrays.asList("normal","weak","resist","immune","reflect","miss").contains(defense))return false;
      if("reflect".equals(defense)!="attacker".equals(target)
          ||(("immune".equals(defense)||"miss".equals(defense)||event.optBoolean("miss"))&&event.optInt("damage")!=0)
          ||(event.optBoolean("reflected")&&!"attacker".equals(target)))return false;
    }
    return !("focus".equals(kind)||"item".equals(kind))||nonnegativeInteger(event,"recovered");
  }
  private JSONObject legacyFrame(JSONObject event,JSONArray baseline,boolean[] uncertain){
    JSONObject frame=copy(event);
    JSONObject presentation=MainActivity.obj("version",0,"before",baseline,"after",baseline,
        "uncertainSides",new JSONArray().put(uncertain[0]).put(uncertain[1]));
    int side="self".equals(event.optString("actor"))?0:1;
    // 기술명/속성은 확정된 당시 출전 개체의 동일 skillId에서만 읽는다. 교체 뒤에는 중립 효과.
    if("skill".equals(event.optString("kind"))&&!uncertain[side]){
      JSONObject actor=baseline.optJSONObject(side),found=null;
      JSONObject attack=actor.optJSONObject("attack");
      if(attack!=null&&event.optString("skillId").equals(attack.optString("id")))found=attack;
      JSONArray skills=actor.optJSONArray("skills");
      if(skills!=null)for(int i=0;i<skills.length();i++){
        JSONObject skill=skills.optJSONObject(i);
        if(skill!=null&&event.optString("skillId").equals(skill.optString("id")))found=skill;
      }
      if(found!=null&&java.util.Arrays.asList("NORMAL","FIRE","WATER","LIGHTNING","ICE","NATURE","HOLY","DARK").contains(found.optString("element"))){
        try{presentation.put("skill",MainActivity.obj("id",found.optString("id"),"name",found.optString("name","공격"),"element",found.optString("element")));}
        catch(JSONException invalid){throw new IllegalArgumentException(invalid);}
      }
    }
    try{frame.put("presentation",presentation);}
    catch(JSONException invalid){throw new IllegalArgumentException(invalid);}
    return frame;
  }

  private void advance() {
    if(!host.current()){suspend();return;}
    JSONObject event=queue.pollFirst();
    if(event==null){playing=false;host.art.clearFrame();host.renderSnapshot(latest);return;}
    JSONObject presentation=event.optJSONObject("presentation");
    JSONObject before=MainActivity.obj("fighters",presentation.optJSONArray("before"));
    JSONObject after=MainActivity.obj("fighters",presentation.optJSONArray("after"));
    host.lockPresentation();
    host.opponentEvent(event);
    host.message.setText(label(event));
    final int token=generation;
    final boolean[] sounded={false};
    animator=ValueAnimator.ofFloat(0,1);
    String kind=event.optString("kind");
    animator.setDuration("skill".equals(kind)?1250:
        ("switch".equals(kind)||"forced-switch".equals(kind))?850:950);
    animator.setInterpolator(new android.view.animation.LinearInterpolator());
    animator.addUpdateListener(value->{
      if(token!=generation||!host.current())return;
      float progress=(float)value.getAnimatedValue();
      host.art.frame(before,after,event,progress);
      if(progress>=.5f&&!sounded[0]){
        sounded[0]=true;
        if("skill".equals(kind)&&event.optInt("damage")>0)host.h.sound.effect("hit");
        else if("focus".equals(kind)||"item".equals(kind))host.h.sound.effect("heal");
      }
    });
    animator.addListener(new AnimatorListenerAdapter(){
      boolean cancelled;
      @Override public void onAnimationCancel(Animator value){cancelled=true;}
      @Override public void onAnimationEnd(Animator value){
        if(cancelled||token!=generation||!playing)return;
        animator=null;
        if(!host.current()){suspend();return;}
        host.art.frame(before,after,event,1);
        // Separate actions visibly; generation prevents delayed work from a closed screen.
        advancePending=()->{advancePending=null;if(token==generation&&playing)advance();};
        host.art.postDelayed(advancePending,180);
      }
    });
    host.art.frame(before,after,event,0);
    animator.start();
  }

  String label(JSONObject event) {
    JSONObject p=event.optJSONObject("presentation");
    int side="self".equals(event.optString("actor"))?0:1;
    JSONObject actor=p.optJSONArray("before").optJSONObject(side);
    JSONObject skill=p.optJSONObject("skill");
    String action;
    switch(event.optString("kind")){
      case "skill":action=skill==null?"공격":skill.optString("name","공격");break;
      case "focus":action="집중 · MP 회복";break;
      case "item":action="아이템 사용";break;
      case "switch":case "forced-switch":action="대원 교체";break;
      case "escape-failed":action="도망 실패";break;
      case "escape-success":action="도망 성공";break;
      default:action="항복";
    }
    JSONArray uncertain=p.optJSONArray("uncertainSides");
    String name=uncertain!=null&&uncertain.optBoolean(side)?"교체 대원":actor.optString("name");
    return (side==0?"아군 · ":"상대 · ")+name+"  "+action;
  }

  /** 중단은 마지막 확정 응답을 기준선으로 삼는다. 복귀 시 지난 이력을 다시 재생하지 않는다. */
  void suspend() {
    generation++;playing=false;queue.clear();
    if(advancePending!=null){host.art.removeCallbacks(advancePending);advancePending=null;}
    ValueAnimator old=animator;animator=null;if(old!=null)old.cancel();
    host.art.clearFrame();host.suspendOpponentInfo();
    if(latest!=null&&latest.optJSONObject("state")!=null)host.art.bind(latest.optJSONObject("state"));
  }
  private void synchronize(){suspend();if(host.current())host.renderSnapshot(latest);}

  static JSONObject copy(JSONObject value) {
    try{return new JSONObject(value.toString());}
    catch(JSONException invalid){throw new IllegalArgumentException(invalid);}
  }
  static boolean validPair(JSONArray pair) {
    if(pair==null||pair.length()!=2)return false;
    for(int i=0;i<2;i++){
      JSONObject f=pair.optJSONObject(i),s=f==null?null:f.optJSONObject("stats");
      if(s==null||f.optString("speciesId").isEmpty()||f.optInt("hp",-1)<0
          ||f.optInt("mp",-1)<0||s.optInt("hp")<=0||s.optInt("mp",-1)<0
          ||f.optInt("hp")>s.optInt("hp")||f.optInt("mp")>s.optInt("mp"))return false;
    }
    return true;
  }
  static boolean samePair(JSONArray a,JSONArray b) {
    if(!validPair(a)||!validPair(b))return false;
    for(int i=0;i<2;i++){
      JSONObject x=a.optJSONObject(i),y=b.optJSONObject(i);
      if(!x.optString("id").equals(y.optString("id"))
          ||!x.optString("speciesId").equals(y.optString("speciesId"))
          ||!x.optString("name").equals(y.optString("name"))
          ||x.optInt("level")!=y.optInt("level")
          ||x.optInt("hp")!=y.optInt("hp")||x.optInt("mp")!=y.optInt("mp")
          ||x.optJSONObject("stats").optInt("hp")!=y.optJSONObject("stats").optInt("hp")
          ||x.optJSONObject("stats").optInt("mp")!=y.optJSONObject("stats").optInt("mp"))return false;
    }
    return true;
  }
  static String canonical(Object value) {
    if(value instanceof JSONObject){
      JSONObject object=(JSONObject)value;List<String> keys=new ArrayList<>();
      Iterator<String> iterator=object.keys();while(iterator.hasNext())keys.add(iterator.next());
      Collections.sort(keys);StringBuilder out=new StringBuilder("{");
      for(String key:keys)out.append(JSONObject.quote(key)).append(':').append(canonical(object.opt(key))).append(',');
      return out.append('}').toString();
    }
    if(value instanceof JSONArray){
      JSONArray array=(JSONArray)value;StringBuilder out=new StringBuilder("[");
      for(int i=0;i<array.length();i++)out.append(canonical(array.opt(i))).append(',');
      return out.append(']').toString();
    }
    return value==null?"null":value instanceof String?JSONObject.quote((String)value):String.valueOf(value);
  }
}
