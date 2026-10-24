package org.dpdns.gmm;
import android.content.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Real authenticated product transport. No offline fixture or client-side damage. */
final class NativeRoster60Battle {
 final MainActivity h; final SharedPreferences prefs;
 static final Set<String> IN_FLIGHT=new HashSet<>();
 String owner,key; int epoch; LinearLayout container; TextView message,stats; Button retry,settle;
 CombatV2ReviewPanel panel; JSONObject view; boolean reading; android.app.AlertDialog selection;
 NativeRoster60Battle(MainActivity host){h=host;prefs=h.getSharedPreferences("roster60_product_battle",Context.MODE_PRIVATE);}
 boolean current(){return epoch==h.epoch&&"roster60Battle".equals(h.screen)&&owner.equals(h.api.token())&&!h.isDestroyed();}
 void open(){
  if(h.api.token().isEmpty()){h.login();return;}
  h.base("전투","roster60Battle");owner=h.api.token();key=NativeProgression.accountKey(BuildConfig.SERVER_URL+"|"+owner);epoch=h.epoch;
  h.page.removeViewAt(h.page.getChildCount()-1);
  container=new LinearLayout(h);container.setOrientation(LinearLayout.HORIZONTAL);h.page.addView(container,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout info=new LinearLayout(h);info.setOrientation(LinearLayout.VERTICAL);container.addView(info,new LinearLayout.LayoutParams(0,-1,2));
  message=h.text("서버 전투 확인 중…",12);message.setMaxLines(2);info.addView(message);
  stats=h.text("",11);stats.setMaxLines(8);info.addView(stats,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout controls=new LinearLayout(h);controls.setOrientation(LinearLayout.VERTICAL);info.addView(controls);
  retry=h.button("다시 확인",this::resume);retry.setTag("roster60-battle-retry");controls.addView(retry,new LinearLayout.LayoutParams(-1,h.dp(48)));
  settle=h.button("결과 받기",()->{if(view!=null)mutate("settle",h.obj("battleId",view.optString("battleId"),"revision",view.optInt("revision")));});settle.setTag("roster60-battle-settle");settle.setEnabled(false);controls.addView(settle,new LinearLayout.LayoutParams(-1,h.dp(48)));
  panel=new CombatV2ReviewPanel(h,this::command,CombatV2ReviewStyle::icon,true);panel.setTag("roster60-battle-panel");container.addView(panel,new LinearLayout.LayoutParams(0,-1,3));resume();
 }
 void resume(){
  if(!current()||reading||(selection!=null&&selection.isShowing()))return;
  String pending=prefs.getString(key+":pending","");if(!pending.isEmpty()){try{send(new JSONObject(pending));}catch(Exception bad){message.setText("저장된 요청 확인이 필요합니다. 새 요청을 만들지 않았습니다.");}return;}
  reading=true;h.api.call("native/roster60/battle/active",null,(r,e)->{
   if(!current())return;reading=false;if(e!=null){error(e);return;}
   JSONArray battles=r.optJSONArray("battles");if(battles!=null&&battles.length()>0){read(battles.optJSONObject(0).optString("battleId"));return;}options();
  });
 }
 void read(String id){reading=true;h.api.call("native/roster60/battle/read",h.obj("battleId",id),(r,e)->{if(!current())return;reading=false;if(e!=null)error(e);else bind(r);});}
 void options(){
  reading=true;h.api.call("native/roster60/battle/options",null,(r,e)->{
   if(!current())return;reading=false;if(e!=null){error(e);return;}
   JSONArray units=r.optJSONArray("units"),encounters=r.optJSONArray("encounters");
   if(units==null||units.length()==0){message.setText("편성할 개체가 없습니다. 튜토리얼에서 갈매미맨으로 시작하세요.");return;}
   if(encounters==null||encounters.length()==0){error("BATTLE_POLICY_PENDING");return;}
   message.setText("전투 개체를 선택하세요.");String[] names=new String[units.length()];for(int i=0;i<names.length;i++)names[i]=units.optJSONObject(i).optString("name");
   selection=new GameDialog(h).navy().setTitle("전투 개체").setItems(names,(d,i)->{
    if(!current())return;String[] targets=new String[encounters.length()];for(int j=0;j<targets.length;j++)targets[j]=encounters.optJSONObject(j).optString("name");
    selection=new GameDialog(h).navy().setTitle("전투 선택").setItems(targets,(dd,j)->{if(current())mutate("create",h.obj("ownedId",units.optJSONObject(i).optString("ownedId"),"encounterId",encounters.optJSONObject(j).optString("id")));}).setNegativeButton("취소",null).show();
   }).setNegativeButton("취소",null).show();
  });
 }
 void command(JSONObject command){
  JSONObject action=h.obj("kind",command.optString("kind"));try{if(command.has("skillId"))action.put("skillId",command.getString("skillId"));}catch(Exception impossible){throw new IllegalStateException(impossible);}
  mutate("action",h.obj("battleId",command.optString("battleId"),"revision",command.optInt("expectedRevision"),"action",action));
 }
 void mutate(String op,JSONObject body){
  if(!current()||!prefs.getString(key+":pending","").isEmpty())return;
  try{body.put("requestId",UUID.randomUUID().toString());JSONObject intent=h.obj("operation",op,"body",body);
   if(!prefs.edit().putString(key+":pending",intent.toString()).commit()){error("요청 저장 실패");return;}send(intent);
  }catch(Exception bad){error("요청 생성 실패");}
 }
 void send(JSONObject intent){
  JSONObject body=intent.optJSONObject("body");if(body==null)return;String id=body.optString("requestId"),op=intent.optString("operation");
  if(!Arrays.asList("create","action","settle").contains(op)||id.isEmpty()){error("저장된 요청이 올바르지 않습니다.");return;}
  if(!IN_FLIGHT.add(id)){message.setText("서버 응답 확인 중…");return;}panel.lock();settle.setEnabled(false);
  h.api.call("native/roster60/battle/"+op,body,(r,e)->{
   IN_FLIGHT.remove(id);if(!owner.equals(h.api.token()))return;
   boolean rejected=Arrays.asList("BATTLE_POLICY_PENDING","BATTLE_POLICY_INVALID","BATTLE_GROWTH_PENDING","BATTLE_SKILL_POLICY_PENDING","FIVE_STAT_REBIRTH_POLICY_PENDING","ROSTER_CATALOG_PENDING","FIVE_STAT_CATALOG_REQUIRED","FIVE_STAT_CATALOG_INVALID","FIVE_STATS_REQUIRED","INVALID_FIVE_STATS","BASE_MP_MISMATCH","ROSTER_CATALOG_INVALID","INSUFFICIENT_MP","STALE_REVISION","UNIT_UNAVAILABLE","UNIT_NOT_FOUND","ENCOUNTER_NOT_ALLOWED","BATTLE_NOT_FOUND","BATTLE_FINISHED","BATTLE_NOT_FINISHED","INVALID_REQUEST","REQUEST_ID_REUSED","CAP_XP_POLICY_REQUIRED").contains(e);
   if(e==null||rejected)prefs.edit().remove(key+":pending").commit();
   if(!current())return;
   if(e!=null){error(e);if(rejected&&view!=null)read(view.optString("battleId"));return;}
   // Receipt may be older than current state after reconnect: read authoritative latest.
   bind(r);read(r.optString("battleId"));
  });
 }
 void error(String e){message.setText(e.contains("PENDING")||e.contains("CATALOG")?h.getString(R.string.roster60_battle_pending)+"\n"+e:e);}
 void bind(JSONObject r){
  try{
   JSONObject s=r.getJSONObject("state");if(!"five-battle-v1".equals(s.getString("version")))throw new JSONException("version");
   JSONArray fighters=s.getJSONArray("fighters");JSONObject self=fighters.getJSONObject(0),foe=fighters.getJSONObject(1);
   StringBuilder text=new StringBuilder();for(JSONObject f:new JSONObject[]{self,foe}){JSONObject v=f.getJSONObject("stats");text.append(f.getString("name")).append(" · 체력 ").append(f.getInt("hp")).append('/').append(v.getInt("hp")).append(" · MP ").append(f.getInt("mp")).append('/').append(v.getInt("mp")).append(" · 내구력 ").append(v.getInt("durability")).append(" · 저항력 ").append(v.getInt("statusResistance")).append(" · 속도 ").append(v.getInt("speed")).append('\n');}
   stats.setText(text);boolean active="ACTIVE".equals(r.getString("status"));JSONArray commands=new JSONArray(),skills=new JSONArray();String[] ids={"attack","skills","focus","item","switch","escape"},labels={"공격","기술","집중","아이템","교체","도망치기"};
   for(int i=0;i<6;i++)commands.put(h.obj("kind",ids[i],"label",labels[i],"enabled",active&&i<3,"reason",i<3?"":"정책 준비 중"));
   JSONArray raw=self.getJSONArray("skills");for(int i=0;i<raw.length();i++){JSONObject sk=new JSONObject(raw.getJSONObject(i).toString());boolean enabled=active&&self.getInt("mp")>=sk.getInt("mpCost");sk.put("enabled",enabled);sk.put("reason",active?(enabled?"":"MP 부족"):"전투 종료");sk.put("description","위력 "+sk.optDouble("power"));skills.put(sk);}
   JSONObject displaySelf=new JSONObject(self.toString());displaySelf.put("maxHp",self.getJSONObject("stats").getInt("hp"));displaySelf.put("maxMp",self.getJSONObject("stats").getInt("mp"));JSONObject displayFoe=new JSONObject(foe.toString());displayFoe.put("maxHp",foe.getJSONObject("stats").getInt("hp"));displayFoe.put("maxMp",foe.getJSONObject("stats").getInt("mp"));
   panel.bind(h.obj("epoch","product-five-battle-v1","battleId",r.getString("battleId"),"revision",r.getInt("revision"),"self",displaySelf,"foe",displayFoe,"commands",commands,"skills",skills,"events",new JSONArray()));
   view=r;message.setText(active?"서버 전투 · 턴 "+s.getInt("turn"):"SETTLED".equals(r.getString("status"))?"정산 완료 · "+r.getJSONObject("settlement").optString("outcome"):"전투 종료 · "+s.optString("outcome"));settle.setEnabled("FINISHED".equals(r.getString("status")));
  }catch(Exception bad){view=null;panel.lock();settle.setEnabled(false);message.setText("서버 전투 데이터 형식을 확인할 수 없습니다.");}
 }
 boolean back(){if(panel!=null){panel.cancelInteraction();if(panel.showCommands())return true;}h.home();return true;}
 void pause(){if(panel!=null)panel.cancelInteraction();if(selection!=null){selection.dismiss();selection=null;}}
}
