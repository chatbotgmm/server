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
 final ArrayList<String> selected=new ArrayList<>();String owner,key;int epoch, rosterPage;boolean busy,paused,waiting;JSONArray units=new JSONArray();
 final boolean management;String focused="";ImageView focusedArt;TextView focusedName;
 LinearLayout root,roster,actions;TextView status,summary;Button save,normal,rank,cancel;
 NativeRoster60Pvp(MainActivity h){this(h,false);}
 NativeRoster60Pvp(MainActivity h,boolean management){this.management=management;this.h=h;prefs=h.getSharedPreferences("roster60_human_queue",0);}
 boolean current(){return epoch==h.epoch&&"roster60Pvp".equals(h.screen)&&owner.equals(h.api.token())&&!h.isDestroyed();}
 void open(){h.base("대전 편성 · 최대 6마리","roster60Pvp");epoch=h.epoch;owner=h.api.token();key=NativeProgression.accountKey(BuildConfig.SERVER_URL+"|"+owner);h.page.removeViewAt(h.page.getChildCount()-1);
 root=new LinearLayout(h);root.setOrientation(1);h.page.addView(root,new LinearLayout.LayoutParams(-1,0,1));status=h.text("서버 편성 확인 중…",12);root.addView(status);summary=h.text("출전 1 + 예비 최대 5 · 순차 1대1",12);root.addView(summary);
 root.setBackground(NavyWorkbenchTheme.surface(h,NavyWorkbenchTheme.PANEL,NavyWorkbenchTheme.LINE,10));
 root.setPadding(h.dp(8),h.dp(4),h.dp(8),h.dp(4));status.setTextColor(NavyWorkbenchTheme.TEXT);summary.setTextColor(NavyWorkbenchTheme.MUTED);
 LinearLayout columns=new LinearLayout(h);root.addView(columns,new LinearLayout.LayoutParams(-1,0,1));
 roster=new LinearLayout(h);roster.setOrientation(1);columns.addView(roster,new LinearLayout.LayoutParams(0,-1,3));
 actions=new LinearLayout(h);actions.setOrientation(1);columns.addView(actions,new LinearLayout.LayoutParams(0,-1,2));
 focusedArt=new ImageView(h);focusedArt.setTag("party-focused-portrait");actions.addView(focusedArt,new LinearLayout.LayoutParams(-1,0,1));focusedName=h.text("대원을 선택하세요",12);focusedName.setTextColor(NavyWorkbenchTheme.TEXT);actions.addView(focusedName);
 save=h.button("편성 저장",this::save);save.setTag("roster60-pvp-party-save");
 normal=h.button("일반 대전",()->join("normal"));normal.setTag("roster60-pvp-normal");
 rank=h.button("랭크 대전",()->join("rank"));rank.setTag("roster60-pvp-rank");
 cancel=h.button("매칭 취소",()->request("cancel",h.obj()));
 Button[] buttons={save,h.button("편성 비우기",()->{if(busy||waiting)return;selected.clear();drawParty();}),normal,rank,cancel,h.button("다시 확인",this::resume)};
 if(management)buttons=new Button[]{buttons[0],buttons[1],buttons[5]};
 for(int row=0;row<(buttons.length+1)/2;row++){LinearLayout line=new LinearLayout(h);actions.addView(line,new LinearLayout.LayoutParams(-1,h.dp(48)));for(int col=0;col<2&&row*2+col<buttons.length;col++){Button button=buttons[row*2+col];style(button);line.addView(button,new LinearLayout.LayoutParams(0,-1,1));}}

 if(management){normal.setVisibility(View.GONE);rank.setVisibility(View.GONE);cancel.setVisibility(View.GONE);}fetchOptions();if(!management)resume();}
 void fetchOptions(){h.api.call("native/roster60/pvp/options",null,(r,e)->{if(!current())return;if(e!=null){status.setText("편성 확인 필요 · "+e);enable();return;}units=r.optJSONArray("units");if(units==null)units=new JSONArray();selected.clear();JSONArray ids=r.optJSONArray("partyIds");if(ids!=null)for(int i=0;i<ids.length();i++)selected.add(ids.optString(i));drawParty();});}
 void style(Button b){CombatV2ReviewStyle.button(b);b.setTextSize(12);b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(h.dp(4),h.dp(2),h.dp(4),h.dp(2));b.setMaxLines(3);}
 JSONObject unit(String id){for(int i=0;i<units.length();i++)if(id.equals(units.optJSONObject(i).optString("ownedId")))return units.optJSONObject(i);return null;}
 void drawParty(){
 roster.removeAllViews();
 LinearLayout slots=new LinearLayout(h);roster.addView(slots,new LinearLayout.LayoutParams(-1,h.dp(64)));
 for(int slot=0;slot<6;slot++){final String id=slot<selected.size()?selected.get(slot):"";JSONObject u=unit(id);String title=(slot==0?"출전":"예비 "+slot)+(u==null?"": " · "+u.optString("name"));Roster60MemberTile tile=new Roster60MemberTile(h,u,title,u!=null&&!busy&&!waiting,()->{focused=id;selected.remove(id);drawParty();});tile.setTag("party-selected-"+slot);slots.addView(tile,new LinearLayout.LayoutParams(0,-1,1));}
 JSONObject focus=unit(focused);if(focus==null&&!selected.isEmpty()){focused=selected.get(0);focus=unit(focused);}if(focus!=null){Roster60Portrait.bind(h,focusedArt,focus.optString("speciesId"));focusedName.setText(focus.optString("name")+(focus.has("level")?" · Lv"+focus.optInt("level"):""));}
int pages=Math.max(1,(units.length()+2)/3);rosterPage=Math.min(rosterPage,pages-1);
 if(units.length()==0){TextView empty=h.text("표시할 새 개체가 없습니다.\n튜토리얼 시작 상태를 확인하세요.",14);empty.setTextColor(NavyWorkbenchTheme.TEXT);roster.addView(empty,new LinearLayout.LayoutParams(-1,0,1));Button start=h.button("튜토리얼 확인",()->new NativeRoster60Onboarding(h).open());style(start);roster.addView(start,new LinearLayout.LayoutParams(-1,h.dp(48)));}
 else for(int row=0;row<1;row++){LinearLayout line=new LinearLayout(h);roster.addView(line,new LinearLayout.LayoutParams(-1,0,1));for(int col=0;col<3;col++){int i=rosterPage*3+col;if(i>=units.length()){line.addView(new android.view.View(h),new LinearLayout.LayoutParams(0,-1,1));continue;}JSONObject u=units.optJSONObject(i);String id=u.optString("ownedId");Roster60MemberTile tile=new Roster60MemberTile(h,u,(selected.contains(id)?"✓ ":"")+u.optString("name")+(selected.indexOf(id)==0?" · 출전":""),!busy&&!waiting,()->{if(busy||waiting)return;focused=id;if(selected.contains(id))selected.remove(id);else if(selected.size()<6)selected.add(id);else status.setText("출전 포함 최대 6마리입니다.");drawParty();});tile.action.setTag("party-unit-"+id);line.addView(tile,new LinearLayout.LayoutParams(0,-1,1));}}
 if(pages>1){LinearLayout nav=new LinearLayout(h);roster.addView(nav,new LinearLayout.LayoutParams(-1,h.dp(48)));Button prev=h.button("‹",()->{rosterPage=Math.max(0,rosterPage-1);drawParty();}),next=h.button("›",()->{rosterPage=Math.min(pages-1,rosterPage+1);drawParty();});style(prev);style(next);prev.setEnabled(rosterPage>0);next.setEnabled(rosterPage<pages-1);nav.addView(prev,new LinearLayout.LayoutParams(0,-1,1));TextView page=h.text((rosterPage+1)+" / "+pages,12);page.setTextColor(NavyWorkbenchTheme.TEXT);nav.addView(page,new LinearLayout.LayoutParams(0,-1,1));nav.addView(next,new LinearLayout.LayoutParams(0,-1,1));}
 summary.setText("편성 "+selected.size()+" / 6 · 먼저 선택한 개체가 첫 출전 · 탭으로 추가/해제");enable();
 }

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
 void resume(){if(management){fetchOptions();return;}paused=false;if(!current()||busy)return;String pending=prefs.getString(key,"");if(!pending.isEmpty()){try{send(new JSONObject(pending));}catch(JSONException e){status.setText("저장된 요청 확인 필요");}return;}busy=true;enable();h.api.call("native/roster60/pvp/current",null,(r,e)->{busy=false;if(!current())return;if(e!=null)status.setText("대전 확인 필요 · "+e);else show(r);enable();schedule();});}
 void show(JSONObject r){JSONObject battle=r.optJSONObject("battle");if(battle!=null){pause();h.openRosterHumanBattle(battle);return;}waiting="waiting".equals(r.optString("status"));status.setText(waiting?"실제 사용자 매칭 대기 중 · 취소 가능":"편성을 저장하고 대전을 시작하세요.");drawParty();}
 void schedule(){timer.removeCallbacksAndMessages(null);if(current()&&!paused&&!management)timer.postDelayed(this::resume,2000);}
 void pause(){paused=true;timer.removeCallbacksAndMessages(null);}
 void back(){if(waiting){status.setText("매칭 취소 후 돌아갈 수 있습니다.");return;}if(busy||!prefs.getString(key,"").isEmpty()){status.setText("요청 결과를 먼저 확인하세요.");return;}pause();h.home();}
}
