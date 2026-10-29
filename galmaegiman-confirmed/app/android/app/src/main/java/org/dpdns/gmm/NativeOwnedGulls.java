package org.dpdns.gmm;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Exact owned individuals. The encyclopedia and saved party keep their own independent order. */
final class NativeOwnedGulls {
  static final String SCREEN="ownedGulls", FLOW="roster60-owned-v1";
  static final String[] ELEMENTS={"NORMAL","FIRE","WATER","LIGHTNING","ICE","NATURE","HOLY","DARK"};
  static final String[] ELEMENT_NAMES={"일반","불","물","번개","얼음","자연","성","어둠"};
  static final String[] STATS={"hp","mp","durability","statusResistance","speed"};
  static final String[] STAT_NAMES={"HP","MP","내구력","상태 저항","속도"};
  final MainActivity h;
  final ArrayList<JSONObject> entries=new ArrayList<>();
  final LinkedHashMap<String,JSONObject> details=new LinkedHashMap<>();
  final HashMap<String,Integer> pending=new HashMap<>();
  String owner="",catalog="",selectedId="",preference="";
  int epoch,generation,index;
  boolean reading,paused,refreshQueued,compactInfo;
  JSONObject shown;
  LinearLayout root;
  TextView notice,name,vitals,position,identity;
  Button previous,next,refresh,stats,elements,skills;
  Portrait portrait;
  AlertDialog modal;

  NativeOwnedGulls(MainActivity h){this.h=h;}
  boolean current(){return !paused&&!h.isDestroyed()&&h.epoch==epoch&&SCREEN.equals(h.screen)&&owner.equals(h.api.token());}
  void open(){
    if(h.villageHome!=null)h.villageHome.pause();
    h.base("내 갈매미",SCREEN);epoch=h.epoch;owner=h.api.token();paused=false;
    preference=NativeProgression.accountKey(BuildConfig.SERVER_URL+"|owned-v1|"+Roster60StoryJournal.accountId(owner));
    selectedId=h.getSharedPreferences("roster60_owned_position",Context.MODE_PRIVATE).getString(preference,"");
    h.page.removeAllViews();h.page.setPadding(0,0,0,0);
    Button close=button("×","owned-gulls-close",h::back);close.setTextSize(28);close.setContentDescription("내 갈매미 닫기");close.setBackgroundColor(android.graphics.Color.TRANSPARENT);
    FrameLayout.LayoutParams closeBox=new FrameLayout.LayoutParams(h.dp(48),h.dp(48),Gravity.TOP|Gravity.RIGHT);h.root.addView(close,closeBox);
    refresh=button("↻","owned-gulls-refresh",this::refresh);refresh.setTextSize(25);refresh.setContentDescription("내 갈매미 · 새로고침");refresh.setBackgroundColor(android.graphics.Color.TRANSPARENT);
    FrameLayout.LayoutParams refreshBox=new FrameLayout.LayoutParams(h.dp(48),h.dp(48),Gravity.TOP|Gravity.RIGHT);refreshBox.rightMargin=h.dp(52);h.root.addView(refresh,refreshBox);
    // Keep the existing scenery, quietly shaded so the original full-body art is the focus.
    for(int i=0;i<h.root.getChildCount();i++)if(h.root.getChildAt(i) instanceof GameShade)h.root.getChildAt(i).setBackgroundColor(0xe60a1018);
    root=new LinearLayout(h);root.setOrientation(LinearLayout.VERTICAL);root.setTag("owned-gulls-root");
    root.setPadding(0,0,0,0);root.setBackgroundColor(android.graphics.Color.TRANSPARENT);
    h.page.addView(root,new LinearLayout.LayoutParams(-1,0,1));
    LinearLayout content=new LinearLayout(h);content.setGravity(Gravity.CENTER_VERTICAL);content.setTag("owned-gulls-stage");root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
    compactInfo=h.getResources().getConfiguration().screenHeightDp<380||(h.getResources().getConfiguration().fontScale>1.15f&&h.getResources().getConfiguration().screenHeightDp<500);boolean brief=compactInfo;
    LinearLayout info=new LinearLayout(h);info.setOrientation(LinearLayout.VERTICAL);info.setGravity(Gravity.CENTER_VERTICAL);info.setPadding(h.dp(brief?12:18),h.dp(4),h.dp(8),h.dp(4));info.setTag("owned-gulls-core");
    ScrollView side=new ScrollView(h);side.setFillViewport(true);side.setVerticalScrollBarEnabled(false);side.addView(info,new ScrollView.LayoutParams(-1,-2));content.addView(side,new LinearLayout.LayoutParams(0,-1,2));
    name=text("",26);name.setTag("owned-gulls-name");name.setTextColor(NavyWorkbenchTheme.GOLD);name.setMaxLines(3);info.addView(name,new LinearLayout.LayoutParams(-1,-2));
    vitals=text("",18);vitals.setTag("owned-gulls-vitals");vitals.setPadding(0,h.dp(brief?0:12),0,h.dp(brief?0:12));vitals.setMaxLines(3);info.addView(vitals,new LinearLayout.LayoutParams(-1,-2));
    identity=text("",12);identity.setTag("owned-gulls-identity");identity.setTextColor(NavyWorkbenchTheme.MUTED);identity.setMaxLines(2);identity.setVisibility(brief?View.GONE:View.VISIBLE);info.addView(identity,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout navigation=new LinearLayout(h);navigation.setTag("owned-gulls-navigation");LinearLayout.LayoutParams navigationBox=new LinearLayout.LayoutParams(-1,h.dp(48));navigationBox.topMargin=h.dp(brief?8:18);info.addView(navigation,navigationBox);
    previous=button("‹","owned-gulls-previous",()->step(-1));previous.setTextSize(28);previous.setContentDescription("내 갈매미 · 이전 개체");
    navigation.addView(previous,new LinearLayout.LayoutParams(h.dp(48),-1));
    next=button("›","owned-gulls-next",()->step(1));next.setTextSize(28);next.setContentDescription("내 갈매미 · 다음 개체");LinearLayout.LayoutParams nextBox=new LinearLayout.LayoutParams(h.dp(48),-1);nextBox.leftMargin=h.dp(10);navigation.addView(next,nextBox);
    notice=text("보유한 갈매미를 확인하고 있어요.",12);notice.setTag("owned-gulls-notice");notice.setMaxLines(2);notice.setTextColor(NavyWorkbenchTheme.MUTED);notice.setPadding(0,h.dp(12),0,0);notice.setVisibility(brief?View.GONE:View.VISIBLE);info.addView(notice,new LinearLayout.LayoutParams(-1,-2));
    portrait=new Portrait();portrait.setTag("owned-gulls-portrait");portrait.setScaleType(ImageView.ScaleType.FIT_CENTER);portrait.setPadding(0,h.dp(4),0,h.dp(4));content.addView(portrait,new LinearLayout.LayoutParams(0,-1,6));
    stats=button("능력치","owned-gulls-stats",()->showDetails(0));elements=button("속성","owned-gulls-elements",()->showDetails(1));skills=button("기술","owned-gulls-skills",()->showDetails(2));
    LinearLayout groups=new LinearLayout(h);groups.setOrientation(LinearLayout.VERTICAL);groups.setGravity(Gravity.CENTER_VERTICAL);groups.setPadding(h.dp(4),0,h.dp(12),0);groups.setTag("owned-gulls-groups");content.addView(groups,new LinearLayout.LayoutParams(0,-1,2));
    for(Button action:new Button[]{stats,elements,skills}){action.setTextSize(18);action.setTextColor(NavyWorkbenchTheme.TEXT);action.setBackground(NavyWorkbenchTheme.surface(h,0xc1111c26,0xff48545c,3));LinearLayout.LayoutParams box=new LinearLayout.LayoutParams(-1,h.dp(64));if(groups.getChildCount()>0)box.topMargin=h.dp(12);groups.addView(action,box);}
    position=text("",12);position.setTag("owned-gulls-position");position.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);position.setPadding(0,0,0,0);position.setMinHeight(h.dp(24));info.addView(position,info.indexOfChild(notice),new LinearLayout.LayoutParams(-1,-2));
    refresh();
  }
  Button button(String label,String tag,Runnable action){
    Button b=h.button(label,()->{if(current()&&!h.updateBlocks())action.run();});b.setTag(tag);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(NavyWorkbenchTheme.TEXT);
    b.setMinWidth(h.dp(48));b.setMinimumWidth(h.dp(48));b.setMinHeight(h.dp(48));b.setMinimumHeight(h.dp(48));b.setPadding(h.dp(8),0,h.dp(8),0);b.setBackgroundTintList(null);
    b.setBackground(NavyWorkbenchTheme.surface(h,NavyWorkbenchTheme.HEADER,NavyWorkbenchTheme.LINE,7));return b;
  }
  TextView text(String value,int size){TextView t=h.text(value,size);NavyWorkbenchTheme.text(h,t,size,false,NavyWorkbenchTheme.TEXT);return t;}
  void refresh(){
    if(!current()){invalidateAccount();return;}if(reading||modal!=null){refreshQueued=true;return;}
    reading=true;final int requested=++generation;details.clear();pending.clear();clearShown();notice.setText("보유한 갈매미의 최신 정보를 확인하고 있어요.");enable();
    h.api.call("native/roster60/owned",null,(result,error)->{
      if(!accept(requested))return;reading=false;if(refreshQueued){refreshQueued=false;refresh();return;}
      if(error!=null){notice.setText("내 갈매미를 불러오지 못했어요. 새로고침으로 다시 확인해 주세요.");enable();return;}
      JSONArray units=result==null?null:result.optJSONArray("units");
      if(result==null||!FLOW.equals(result.optString("flow"))||units==null){notice.setText("내 갈매미 서버 응답을 확인할 수 없어요.");enable();return;}
      catalog=result.optString("catalogVersion");entries.clear();HashSet<String> identities=new HashSet<>();
      for(int i=0;i<units.length();i++){JSONObject u=units.optJSONObject(i);if(u!=null&&u.opt("ownedId") instanceof String&&!u.optString("ownedId").isEmpty()&&identities.add(u.optString("ownedId")))entries.add(copy(u));}
      index=0;for(int i=0;i<entries.size();i++)if(selectedId.equals(entries.get(i).optString("ownedId"))){index=i;break;}
      if(entries.isEmpty()){selectedId="";notice.setText("아직 보유한 갈매미가 없어요.");position.setText("보유 0마리");enable();return;}
      select(index);
    });
  }
  boolean accept(int requested){if(!current()){invalidateAccount();return false;}return requested==generation;}
  void invalidateAccount(){
    if(owner.equals(h.api.token())&&h.epoch==epoch&&SCREEN.equals(h.screen))return;
    generation++;refreshQueued=false;details.clear();pending.clear();entries.clear();shown=null;
    if(portrait!=null){Roster60Portrait.clear(portrait);name.setText("");vitals.setText("");identity.setText("");notice.setText("계정이 바뀌었어요. 내 갈매미를 다시 열어 주세요.");notice.setVisibility(View.VISIBLE);position.setText("");}
    closeDetails();enable();
  }
  void step(int direction){if(!current()||modal!=null||reading||entries.isEmpty())return;int to=index+(direction<0?-1:1);if(to<0||to>=entries.size())return;select(to);}
  void select(int at){
    if(!current()||modal!=null||at<0||at>=entries.size())return;index=at;selectedId=entries.get(index).optString("ownedId");
    h.getSharedPreferences("roster60_owned_position",Context.MODE_PRIVATE).edit().putString(preference,selectedId).apply();
    clearShown();position.setText((index+1)+" / "+entries.size());position.setContentDescription((index+1)+" / "+entries.size()+" · 한 번 밀 때 한 마리씩");
    notice.setText("선택한 개체의 정보를 확인하고 있어요.");trim();enable();
    JSONObject ready=details.get(selectedId);if(ready!=null)bind(ready);else read(selectedId);
    prefetch();
  }
  void trim(){Iterator<String> it=details.keySet().iterator();while(it.hasNext())if(!near(it.next()))it.remove();}
  boolean near(String id){for(int i=Math.max(0,index-1);i<=Math.min(entries.size()-1,index+1);i++)if(id.equals(entries.get(i).optString("ownedId")))return true;return false;}
  void prefetch(){
    for(int i:new int[]{index-1,index+1})if(i>=0&&i<entries.size()){
      JSONObject neighbor=entries.get(i);read(neighbor.optString("ownedId"));
      try{JSONObject front=Roster60Portrait.front(h,neighbor.optString("speciesId"));if(front!=null){String file=front.optString("file");if(file.matches("u[0-9]{2}-front\\.webp"))LocalGameArt.load(h,"roster60-art/"+file,4,unused->{});}}catch(Exception unavailable){}
    }
  }
  void read(String id){
    if(id.isEmpty()||details.containsKey(id)||pending.containsKey(id)||!near(id))return;
    final int requested=generation;pending.put(id,requested);
    h.api.call("native/roster60/owned/read",MainActivity.obj("ownedId",id),(result,error)->{
      if(!accept(requested))return;if(pending.get(id)==null||pending.get(id)!=requested)return;pending.remove(id);
      if(!near(id))return;
      JSONObject unit=result==null?null:result.optJSONObject("unit");
      boolean valid=error==null&&result!=null&&FLOW.equals(result.optString("flow"))&&catalog.equals(result.optString("catalogVersion"))&&unit!=null&&id.equals(unit.optString("ownedId"))&&unit.optJSONObject("stats")!=null;
      if(!valid){if(id.equals(selectedId)){clearShown();notice.setText("선택한 개체의 정보를 확인하지 못했어요. 새로고침해 주세요.");enable();}return;}
      JSONObject exact=copy(unit);details.put(id,exact);trim();if(id.equals(selectedId))bind(exact);
    });
  }
  void clearShown(){shown=null;if(portrait==null)return;notice.setVisibility(View.VISIBLE);Roster60Portrait.clear(portrait);portrait.setContentDescription("내 갈매미 · 선택한 개체 정보 확인 중");name.setText("");vitals.setText("");identity.setText("");}
  void bind(JSONObject unit){
    if(!current()||!selectedId.equals(unit.optString("ownedId"))||modal!=null)return;
    shown=unit;name.setText(unit.optString("name","갈매미")+"\nLv."+value(unit,"level"));
    JSONObject s=unit.optJSONObject("stats");vitals.setText("HP "+value(s,"hp")+"\nMP "+value(s,"mp"));
    String party=partyText(unit);identity.setText("보유 개체 "+(index+1)+" · "+party+(unit.optBoolean("locked")?" · 잠금":""));
    portrait.setContentDescription("내 갈매미 · "+unit.optString("name")+" · 보유 개체 "+(index+1)+" · Lv."+value(unit,"level"));
    Roster60Portrait.bind(h,portrait,unit.optString("speciesId"));
    notice.setText("개체별 최신 정보");notice.setVisibility(compactInfo?View.GONE:View.VISIBLE);enable();
  }
  void enable(){
    if(previous==null)return;boolean active=current()&&modal==null;
    previous.setEnabled(active&&!reading&&index>0&&!entries.isEmpty());next.setEnabled(active&&!reading&&index+1<entries.size());refresh.setEnabled(active&&!reading);
    for(Button b:new Button[]{stats,elements,skills})b.setEnabled(active&&shown!=null);
    portrait.setEnabled(active&&!entries.isEmpty());
  }
  void showDetails(int tab){
    if(!current()||modal!=null||shown==null)return;final String exactId=selectedId;final JSONObject snapshot=copy(shown);
    String label=tab==0?"능력치":tab==1?"속성":"기술";
    TextView body=text((tab==0?rarityText(snapshot)+"\n"+partyText(snapshot)+(snapshot.optBoolean("locked")?" · 잠금":"")+"\n\n":"")+detailText(snapshot,tab),16);body.setTag("owned-gulls-detail-body");body.setPadding(h.dp(14),h.dp(10),h.dp(14),h.dp(10));
    ScrollView scroll=new ScrollView(h);scroll.setTag("owned-gulls-detail-scroll");scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
    modal=new GameDialog(h).navy().setTitle(snapshot.optString("name")+" · "+label).setView(scroll).setPositiveButton("닫기",(d,w)->closeDetails()).show();
    modal.getWindow().getDecorView().setTag("owned-gulls-modal-"+exactId);
    final AlertDialog opened=modal;
    modal.setOnDismissListener(d->{if(h.visibleDialog==opened)h.visibleDialog=null;if(modal==opened)modal=null;if(current()&&exactId.equals(selectedId)){enable();refreshAfterDetails();}});enable();
    modal.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->closeDetails());
    modal.setOnKeyListener((d,key,event)->{if(key==android.view.KeyEvent.KEYCODE_BACK){if(event.getAction()==android.view.KeyEvent.ACTION_UP&&!event.isCanceled())closeDetails();return true;}return false;});
  }
  static JSONObject copy(JSONObject value){try{return new JSONObject(value.toString());}catch(Exception impossible){throw new IllegalArgumentException(impossible);}}
  static String value(JSONObject value,String key){Object raw=value==null?null:value.opt(key);return raw==null||raw==JSONObject.NULL?"정보 없음":String.valueOf(raw);}
  static String partyText(JSONObject unit){JSONObject party=unit.optJSONObject("party"),pvp=party==null?null:party.optJSONObject("pvp");if(pvp==null||!pvp.has("position"))return "편성 정보 없음";return pvp.isNull("position")?"편성되지 않음":"편성 "+value(pvp,"position")+"번";}
  static String rarityText(JSONObject unit){Object value=unit==null?null:unit.opt("rarity");if(!(value instanceof String)||((String)value).isEmpty())return "등급 확인 필요";String code=(String)value,label=Roster60RecruitPurchase.gradeName(code);return label.equals(code)?"등급 확인 필요":"등급 "+label;}
  static String detailText(JSONObject unit,int tab){
    if(tab==0){
      StringBuilder t=new StringBuilder("현재 최종 능력치\n");JSONObject total=unit.optJSONObject("stats");
      for(int i=0;i<STATS.length;i++)t.append(STAT_NAMES[i]).append(' ').append(value(total,STATS[i])).append(i%2==0?"  ":"\n");
      JSONObject g=unit.optJSONObject("growth");boolean maximum=g!=null&&g.has("xpToNext")&&g.isNull("xpToNext")&&unit.optInt("level",-1)==unit.optInt("maxLevel",-2);t.append("\n\nLv.").append(value(unit,"level")).append(" / ").append(value(unit,"maxLevel")).append(" · 환생 ").append(value(unit,"reincarnation")).append("회\n누적 XP ").append(value(g,"totalXp")).append("\n").append(maximum?"최대 레벨":"다음 레벨까지 "+value(g,"xpToNext"));
      JSONObject breakdown=unit.optJSONObject("statBreakdown");
      for(String part:new String[]{"base","growth","rebirth"}){String label=part.equals("base")?"기본":part.equals("growth")?"레벨 성장":"환생 보정";t.append("\n\n").append(label).append('\n');JSONObject values=breakdown==null?null:breakdown.optJSONObject(part);for(int i=0;i<STATS.length;i++)t.append(STAT_NAMES[i]).append(' ').append(value(values,STATS[i])).append(i%2==0?"  ":"\n");}
      t.append("\n\n기타 보정: ").append(breakdown!=null&&"NO_OTHER_BONUS_MODEL".equals(breakdown.optString("otherStatus"))?"해당 보정 체계 없음":"정보 없음");return t.toString();
    }
    if(tab==1){StringBuilder t=new StringBuilder("속성별 내성 · 내 개체의 실제 값\n");JSONObject resist=unit.optJSONObject("ownResistance"),aptitude=unit.optJSONObject("aptitude");for(int i=0;i<ELEMENTS.length;i++)t.append(ELEMENT_NAMES[i]).append(" · ").append(resistance(value(resist,ELEMENTS[i]))).append("\n적합도 ").append(value(aptitude,ELEMENTS[i])).append("\n\n");return t.toString();}
    JSONObject skills=unit.optJSONObject("skills");JSONArray slots=skills==null?null:skills.optJSONArray("slots");StringBuilder t=new StringBuilder("기술은 유닛 레벨에 따라 해금돼요.\n현재 유닛 Lv.").append(value(unit,"level")).append(slots==null?"\n\n사용 가능한 기술\n":"\n\n현재 기술 슬롯 · 해금 상태\n");HashSet<String> ids=new HashSet<>();JSONArray list=skills==null?null:skills.optJSONArray("current");JSONObject attack=skills==null?null:skills.optJSONObject("attack");if(attack!=null){if(slots!=null)t.append("일반 공격\n");appendSkill(t,attack,true);ids.add(attack.optString("id"));}
    if(slots!=null){for(int i=0;i<slots.length();i++){JSONObject skill=slots.optJSONObject(i);if(skill!=null&&ids.add(skill.optString("id")))appendSkill(t,skill,skill.opt("unlocked") instanceof Boolean?skill.optBoolean("unlocked")?"사용 가능":"미해금":"해금 상태 정보 없음");}}
    else for(int i=0;list!=null&&i<list.length();i++){JSONObject skill=list.optJSONObject(i);if(skill!=null&&ids.add(skill.optString("id")))appendSkill(t,skill,true);}
    if(ids.isEmpty())t.append("제공된 기술 정보가 없어요.\n");JSONObject next=skills==null?null:skills.optJSONObject("nextLearn");
    if(next!=null){t.append("\n다음 해금 조건 · 유닛 Lv.").append(value(next,"level")).append('\n');if(slots==null){JSONArray upcoming=next.optJSONArray("skills");for(int i=0;upcoming!=null&&i<upcoming.length();i++){JSONObject skill=upcoming.optJSONObject(i);if(skill!=null)appendSkill(t,skill,false);}}}
    else t.append("\n추가 해금 정보 없음\n");
    return t.toString();
  }
  static void appendSkill(StringBuilder out,JSONObject skill,boolean available){appendSkill(out,skill,available?"사용 가능":"미해금");}
  static void appendSkill(StringBuilder out,JSONObject skill,String availability){if(skill.optBoolean("inherited"))out.append("계승 기술 · ");out.append(skill.optString("name","기술")).append(" · MP ").append(value(skill,"mpCost"));String element=Roster60SkillElements.elementName(skill.optString("element"));if(!element.isEmpty())out.append(" · ").append(element);out.append("\n");if(skill.has("learnLevel")&&!skill.isNull("learnLevel"))out.append("유닛 Lv.").append(value(skill,"learnLevel")).append(" 해금");else out.append("해금 레벨 정보 없음");out.append(" · ").append(availability).append("\n").append(skill.optString("effectText","효과 정보 없음")).append("\n\n");}
  static String resistance(String value){switch(value){case "weak":return "약점";case "normal":return "보통";case "resist":return "내성";case "immune":return "무효";case "reflect":return "반사";default:return value;}}
  void refreshAfterDetails(){if(refreshQueued&&current()&&modal==null&&!reading){refreshQueued=false;refresh();}}
  void closeDetails(){AlertDialog opened=modal;modal=null;if(opened!=null){if(h.visibleDialog==opened)h.visibleDialog=null;opened.dismiss();}enable();refreshAfterDetails();}
  boolean back(){if(modal!=null){closeDetails();return true;}pause();h.home();return true;}
  void pause(){paused=true;generation++;reading=false;refreshQueued=false;details.clear();pending.clear();closeDetails();clearShown();}
  void resume(){if(!owner.equals(h.api.token())){invalidateAccount();return;}if(h.epoch!=epoch||!SCREEN.equals(h.screen))return;paused=false;refresh();}

  final class Portrait extends androidx.appcompat.widget.AppCompatImageView {
    float downX,downY;boolean tracking,multiple;
    Portrait(){super(h);setClickable(true);setFocusable(true);}
    @Override public boolean onTouchEvent(MotionEvent event){
      if(!current()||modal!=null||!isEnabled())return false;
      int action=event.getActionMasked();
      if(action==MotionEvent.ACTION_DOWN){tracking=true;multiple=false;downX=event.getX();downY=event.getY();return true;}
      if(action==MotionEvent.ACTION_POINTER_DOWN){multiple=true;return true;}
      if(action==MotionEvent.ACTION_CANCEL){tracking=false;return true;}
      if(action==MotionEvent.ACTION_MOVE){if(Math.abs(event.getY()-downY)>Math.max(h.dp(24),Math.abs(event.getX()-downX)))tracking=false;return true;}
      if(action==MotionEvent.ACTION_UP){boolean move=tracking&&!multiple;tracking=false;float dx=event.getX()-downX,dy=event.getY()-downY;int distance=Math.max(h.dp(40),ViewConfiguration.get(h).getScaledTouchSlop()*2);if(move&&Math.abs(dx)>distance&&Math.abs(dx)>Math.abs(dy)*1.25f)step(dx<0?1:-1);else performClick();return true;}
      return tracking;
    }
    @Override public boolean performClick(){return super.performClick();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(!focused)tracking=false;}
    @Override protected void onDetachedFromWindow(){tracking=false;super.onDetachedFromWindow();}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setScrollable(entries.size()>1&&modal==null);if(modal==null){if(index+1<entries.size())info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);if(index>0)info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);}}
    @Override public boolean performAccessibilityAction(int action,Bundle args){if(current()&&modal==null){if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD){step(1);return true;}if(action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){step(-1);return true;}}return super.performAccessibilityAction(action,args);}
  }
}
