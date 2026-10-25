package org.dpdns.gmm;
import android.content.*;
import android.graphics.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
/** Server-owned, unclaimed candidate rounds. No legacy owned unit is ever replaced. */
final class NativeRecruitSession {
 final MainActivity h;final SharedPreferences prefs;final String owner,key;int epoch;boolean busy;JSONObject state,info;
 JSONArray candidates=new JSONArray();int selected;
 LinearLayout root,controls;TextView message;RecruitCardTransition card;
 // 예전 모집소 화면에서 들어온 경우, 수령 뒤에는 그 화면으로 돌아간다(이 화면의 별도 시작 버튼 대신).
 boolean fromCounter;
 NativeRecruitSession(MainActivity h){this.h=h;owner=h.api.token();key=NativeProgression.accountKey(BuildConfig.SERVER_URL+"|"+owner);prefs=h.getSharedPreferences("roster60_recruit_session",0);}
 boolean hasSavedResult(){try{String raw=prefs.getString(key+".result","");if(raw.isEmpty())return false;JSONObject saved=new JSONObject(raw);JSONArray list=saved.optJSONArray("sessions");if(list==null)return "CLAIMED".equals(saved.optString("status"));if(list.length()==0)return false;for(int i=0;i<list.length();i++)if(!"CLAIMED".equals(list.optJSONObject(i).optString("status")))return false;return true;}catch(JSONException bad){return false;}}
 TextView label(String text,int size){TextView v=h.text(text,size);NavyWorkbenchTheme.text(h,v,size,false,NavyWorkbenchTheme.TEXT);return v;}
 Button button(String text,Runnable action){Button b=h.button(text,action);NavyWorkbenchTheme.text(h,b,14,true,NavyWorkbenchTheme.TEXT);b.setBackgroundTintList(null);b.setMinHeight(h.dp(48));b.setMinimumHeight(h.dp(48));
 android.graphics.drawable.StateListDrawable bg=new android.graphics.drawable.StateListDrawable();bg.addState(new int[]{-android.R.attr.state_enabled},NavyWorkbenchTheme.surface(h,0xff26343d,0xff43505a,6));bg.addState(new int[]{android.R.attr.state_pressed},NavyWorkbenchTheme.surface(h,0xff34566b,0xff93b3c6,6));bg.addState(new int[]{},NavyWorkbenchTheme.surface(h,0xee173345,0xff607d8b,6));b.setBackground(bg);b.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xff91a0aa,NavyWorkbenchTheme.TEXT}));return b;}
 boolean current(){return epoch==h.epoch&&owner.equals(h.api.token())&&!h.isDestroyed();}
 void open(JSONObject response){info=response;h.base("모집","draw");AppSkin.apply(h,"reception");epoch=h.epoch;h.page.removeViewAt(h.page.getChildCount()-1);
 root=new LinearLayout(h);root.setOrientation(1);root.setBackground(NavyWorkbenchTheme.surface(h,0xee102433,NavyWorkbenchTheme.EDGE,10));h.page.addView(root,new LinearLayout.LayoutParams(-1,0,1));
 message=label("후보 확인 후 수령 · 새로고침은 후보마다 1회, 동일 등급",12);root.addView(message);
 LinearLayout columns=new LinearLayout(h);root.addView(columns,new LinearLayout.LayoutParams(-1,0,1));card=new RecruitCardTransition(h);columns.addView(card,new LinearLayout.LayoutParams(0,-1,1));
 ScrollView scroll=new ScrollView(h);columns.addView(scroll,new LinearLayout.LayoutParams(0,-1,1));controls=new LinearLayout(h);controls.setOrientation(1);scroll.addView(controls);
 JSONArray pending=response.optJSONArray("sessions");if(pending!=null&&pending.length()>0){candidates=pending;state=candidates.optJSONObject(0);}
 if(state==null)try{String saved=prefs.getString(key+".result","");if(!saved.isEmpty()){JSONObject cached=new JSONObject(saved);JSONArray list=cached.optJSONArray("sessions");if(list!=null){candidates=list;state=list.optJSONObject(0);}else{state=cached;candidates.put(cached);}}}catch(JSONException ignored){}
 render(false);if(!prefs.getString(key,"").isEmpty())send();
 }
 void render(boolean shred){controls.removeAllViews();
 if(candidates.length()>1){LinearLayout pages=new LinearLayout(h);Button previous=button("‹",()->page(-1)),next=button("›",()->page(1));previous.setEnabled(!busy&&selected>0&&prefs.getString(key,"").isEmpty());next.setEnabled(!busy&&selected<candidates.length()-1&&prefs.getString(key,"").isEmpty());pages.addView(previous,new LinearLayout.LayoutParams(0,h.dp(48),1));TextView index=label((selected+1)+" / "+candidates.length(),14);index.setGravity(Gravity.CENTER);pages.addView(index,new LinearLayout.LayoutParams(0,h.dp(48),1));pages.addView(next,new LinearLayout.LayoutParams(0,h.dp(48),1));controls.addView(pages);}
if(state!=null){JSONObject candidate=state.optJSONObject("candidate");if(candidate!=null)card.replace(candidateView(candidate),shred&&motion());
 if("PENDING".equals(state.optString("status"))){Button refresh=button("새로고침 · 지원서 "+state.optInt("rerollCost")+"장 · 남은 "+(1-state.optInt("rerolls"))+"회",()->action("reroll"));refresh.setTag("recruit-reroll");refresh.setEnabled(!busy&&state.optInt("rerolls")==0&&prefs.getString(key,"").isEmpty());controls.addView(refresh);
 Button claim=button("이 후보 수령",()->action("claim"));claim.setTag("recruit-claim");claim.setEnabled(!busy&&prefs.getString(key,"").isEmpty());controls.addView(claim);
 }else controls.addView(label("영구 지급 완료 · 이미 받은 개체는 파쇄되지 않습니다.",12));}
 if(fromCounter&&!hasPending()){Button back=button("모집소로 돌아가기",h::draw);back.setTag("recruit-back-counter");back.setEnabled(!busy&&prefs.getString(key,"").isEmpty());controls.addView(back);}
 else if(!hasPending()){JSONArray offers=info.optJSONArray("offers");if(offers!=null)for(int i=0;i<offers.length();i++){JSONObject o=offers.optJSONObject(i);Button start=button(o.optString("label")+" 모집 · 지원서 "+o.optInt("cost")+"장",()->start(o));start.setEnabled(info.optBoolean("enabled")&&!busy&&prefs.getString(key,"").isEmpty());controls.addView(start);}}
 if(pendingRows().length()>1){Button all=button("남은 후보 모두 수령",()->queue("claim-all",h.obj("sessions",pendingRows())));all.setTag("recruit-claim-all");all.setEnabled(!busy&&prefs.getString(key,"").isEmpty());controls.addView(all);}
 controls.addView(button("전송 결과 다시 확인 (무료)",this::send));
 }
 void page(int delta){if(busy||!prefs.getString(key,"").isEmpty())return;selected=Math.max(0,Math.min(candidates.length()-1,selected+delta));state=candidates.optJSONObject(selected);render(false);}
 boolean hasPending(){return pendingRows().length()>0;}
 JSONArray pendingRows(){JSONArray rows=new JSONArray();for(int i=0;i<candidates.length();i++){JSONObject c=candidates.optJSONObject(i);if(c!=null&&"PENDING".equals(c.optString("status")))rows.put(h.obj("sessionId",c.optString("sessionId"),"revision",c.optInt("revision")));}return rows;}
 JSONObject merged(JSONObject response){
 JSONArray incoming=response.optJSONArray("sessions");if(incoming==null)incoming=new JSONArray().put(response);
 String group=incoming.length()>0?incoming.optJSONObject(0).optString("batchId"):"";
 JSONArray merged=new JSONArray();for(int i=0;i<candidates.length();i++){JSONObject old=candidates.optJSONObject(i);if(group.isEmpty()||group.equals(old.optString("batchId")))merged.put(old);}
 for(int n=0;n<incoming.length();n++){JSONObject item=incoming.optJSONObject(n);if(item==null||item.optString("sessionId").isEmpty())continue;int at=-1;for(int j=0;j<merged.length();j++)if(item.optString("sessionId").equals(merged.optJSONObject(j).optString("sessionId"))){at=j;break;}try{if(at<0)merged.put(item);else if(item.optInt("revision")>=merged.optJSONObject(at).optInt("revision"))merged.put(at,item);}catch(JSONException ignored){}}
 return h.obj("sessions",merged);
 }
 void acceptResult(JSONObject response){JSONObject combined=merged(response);candidates=combined.optJSONArray("sessions");selected=Math.min(selected,Math.max(0,candidates.length()-1));state=candidates.optJSONObject(selected);}
 void refreshAll(int index){if(index==0){busy=true;render(false);}if(index>=candidates.length()){busy=false;prefs.edit().putString(key+".result",h.obj("sessions",candidates).toString()).commit();render(false);return;}JSONObject item=candidates.optJSONObject(index);h.api.call("native/roster60/recruit/session/read",h.obj("sessionId",item.optString("sessionId")),(fresh,error)->{if(!current())return;if(error!=null){busy=false;render(false);message.setText("결과 동기화 실패 · 모집소를 다시 열어 주세요.");return;}acceptResult(fresh);refreshAll(index+1);});}
 boolean motion(){return h.motionEnabled&&Settings.Global.getFloat(h.getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1)>0;}
 // 등록증 v10 완성 카드에 제목·이름·등급이 인쇄돼 있으므로 상태와 레벨만 따로 표시한다. 카드가 없으면 기존 정면 그림.
 View candidateView(JSONObject u){LinearLayout view=new LinearLayout(h);view.setOrientation(1);view.setPadding(h.dp(8),h.dp(8),h.dp(8),h.dp(8));view.setBackground(NavyWorkbenchTheme.surface(h,0xee102433,NavyWorkbenchTheme.EDGE,8));String status=state!=null&&"CLAIMED".equals(state.optString("status"))?"수령 완료":"미수령 후보";TextView heading=label("방범대 등록증 · "+status,13);view.addView(heading);ImageView image=new ImageView(h);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setTag("recruit-registration-card");view.addView(image,new LinearLayout.LayoutParams(-1,0,1));
 if(RegistrationV10Art.bind(h,image,u.optString("speciesId"))){heading.setText(status);image.setContentDescription("방범대 등록증 · "+u.optString("name")+" · "+status);view.addView(label("Lv"+u.optInt("level"),14));return view;}
 try{NativeRoster60Art loader=new NativeRoster60Art(h);String file=loader.entry(u.getString("speciesId")).getJSONObject("views").getJSONObject("front").getString("file");if(file.contains("/"))throw new IOException();BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=2;try(InputStream in=h.getAssets().open("roster60-art/"+file)){image.setImageBitmap(BitmapFactory.decodeStream(in,null,opts));}}catch(Exception e){image.setContentDescription("아트 준비 중");}view.addView(label(u.optString("name")+" · "+u.optString("rarity")+" · Lv"+u.optInt("level"),14));return view;}
 void start(JSONObject offer){if(busy||!current()||!prefs.getString(key,"").isEmpty())return;
 // Initial purchase confirmation is separate from the explicitly modal-free reroll.
 new GameDialog(h).navy().setTitle("모집").setMessage("지원서 "+offer.optInt("cost")+"장 · 미수령 후보 1개").setPositiveButton("모집",(d,w)->queue("start",h.obj("kind",offer.optString("kind"),"catalogVersion",info.optString("catalogVersion")))).setNegativeButton("취소",null).show();}
 void action(String kind){if(state==null)return;queue("action",h.obj("sessionId",state.optString("sessionId"),"revision",state.optInt("revision"),"action",kind));}
 void queue(String endpoint,JSONObject body){if(busy||!current()||!prefs.getString(key,"").isEmpty())return;try{body.put("requestId",UUID.randomUUID().toString());JSONObject request=h.obj("endpoint",endpoint,"body",body);if(prefs.edit().putString(key,request.toString()).commit())send();}catch(JSONException ignored){}}
 void send(){if(busy||!current())return;String raw=prefs.getString(key,"");if(raw.isEmpty())return;try{JSONObject request=new JSONObject(raw);JSONObject body=request.getJSONObject("body");busy=true;render(false);message.setText("서버 결과 확인 중 · 기존 후보 유지");
 h.api.call("native/roster60/recruit/session/"+request.getString("endpoint"),body,(r,e)->{busy=false;
 boolean rejected=Arrays.asList("INSUFFICIENT_CREDITS","STALE_REVISION","RECRUIT_REROLL_USED","RECRUIT_SESSION_NOT_FOUND","RECRUIT_SESSION_PENDING","RECRUIT_SESSION_POLICY_PENDING","RECRUIT_ROUNDING_PENDING","CATALOG_CHANGED","REQUEST_ID_REUSED","INVALID_REQUEST","MENU_LOCKED").contains(e);
 if(raw.equals(prefs.getString(key,""))){if(e==null&&r!=null&&body.optString("requestId").equals(r.optString("requestId"))){if(!prefs.edit().putString(key+".result",merged(r).toString()).remove(key).commit()){if(current())message.setText("결과 저장 실패 · 다시 확인하세요.");return;}}else if(rejected)prefs.edit().remove(key).commit();}
 if(!current())return;if(e==null&&(r==null||!body.optString("requestId").equals(r.optString("requestId")))){message.setText("응답 식별자 불일치 · 같은 요청으로 다시 확인하세요.");return;}if(e!=null){render(false);message.setText("후보 유지 · "+e+"\n연결 실패는 같은 요청으로 무료 재확인합니다.");if("STALE_REVISION".equals(e)&&"claim-all".equals(request.optString("endpoint"))){refreshAll(0);return;}if("STALE_REVISION".equals(e)&&state!=null)h.api.call("native/roster60/recruit/session/read",h.obj("sessionId",state.optString("sessionId")),(fresh,error)->{if(!current())return;if(error==null){acceptResult(fresh);prefs.edit().putString(key+".result",merged(fresh).toString()).commit();render(false);message.setText("서버의 최신 후보/수령 상태를 복구했습니다.");}});return;}
 boolean changed=state!=null&&state.optInt("revision")<r.optInt("revision")&&"reroll".equals(body.optString("action"));acceptResult(r);render(changed);message.setText("지원서 "+r.optInt("credits")+" · "+(!hasPending()?"수령 완료":"미수령 후보 · 새로고침 시 이 후보 포기"));
 });}catch(JSONException bad){message.setText("저장된 요청 확인이 필요합니다.");}}
}
