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
 final ArrayList<String> selected=new ArrayList<>();String owner,key;int epoch,optionsGeneration;long selectionRevision;boolean busy,paused,waiting,optionsInFlight,optionsRefreshQueued;JSONArray units=new JSONArray();
 final boolean management;final String entryMode;String selectedMode,focused="";boolean pendingModeUnknown;ImageView focusedArt;TextView focusedName;
 LinearLayout root,roster,actions,slots,emptyPanel;TextView status,summary,ownedHeading;Button save,normal,rank,cancel;
 Roster60CardStrip ownedStrip;String renderedUnits="",renderedSlots="",renderedTileState="",focusSpecies="";JSONArray renderedSource;boolean catalogueDirty=true;long catalogueRevision;
 final LinkedHashMap<String,Roster60MemberTile> ownedTiles=new LinkedHashMap<>();final HashMap<String,String> ownedState=new HashMap<>();final String[] slotContent=new String[6],slotState=new String[6];
 long catalogueSerializations,slotReplacements,focusBindings,tileStateUpdates;
 NativeRoster60Pvp(MainActivity h){this(h,false);}
 NativeRoster60Pvp(MainActivity h,boolean management){this(h,management,"");}
 NativeRoster60Pvp(MainActivity h,boolean management,String mode){this.management=management;this.entryMode=validMode(mode)?mode:"";this.selectedMode=entryMode;this.h=h;prefs=h.getSharedPreferences("roster60_human_queue",0);}
 static boolean validMode(String mode){return "normal".equals(mode)||"rank".equals(mode);}
 String modeLabel(){return "rank".equals(selectedMode)?"랭크게임":"normal".equals(selectedMode)?"일반게임":"대전";}
 void modeUi(){if(normal==null)return;normal.setVisibility(management||pendingModeUnknown||"rank".equals(selectedMode)?View.GONE:View.VISIBLE);rank.setVisibility(management||pendingModeUnknown||"normal".equals(selectedMode)?View.GONE:View.VISIBLE);h.title.setText(pendingModeUnknown?"대전 확인 중":modeLabel()+" · 편성");}
 void restoreMode(String mode){if(!validMode(mode))return;pendingModeUnknown=false;selectedMode=mode;h.rosterPvpMode=mode;modeUi();}
 boolean current(){return epoch==h.epoch&&"roster60Pvp".equals(h.screen)&&owner.equals(h.api.token())&&!h.isDestroyed();}
 void open(){h.base("대전 편성 · 최대 6마리","roster60Pvp");epoch=h.epoch;owner=h.api.token();key=NativeProgression.accountKey(BuildConfig.SERVER_URL+"|"+owner);optionsGeneration++;optionsInFlight=false;optionsRefreshQueued=false;busy=false;waiting=false;selectionRevision++;selected.clear();units=new JSONArray();focused="";renderedUnits="";renderedSlots="";renderedTileState="";focusSpecies="";renderedSource=null;catalogueDirty=true;catalogueRevision=0;ownedTiles.clear();ownedState.clear();Arrays.fill(slotContent,null);Arrays.fill(slotState,null);h.page.removeViewAt(h.page.getChildCount()-1);
 root=new LinearLayout(h);root.setOrientation(1);h.page.addView(root,new LinearLayout.LayoutParams(-1,0,1));status=h.text("서버 편성 확인 중…",12);status.setSingleLine(true);status.setEllipsize(android.text.TextUtils.TruncateAt.END);root.addView(status);
 root.setBackground(NavyWorkbenchTheme.surface(h,NavyWorkbenchTheme.PANEL,NavyWorkbenchTheme.LINE,10));
 root.setPadding(h.dp(8),h.dp(4),h.dp(8),h.dp(4));status.setTextColor(NavyWorkbenchTheme.TEXT);
 LinearLayout columns=new LinearLayout(h);root.addView(columns,new LinearLayout.LayoutParams(-1,0,1));
 roster=new LinearLayout(h);roster.setOrientation(1);roster.setPadding(0,h.dp(4),h.dp(8),0);columns.addView(roster,new LinearLayout.LayoutParams(0,-1,3));
 // Separate the saved order from the owned catalogue without adding another decorative frame.
 LinearLayout current=new LinearLayout(h);current.setOrientation(1);current.setPadding(h.dp(6),h.dp(3),h.dp(6),h.dp(4));current.setTag("party-current-section");current.setBackground(NavyWorkbenchTheme.surface(h,NavyWorkbenchTheme.HEADER,0,6));roster.addView(current,new LinearLayout.LayoutParams(-1,0,1.3f));
 summary=heading("현재 편성 · 출전 1 + 예비 5");current.addView(summary,new LinearLayout.LayoutParams(-1,h.dp(20)));slots=new LinearLayout(h);slots.setTag("party-current-slots");slots.setGravity(Gravity.CENTER_VERTICAL);current.addView(slots,new LinearLayout.LayoutParams(-1,0,1));
 LinearLayout owned=new LinearLayout(h);owned.setOrientation(1);owned.setPadding(h.dp(6),h.dp(3),h.dp(6),h.dp(4));owned.setTag("party-owned-section");owned.setBackground(NavyWorkbenchTheme.surface(h,NavyWorkbenchTheme.SLOT,0,6));LinearLayout.LayoutParams ownedBox=new LinearLayout.LayoutParams(-1,0,1);ownedBox.topMargin=h.dp(8);roster.addView(owned,ownedBox);
 ownedHeading=heading("보유 대원 · 좌우로 밀어보기");owned.addView(ownedHeading,new LinearLayout.LayoutParams(-1,h.dp(20)));
 ownedStrip=new Roster60CardStrip(h);ownedStrip.setTag("party-owned-scroll");owned.addView(ownedStrip,new LinearLayout.LayoutParams(-1,0,1));
 emptyPanel=new LinearLayout(h);emptyPanel.setOrientation(1);emptyPanel.setVisibility(View.GONE);TextView empty=h.text("표시할 새 개체가 없습니다. 튜토리얼 시작 상태를 확인하세요.",12);empty.setTextColor(NavyWorkbenchTheme.TEXT);emptyPanel.addView(empty,new LinearLayout.LayoutParams(-1,0,1));Button tutorial=h.button("튜토리얼 확인",()->new NativeRoster60Onboarding(h).open());style(tutorial);emptyPanel.addView(tutorial,new LinearLayout.LayoutParams(-1,h.dp(48)));owned.addView(emptyPanel,new LinearLayout.LayoutParams(-1,0,1));
 actions=new LinearLayout(h);actions.setOrientation(1);columns.addView(actions,new LinearLayout.LayoutParams(0,-1,1));
 focusedArt=new ImageView(h);focusedArt.setTag("party-focused-portrait");actions.addView(focusedArt,new LinearLayout.LayoutParams(-1,0,1));focusedName=h.text("대원을 선택하세요",12);focusedName.setTextColor(NavyWorkbenchTheme.TEXT);actions.addView(focusedName);
 save=h.button("편성 저장",this::save);save.setTag("roster60-pvp-party-save");
 normal=h.button("일반 대전",()->join("normal"));normal.setTag("roster60-pvp-normal");
 rank=h.button("랭크 대전",()->join("rank"));rank.setTag("roster60-pvp-rank");
 cancel=h.button("매칭 취소",()->request("cancel",h.obj()));
 Button[] buttons={save,h.button("편성 비우기",()->{if(busy||waiting)return;selectionRevision++;selected.clear();drawParty();}),normal,rank,cancel,h.button("다시 확인",this::resume)};
 if(management)buttons=new Button[]{buttons[0],buttons[1],buttons[5]};
 for(int row=0;row<(buttons.length+1)/2;row++){LinearLayout line=new LinearLayout(h);actions.addView(line,new LinearLayout.LayoutParams(-1,h.dp(48)));for(int col=0;col<2&&row*2+col<buttons.length;col++){Button button=buttons[row*2+col];style(button);line.addView(button,new LinearLayout.LayoutParams(0,-1,1));}}

 if(management){normal.setVisibility(View.GONE);rank.setVisibility(View.GONE);cancel.setVisibility(View.GONE);}else restoreMode(selectedMode);fetchOptions();if(!management)resume();}
 void fetchOptions(){
  if(!current()||optionsInFlight)return;optionsInFlight=true;final long requestedRevision=selectionRevision;final int requestedGeneration=++optionsGeneration,requestedEpoch=epoch;final String requestedOwner=owner;
  h.api.call("native/roster60/pvp/options",null,(r,e)->{
   if(requestedGeneration!=optionsGeneration||requestedEpoch!=epoch||!requestedOwner.equals(owner))return;
   optionsInFlight=false;if(!current())return;if(e!=null){status.setText("편성 확인 필요 · "+e);enable();finishOptionsRead();return;}
   units=r.optJSONArray("units");if(units==null)units=new JSONArray();catalogueDirty=true;
   if(selectionRevision==requestedRevision){selected.clear();JSONArray ids=r.optJSONArray("partyIds");if(ids!=null)for(int i=0;i<ids.length();i++)selected.add(ids.optString(i));}
   else{for(Iterator<String> it=selected.iterator();it.hasNext();)if(unit(it.next())==null)it.remove();}
   if(management&&(status.getText().toString().equals("서버 편성 확인 중…")||status.getText().toString().startsWith("편성 확인 필요 · ")))status.setText("탭으로 추가/해제 · 먼저 선택한 대원이 첫 출전합니다.");drawParty();finishOptionsRead();
  });
 }
 void finishOptionsRead(){if(optionsRefreshQueued){optionsRefreshQueued=false;fetchOptions();}}
 void refreshOptionsAfterSave(){if(optionsInFlight)optionsRefreshQueued=true;else fetchOptions();}
 void style(Button b){CombatV2ReviewStyle.button(b);b.setTextSize(12);b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(h.dp(4),h.dp(2),h.dp(4),h.dp(2));b.setMaxLines(3);}
 JSONObject unit(String id){for(int i=0;i<units.length();i++)if(id.equals(units.optJSONObject(i).optString("ownedId")))return units.optJSONObject(i);return null;}
 TextView heading(String text){TextView v=h.text(text,12);CombatV2ReviewStyle.text(v);v.setTextColor(NavyWorkbenchTheme.TEXT);v.setSingleLine(true);v.setEllipsize(android.text.TextUtils.TruncateAt.END);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
 void drawParty(){
 // JSON comparison belongs to a catalogue response, never every local badge/focus update.
 if(catalogueDirty||renderedSource!=units){
  String signature=units.toString();catalogueSerializations++;catalogueDirty=false;renderedSource=units;
  if(!signature.equals(renderedUnits)){
   renderedUnits=signature;catalogueRevision++;ownedTiles.clear();ownedState.clear();renderedTileState="";ArrayList<String> ids=new ArrayList<>();ArrayList<Roster60MemberTile> tiles=new ArrayList<>();
   for(int i=0;i<units.length();i++){
    JSONObject u=units.optJSONObject(i);String id=u.optString("ownedId");
    Roster60MemberTile tile=new Roster60MemberTile(h,u,u.optString("name"),!busy&&!waiting,()->{if(busy||waiting||ownedStrip.blocksClick())return;selectionRevision++;focused=id;if(selected.contains(id))selected.remove(id);else if(selected.size()<6)selected.add(id);else status.setText("출전 포함 최대 6마리입니다.");drawParty();},null,false,1,0,true);
    tile.action.setTag("party-unit-"+id);ids.add(id);tiles.add(tile);ownedTiles.put(id,tile);
   }
   ownedStrip.replace(ids,tiles);
  }
 }
 String slotsKey=selected.toString()+"|"+catalogueRevision;
 if(!slotsKey.equals(renderedSlots)){
  for(int slot=0;slot<6;slot++){
   final String id=slot<selected.size()?selected.get(slot):"";JSONObject u=unit(id);String content=id+"|"+(u==null?"":u.toString());
   if(!content.equals(slotContent[slot])){
    Roster60MemberTile tile=new Roster60MemberTile(h,u,u==null?"빈 칸":u.optString("name"),u!=null&&!busy&&!waiting,()->{if(busy||waiting)return;selectionRevision++;focused=id;selected.remove(id);drawParty();},Roster60FormationView.role(slot),u!=null&&focused.equals(id),1,0);
    tile.setTag("party-selected-"+slot);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);if(slot<5)p.rightMargin=h.dp(4);
    if(slot<slots.getChildCount())slots.removeViewAt(slot);slots.addView(tile,slot,p);slotContent[slot]=content;slotState[slot]=null;slotReplacements++;
   }
  }
  renderedSlots=slotsKey;
 }
 JSONObject focus=unit(focused);if(focus==null){focused=!selected.isEmpty()?selected.get(0):units.length()>0?units.optJSONObject(0).optString("ownedId"):"";focus=unit(focused);}
 if(focus!=null){String species=focus.optString("speciesId");if(!species.equals(focusSpecies)){Roster60Portrait.bind(h,focusedArt,species);focusSpecies=species;focusBindings++;}Roster60FormationView.text(focusedName,Roster60FormationView.memberDetails(focus));}
 else{if(!focusSpecies.isEmpty()){Roster60Portrait.clear(focusedArt);focusSpecies="";}Roster60FormationView.text(focusedName,"대원을 선택하세요");}
 boolean empty=units.length()==0;ownedStrip.setVisibility(empty?View.GONE:View.VISIBLE);emptyPanel.setVisibility(empty?View.VISIBLE:View.GONE);
 Roster60FormationView.text(summary,"현재 편성 "+selected.size()+" / 6 · 출전 1 + 예비 5");Roster60FormationView.text(ownedHeading,"보유 대원 "+units.length()+"명 · 좌우로 밀어보기");enable();
 }
 void tileStates(){
  String state=selected.toString()+"|"+focused+"|"+busy+"|"+waiting+"|"+catalogueRevision;
  if(state.equals(renderedTileState))return;
  for(int i=0;i<slots.getChildCount();i++){
   String id=i<selected.size()?selected.get(i):"";boolean active=!id.isEmpty()&&focused.equals(id),enabled=!id.isEmpty()&&!busy&&!waiting;String tuple=active+"|"+enabled;
   if(!tuple.equals(slotState[i])){((Roster60MemberTile)slots.getChildAt(i)).state(Roster60FormationView.role(i),active,enabled);slotState[i]=tuple;tileStateUpdates++;}
  }
  for(Map.Entry<String,Roster60MemberTile> entry:ownedTiles.entrySet()){
   String id=entry.getKey();int slot=selected.indexOf(id);String role=slot<0?null:"✓ "+Roster60FormationView.role(slot);boolean active=focused.equals(id),enabled=!busy&&!waiting;String tuple=role+"|"+active+"|"+enabled;
   if(!tuple.equals(ownedState.get(id))){entry.getValue().state(role,active,enabled);ownedState.put(id,tuple);tileStateUpdates++;}
  }
  renderedTileState=state;
 }

 void enable(){tileStates();boolean free=!busy&&!waiting&&prefs.getString(key,"").isEmpty();save.setEnabled(free&&!selected.isEmpty());normal.setEnabled(free&&!selected.isEmpty());rank.setEnabled(free&&!selected.isEmpty());cancel.setEnabled(waiting&&!busy);}
 void save(){if(busy||waiting||selected.isEmpty()||selected.size()>6)return;selectionRevision++;request("party",h.obj("ownedIds",new JSONArray(selected)));}
 void join(String mode){if(!validMode(mode)||(!selectedMode.isEmpty()&&!selectedMode.equals(mode))||!current()||busy||waiting||selected.isEmpty()||selected.size()>6||!prefs.getString(key,"").isEmpty()||ownedStrip.blocksClick())return;restoreMode(mode);selectionRevision++;busy=true;enable();final int requestedEpoch=epoch;final String requestedOwner=owner;JSONArray ids=new JSONArray(selected);h.api.call("native/roster60/pvp/party",h.obj("ownedIds",ids),(r,e)->{if(requestedEpoch!=epoch||!requestedOwner.equals(owner))return;busy=false;if(!current())return;if(e!=null){status.setText(e);enable();return;}request("join",h.obj("mode",mode));});}
 void request(String op,JSONObject body){if(!current()||busy||!prefs.getString(key,"").isEmpty())return;try{body.put("requestId",UUID.randomUUID().toString());JSONObject saved=h.obj("op",op,"body",body,"mode",selectedMode);if(prefs.edit().putString(key,saved.toString()).commit())send(saved);}catch(JSONException e){status.setText("요청 저장 실패");}}
 void send(JSONObject saved){if(busy||!current())return;busy=true;enable();String op=saved.optString("op");JSONObject body=saved.optJSONObject("body");if(!Arrays.asList("party","join","cancel").contains(op)||body==null){busy=false;status.setText("저장된 요청 확인 필요");return;}if(op.equals("join"))restoreMode(body.optString("mode"));else if(validMode(saved.optString("mode")))restoreMode(saved.optString("mode"));else if(op.equals("cancel")){pendingModeUnknown=true;selectedMode="";h.rosterPvpMode="";modeUi();}
 // Party persistence is an idempotent replacement, and its schema has no requestId.
 JSONObject wire=body;try{if(op.equals("party")){wire=new JSONObject(body.toString());wire.remove("requestId");}}catch(JSONException ignored){}
 final int requestedEpoch=epoch;final String requestedOwner=owner,requestedKey=key,requestedPending=prefs.getString(key,"");
 h.api.call("native/roster60/pvp/"+op,wire,(r,e)->{if(!requestedOwner.equals(h.api.token()))return;
 boolean rejected=e!=null&&(e.contains("PENDING")||Arrays.asList("INVALID_PARTY","UNIT_UNAVAILABLE","UNIT_NOT_FOUND","INVALID_REQUEST","REQUEST_ID_REUSED","PVP_ALREADY_ACTIVE","PVP_QUEUE_ACTIVE","PVP_BUSY","PARTY_REQUIRED").contains(e));
 if((e==null||rejected)&&prefs.getString(requestedKey,"").equals(requestedPending))prefs.edit().remove(requestedKey).commit();
 if(requestedEpoch!=epoch||!requestedOwner.equals(owner))return;busy=false;if(!current())return;if(e!=null){status.setText("확인 필요 · "+e);enable();schedule();return;}if(op.equals("party")){status.setText("편성 저장 완료");refreshOptionsAfterSave();}else show(r);enable();schedule();});}
 void resume(){paused=false;if(!current()||busy)return;String pending=prefs.getString(key,"");if(!pending.isEmpty()){try{send(new JSONObject(pending));}catch(JSONException e){status.setText("저장된 요청 확인 필요");}return;}if(management){fetchOptions();return;}busy=true;enable();final int requestedEpoch=epoch;final String requestedOwner=owner;h.api.call("native/roster60/pvp/current",null,(r,e)->{if(requestedEpoch!=epoch||!requestedOwner.equals(owner))return;busy=false;if(!current())return;if(e!=null)status.setText("대전 확인 필요 · "+e);else show(r);enable();schedule();});}
 void show(JSONObject r){JSONObject battle=r.optJSONObject("battle");if(battle!=null){restoreMode(r.optString("mode",battle.optString("mode")));pause();h.openRosterHumanBattle(battle);return;}waiting="waiting".equals(r.optString("status"));if(waiting)restoreMode(r.optString("mode"));else if(pendingModeUnknown){pendingModeUnknown=false;selectedMode=entryMode;h.rosterPvpMode=entryMode;modeUi();}status.setText(waiting?modeLabel()+" · 실제 사용자 매칭 대기 중 · 취소 가능":modeLabel()+" · 편성을 확인하고 대전을 시작하세요.");drawParty();}
 void schedule(){timer.removeCallbacksAndMessages(null);if(current()&&!paused&&!management)timer.postDelayed(this::resume,2000);}
 void pause(){paused=true;timer.removeCallbacksAndMessages(null);}
 void back(){pause();if(management)h.home();else h.openPlayMenu();}
}
