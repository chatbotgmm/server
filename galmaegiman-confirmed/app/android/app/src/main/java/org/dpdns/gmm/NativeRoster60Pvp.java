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
 final Roster60QueueClock queueClock=new Roster60QueueClock();String queueError="",normalXpCondition="",rankCondition="",matchPoolCondition="";JSONObject rankEligibility;
 final Runnable waitingTick=new Runnable(){@Override public void run(){if(current()&&!paused&&waiting){waitingStatus();timer.postDelayed(this,1000);}}};
 final ArrayList<String> selected=new ArrayList<>();String owner,key;int epoch,optionsGeneration,currentGeneration;long selectionRevision;boolean draftLoaded,busy,paused,waiting,currentRead,optionsInFlight,optionsRefreshQueued;JSONArray units=new JSONArray();
 final boolean management;final String entryMode;String selectedMode,focused="";boolean pendingModeUnknown;ImageView focusedArt;TextView focusedName;
 LinearLayout root,roster,actions,slots,emptyPanel;TextView status,rankNotice,summary,ownedHeading;Button save,normal,rank,cancel;
 Roster60CardStrip ownedStrip;String renderedUnits="",renderedSlots="",renderedTileState="",focusSpecies="";JSONArray renderedSource;boolean catalogueDirty=true;long catalogueRevision;
 final LinkedHashMap<String,Roster60MemberTile> ownedTiles=new LinkedHashMap<>();final HashMap<String,String> ownedState=new HashMap<>();final String[] slotContent=new String[6],slotState=new String[6];
 long catalogueSerializations,slotReplacements,focusBindings,tileStateUpdates;
 final ArrayList<String> savedIds=new ArrayList<>();boolean editing,repairRequired;LinearLayout ownedSection;Button editParty,clearParty;
 NativeRoster60Pvp(MainActivity h){this(h,false);}
 NativeRoster60Pvp(MainActivity h,boolean management){this(h,management,"");}
 NativeRoster60Pvp(MainActivity h,boolean management,String mode){this.management=management;this.entryMode=validMode(mode)?mode:"";this.selectedMode=entryMode;this.h=h;prefs=h.getSharedPreferences("roster60_human_queue",0);}
 static boolean validMode(String mode){return "normal".equals(mode)||"rank".equals(mode);}
 String modeLabel(){return "rank".equals(selectedMode)?"랭크게임":"normal".equals(selectedMode)?"일반게임":"대전";}
 void modeUi(){if(normal==null)return;normal.setVisibility(management||editing||pendingModeUnknown||"rank".equals(selectedMode)?View.GONE:View.VISIBLE);rank.setVisibility(management||editing||pendingModeUnknown||"normal".equals(selectedMode)?View.GONE:View.VISIBLE);h.title.setText(pendingModeUnknown?"대전 확인 중":modeLabel()+(editing?" · 편성대":" · 준비"));rankNotice();}
 void restoreMode(String mode){if(!validMode(mode))return;pendingModeUnknown=false;selectedMode=mode;h.rosterPvpMode=mode;modeUi();}
 boolean current(){return epoch==h.epoch&&"roster60Pvp".equals(h.screen)&&owner.equals(h.api.token())&&!h.isDestroyed();}
 void open(){editing=management;repairRequired=false;savedIds.clear();PartyEntryRead entry=management?h.partyEntryRead:null;if(management)h.partyEntryRead=null;
 if(entry!=null&&!entry.matches(h.api.token(),h.epoch))entry=null;
 h.base(management?"전투 편성":"대전 편성","roster60Pvp");epoch=h.epoch;owner=h.api.token();key=NativeProgression.accountKey(BuildConfig.SERVER_URL+"|"+owner);optionsGeneration++;currentGeneration++;currentRead=false;optionsInFlight=false;optionsRefreshQueued=false;busy=false;waiting=false;draftLoaded=false;queueClock.clear();queueError="";normalXpCondition="";rankCondition="";matchPoolCondition="";rankEligibility=null;selectionRevision++;selected.clear();units=new JSONArray();focused="";renderedUnits="";renderedSlots="";renderedTileState="";focusSpecies="";renderedSource=null;catalogueDirty=true;catalogueRevision=0;ownedTiles.clear();ownedState.clear();Arrays.fill(slotContent,null);Arrays.fill(slotState,null);h.page.removeViewAt(h.page.getChildCount()-1);
 root=new LinearLayout(h);root.setOrientation(1);h.page.addView(root,new LinearLayout.LayoutParams(-1,0,1));status=h.text("서버 편성 확인 중…",12);status.setSingleLine(true);status.setEllipsize(android.text.TextUtils.TruncateAt.END);root.addView(status);
 root.setBackground(NavyWorkbenchTheme.surface(h,NavyWorkbenchTheme.PANEL,NavyWorkbenchTheme.LINE,10));
 root.setPadding(h.dp(8),h.dp(4),h.dp(8),h.dp(4));status.setTextColor(NavyWorkbenchTheme.TEXT);
 editParty=h.button("편성대",this::editParty);editParty.setTag("roster60-pvp-edit");style(editParty);
 rankNotice=h.text("",11);rankNotice.setTag("roster60-rank-eligibility");rankNotice.setTextColor(NavyWorkbenchTheme.TEXT);rankNotice.setMaxLines(2);root.addView(rankNotice,new LinearLayout.LayoutParams(-1,-2));
 LinearLayout columns=new LinearLayout(h);root.addView(columns,new LinearLayout.LayoutParams(-1,0,1));
 roster=new LinearLayout(h);roster.setOrientation(1);roster.setPadding(0,h.dp(4),h.dp(8),0);columns.addView(roster,new LinearLayout.LayoutParams(0,-1,3));
 // Separate the saved order from the owned catalogue without adding another decorative frame.
 LinearLayout current=new LinearLayout(h);current.setOrientation(1);current.setClipChildren(false);current.setPadding(h.dp(6),h.dp(3),h.dp(6),h.dp(4));current.setTag("party-current-section");current.setBackground(NavyWorkbenchTheme.surface(h,NavyWorkbenchTheme.HEADER,0,6));roster.addView(current,new LinearLayout.LayoutParams(-1,0,1.3f));
 summary=heading("현재 편성 · 출전 1 + 예비 5");current.addView(summary,new LinearLayout.LayoutParams(-1,headingHeight(summary)));slots=new Roster60PartySlots(h,()->current()&&!busy&&!waiting&&editing,()->selected.size(),this::moveSelected);slots.setTag("party-current-slots");slots.setGravity(Gravity.CENTER_VERTICAL);current.addView(slots,new LinearLayout.LayoutParams(-1,0,1));
 LinearLayout owned=new LinearLayout(h);ownedSection=owned;owned.setOrientation(1);owned.setPadding(h.dp(6),h.dp(3),h.dp(6),h.dp(4));owned.setTag("party-owned-section");owned.setBackground(NavyWorkbenchTheme.surface(h,NavyWorkbenchTheme.SLOT,0,6));LinearLayout.LayoutParams ownedBox=new LinearLayout.LayoutParams(-1,0,1);ownedBox.topMargin=h.dp(8);roster.addView(owned,ownedBox);
 ownedHeading=heading("보유 대원 · 좌우로 밀어보기");owned.addView(ownedHeading,new LinearLayout.LayoutParams(-1,headingHeight(ownedHeading)));
 ownedStrip=new Roster60CardStrip(h);ownedStrip.setTag("party-owned-scroll");owned.addView(ownedStrip,new LinearLayout.LayoutParams(-1,0,1));
 emptyPanel=new LinearLayout(h);emptyPanel.setOrientation(1);emptyPanel.setVisibility(View.GONE);TextView empty=h.text("표시할 새 개체가 없습니다. 튜토리얼 시작 상태를 확인하세요.",12);empty.setTextColor(NavyWorkbenchTheme.TEXT);emptyPanel.addView(empty,new LinearLayout.LayoutParams(-1,0,1));Button tutorial=h.button("튜토리얼 확인",()->new NativeRoster60Onboarding(h).open());style(tutorial);emptyPanel.addView(tutorial,new LinearLayout.LayoutParams(-1,h.dp(48)));owned.addView(emptyPanel,new LinearLayout.LayoutParams(-1,0,1));
 actions=new LinearLayout(h);actions.setOrientation(1);columns.addView(actions,new LinearLayout.LayoutParams(0,-1,1));if(!management)actions.addView(editParty,new LinearLayout.LayoutParams(-1,h.dp(48)));
 focusedArt=new ImageView(h);focusedArt.setTag("party-focused-portrait");actions.addView(focusedArt,new LinearLayout.LayoutParams(-1,0,1));focusedName=h.text("대원을 선택하세요",12);focusedName.setTextColor(NavyWorkbenchTheme.TEXT);actions.addView(focusedName);
 save=h.button("편성 저장",this::save);save.setTag("roster60-pvp-party-save");
 normal=h.button("일반 진행",()->join("normal"));normal.setTag("roster60-pvp-normal");
 rank=h.button("랭크 진행",()->join("rank"));rank.setTag("roster60-pvp-rank");
 cancel=h.button("매칭 취소",this::cancelWaiting);
 clearParty=h.button("편성 비우기",()->{if(busy||waiting)return;selectionRevision++;selected.clear();drawParty();});Button[] buttons={save,clearParty,normal,rank,cancel,h.button("다시 확인",this::resume)};
 if(management)buttons=new Button[]{buttons[0],buttons[1],buttons[5]};
 for(int row=0;row<(buttons.length+1)/2;row++){LinearLayout line=new LinearLayout(h);actions.addView(line,new LinearLayout.LayoutParams(-1,h.dp(48)));for(int col=0;col<2&&row*2+col<buttons.length;col++){Button button=buttons[row*2+col];style(button);line.addView(button,new LinearLayout.LayoutParams(0,-1,1));}}

 if(management){normal.setVisibility(View.GONE);rank.setVisibility(View.GONE);cancel.setVisibility(View.GONE);}else restoreMode(selectedMode);fetchOptions(entry);if(!management)resume();}
 void fetchOptions(){fetchOptions(null);}
 void fetchOptions(PartyEntryRead entry){
  if(!current()||optionsInFlight)return;optionsInFlight=true;final long requestedRevision=selectionRevision;final int requestedGeneration=++optionsGeneration,requestedEpoch=epoch;final String requestedOwner=owner;
  NativeApi.Result completed=(r,e)->{
   if(requestedGeneration!=optionsGeneration||requestedEpoch!=epoch||!requestedOwner.equals(owner))return;
   optionsInFlight=false;
   // NativeApi clears an expired token before delivery. Handle that current-screen
   // failure before current(), while rejecting old epoch/account callbacks as usual.
   if(e!=null&&!requestedOwner.isEmpty()&&h.api.token().isEmpty()&&requestedEpoch==h.epoch
       &&"roster60Pvp".equals(h.screen)&&!h.isFinishing()&&!h.isDestroyed()&&!h.updateBlocks()){
    h.login();h.message(e);return;
   }
   if(!current())return;if(e!=null){rankEligibility=null;status.setText("편성 확인 필요 · "+normalXpNotice(e));enable();finishOptionsRead();return;}
   NativeCommander.sync(h,r.optJSONObject("commander"),owner,epoch);rankEligibility=r.optJSONObject("rankEligibility");units=NativeOwnedRoster.levelOrdered(r.optJSONArray("units"));catalogueDirty=true;
   if(selectionRevision==requestedRevision){savedIds.clear();JSONArray ids=r.optJSONArray("partyIds");if(ids!=null)for(int i=0;i<ids.length();i++)savedIds.add(ids.opt(i) instanceof String?ids.optString(i):"");}
   if(!draftLoaded&&selectionRevision==requestedRevision){selected.clear();for(String id:savedIds)if(!id.isEmpty()&&!selected.contains(id))selected.add(id);if(!Roster60PreparationView.valid(new JSONArray(savedIds),units)){repairRequired=true;editing=true;modeUi();status.setText("편성대에서 출전 가능한 대원을 저장해 주세요.");}}
   draftLoaded=true; // 이후 조회는 보유 목록만 갱신하고 사용자가 편집한 순서는 보존한다.
   if(management&&(status.getText().toString().equals("서버 편성 확인 중…")||status.getText().toString().startsWith("편성 확인 필요 · ")))status.setText("탭으로 추가/해제 · 먼저 선택한 대원이 첫 출전합니다.");drawParty();finishOptionsRead();
  };if(entry!=null)entry.consume(completed);else h.api.call("native/roster60/pvp/options",null,completed);
 }
 void finishOptionsRead(){if(optionsRefreshQueued){optionsRefreshQueued=false;fetchOptions();}}
 void refreshOptionsAfterSave(){if(optionsInFlight)optionsRefreshQueued=true;else fetchOptions();}
 void style(Button b){CombatV2ReviewStyle.button(b);b.setTextSize(12);b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(h.dp(4),h.dp(2),h.dp(4),h.dp(2));b.setMaxLines(3);}
 JSONObject unit(String id){for(int i=0;i<units.length();i++)if(id.equals(units.optJSONObject(i).optString("ownedId")))return units.optJSONObject(i);return null;}
 boolean rankMode(){return !management&&"rank".equals(selectedMode);}
 static boolean serverTrue(JSONObject object,String key){return object!=null&&Boolean.TRUE.equals(object.opt(key));}
 static boolean levelAtLeast(JSONObject object,String key,int minimum){Object value=object==null?null:object.opt(key);if(!(value instanceof Number))return false;Number n=(Number)value;return n.doubleValue()==n.longValue()&&n.longValue()>=minimum;}
 String rankAccountReason(){
  if(rankEligibility==null||!"rank-only".equals(rankEligibility.optString("scope"))||!levelAtLeast(rankEligibility,"accountLevel",0))return "ACCOUNT_LEVEL_POLICY_PENDING";
  if(!levelAtLeast(rankEligibility,"accountLevel",60))return "RANK_ACCOUNT_LEVEL_REQUIRED";
  if(serverTrue(rankEligibility,"eligible"))return "";String reason=rankEligibility.optString("reason");return reason.isEmpty()?"ACCOUNT_LEVEL_POLICY_PENDING":reason;
 }
 boolean rankUnitAllowed(JSONObject unit){return serverTrue(unit,"rankEligible")&&levelAtLeast(unit,"level",60);}
 String rankUnitReason(JSONObject unit){if(!levelAtLeast(unit,"level",60))return "RANK_UNIT_LEVEL_REQUIRED";String reason=unit==null?"":unit.optString("rankReason");return reason.isEmpty()?"RANK_UNIT_ELIGIBILITY_PENDING":reason;}
 String rankPartyReason(){String account=rankAccountReason();if(!account.isEmpty())return account;for(String id:selected)if(!rankUnitAllowed(unit(id)))return rankUnitReason(unit(id));return "";}
 void rankNotice(){
  if(rankNotice==null)return;boolean shown=!management&&!pendingModeUnknown&&!"normal".equals(selectedMode);rankNotice.setVisibility(shown?View.VISIBLE:View.GONE);if(!shown)return;
  String reason=rankPartyReason();String text=reason.isEmpty()?"랭크 · 방범대장 Lv."+rankEligibility.optLong("accountLevel")+" · 각 대원 Lv.60 이상":rankMessage(reason,rankEligibility);
  JSONObject focus=unit(focused);if(reason.isEmpty()&&rankMode()&&focus!=null&&!rankUnitAllowed(focus))text="선택 불가 · "+rankMessage(rankUnitReason(focus),rankEligibility);
  Roster60FormationView.text(rankNotice,text);
 }
 static boolean rankRejection(String code){return Arrays.asList("ACCOUNT_LEVEL_POLICY_PENDING","ACCOUNT_LEVEL_DATA_INVALID","RANK_ACCOUNT_LEVEL_REQUIRED","RANK_UNIT_LEVEL_REQUIRED","RANK_REBIRTH_HISTORY_PENDING","RANK_QUEUE_POLICY_PENDING","RANK_QUEUE_CLASS_CHANGED","RANK_CATALOG_CHANGED").contains(code);}
 static boolean poolRejection(String code){return Arrays.asList("MATCH_POOL_POLICY_PENDING","MATCH_POOL_QUEUE_POLICY_PENDING","MATCH_POOL_CLASS_CHANGED","MATCH_POOL_HISTORY_CHANGED").contains(code);}
 static String poolMessage(String code){if("MATCH_POOL_OPPONENT_CHANGED".equals(code))return "상대의 참가 조건이 바뀌어 대전이 종료됐어요.";if("MATCH_POOL_CLASS_CHANGED".equals(code)||"MATCH_POOL_HISTORY_CHANGED".equals(code))return "환생 상태가 바뀌었어요. 매칭을 취소하고 다시 참가해 주세요.";return "대기 조건 확인 필요 · 매칭을 취소하고 다시 참가해 주세요.";}
 static String rankMessage(String code,JSONObject eligibility){
  if("RANK_ACCOUNT_LEVEL_REQUIRED".equals(code))return "랭크 잠금 · 방범대장 Lv."+(levelAtLeast(eligibility,"accountLevel",0)?eligibility.optLong("accountLevel"):"?")+" / 필요 Lv.60";
  if("RANK_UNIT_LEVEL_REQUIRED".equals(code))return "랭크 출전 대원은 모두 Lv.60 이상이어야 해요.";
  if("RANK_UNIT_ELIGIBILITY_PENDING".equals(code))return "랭크 대원의 출전 조건을 확인하고 있어요.";
  if("RANK_OPPONENT_ELIGIBILITY_CHANGED".equals(code))return "상대의 참가 조건이 바뀌어 랭크 대전이 종료됐어요.";
  if("RANK_REBIRTH_HISTORY_PENDING".equals(code)||"RANK_QUEUE_CLASS_CHANGED".equals(code))return "환생 이력을 확인한 뒤 랭크에 다시 참가해 주세요.";
  if("RANK_CATALOG_CHANGED".equals(code)||"RANK_QUEUE_POLICY_PENDING".equals(code))return "랭크 조건이 바뀌었어요. 대기를 취소하고 다시 확인해 주세요.";
  return "랭크 잠금 · 방범대장 레벨을 확인하고 있어요.";
 }
 int headingHeight(TextView text){return Math.max(h.dp(20),(int)Math.ceil(text.getPaint().getFontSpacing()));}
 TextView heading(String text){TextView v=h.text(text,12);v.setPadding(h.dp(4),0,h.dp(4),0);CombatV2ReviewStyle.text(v);v.setTextColor(NavyWorkbenchTheme.TEXT);v.setSingleLine(true);v.setEllipsize(android.text.TextUtils.TruncateAt.END);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
 // List.sort는 동레벨의 기존 목록 순서를 보존한다. 실제 selected 편성은 정렬하지 않는다.
 static ArrayList<JSONObject> levelOrderedCandidates(JSONArray units){
  return NativeOwnedRoster.levelOrderedCandidates(units,"ownedId");
 }
 void drawParty(){
 // JSON comparison belongs to a catalogue response, never every local badge/focus update.
 if(catalogueDirty||renderedSource!=units){
  String signature=units.toString();catalogueSerializations++;catalogueDirty=false;renderedSource=units;
  if(!signature.equals(renderedUnits)){
   renderedUnits=signature;catalogueRevision++;ownedTiles.clear();ownedState.clear();renderedTileState="";ArrayList<String> ids=new ArrayList<>();ArrayList<Roster60MemberTile> tiles=new ArrayList<>();
   for(JSONObject u:levelOrderedCandidates(units)){
    String id=u.optString("ownedId");
    Roster60MemberTile tile=new Roster60MemberTile(h,u,u.optString("name"),!busy&&!waiting,()->{if(busy||waiting||!editing||ownedStrip.blocksClick())return;focused=id;if(selected.contains(id)){selectionRevision++;selected.remove(id);}else if(rankMode()&&!rankUnitAllowed(u)){status.setText(rankMessage(rankUnitReason(u),rankEligibility));}else if(selected.size()<6){selectionRevision++;selected.add(id);}else status.setText("출전 포함 최대 6마리입니다.");drawParty();},null,false,1,0,true);
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
    Roster60MemberTile tile=new Roster60MemberTile(h,u,u==null?"빈 칸":u.optString("name"),u!=null&&!busy&&!waiting&&editing,()->{if(busy||waiting||!editing)return;selectionRevision++;focused=id;selected.remove(id);drawParty();},Roster60FormationView.role(slot),u!=null&&focused.equals(id),1,0);
    tile.setTag("party-selected-"+slot);((Roster60PartySlots)slots).installAccessibility(tile.action,slot);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);if(slot<5)p.rightMargin=h.dp(4);
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
  String state=selected.toString()+"|"+focused+"|"+busy+"|"+waiting+"|"+selectedMode+"|"+editing+"|"+catalogueRevision;
  if(state.equals(renderedTileState))return;
  for(int i=0;i<slots.getChildCount();i++){
   String id=i<selected.size()?selected.get(i):"";boolean active=!id.isEmpty()&&focused.equals(id),enabled=!id.isEmpty()&&!busy&&!waiting&&editing;String tuple=active+"|"+enabled;
   if(!tuple.equals(slotState[i])){((Roster60MemberTile)slots.getChildAt(i)).state(Roster60FormationView.role(i),active,enabled,Roster60FormationView.levelBadge(unit(id)));slotState[i]=tuple;tileStateUpdates++;}
  }
  for(Map.Entry<String,Roster60MemberTile> entry:ownedTiles.entrySet()){
   String id=entry.getKey();int slot=selected.indexOf(id);String role=slot<0?(rankMode()&&!rankUnitAllowed(unit(id))?"랭크 불가":null):"✓ "+Roster60FormationView.role(slot);boolean active=focused.equals(id),enabled=!busy&&!waiting&&editing;String tuple=role+"|"+active+"|"+enabled;
   if(!tuple.equals(ownedState.get(id))){entry.getValue().state(role,active,enabled,Roster60FormationView.levelBadge(unit(id)));ownedState.put(id,tuple);tileStateUpdates++;}
  }
  renderedTileState=state;
 }

 void editorVisibility(){if(ownedSection==null)return;ownedSection.setVisibility(editing?View.VISIBLE:View.GONE);focusedArt.setVisibility(editing?View.VISIBLE:View.GONE);focusedName.setVisibility(editing?View.VISIBLE:View.GONE);save.setVisibility(editing?View.VISIBLE:View.GONE);clearParty.setVisibility(editing?View.VISIBLE:View.GONE);editParty.setText(editing?"편성 변경 취소":"편성대");editParty.setEnabled(!busy&&!waiting&&prefs.getString(key,"").isEmpty());for(int i=0;i<actions.getChildCount();i++){View child=actions.getChildAt(i);if(child instanceof LinearLayout){LinearLayout line=(LinearLayout)child;boolean visible=false;for(int j=0;j<line.getChildCount();j++)visible|=line.getChildAt(j).getVisibility()!=View.GONE;line.setVisibility(visible?View.VISIBLE:View.GONE);}}}
 void restoreSavedSelection(){selected.clear();for(String id:savedIds)if(!id.isEmpty()&&!selected.contains(id))selected.add(id);}
 void editParty(){if(!current()||busy||waiting||!prefs.getString(key,"").isEmpty())return;editing=!editing;if(!editing){restoreSavedSelection();selectionRevision++;}modeUi();drawParty();}
 void enable(){tileStates();editorVisibility();boolean free=!busy&&!waiting&&prefs.getString(key,"").isEmpty(),rankAllowed=rankPartyReason().isEmpty(),valid=Roster60PreparationView.valid(new JSONArray(selected),units);save.setEnabled(free&&valid&&(!rankMode()||rankAllowed));normal.setEnabled(free&&valid&&!editing&&!repairRequired);rank.setEnabled(free&&valid&&rankAllowed&&!editing&&!repairRequired);cancel.setEnabled(waiting&&(!busy||currentRead)&&prefs.getString(key,"").isEmpty());if(free&&draftLoaded&&(!valid||repairRequired)&&!editing)status.setText("편성대에서 출전 가능한 대원을 저장해 주세요.");rankNotice();}
 void moveSelected(int from,int to){if(!current()||busy||waiting||!editing)return;if(Roster60PartySlots.insert(selected,from,to)){selectionRevision++;focused=selected.get(to);drawParty();}}
 void cancelWaiting(){if(!current()||!waiting)return;if(currentRead){currentRead=false;currentGeneration++;busy=false;}request("cancel",h.obj());}
 void save(){if(busy||waiting||selected.isEmpty()||selected.size()>6)return;if(rankMode()&&!rankPartyReason().isEmpty()){status.setText(rankMessage(rankPartyReason(),rankEligibility));return;}selectionRevision++;JSONObject body=h.obj("ownedIds",new JSONArray(selected));if(rankMode())try{body.put("mode","rank");}catch(JSONException impossible){throw new IllegalStateException(impossible);}request("party",body);}
 void join(String mode){
  if(!validMode(mode)||(!selectedMode.isEmpty()&&!selectedMode.equals(mode))||!current()||busy||waiting||editing||repairRequired||selected.isEmpty()||selected.size()>6||!prefs.getString(key,"").isEmpty()||ownedStrip.blocksClick())return;
  if("rank".equals(mode)&&!rankPartyReason().isEmpty()){status.setText(rankMessage(rankPartyReason(),rankEligibility));rankNotice();return;}
  restoreMode(mode);selectionRevision++;busy=true;enable();final int requestedEpoch=epoch;final String requestedOwner=owner;
  JSONObject body=h.obj("ownedIds",new JSONArray(selected));if("rank".equals(mode))try{body.put("mode","rank");}catch(JSONException impossible){throw new IllegalStateException(impossible);}
  h.api.call("native/roster60/pvp/party",body,(r,e)->{if(requestedEpoch!=epoch||!requestedOwner.equals(owner))return;busy=false;if(!current())return;if(e!=null){if(partyRejection(e)){repairRequired=true;editing=true;modeUi();status.setText("편성대에서 출전 대원을 다시 저장해 주세요.");}else status.setText(normalXpNotice(e));enable();if(rankRejection(e)||poolRejection(e)||partyRejection(e))fetchOptions();return;}request("join",h.obj("mode",mode));});
 }
 void request(String op,JSONObject body){if(!current()||busy||!prefs.getString(key,"").isEmpty())return;try{body.put("requestId",UUID.randomUUID().toString());JSONObject saved=h.obj("op",op,"body",body,"mode",selectedMode);if(prefs.edit().putString(key,saved.toString()).commit())send(saved,false);}catch(JSONException e){status.setText("요청 저장 실패");}}
 void send(JSONObject saved){send(saved,true);}
 void send(JSONObject saved,boolean recovering){if(busy||!current())return;busy=true;enable();String op=saved.optString("op");JSONObject body=saved.optJSONObject("body");if(!Arrays.asList("party","join","cancel").contains(op)||body==null){busy=false;status.setText("저장된 요청 확인 필요");return;}if(op.equals("join"))restoreMode(body.optString("mode"));else if(validMode(saved.optString("mode")))restoreMode(saved.optString("mode"));else if(op.equals("cancel")){pendingModeUnknown=true;selectedMode="";h.rosterPvpMode="";modeUi();}
 // Party persistence is an idempotent replacement, and its schema has no requestId.
 JSONObject wire=body;try{if(op.equals("party")){wire=new JSONObject(body.toString());wire.remove("requestId");if("rank".equals(selectedMode))wire.put("mode","rank");}}catch(JSONException ignored){}
 final int requestedEpoch=epoch;final String requestedOwner=owner,requestedKey=key,requestedPending=prefs.getString(key,"");
 h.api.call("native/roster60/pvp/"+op,wire,(r,e)->{if(!requestedOwner.equals(h.api.token()))return;
 boolean rejected=e!=null&&(e.contains("PENDING")||rankRejection(e)||poolRejection(e)||Arrays.asList("NORMAL_XP_POLICY_INVALID","NORMAL_XP_SOURCE_INVALID","NORMAL_XP_CATALOG_CHANGED","INVALID_PARTY","UNIT_UNAVAILABLE","UNIT_NOT_FOUND","INVALID_REQUEST","REQUEST_ID_REUSED","PVP_ALREADY_ACTIVE","PVP_QUEUE_ACTIVE","PVP_BUSY","PARTY_REQUIRED").contains(e));
 if((e==null||rejected)&&prefs.getString(requestedKey,"").equals(requestedPending))prefs.edit().remove(requestedKey).commit();
 if(requestedEpoch!=epoch||!requestedOwner.equals(owner))return;busy=false;if(!current())return;if(e!=null){queueError="확인 필요 · "+normalXpNotice(e);if(waiting)waitingStatus();else status.setText(queueError);enable();if(rankRejection(e)||poolRejection(e))fetchOptions();schedule();return;}if(recovering&&op.equals("join")){status.setText("서버 대기 시간과 매칭 확인 중…");resume();return;}if(op.equals("party")){savedIds.clear();JSONArray savedParty=body.optJSONArray("ownedIds");for(int i=0;savedParty!=null&&i<savedParty.length();i++)savedIds.add(savedParty.optString(i));repairRequired=false;editing=management;modeUi();status.setText("편성 저장 완료 · 진행을 누르면 대전을 시작합니다.");refreshOptionsAfterSave();}else show(r);enable();schedule();});}
 void resume(){paused=false;if(!current()||busy)return;String pending=prefs.getString(key,"");if(!pending.isEmpty()){try{send(new JSONObject(pending));}catch(JSONException e){status.setText("저장된 요청 확인 필요");}return;}if(management){fetchOptions();return;}currentRead=true;busy=true;enable();final int requestedEpoch=epoch,requestedCurrent=++currentGeneration;final String requestedOwner=owner;h.api.call("native/roster60/pvp/current",null,(r,e)->{if(requestedCurrent!=currentGeneration||requestedEpoch!=epoch||!requestedOwner.equals(owner))return;currentRead=false;busy=false;if(!current())return;if(e!=null){queueError="대전 확인 필요 · "+normalXpNotice(e);if(waiting)waitingStatus();else status.setText(queueError);}else show(r);enable();schedule();});}
 void show(JSONObject r){queueError="";normalXpCondition=r.optString("normalXpCondition","");rankCondition=r.optString("rankCondition","");matchPoolCondition=r.optString("matchPoolCondition","");JSONObject battle=r.optJSONObject("battle");if(battle!=null){restoreMode(r.optString("mode",battle.optString("mode")));waiting=false;queueClock.clear();pause();h.openRosterHumanBattle(battle);return;}waiting="waiting".equals(r.optString("status"));if(waiting){editing=false;restoreMode(r.optString("mode"));queueClock.update(selectedMode,r.optLong("queuedAt",-1),r.optLong("serverNow",-1),r.isNull("aiAvailableAt")?-1:r.optLong("aiAvailableAt",-1),SystemClock.elapsedRealtime());waitingStatus();}else{queueClock.clear();if(pendingModeUnknown){pendingModeUnknown=false;selectedMode=entryMode;h.rosterPvpMode=entryMode;modeUi();}status.setText(modeLabel()+" · 편성을 확인하고 대전을 시작하세요.");}drawParty();}
 static boolean partyRejection(String error){return Arrays.asList("INVALID_PARTY","UNIT_UNAVAILABLE","UNIT_NOT_FOUND","PARTY_REQUIRED").contains(error);}
 static String normalXpNotice(String error){if(error==null)return "";if(poolRejection(error))return poolMessage(error);if(rankRejection(error))return rankMessage(error,null);if("NORMAL_XP_CATALOG_CHANGED".equals(error))return "성장 설정이 바뀌었어요. 대기를 취소한 뒤 다시 참가해 주세요.";if(error.startsWith("NORMAL_XP_"))return "일반게임의 성장 보상을 준비 중이에요. 잠시 뒤 다시 확인해 주세요.";return error;}
 void waitingStatus(){if(poolRejection(matchPoolCondition)){status.setText(poolMessage(matchPoolCondition));return;}if("rank".equals(selectedMode)&&rankRejection(rankCondition)){status.setText(rankMessage(rankCondition,rankEligibility)+" · 매칭 취소 가능");return;}if("normal".equals(selectedMode)&&normalXpCondition.startsWith("NORMAL_XP_")){status.setText("NORMAL_XP_CATALOG_CHANGED".equals(normalXpCondition)?"성장 설정이 바뀌었어요. 대기를 취소한 뒤 다시 참가해 주세요.":"성장 설정을 확인하고 있어요. 대기를 취소한 뒤 다시 참가해 주세요.");return;}status.setText(modeLabel()+" · "+queueClock.label(selectedMode,SystemClock.elapsedRealtime())+(!prefs.getString(key,"").isEmpty()||busy&&!currentRead?" · 요청 확인 중":queueError.isEmpty()?" · 취소 가능":" · 연결 확인 중"));}
 void schedule(){timer.removeCallbacksAndMessages(null);if(current()&&!paused&&!management&&(waiting||!prefs.getString(key,"").isEmpty())){if(waiting)timer.postDelayed(waitingTick,1000);timer.postDelayed(this::resume,2000);}}
 void pause(){paused=true;timer.removeCallbacksAndMessages(null);if(slots instanceof Roster60PartySlots)((Roster60PartySlots)slots).reset();}
 void back(){if(editing&&!management){if(busy||!prefs.getString(key,"").isEmpty())return;restoreSavedSelection();selectionRevision++;editing=false;modeUi();drawParty();return;}pause();if(management)h.home();else h.openPlayMenu();}
}
