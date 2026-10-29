package org.dpdns.gmm;

import android.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;

/** A species card only opens its members. Every selection still names one owned individual. */
final class Roster60OwnedGroups {
  final MainActivity h;
  final Roster60CardStrip strip;
  final Consumer<JSONObject> choose;
  final BooleanSupplier canChoose;
  final String tag;
  final LinkedHashMap<String,Roster60MemberTile> tiles=new LinkedHashMap<>();
  final LinkedHashMap<String,TextView> counts=new LinkedHashMap<>();
  final LinkedHashMap<String,Button> memberButtons=new LinkedHashMap<>();
  ArrayList<NativeOwnedRosterGroups.Group> groups=new ArrayList<>();
  ArrayList<String> selected=new ArrayList<>();
  String focused="",openKey="",lastState="";
  AlertDialog dialog;TextView popupNotice;

  Roster60OwnedGroups(MainActivity host,Roster60CardStrip strip,String tag,
      Consumer<JSONObject> choose,BooleanSupplier canChoose){
    h=host;this.strip=strip;this.tag=tag;this.choose=choose;this.canChoose=canChoose;
    strip.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
      public void onViewAttachedToWindow(View view){}
      public void onViewDetachedFromWindow(View view){close();}
    });
  }
  void replace(JSONArray units,float scale){
    close();lastState="";groups=NativeOwnedRosterGroups.group(units);tiles.clear();counts.clear();
    ArrayList<String> keys=new ArrayList<>();ArrayList<Roster60MemberTile> cards=new ArrayList<>();
    for(NativeOwnedRosterGroups.Group group:groups){
      Roster60MemberTile card=new Roster60MemberTile(h,group.representative(),group.name,true,
          ()->open(group.key),null,false,scale,1.15f,true);
      card.setTag(tag+"-group-card-"+group.key);card.action.setTag(tag+"-group-"+group.key);
      card.portrait.setPadding(h.dp(3*scale),h.dp(2*scale),h.dp(3*scale),h.dp(44*scale));
      TextView count=h.text("보유 "+group.count()+"명",Math.max(10,Math.round(11*scale)));
      count.setTextColor(NavyWorkbenchTheme.TEXT);count.setGravity(android.view.Gravity.CENTER);
      count.setIncludeFontPadding(false);count.setPadding(0,0,0,0);
      count.setSingleLine(true);count.setTag(tag+"-group-count-"+group.key);
      int countHeight=Math.max(h.dp(18*scale),(int)Math.ceil(count.getPaint().getFontSpacing()))+h.dp(2);
      int nameHeight=Math.max(h.dp(20*scale),(int)Math.ceil(card.action.getPaint().getFontSpacing())+h.dp(4));
      android.widget.FrameLayout.LayoutParams countBox=new android.widget.FrameLayout.LayoutParams(-1,countHeight,android.view.Gravity.BOTTOM);
      countBox.bottomMargin=nameHeight;card.addView(count,countBox);
      card.portrait.setPadding(h.dp(3*scale),h.dp(2*scale),h.dp(3*scale),countHeight+nameHeight+h.dp(4));
      keys.add(group.key);cards.add(card);tiles.put(group.key,card);counts.put(group.key,count);
    }
    strip.replace(keys,cards);refresh(selected,focused);
  }
  void refresh(List<String> party,String focus){
    boolean enabled=canChoose.getAsBoolean();String state=party.toString()+"|"+focus+"|"+enabled;if(state.equals(lastState))return;lastState=state;
    selected=new ArrayList<>(party);focused=focus==null?"":focus;
    for(NativeOwnedRosterGroups.Group group:groups){
      int chosen=0;boolean active=false;
      for(JSONObject member:group.members){String id=member.optString("ownedId");if(selected.contains(id))chosen++;active|=focused.equals(id);}
      Roster60MemberTile card=tiles.get(group.key);if(card==null)continue;
      card.state(chosen==0?null:"편성 "+chosen+"명",active,enabled,"최고 Lv."+group.highestLevel);
      TextView count=counts.get(group.key);count.setText("보유 "+group.count()+"명"+(chosen==0?"":" · ✓"+chosen));
      card.action.setContentDescription(group.name+" · "+group.summary()+" · 편성 "+chosen+"명 · 개체 목록 펼치기");
    }
    refreshMembers();
  }
  NativeOwnedRosterGroups.Group group(String key){for(NativeOwnedRosterGroups.Group group:groups)if(group.key.equals(key))return group;return null;}
  void open(String key){
    if(!canChoose.getAsBoolean()||strip.blocksClick())return;
    NativeOwnedRosterGroups.Group group=group(key);if(group==null)return;close();openKey=key;
    LinearLayout rows=new LinearLayout(h);rows.setOrientation(LinearLayout.VERTICAL);rows.setPadding(h.dp(8),h.dp(4),h.dp(8),h.dp(4));
    for(JSONObject member:group.members){
      String id=member.optString("ownedId");
      Button button=h.button("",()->{if(canChoose.getAsBoolean()&&dialog!=null&&dialog.isShowing()){
        boolean wasSelected=selected.contains(id);choose.accept(member);
        if(!wasSelected&&!selected.contains(id)&&popupNotice!=null)popupNotice.setText("추가하지 못했어요. 빈 칸과 출전 조건을 확인해 주세요.");
      }});
      button.setTag(tag+"-individual-"+id);button.setTextSize(12);button.setSingleLine(false);button.setMaxLines(3);button.setEllipsize(android.text.TextUtils.TruncateAt.END);
      NavyWorkbenchTheme.tab(h,button,false);rows.addView(button,new LinearLayout.LayoutParams(-1,h.dp(72)));memberButtons.put(id,button);
    }
    ScrollView list=new ScrollView(h);list.setTag(tag+"-group-members");list.setFillViewport(false);list.addView(rows,new ScrollView.LayoutParams(-1,-2));
    int height=Math.max(h.dp(96),Math.min(h.dp(240),Math.round(h.getResources().getDisplayMetrics().heightPixels*.42f)));
    LinearLayout content=new LinearLayout(h);content.setOrientation(LinearLayout.VERTICAL);
    popupNotice=h.text("",12);popupNotice.setTextColor(NavyWorkbenchTheme.TEXT);popupNotice.setMaxLines(2);content.addView(popupNotice,new LinearLayout.LayoutParams(-1,h.dp(40)));content.addView(list,new LinearLayout.LayoutParams(-1,height));
    dialog=new GameDialog(h).navy().setTitle(group.name+" · "+group.summary()).setView(content).setNegativeButton("닫기",null).show();
    final AlertDialog shown=dialog;shown.setOnDismissListener(d->{if(h.visibleDialog==shown)h.visibleDialog=null;if(dialog==shown){dialog=null;openKey="";popupNotice=null;memberButtons.clear();}});
    refreshMembers();
  }
  void refreshMembers(){
    NativeOwnedRosterGroups.Group group=group(openKey);if(group==null)return;boolean enabled=canChoose.getAsBoolean();int number=0;if(popupNotice!=null)popupNotice.setText("현재 편성 "+selected.size()+"마리 · 개체를 골라 추가하거나 해제하세요.");
    for(JSONObject member:group.members){
      number++;String id=member.optString("ownedId");Button button=memberButtons.get(id);if(button==null)continue;
      int slot=selected.indexOf(id);String role=slot<0?"편성에 추가":"✓ "+Roster60FormationView.role(slot)+" · 해제";
      button.setText("Lv."+member.optInt("level",1)+" · "+member.optString("name")+" · "+role+"\n개체 "+number+" · "+id);
      button.setContentDescription(member.optString("name")+" · Lv."+member.optInt("level",1)+" · 소유 개체 "+id+" · "+role);
      button.setEnabled(enabled);NavyWorkbenchTheme.tab(h,button,slot>=0);
    }
  }
  void close(){AlertDialog old=dialog;dialog=null;openKey="";popupNotice=null;memberButtons.clear();if(old!=null){if(h.visibleDialog==old)h.visibleDialog=null;old.dismiss();}}

  /** Legacy story owns key/id records. Its already-aggregated PvP catalogue never uses this picker. */
  static AlertDialog openLegacy(MainActivity h,NativeOwnedRosterGroups.Group group,
      List<String> selected,Consumer<JSONObject> choose){
    final int epoch=h.epoch;final String owner=h.api.token();final AlertDialog[] shown={null};
    LinearLayout rows=new LinearLayout(h);rows.setOrientation(LinearLayout.VERTICAL);int number=0;
    for(JSONObject member:group.members){
      number++;String key=member.optString("key");
      String health=member.has("hp")?" · HP "+member.optInt("hp")+"/"+member.optInt("maxHp"):"";
      Button button=h.button("Lv."+member.optInt("level",1)+" · "+member.optString("name")+health+(selected.contains(key)?" · ✓ 편성됨":"")+"\n개체 "+number+" · "+key,()->{
        if(h.busy||h.epoch!=epoch||!owner.equals(h.api.token())||h.isFinishing()||h.isDestroyed())return;
        choose.accept(member);if(shown[0]!=null)shown[0].dismiss();
      });
      button.setTag("team-individual-"+key);button.setTextSize(12);button.setSingleLine(false);button.setMaxLines(3);button.setEllipsize(android.text.TextUtils.TruncateAt.END);NavyWorkbenchTheme.tab(h,button,selected.contains(key));
      rows.addView(button,new LinearLayout.LayoutParams(-1,h.dp(72)));
    }
    ScrollView list=new ScrollView(h);list.setTag("team-group-members");list.addView(rows,new ScrollView.LayoutParams(-1,-2));
    LinearLayout content=new LinearLayout(h);content.setOrientation(LinearLayout.VERTICAL);
    content.addView(list,new LinearLayout.LayoutParams(-1,Math.max(h.dp(96),Math.min(h.dp(288),Math.round(h.getResources().getDisplayMetrics().heightPixels*.5f)))));
    shown[0]=new GameDialog(h).navy().setTitle(group.name+" · "+group.summary()).setView(content).setNegativeButton("닫기",null).show();return shown[0];
  }
}
