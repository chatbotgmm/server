package org.dpdns.gmm;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
/** Product six-member party editor and authenticated human matching entry. */
final class NativeRoster60Pvp {
 final MainActivity h;final SharedPreferences prefs;final Handler timer=new Handler(Looper.getMainLooper());
 final ArrayList<String> selected=new ArrayList<>();String owner,key;int epoch;boolean busy,paused,waiting;JSONArray units=new JSONArray();
 LinearLayout root,roster,actions;TextView status,summary;Button save,normal,rank,cancel;
 NativeRoster60Pvp(MainActivity h){this.h=h;prefs=h.getSharedPreferences("roster60_human_queue",0);}
 boolean current(){return epoch==h.epoch&&"roster60Pvp".equals(h.screen)&&owner.equals(h.api.token())&&!h.isDestroyed();}
 void open(){h.base("대전 편성 · 최대 6마리","roster60Pvp");epoch=h.epoch;owner=h.api.token();key=NativeProgression.accountKey(BuildConfig.SERVER_URL+"|"+owner);h.page.removeViewAt(h.page.getChildCount()-1);
 root=new LinearLayout(h);root.setOrientation(1);h.page.addView(root,new LinearLayout.LayoutParams(-1,0,1));status=h.text("서버 편성 확인 중…",12);root.addView(status);summary=h.text("출전 1 + 예비 최대 5 · 순차 1대1",12);root.addView(summary);
 LinearLayout columns=new LinearLayout(h);root.addView(columns,new LinearLayout.LayoutParams(-1,0,1));ScrollView list=new ScrollView(h);columns.addView(list,new LinearLayout.LayoutParams(0,-1,2));roster=new LinearLayout(h);roster.setOrientation(1);list.addView(roster);ScrollView menu=new ScrollView(h);columns.addView(menu,new LinearLayout.LayoutParams(0,-1,1));actions=new LinearLayout(h);actions.setOrientation(1);menu.addView(actions);
 save=h.button("편성 저장",this::save);save.setTag("roster60-pvp-party-save");actions.addView(save);actions.addView(h.button("편성 비우기",()->{if(busy||waiting)return;selected.clear();drawParty();}));normal=h.button("일반 대전",()->join("normal"));normal.setTag("roster60-pvp-normal");actions.addView(normal);rank=h.button("랭크 대전",()->join("rank"));rank.setTag("roster60-pvp-rank");actions.addView(rank);cancel=h.button("매칭 취소",()->request("cancel",h.obj()));actions.addView(cancel);actions.addView(h.button("다시 확인",this::resume));
 fetchOptions();resume();}
 void fetchOptions(){h.api.call("native/roster60/pvp/options",null,(r,e)->{if(!current())return;if(e!=null){status.setText("편성 확인 필요 · "+e);enable();return;}units=r.optJSONArray("units");if(units==null)units=new JSONArray();selected.clear();JSONArray ids=r.optJSONArray("partyIds");if(ids!=null)for(int i=0;i<ids.length();i++)selected.add(ids.optString(i));drawParty();});}
 void drawParty(){roster.removeAllViews();for(int i=0;i<units.length();i++){JSONObject u=units.optJSONObject(i);String id=u.optString("ownedId");Button b=h.button((selected.contains(id)?"✓ ":"")+u.optString("name")+(selected.indexOf(id)==0?" · 첫 출전":""),()->{if(busy||waiting)return;if(selected.contains(id))selected.remove(id);else if(selected.size()<6)selected.add(id);else{status.setText("출전 포함 최대 6마리입니다.");return;}drawParty();});b.setTag("party-unit-"+id);b.setEnabled(!busy&&!waiting);roster.addView(b,new LinearLayout.LayoutParams(-1,h.dp(48)));}summary.setText("편성 "+selected.size()+" / 6 · 먼저 선택한 개체가 첫 출전 · 탭으로 추가/해제");enable();}
 void enable(){boolean free=!busy&&!waiting&&prefs.getString(key,"").isEmpty();save.setEnabled(free&&!selected.isEmpty());normal.setEnabled(free&&!selected.isEmpty());rank.setEnabled(free&&!selected.isEmpty());cancel.setEnabled(waiting&&!busy);}
 void save(){if(busy||waiting||selected.isEmpty()||selected.size()>6)return;request("party",h.obj("ownedIds",new JSONArray(selected)));}
 void join(String mode){if(busy||waiting||selected.isEmpty())return;busy=true;enable();JSONArray ids=new JSONArray(selected);h.api.call("native/roster60/pvp/party",h.obj("ownedIds",ids),(r,e)->{busy=false;if(!current())return;if(e!=null){status.setText(e);enable();return;}request("join",h.obj("mode",mode));});}
 void request(String op,JSONObject body){if(!current()||busy||!prefs.getString(key,"").isEmpty())return;try{body.put("requestId",UUID.randomUUID().toString());JSONObject saved=h.obj("op",op,"body",body);if(prefs.edit().putString(key,saved.toString()).commit())send(saved);}catch(JSONException e){status.setText("요청 저장 실패");}}
 void send(JSONObject saved){if(busy||!current())return;busy=true;enable();String op=saved.optString("op");JSONObject body=saved.optJSONObject("body");if(!Arrays.asList("party","join","cancel").contains(op)||body==null){busy=false;status.setText("저장된 요청 확인 필요");return;}
 // Party persistence is an idempotent replacement, and its schema has no requestId.
 JSONObject wire=body;try{if(op.equals("party")){wire=new JSONObject(body.toString());wire.remove("requestId");}}catch(JSONException ignored){}
 h.api.call("native/roster60/pvp/"+op,wire,(r,e)->{busy=false;if(!owner.equals(h.api.token()))return;
 boolean rejected=e!=null&&(e.contains("PENDING")||Arrays.asList("INVALID_PARTY","UNIT_UNAVAILABLE","UNIT_NOT_FOUND","INVALID_REQUEST","REQUEST_ID_REUSED","PVP_ALREADY_ACTIVE","PVP_QUEUE_ACTIVE","PVP_BUSY","PARTY_REQUIRED").contains(e));
 if(e==null||rejected)prefs.edit().remove(key).commit();if(!current())return;if(e!=null){status.setText("확인 필요 · "+e);enable();schedule();return;}if(op.equals("party")){status.setText("편성 저장 완료");fetchOptions();}else show(r);enable();schedule();});}
 void resume(){paused=false;if(!current()||busy)return;String pending=prefs.getString(key,"");if(!pending.isEmpty()){try{send(new JSONObject(pending));}catch(JSONException e){status.setText("저장된 요청 확인 필요");}return;}busy=true;enable();h.api.call("native/roster60/pvp/current",null,(r,e)->{busy=false;if(!current())return;if(e!=null)status.setText("대전 확인 필요 · "+e);else show(r);enable();schedule();});}
 void show(JSONObject r){JSONObject battle=r.optJSONObject("battle");if(battle!=null){pause();h.openRosterHumanBattle(battle);return;}waiting="waiting".equals(r.optString("status"));status.setText(waiting?"실제 사용자 매칭 대기 중 · 취소 가능":"편성을 저장하고 대전을 시작하세요.");drawParty();}
 void schedule(){timer.removeCallbacksAndMessages(null);if(current()&&!paused)timer.postDelayed(this::resume,2000);}
 void pause(){paused=true;timer.removeCallbacksAndMessages(null);}
 void back(){if(waiting){status.setText("매칭 취소 후 돌아갈 수 있습니다.");return;}if(busy||!prefs.getString(key,"").isEmpty()){status.setText("요청 결과를 먼저 확인하세요.");return;}pause();h.home();}
}
