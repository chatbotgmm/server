package org.dpdns.gmm;
import org.json.*;
import java.util.*;

/** In-memory synthetic review engine only. No accounts, product balance or persistence API. */
final class CombatV2OfflineEngine {
 private static final String[] ELEMENTS={"NORMAL","FIRE","WATER","LIGHTNING","ICE","NATURE","HOLY","DARK"};
 private static boolean integer(Object value){return value instanceof Number&&Double.isFinite(((Number)value).doubleValue())&&((Number)value).doubleValue()>=0&&((Number)value).doubleValue()<=Integer.MAX_VALUE&&((Number)value).doubleValue()==Math.floor(((Number)value).doubleValue());}
 private static void validateSkill(JSONObject skill)throws Exception {
  if(!(skill.opt("id") instanceof String)||skill.getString("id").isEmpty()||!(skill.opt("name") instanceof String)||!Arrays.asList(ELEMENTS).contains(skill.opt("element"))||!integer(skill.opt("mpCost")))fail("INVALID_SKILL");
 }
 private static void validateFighter(JSONObject f)throws Exception {
  if(!(f.opt("id") instanceof String)||f.getString("id").isEmpty()||!integer(f.opt("level"))||f.getInt("level")<1||!integer(f.opt("maxHp"))||f.getInt("maxHp")<1||f.optJSONArray("skills")==null||f.getJSONArray("skills").length()>6)fail("INVALID_FIGHTER");
  if(!integer(f.opt("maxMp"))||f.getInt("maxMp")<1)fail("MP_GROWTH_REQUIRED");
  JSONObject attack=f.optJSONObject("attack");if(attack==null)fail("INVALID_SKILL");validateSkill(attack);
  Set<String> ids=new HashSet<>();for(int i=0;i<f.getJSONArray("skills").length();i++){JSONObject sk=f.getJSONArray("skills").optJSONObject(i);if(sk==null)fail("INVALID_SKILL");validateSkill(sk);if(!ids.add(sk.getString("id")))fail("DUPLICATE_SKILL");}
  JSONObject fit=f.optJSONObject("suitability"),def=f.optJSONObject("defense");if(fit==null||def==null)fail("PROFILE_REQUIRED");
  for(String e:ELEMENTS)if(!(fit.opt(e) instanceof Number)||!Double.isFinite(((Number)fit.opt(e)).doubleValue())||!Arrays.asList("weak","normal","resist","immune","reflect").contains(def.opt(e)))fail("PROFILE_REQUIRED");
 }
 private JSONObject state;
 private final double baseDamage;
 private final Map<String,String> signatures=new HashMap<>();
 private final Map<String,JSONObject> receipts=new HashMap<>();
 static JSONObject copy(JSONObject value)throws Exception{return new JSONObject(value.toString());}
 static void fail(String code){throw new IllegalArgumentException(code);}
 CombatV2OfflineEngine(JSONObject config)throws Exception {
  if(!config.optBoolean("fixtureOnly")||!"synthetic-fixture-not-product".equals(config.optString("balanceVersion"))
    ||!"fixture-base-times-fit".equals(config.optString("reflectionPolicy")))fail("REVIEW_FIXTURE_REQUIRED");
  baseDamage=config.getDouble("baseDamage");if(!Double.isFinite(baseDamage)||baseDamage<0)fail("INVALID_DAMAGE_POLICY");
  JSONObject raw=config.getJSONObject("initial");validateFighter(raw.getJSONObject("self"));validateFighter(raw.getJSONObject("foe"));
  JSONObject initial=CombatV2ReviewContract.checked(raw);
  if(initial.getInt("revision")!=0||initial.getInt("viewerSide")!=0)fail("INITIAL_STATE_REQUIRED");
  state=new JSONObject().put("battleId",initial.getString("battleId")).put("revision",0).put("turn",1)
    .put("activeSide",initial.getInt("activeSide")).put("phase","action").put("winner",JSONObject.NULL)
    .put("fighters",new JSONArray().put(copy(initial.getJSONObject("self"))).put(copy(initial.getJSONObject("foe")))).put("events",new JSONArray());
  for(int side=0;side<2;side++){JSONObject f=state.getJSONArray("fighters").getJSONObject(side);f.put("hp",f.getInt("maxHp"));f.put("mp",f.getInt("maxMp"));}
 }
 private CombatV2OfflineEngine(CombatV2OfflineEngine source)throws Exception {
  baseDamage=source.baseDamage;state=copy(source.state);signatures.putAll(source.signatures);receipts.putAll(source.receipts);
 }
 synchronized CombatV2OfflineEngine fork()throws Exception{return new CombatV2OfflineEngine(this);}
 private String reason(JSONObject s,int side,int cost)throws Exception {
  if("over".equals(s.getString("phase")))return "전투 종료";
  if(s.getInt("activeSide")!=side)return "상대 행동 중";
  return s.getJSONArray("fighters").getJSONObject(side).getInt("mp")<cost?"MP 부족":"";
 }
 private JSONObject option(String kind,String label,String reason)throws Exception{return new JSONObject().put("kind",kind).put("label",label).put("enabled",reason.isEmpty()).put("reason",reason);}
 private JSONObject snapshot(JSONObject s,int side)throws Exception {
  if(side!=0&&side!=1)fail("UNAUTHORIZED");
  JSONObject self=s.getJSONArray("fighters").getJSONObject(side);
  JSONArray commands=new JSONArray().put(option("attack","공격",reason(s,side,self.getJSONObject("attack").getInt("mpCost"))))
    .put(option("skills","기술",reason(s,side,0))).put(option("focus","집중",reason(s,side,0)))
    .put(option("item","아이템","아이템 계약 미연결")).put(option("switch","교체","현재 1대1 · 교체 계약 미연결")).put(option("escape","도망치기","도망 판정 미연결"));
  JSONArray skills=new JSONArray();for(int i=0;i<self.getJSONArray("skills").length();i++){
   JSONObject sk=copy(self.getJSONArray("skills").getJSONObject(i));String why=reason(s,side,sk.getInt("mpCost"));skills.put(sk.put("enabled",why.isEmpty()).put("reason",why));
  }
  return new JSONObject().put("epoch",CombatV2ReviewContract.EPOCH).put("battleId",s.getString("battleId"))
    .put("revision",s.getInt("revision")).put("turn",s.getInt("turn")).put("phase",s.getString("phase"))
    .put("activeSide",s.getInt("activeSide")).put("viewerSide",side).put("winner",s.get("winner"))
    .put("self",copy(self)).put("foe",copy(s.getJSONArray("fighters").getJSONObject(1-side)))
    .put("commands",commands).put("skills",skills).put("events",new JSONArray(s.getJSONArray("events").toString()));
 }
 synchronized JSONObject snapshot(int side)throws Exception{return snapshot(state,side);}
 synchronized JSONObject command(int side,JSONObject input)throws Exception {
  if(side!=0&&side!=1)fail("UNAUTHORIZED");
  String kind=input.optString("kind");Set<String> expected=new TreeSet<>(Arrays.asList("battleId","requestId","expectedRevision","kind"));if("skill".equals(kind))expected.add("skillId");
  Set<String> keys=new TreeSet<>();Iterator<String> it=input.keys();while(it.hasNext())keys.add(it.next());
  Object revision=input.opt("expectedRevision"),id=input.opt("requestId");
  if(!keys.equals(expected)||!state.getString("battleId").equals(input.opt("battleId"))||!(id instanceof String)||((String)id).isEmpty()
    ||!(revision instanceof Number)||((Number)revision).doubleValue()<0||((Number)revision).doubleValue()!=Math.floor(((Number)revision).doubleValue())
    ||((Number)revision).doubleValue()>9007199254740991d)fail("INVALID_COMMAND");
  String signature=new JSONArray().put(input.get("battleId")).put(revision).put(input.get("kind")).put(input.opt("skillId")==null?JSONObject.NULL:input.opt("skillId")).toString();
  String key=side+":"+id;
  if(receipts.containsKey(key)){if(!signature.equals(signatures.get(key)))fail("IDEMPOTENCY_CONFLICT");return copy(receipts.get(key));}
  if(((Number)revision).doubleValue()!=state.getInt("revision"))fail("STALE_REVISION");
  if("over".equals(state.getString("phase")))fail("BATTLE_OVER");
  if(state.getInt("activeSide")!=side)fail("NOT_YOUR_TURN");
  if(!Arrays.asList("attack","skill","focus").contains(kind))fail("ACTION_NOT_CONNECTED");
  JSONObject next=copy(state),a=next.getJSONArray("fighters").getJSONObject(side),d=next.getJSONArray("fighters").getJSONObject(1-side);
  int mp=a.getInt("mp");JSONObject event=new JSONObject().put("seq",next.getInt("revision")+1).put("kind",kind).put("actor",side).put("mpBefore",mp);
  if("focus".equals(kind)){
   a.put("mp",(int)Math.min((long)a.getInt("maxMp"),(long)mp+(long)Math.floor(a.getInt("maxMp")*.25)));event.put("mpRecovered",a.getInt("mp")-mp);
  }else{
   JSONObject sk=null;if("attack".equals(kind))sk=a.getJSONObject("attack");else for(int i=0;i<a.getJSONArray("skills").length();i++){
    JSONObject candidate=a.getJSONArray("skills").getJSONObject(i);if(candidate.getString("id").equals(input.opt("skillId")))sk=candidate;
   }
   if(sk==null)fail("UNKNOWN_SKILL");int cost=sk.getInt("mpCost");if(mp<cost)fail("INSUFFICIENT_MP");
   String element=sk.getString("element"),defense=d.getJSONObject("defense").getString(element);
   double fit=Math.max(0,1+.1*a.getJSONObject("suitability").getDouble(element));
   double multiplier="weak".equals(defense)?1.5:"resist".equals(defense)?.5:1;
   double raw="immune".equals(defense)?0:baseDamage*fit*multiplier;
   if(!Double.isFinite(raw)||raw<0)fail("INVALID_DAMAGE_POLICY");
   JSONObject target="reflect".equals(defense)?a:d;int before=target.getInt("hp"),damage=(int)Math.min(before,Math.floor(raw));target.put("hp",before-damage);
   event.put("skillId",sk.getString("id")).put("element",element).put("suitabilityMultiplier",fit).put("defense",defense)
    .put("target","reflect".equals(defense)?side:1-side).put("hpBefore",before).put("hpAfter",target.getInt("hp")).put("damage",damage);
   a.put("mp",mp-cost);
  }
  event.put("mpAfter",a.getInt("mp"));next.getJSONArray("events").put(event);next.put("revision",next.getInt("revision")+1).put("turn",next.getInt("turn")+1).put("activeSide",1-side);
  if(a.getInt("hp")==0||d.getInt("hp")==0)next.put("phase","over").put("winner",a.getInt("hp")==0?1-side:side);
  JSONObject response=snapshot(next,side);state=next;signatures.put(key,signature);receipts.put(key,copy(response));return response;
 }
}
