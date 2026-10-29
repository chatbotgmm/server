package org.dpdns.gmm;

import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;

/** Optional editor before departure. Only Proceed creates a battle or a queue request. */
final class Roster60PreparationView extends LinearLayout {
  final MainActivity h;
  final JSONArray units;
  final Roster60PartyPolicy policy;
  final Consumer<JSONArray> proceed;
  final Runnable cancel;
  final BooleanSupplier current;
  final TextView notice, summary, recommended;
  final Button start, edit, leave;
  final LinearLayout overview;
  final FrameLayout content;
  JSONArray saved;
  Roster60FormationView formation;
  boolean locked, saving, invalid;
  Runnable retry;
  int generation;

  Roster60PreparationView(MainActivity host, JSONArray available, JSONArray party,
      String heading, Consumer<JSONArray> proceed, Runnable cancel, BooleanSupplier current) {
    this(host,available,party,heading,proceed,cancel,current,Roster60PartyPolicy.current());
  }
  Roster60PreparationView(MainActivity host, JSONArray available, JSONArray party,
      String heading, Consumer<JSONArray> proceed, Runnable cancel, BooleanSupplier current,Roster60PartyPolicy policy) {
    super(host);
    h=host;units=NativeOwnedRoster.levelOrdered(available);saved=copy(party);this.proceed=proceed;this.cancel=cancel;this.current=current;this.policy=policy;invalid=policy.selectionRequired;
    setOrientation(VERTICAL);setTag("roster60-preparation");
    setPadding(h.dp(12),h.dp(8),h.dp(12),h.dp(8));
    setBackground(NavyWorkbenchTheme.surface(h,NavyWorkbenchTheme.PANEL,NavyWorkbenchTheme.LINE,8));
    notice=h.text(heading,14);notice.setTextColor(NavyWorkbenchTheme.TEXT);notice.setMaxLines(2);
    addView(notice,new LayoutParams(-1,-2));
    recommended=h.text("",11);recommended.setTag("preparation-recommended-level");recommended.setTextColor(NavyWorkbenchTheme.MUTED);recommended.setMaxLines(2);recommended.setVisibility(View.GONE);addView(recommended,new LayoutParams(-1,-2));
    content=new FrameLayout(h);addView(content,new LayoutParams(-1,0,1));
    overview=new LinearLayout(h);overview.setOrientation(VERTICAL);overview.setGravity(android.view.Gravity.CENTER);
    content.addView(overview,new FrameLayout.LayoutParams(-1,-1));
    summary=h.text("",14);summary.setTextColor(NavyWorkbenchTheme.TEXT);summary.setSingleLine(false);
    android.widget.ScrollView savedTeam=new android.widget.ScrollView(h);savedTeam.setFillViewport(false);savedTeam.addView(summary,new android.widget.ScrollView.LayoutParams(-1,-2));
    overview.addView(savedTeam,new LayoutParams(-1,0,1));
    LinearLayout actions=new LinearLayout(h);overview.addView(actions,new LayoutParams(-1,h.dp(52)));
    start=h.button("진행",this::submit);start.setTag("preparation-proceed");
    edit=h.button("편성대",this::edit);edit.setTag("preparation-edit");
    leave=h.button("취소",this::cancel);leave.setTag("preparation-cancel");
    for(Button button:new Button[]{start,edit,leave}){
      NavyWorkbenchTheme.tab(h,button,false);button.setTextSize(13);
      actions.addView(button,new LayoutParams(0,-1,1));
    }
    refresh();
  }

  static JSONArray copy(JSONArray ids){JSONArray result=new JSONArray();for(int i=0;ids!=null&&i<ids.length();i++)result.put(ids.opt(i) instanceof String?ids.opt(i):JSONObject.NULL);return result;}
  void recommendation(JSONObject quest){String label=Roster60RecommendedLevel.label(quest);recommended.setText(label);recommended.setVisibility(label.isEmpty()?View.GONE:View.VISIBLE);}
  static boolean valid(JSONArray ids,JSONArray units){return Roster60PartyPolicy.current().allows(ids,units);}
  void refresh(){
    StringBuilder text=new StringBuilder("저장된 편성 "+saved.length()+"마리 · 출전 1명 + 예비 최대 "+(policy.maxParty-1)+"명");
    for(int n=0;n<saved.length();n++)for(int i=0;i<units.length();i++){
      JSONObject unit=units.optJSONObject(i);if(unit!=null&&saved.optString(n).equals(unit.optString("ownedId"))){text.append("\n").append(n+1).append(". ").append(unit.optString("name")).append(" · Lv.").append(unit.optInt("level",1));break;}
    }
    boolean valid=policy.allows(saved,units)&&!invalid;
    if(!policy.ready)text.append("\n").append(Roster60PartyPolicy.compatibilityMessage());else if(!valid)text.append("\n").append(Roster60PartyPolicy.selectionMessage());
    summary.setText(text);start.setText(retry==null?"진행":"연결 다시 확인");start.setEnabled(!locked&&!saving&&(retry!=null||valid));edit.setEnabled(policy.ready&&!locked&&!saving&&retry==null);leave.setEnabled(!locked&&!saving);
  }
  void submit(){if(!current.getAsBoolean()||locked||saving)return;if(retry!=null){locked=true;refresh();retry.run();return;}if(invalid||!policy.allows(saved,units))return;locked=true;refresh();proceed.accept(copy(saved));}
  void edit(){
    if(!current.getAsBoolean()||locked||saving||retry!=null||!policy.ready)return;
    overview.setVisibility(View.GONE);
    formation=new Roster60FormationView(h,units,this::save,this::cancelEdit,policy);
    formation.start.setText("편성 저장");formation.start.setTag("preparation-save");
    for(int i=0;i<saved.length();i++)if(!saved.optString(i).isEmpty()&&!formation.selected.contains(saved.optString(i)))formation.selected.add(saved.optString(i));
    content.addView(formation,new FrameLayout.LayoutParams(-1,-1));
  }
  void save(JSONArray ids){
    if(!current.getAsBoolean()||locked||saving||!policy.allows(ids,units))return;
    saving=true;final int requested=++generation;notice.setText("편성을 저장하고 있어요…");
    h.api.call("native/roster60/pvp/party",h.obj("ownedIds",copy(ids)),(result,error)->{
      if(!current.getAsBoolean()||requested!=generation)return;saving=false;
      if(error!=null){notice.setText(NativeRoster60StoryController.message(error));if(formation!=null)formation.unlock();refresh();return;}
      saved=copy(ids);invalid=false;notice.setText("편성 저장 완료 · 진행을 누르면 출발합니다.");closeEditor();
    });
  }
  void closeEditor(){if(formation!=null){content.removeView(formation);formation=null;}overview.setVisibility(View.VISIBLE);refresh();}
  void cancelEdit(){if(!current.getAsBoolean()||saving||locked)return;closeEditor();}
  void cancel(){if(!current.getAsBoolean()||saving||locked)return;generation++;cancel.run();}
  boolean back(){if(saving||locked)return true;if(formation!=null){cancelEdit();return true;}cancel();return true;}
  void unlock(String error){locked=false;retry=null;invalid=java.util.Arrays.asList("INVALID_PARTY","UNIT_UNAVAILABLE","UNIT_NOT_FOUND","PARTY_REQUIRED",Roster60PartyPolicy.RESELECT).contains(error);if(formation!=null)formation.unlock();notice.setText(NativeRoster60StoryController.message(error));refresh();}
  void retryPending(String error,Runnable action){unlock(error);retry=action;refresh();}
}
