package org.dpdns.gmm;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.json.JSONObject;

/** Authenticated server snapshots only. Owned-unit levels and reincarnation never calculate this state. */
final class NativeCommander {
 private static final class Entry {JSONObject known,display;long revision=-1;}
 private static final Map<String,Entry> ACCOUNTS=new HashMap<>();
 private static final String[] FIELDS={"name","level","totalXp","nextLevelXp","xpToNext","maxLevel","policyVersion","revision","initialized","status","testValues","productionBalanceApproved","initialization","unitXpIndependent","totalXpPolicy"};
 private NativeCommander(){}
 static boolean current(MainActivity h,String owner,int epoch){return owner!=null&&!owner.isEmpty()&&owner.equals(h.api.token())&&epoch==h.epoch&&!h.isFinishing()&&!h.isDestroyed();}
 private static String scope(String owner){return NativeProgression.accountKey(BuildConfig.SERVER_URL+"|commander-v1|"+Roster60StoryJournal.accountId(owner));}
 private static JSONObject copy(JSONObject value){try{return new JSONObject(value.toString());}catch(Exception invalid){return pending("DATA_INVALID");}}
 static JSONObject pending(String status){return MainActivity.obj("name","방범대장","level",JSONObject.NULL,"totalXp",JSONObject.NULL,"nextLevelXp",JSONObject.NULL,"xpToNext",JSONObject.NULL,"maxLevel",JSONObject.NULL,"policyVersion",JSONObject.NULL,"revision",JSONObject.NULL,"initialized",false,"status",status,"testValues",false,"productionBalanceApproved",false,"initialization","UNCONFIGURED","unitXpIndependent",true,"totalXpPolicy","retain-lifetime-total");}
 private static Long integer(JSONObject value,String key){Object raw=value.opt(key);if(!(raw instanceof Number))return null;Number number=(Number)raw;return number.longValue()>=0&&number.doubleValue()==number.longValue()?number.longValue():null;}
 private static Object decimal(JSONObject value,String key){Object raw=value.opt(key);return raw==null||raw==JSONObject.NULL?JSONObject.NULL:raw instanceof String&&((String)raw).matches("[0-9]+")?raw:null;}
 private static JSONObject normalized(JSONObject value){
  if(value==null)return pending("POLICY_PENDING");String status=value.optString("status");
  if(!Arrays.asList("READY","POLICY_PENDING","POLICY_MISMATCH","CURVE_EXTENSION_PENDING","DATA_INVALID").contains(status))return pending("DATA_INVALID");
  if(!Arrays.asList("READY","CURVE_EXTENSION_PENDING").contains(status))return pending(status);
  Long level=integer(value,"level"),revision=integer(value,"revision"),max=integer(value,"maxLevel");Object total=decimal(value,"totalXp"),next=decimal(value,"nextLevelXp"),left=decimal(value,"xpToNext");
  if(level==null||level<1||revision==null||max==null||max<level||total==null||total==JSONObject.NULL||next==null||left==null||value.optString("policyVersion").isEmpty()||!Boolean.TRUE.equals(value.opt("unitXpIndependent"))||!"retain-lifetime-total".equals(value.optString("totalXpPolicy")))return pending("DATA_INVALID");
  return MainActivity.obj("name","방범대장","level",level,"totalXp",total,"nextLevelXp",next,"xpToNext",left,"maxLevel",max,"policyVersion",value.optString("policyVersion"),"revision",revision,"initialized",value.optBoolean("initialized"),"status",status,"testValues",value.optBoolean("testValues"),"productionBalanceApproved",value.optBoolean("productionBalanceApproved"),"initialization",value.optString("initialization","UNCONFIGURED"),"unitXpIndependent",true,"totalXpPolicy","retain-lifetime-total");
 }
 private static boolean same(JSONObject first,JSONObject second){
  for(String key:FIELDS){
   Object before=first.opt(key),after=second.opt(key);
   // JSONObject's defensive copy can read a small Long back as an Integer.
   // Normalized numeric fields are exact integers, so compare their values.
   if(before instanceof Number&&after instanceof Number){if(((Number)before).longValue()!=((Number)after).longValue())return false;}
   else if(!Objects.equals(before,after))return false;
  }
  return true;
 }
 static boolean sync(MainActivity h,JSONObject value,String owner,int epoch){return sync(h,value,owner,epoch,false);}
 static boolean syncReward(MainActivity h,JSONObject value,String owner,int epoch){return sync(h,value,owner,epoch,true);}
 private static synchronized boolean sync(MainActivity h,JSONObject value,String owner,int epoch,boolean receipt){
  if(!current(h,owner,epoch))return false;JSONObject snapshot=normalized(value);Entry entry=ACCOUNTS.get(scope(owner));if(entry==null){entry=new Entry();ACCOUNTS.put(scope(owner),entry);}
  Long revision=integer(snapshot,"revision");
  if(revision==null){if(receipt)return false;entry.display=snapshot;return true;}
  if(receipt&&(entry.known==null||entry.display==null||!Arrays.asList("READY","CURVE_EXTENSION_PENDING").contains(entry.display.optString("status"))||!Objects.equals(entry.known.opt("policyVersion"),snapshot.opt("policyVersion"))))return false;
  if(revision<entry.revision||(receipt&&revision<=entry.revision))return false;
  if(revision==entry.revision&&entry.known!=null&&!same(entry.known,snapshot)){entry.display=pending("DATA_INVALID");return false;}
  entry.known=copy(snapshot);entry.display=copy(snapshot);entry.revision=revision;return true;
 }
 static synchronized JSONObject state(MainActivity h){String owner=h.api.token();Entry entry=owner.isEmpty()?null:ACCOUNTS.get(scope(owner));return entry==null||entry.display==null?pending("POLICY_PENDING"):copy(entry.display);}
 static void refresh(MainActivity h,Consumer<JSONObject> receiver){
  String owner=h.api.token();int epoch=h.epoch;if(!current(h,owner,epoch))return;
  h.api.call("native/roster60/commander",null,(result,error)->{if(!current(h,owner,epoch))return;JSONObject snapshot=result==null?null:result.has("commander")?result.optJSONObject("commander"):result;sync(h,error==null?snapshot:null,owner,epoch);receiver.accept(state(h));});
 }
 static String profileText(JSONObject state){
  String status=state.optString("status");if(!Arrays.asList("READY","CURVE_EXTENSION_PENDING").contains(status))return "방범대장 성장 정보 "+(status.equals("DATA_INVALID")||status.equals("POLICY_MISMATCH")?"확인 중":"준비 중");
  String text="방범대장 Lv."+state.optLong("level")+(state.optBoolean("testValues")?" · 시험 수치":"")+"\n누적 XP "+state.optString("totalXp");
  return text+"\n"+(status.equals("CURVE_EXTENSION_PENDING")?"다음 성장 구간 준비 중":state.isNull("xpToNext")?"다음 성장 정보 준비 중":"다음까지 "+state.optString("xpToNext")+" XP");
 }
 static String rewardText(JSONObject settlement){
  JSONObject reward=settlement==null?null:settlement.optJSONObject("commanderReward");if(reward==null)return "방범대장 XP · 정산 정보 준비 중";
  String status=reward.optString("status");if(status.equals("POLICY_PENDING"))return "방범대장 XP · 성장 정보 준비 중";
  if(!Arrays.asList("APPLIED","RECORDED_ZERO").contains(status))return "방범대장 XP · 정산 정보 확인 중";
  Long awarded=integer(reward,"xpAwarded"),recorded=integer(reward,"recordedXp");if(awarded==null||recorded==null)return "방범대장 XP · 정산 정보 확인 중";
  String text=reward.optBoolean("replayed")?"방범대장 XP "+recorded+" · 이전 정산 기록\n이번 추가 지급 "+awarded:"방범대장 XP +"+awarded+" · 기록 완료";
  JSONObject commander=reward.optJSONObject("commander");return text+(commander!=null&&commander.optBoolean("testValues")?" · 시험 수치":"");
 }
 static CharSequence styled(String text){SpannableString value=new SpannableString(text);int at=text.indexOf("시험 수치");if(at>=0)value.setSpan(new RelativeSizeSpan(.82f),at,at+5,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);return value;}
}
