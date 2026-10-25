package org.dpdns.gmm;
import android.content.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
/**
 * 예전 모집소 화면(트레이·수량·지원서 받기)에서 새 60종 모집 API를 쓰는 연결부.
 * 비용·확률·활성 등급은 서버 응답 그대로(하급 2·중급 3·고급 5장, credits 차감). 천장은 표시하지 않는다.
 * 요청 번호를 먼저 저장해 중복 클릭·재시도·화면 이탈에도 같은 요청만 다시 보낸다(서버가 한 번만 차감).
 * 저장 키는 NativeRoster60Library와 같아 이전 화면의 미확인 요청·결과도 이어서 확인한다.
 */
final class Roster60RecruitPurchase {
 static final List<String> REJECTED=Arrays.asList("ROSTER_CATALOG_PENDING","CATALOG_CHANGED","INVALID_REQUEST","RECRUIT_GRADE_POLICY_PENDING","ROSTER_START_REQUIRED","INSUFFICIENT_CREDITS","REQUEST_ID_REUSED");
 final MainActivity h;final SharedPreferences prefs;final String owner,key;final JSONObject info;boolean busy;
 // 요청 상태가 바뀌면 화면 버튼(모집하기/결과 확인 중)을 다시 그린다.
 Runnable changed=()->{};
 // 후보 회차 정보. 있으면 모집하기가 1회 후보 회차를 시작하고(수량 1), 후보 화면에서 수령·새로고침한다.
 JSONObject session;
 Roster60RecruitPurchase(MainActivity h,JSONObject info){this.h=h;this.info=info;owner=h.api.token();key=NativeProgression.accountKey(BuildConfig.SERVER_URL+"|"+owner);prefs=h.getSharedPreferences("roster60_recruit",Context.MODE_PRIVATE);}
 boolean pending(){return session==null&&(busy||!prefs.getString(key,"").isEmpty());}
 boolean current(int epoch){return epoch==h.epoch&&owner.equals(h.api.token())&&!h.isDestroyed();}
 JSONObject receipt(){try{String raw=prefs.getString(key+".receipt","");return raw.isEmpty()?null:new JSONObject(raw);}catch(JSONException bad){return null;}}

 /** 예전 모집소 화면이 읽는 형태로 만든다: 새 60종 모집 등급·비용·활성 여부 + 상태(me)의 지원서 받기 시각. 천장(pity)은 넣지 않는다. */
 static JSONObject merge(JSONObject me,JSONObject roster,long now)throws JSONException{
  JSONArray gachas=new JSONArray(),offers=roster.optJSONArray("offers");
  // 후보 회차 정보에는 등급별 enabled가 없고 전체 enabled만 있다.
  boolean all=roster.has("sessions")&&roster.optBoolean("enabled");
  if(offers!=null)for(int i=0;i<offers.length();i++){JSONObject o=offers.optJSONObject(i);if(o==null)continue;JSONObject g=MainActivity.obj("kind",o.optString("kind"),"label",o.optString("label"),"cost",o.optInt("cost"),"rerollCost",o.opt("rerollCost"),"enabled",all||o.optBoolean("enabled"),"reason",o.optString("reason"));if(o.optJSONObject("weights")!=null)g.put("weights",o.optJSONObject("weights"));gachas.put(g);}
  long wait=0;String ready=me==null?"":me.optString("claimReadyAt","");
  if(!ready.isEmpty())try{wait=Math.max(0,java.time.Instant.parse(ready).toEpochMilli()-now);}catch(Exception ignored){}
  return MainActivity.obj("roster60",true,"gachas",gachas,"credits",roster.optInt("credits",me==null?0:me.optInt("credits")),"wait",wait);
 }

 static final String[][] GRADES={{"COMMON","커먼"},{"UNCOMMON","언커먼"},{"RARE","레어"},{"EPIC","에픽"},{"LEGEND","전설"},{"HIDDEN","히든"},{"LIMITED","제한"},{"TRANSCEND","초월"},{"IMMORTAL","불멸"},{"ETERNAL","영원"}};
 static String gradeName(String code){for(String[] g:GRADES)if(g[0].equals(code))return g[1];return code;}
 /** 서버가 보낸 등급별 가중치(합계 100 또는 1000)를 [{tier,tierName,w(%)}]로 바꾼다. 없거나 이상하면 빈 배열(숫자를 만들지 않음). */
 static JSONArray weightRows(JSONObject offer){
  JSONArray rows=new JSONArray();JSONObject w=offer==null?null:offer.optJSONObject("weights");if(w==null)return rows;
  long total=0;Iterator<String> it=w.keys();while(it.hasNext()){Object v=w.opt(it.next());if(!(v instanceof Number)||((Number)v).doubleValue()<0)return new JSONArray();total+=((Number)v).longValue();}
  if(total!=100&&total!=1000)return rows;
  // 등급 순서(커먼→영원)로 보여 준다.
  for(String[] g:GRADES)if(w.has(g[0])){double pct=w.optDouble(g[0])*100.0/total;rows.put(MainActivity.obj("tier",g[0],"tierName",g[1],"w",new java.math.BigDecimal(pct).setScale(1,java.math.RoundingMode.HALF_UP).stripTrailingZeros()));}
  return rows;
 }
 /** 한 줄에 perLine개씩 끊어 등급 이름 중간에서 줄이 바뀌지 않게 한다. */
 static String oddsLines(JSONObject offer,int perLine){String[] parts=oddsText(offer,"\u0000").split("\u0000");if(parts.length==1&&parts[0].isEmpty())return "";StringBuilder b=new StringBuilder();for(int i=0;i<parts.length;i++){if(i>0)b.append(i%perLine==0?"\n":" · ");b.append(parts[i]);}return b.toString();}
 static String oddsText(JSONObject offer,String separator){JSONArray rows=weightRows(offer);StringBuilder b=new StringBuilder();for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(b.length()>0)b.append(separator);b.append(r.optString("tierName")).append(' ').append(RecruitmentProbabilityView.number((Number)r.opt("w"))).append('%');}return b.toString();}

 void buy(JSONObject g,int times){
  if(session!=null){if(!g.optBoolean("enabled"))return;NativeRecruitSession s=new NativeRecruitSession(h);s.fromCounter=true;s.open(session);s.queue("start",MainActivity.obj("kind",g.optString("kind"),"catalogVersion",session.optString("catalogVersion")));return;}
  if(pending()||!g.optBoolean("enabled"))return;
  try{JSONObject body=MainActivity.obj("kind",g.optString("kind"),"times",times,"catalogVersion",info.optString("catalogVersion"),"requestId",UUID.randomUUID().toString());if(prefs.edit().putString(key,body.toString()).commit()){changed.run();send();}}catch(Exception ignored){}
 }

 /** 저장된 요청이 있으면 같은 요청 번호로 다시 보낸다. 성공하면 결과를 저장한 뒤 요청을 지우고 등록증 카드로 보여 준다. */
 void send(){
  if(busy)return;String raw=prefs.getString(key,"");if(raw.isEmpty())return;int epoch=h.epoch;
  try{JSONObject body=new JSONObject(raw);busy=true;
   changed.run();
   h.api.call("native/roster60/recruit",body,(r,e)->{busy=false;
    if(raw.equals(prefs.getString(key,""))){
     if(e==null&&r!=null&&body.optString("requestId").equals(r.optString("requestId"))){if(!prefs.edit().putString(key+".receipt",r.toString()).remove(key).commit()){if(current(epoch))Toast.makeText(h,"결과 저장 실패 · 다시 열어 확인해 주세요.",Toast.LENGTH_LONG).show();return;}}
     else if(REJECTED.contains(e))prefs.edit().remove(key).commit();
    }
    if(!current(epoch))return;changed.run();
    if(e!=null){new GameDialog(h).navy().setTitle("모집").setMessage(REJECTED.contains(e)?"모집하지 못했어요 · "+e:"결과를 확인하지 못했어요. 지원서는 한 번만 사용돼요.\n화면을 다시 열면 같은 요청으로 확인합니다.").setPositiveButton("확인",(d,w)->h.draw()).show();return;}
    if(r==null||!body.optString("requestId").equals(r.optString("requestId"))){Toast.makeText(h,"응답 확인이 필요해요. 화면을 다시 열어 주세요.",Toast.LENGTH_LONG).show();return;}
    showCards(h,r,h::draw);
   });
  }catch(JSONException bad){prefs.edit().remove(key).commit();}
 }

 /** 받은 개체를 방범대 등록증 카드로 보여 준다. 카드가 없는 개체는 이름·레벨만 표시한다(그림을 지어내지 않음). */
 static void showCards(MainActivity h,JSONObject receipt,Runnable after){
  JSONArray items=receipt.optJSONArray("items");if(items==null||items.length()==0){if(after!=null)after.run();return;}
  LinearLayout row=new LinearLayout(h);row.setTag("roster60-recruit-cards");row.setGravity(Gravity.CENTER);
  int cardHeight=Math.max(h.dp(160),Math.round(h.getResources().getDisplayMetrics().heightPixels*.5f)),cardWidth=cardHeight*3/4;
  for(int i=0;i<items.length();i++){JSONObject u=items.optJSONObject(i);if(u==null)continue;LinearLayout cell=new LinearLayout(h);cell.setOrientation(LinearLayout.VERTICAL);cell.setGravity(Gravity.CENTER_HORIZONTAL);
   ImageView card=new ImageView(h);card.setTag("roster60-recruit-card-"+i);if(RegistrationV10Art.bind(h,card,u.optString("speciesId"))){card.setContentDescription("방범대 등록증 · "+u.optString("name"));cell.addView(card,new LinearLayout.LayoutParams(cardWidth,cardHeight));}
   TextView label=h.text(u.optString("name")+" · Lv"+u.optInt("level"),14);label.setGravity(Gravity.CENTER);cell.addView(label,new LinearLayout.LayoutParams(Math.max(cardWidth,h.dp(120)),-2));
   LinearLayout.LayoutParams slot=new LinearLayout.LayoutParams(-2,-2);slot.setMargins(h.dp(6),0,h.dp(6),0);row.addView(cell,slot);}
  HorizontalScrollView scroll=new HorizontalScrollView(h);scroll.addView(row);
  new GameDialog(h).navy().setTitle("모집 결과 · "+items.length()+"명 영구 지급").setView(scroll).setPositiveButton("확인",(d,w)->{if(after!=null)after.run();}).show();
 }
}
