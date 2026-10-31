package org.dpdns.gmm;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only directions from the existing story snapshot. Following a target never executes it. */
final class NativeVillageQuestTracker extends LinearLayout {
  interface Host { boolean current(); void focus(String targetKey); }
  final MainActivity h;
  final Host host;
  final SharedPreferences preferences;
  final String key;
  final Button toggle,select;
  final LinearLayout header;
  final ScrollView scroll;
  final LinearLayout list;
  final TextView notice;
  final List<String> selected=new ArrayList<>();
  final Map<String,View> cards=new LinkedHashMap<>();
  final Map<String,JSONObject> eligible=new LinkedHashMap<>();
  JSONObject snapshot,main;
  AlertDialog pinDialog;
  NativeVillageHomeView village;
  boolean expanded,initialized;
  int generation;

  NativeVillageQuestTracker(NativeVillageHomeView village){
    this(village.h,new Host(){public boolean current(){return village.owner.current();}public void focus(String key){NativeVillageQuestTracker.focus(village,key);}},village.owner.preferences,village.owner.scope+":quest-tracker");
    this.village=village;
  }
  NativeVillageQuestTracker(MainActivity h,Host host,SharedPreferences preferences,String scope){
    super(h);this.h=h;this.host=host;this.preferences=preferences;key=scope;
    setTag("village-quest-tracker");setOrientation(VERTICAL);setPadding(0,h.dp(4),0,h.dp(4));
    expanded=preferences.getBoolean(key+":expanded",false);initialized=preferences.contains(key+":selected");
    try{JSONArray saved=new JSONArray(preferences.getString(key+":selected","[]"));for(int i=0;i<saved.length()&&selected.size()<2;i++){String id=saved.optString(i);if(!id.isEmpty()&&!selected.contains(id))selected.add(id);}}catch(Exception invalid){selected.clear();initialized=false;}
    header=new LinearLayout(h);header.setOrientation(HORIZONTAL);header.setBackground(NavyWorkbenchTheme.surface(h,0xf2102433,NavyWorkbenchTheme.LINE,10));
    LayoutParams headerBox=new LayoutParams(h.dp(176),h.dp(48));headerBox.gravity=Gravity.RIGHT;addView(header,headerBox);
    toggle=h.button("목표 ▴",this::toggle);toggle.setTag("quest-tracker-toggle");NativeVillageShortcuts.flatButton(h,toggle);header.addView(toggle,new LayoutParams(0,-1,1));
    select=h.button("추적할 부탁",this::chooseResidents);select.setTag("quest-tracker-select-residents");NativeVillageShortcuts.flatButton(h,select);select.setTextSize(h.uiText(14));
    list=new LinearLayout(h);list.setOrientation(VERTICAL);list.setTag("quest-tracker-list");
    scroll=new ScrollView(h);scroll.setFillViewport(false);scroll.setBackground(NavyWorkbenchTheme.surface(h,0xf2102433,NavyWorkbenchTheme.LINE,10));scroll.addView(list,new ScrollView.LayoutParams(-1,-2));addView(scroll,new LayoutParams(-1,0,1));
    notice=label("목표를 확인하고 있어요.",12);notice.setTag("quest-tracker-notice");render();
  }
  TextView label(String text,int size){TextView value=h.text(text,size);value.setTextColor(NavyWorkbenchTheme.TEXT);value.setIncludeFontPadding(false);value.setPadding(0,h.dp(1),0,h.dp(1));return value;}
  void bindStoryState(JSONObject state,String error){
    generation++;
    if(pinDialog!=null){pinDialog.dismiss();pinDialog=null;}
    snapshot=error==null?NativeVillageHomeController.copyStory(state):null;main=null;eligible.clear();
    if(snapshot!=null){
      JSONObject current=snapshot.optJSONObject("currentQuest");if(current!=null&&"main".equals(current.optString("kind"))&&!"COMPLETED".equals(current.optString("status")))main=current;
      JSONArray quests=snapshot.optJSONArray("quests");
      for(int i=0;quests!=null&&i<quests.length();i++){
        JSONObject quest=quests.optJSONObject(i);if(quest==null||!"resident".equals(quest.optString("kind"))||!Arrays.asList("ACTIVE","READY").contains(quest.optString("status")))continue;
        String id=NativeRoster60StoryController.reference(quest,"id");if(!id.isEmpty())eligible.putIfAbsent(id,quest);
      }
      if(!initialized&&!eligible.isEmpty()){selected.clear();for(String id:eligible.keySet()){if(selected.size()==2)break;selected.add(id);}initialized=true;persistSelection();}
      else if(initialized){boolean changed=selected.removeIf(id->!eligible.containsKey(id));if(changed)persistSelection();}
      notice.setText(main==null&&selected.isEmpty()?"진행 중인 목표가 없어요.":"");
    }else notice.setText(error==null?"목표를 확인하고 있어요.":"목표 상태를 불러오지 못했어요.");
    render();
  }
  void persistSelection(){JSONArray ids=new JSONArray();for(String id:selected)ids.put(id);preferences.edit().putString(key+":selected",ids.toString()).apply();}
  boolean setPinned(List<String> ids){
    if(!host.current()||snapshot==null||ids==null||ids.size()>2)return false;
    LinkedHashSet<String> distinct=new LinkedHashSet<>();for(String id:ids)if(id==null||!eligible.containsKey(id)||!distinct.add(id))return false;
    selected.clear();selected.addAll(distinct);initialized=true;persistSelection();render();return true;
  }
  void chooseResidents(){
    if(!host.current()||snapshot==null||h.busy||h.updateBlocks()||eligible.isEmpty())return;
    if(pinDialog!=null)pinDialog.dismiss();
    List<String> ids=new ArrayList<>(eligible.keySet());String[] labels=new String[ids.size()];boolean[] checked=new boolean[ids.size()];final int ticket=generation;
    for(int i=0;i<ids.size();i++){JSONObject quest=eligible.get(ids.get(i));labels[i]=identity(snapshot,quest)+" · "+quest.optString("title","주민 부탁");checked[i]=selected.contains(ids.get(i));}
    AlertDialog showing=new GameDialog(h).navy().setTitle("주민 부탁 추적 · 최대 2개").setMultiChoiceItems(labels,checked,(dialog,index,on)->{
      ListView choices=((AlertDialog)dialog).getListView();
      if(on&&choices.getCheckedItemCount()>2)choices.setItemChecked(index,false);
    }).setPositiveButton("선택 저장",null).setNegativeButton("취소",null).show();
    showing.setCanceledOnTouchOutside(true);
    pinDialog=showing;
    showing.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->{
      if(ticket!=generation||!host.current())return;
      List<String> draft=new ArrayList<>();ListView choices=showing.getListView();
      for(int i=0;i<ids.size();i++)if(choices.isItemChecked(i))draft.add(ids.get(i));
      if(!setPinned(draft))return;
      showing.dismiss();if(h.visibleDialog==showing)h.visibleDialog=null;if(pinDialog==showing)pinDialog=null;
    });
    showing.setOnDismissListener(dialog->{if(h.visibleDialog==showing)h.visibleDialog=null;if(pinDialog==showing)pinDialog=null;});
  }
  void toggle(){if(!host.current()||h.busy||h.updateBlocks())return;expanded=!expanded;if(expanded&&village!=null)village.shortcuts.collapse();preferences.edit().putBoolean(key+":expanded",expanded).apply();render();}
  void collapse(){if(expanded){expanded=false;preferences.edit().putBoolean(key+":expanded",false).apply();render();}}
  void render(){
    if(list==null)return;list.removeAllViews();cards.clear();
    list.addView(select,new LayoutParams(-1,h.dp(48)));
    if(main!=null)addQuest(main);
    for(String id:selected){JSONObject quest=eligible.get(id);if(quest!=null)addQuest(quest);}
    if(cards.isEmpty())list.addView(notice,new LayoutParams(-1,-2));
    scroll.setVisibility(expanded?VISIBLE:GONE);select.setEnabled(snapshot!=null&&!eligible.isEmpty());
    toggle.setText("목표 "+cards.size()+(expanded?" ▴":" ▾"));toggle.setContentDescription("퀘스트 목표 "+(expanded?"접기":"펼치기")+" · "+cards.size()+"개");requestLayout();
  }
  void addQuest(JSONObject quest){
    final int ticket=generation;
    String id=quest.optString("id"),destination=target(snapshot,quest),identity=identity(snapshot,quest),direction=action(snapshot,quest),progress=progress(quest);
    LinearLayout card=new LinearLayout(h);card.setOrientation(HORIZONTAL);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(h.dp(6),h.dp(5),h.dp(6),h.dp(5));card.setMinimumHeight(h.dp(76));
    card.setTag("quest-tracker-"+id);card.setBackground(NavyWorkbenchTheme.surface(h,NavyWorkbenchTheme.HEADER,NavyWorkbenchTheme.LINE,6));
    View portrait;JSONObject requester=requester(snapshot,quest);
    if(requester!=null){String npc=requester.optString("id");VillageLayout.Asset asset=null;try{asset=VillageLayout.load(h).assets.get("guard".equals(npc)?"patrol":npc);}catch(IllegalStateException unavailable){}
      portrait=new NativeRoster60StoryView.ResidentPortrait(h,asset,host::current);portrait.setContentDescription(identity+" 초상화");
    }else{TextView icon=label("main".equals(quest.optString("kind"))?"◆":"주민",18);icon.setGravity(Gravity.CENTER);portrait=icon;}
    portrait.setTag("quest-tracker-portrait-"+id);card.addView(portrait,new LayoutParams(h.dp(48),h.dp(64)));
    LinearLayout text=new LinearLayout(h);text.setOrientation(VERTICAL);text.setPadding(h.dp(6),0,0,0);card.addView(text,new LayoutParams(0,-2,1));
    for(TextView line:new TextView[]{label(identity,11),label(quest.optString("title","이야기 목표"),13),label(direction,12),label(progress,11)}){line.setSingleLine(true);line.setEllipsize(android.text.TextUtils.TruncateAt.END);text.addView(line,new LayoutParams(-1,-2));}
    card.setContentDescription(identity+" · "+quest.optString("title")+" · "+direction+" · "+progress);card.setEnabled(!destination.isEmpty());card.setFocusable(!destination.isEmpty());card.setClickable(!destination.isEmpty());
    card.setOnClickListener(v->{if(ticket==generation&&snapshot!=null&&host.current()&&!h.busy&&!h.updateBlocks()&&!destination.isEmpty()){h.sound.click();host.focus(destination);}});
    LayoutParams box=new LayoutParams(-1,-2);box.topMargin=h.dp(4);list.addView(card,box);cards.put(id,card);
  }
  static JSONObject requester(JSONObject state,JSONObject quest){return NativeVillageStoryOverlay.requester(state,quest);}
  static String identity(JSONObject state,JSONObject quest){
    if("main".equals(quest.optString("kind")))return "메인 이야기";
    JSONObject resident=requester(state,quest);if(resident==null)return "주민 부탁";
    String name=resident.optString("name","주민"),role=resident.optString("role");return role.isEmpty()||role.equals(name)?name:name+" · "+role;
  }
  static String target(JSONObject state,JSONObject quest){
    if(state==null||quest==null)return "";
    if("main".equals(quest.optString("kind"))){String target=NativeVillageHomeController.mainStoryTarget(state);return "boat".equals(target)?"building:harbor":target;}
    if(!"resident".equals(quest.optString("kind")))return "";
    JSONObject resident=requester(state,quest);String npc=resident==null?"":resident.optString("id");
    if("READY".equals(quest.optString("status")))return npc.isEmpty()?"":"resident:"+npc;
    if(!"ACTIVE".equals(quest.optString("status")))return "";
    JSONObject scene=state.optJSONObject("scene");if(scene!=null&&quest.optString("id").equals(scene.optString("questId"))&&Arrays.asList("intro","return").contains(scene.optString("phase")))return npc.isEmpty()?"":"resident:"+npc;
    return quest.optBoolean("canDepart")||!NativeRoster60StoryController.reference(quest,"battleId").isEmpty()||!NativeRoster60StoryController.reference(quest,"adventureId").isEmpty()?"building:harbor":"";
  }
  static String action(JSONObject state,JSONObject quest){
    String target=target(state,quest),status=quest.optString("status");
    if("building:harbor".equals(target))return !NativeRoster60StoryController.reference(quest,"battleId").isEmpty()?"선착장에서 전투 이어가기":!NativeRoster60StoryController.reference(quest,"adventureId").isEmpty()?"선착장에서 모험 귀환·진행 확인":"선착장에서 출항 준비";
    if("statue".equals(target))return "READY".equals(status)?"석상에 돌아가 이야기 마치기":"석상에서 이야기 듣기";
    if(target.startsWith("resident:"))return "READY".equals(status)?"주민에게 돌아가 보고하기":"주민에게 이야기 확인하기";
    return "다음 행동 상태 확인 필요";
  }
  static String progress(JSONObject quest){
    JSONObject objective=quest==null?null:quest.optJSONObject("objective");if(objective==null)return "진행 상황 확인 필요";
    if("scene".equals(objective.optString("kind")))return "대화 목표";
    Object wins=objective.opt("wins"),required=objective.opt("requiredWins");
    if(!"adventure_win".equals(objective.optString("kind"))||!whole(wins)||!whole(required)||((Number)required).longValue()<1)return "진행 상황 확인 필요";
    return "승리 "+((Number)wins).longValue()+" / "+((Number)required).longValue();
  }
  static boolean whole(Object value){if(!(value instanceof Number))return false;double n=((Number)value).doubleValue();return Double.isFinite(n)&&n>=0&&n==Math.rint(n)&&n<=Integer.MAX_VALUE;}
  RectF panelBounds(RectF safe){return panelBounds(h,safe,expanded);}
  @Override protected void onMeasure(int widthSpec,int heightSpec){
    LayoutParams box=(LayoutParams)header.getLayoutParams();box.width=Math.min(h.dp(176),Math.max(1,MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight()));
    super.onMeasure(widthSpec,heightSpec);
  }
  static RectF panelBounds(MainActivity h,RectF safe,boolean expanded){
    float right=safe.right-h.dp(8),top=safe.top+h.dp(64),width=Math.min(h.dp(expanded?304:176),Math.max(h.dp(48),safe.width()-h.dp(16)));
    float height=expanded?Math.min(h.dp(296),Math.max(h.dp(56),safe.bottom-h.dp(8)-top)):h.dp(56);
    return new RectF(right-width,top,right,Math.min(safe.bottom-h.dp(8),top+height));
  }
  boolean contains(float x,float y){
    if(getVisibility()!=VISIBLE)return false;
    float localX=x-getLeft(),localY=y-getTop();
    return inside(header,localX,localY)||expanded&&inside(scroll,localX,localY);
  }
  static boolean inside(View child,float x,float y){return child.getVisibility()==VISIBLE&&x>=child.getLeft()&&x<child.getRight()&&y>=child.getTop()&&y<child.getBottom();}
  void setHomeVisible(boolean visible){setVisibility(visible?VISIBLE:INVISIBLE);if(!visible){if(pinDialog!=null)pinDialog.dismiss();if(village!=null&&(!host.current()||village.storyOverlayCoversHud()))clearHighlight(village);}}
  @Override protected void onDetachedFromWindow(){if(pinDialog!=null)pinDialog.dismiss();if(village!=null)clearHighlight(village);super.onDetachedFromWindow();}

  static final Map<NativeVillageHomeView,WeakReference<FocusHighlight>> highlights=new WeakHashMap<>();
  static boolean focus(NativeVillageHomeView village,String key){
    if(village==null||!village.owner.current()||village.h.busy||village.h.updateBlocks()||!village.sceneReady())return false;
    Button button;double x;
    if("statue".equals(key)){if(village.layout.landmark==null||village.owner.statueStage<1)return false;button=village.statue;x=village.layout.landmark.x;}
    else if(key.startsWith("building:")){VillageLayout.Building building=village.layout.building(key.substring(9));if(building==null)return false;button=village.buildingButtons.get(building.id);x=building.x;}
    else if(key.startsWith("resident:")){VillageLayout.Resident resident=village.layout.resident(key.substring(9));if(resident==null)return false;button=village.residentButtons.get(resident.id);x=resident.x;}
    else return false;
    village.stopMotion();village.moved=false;village.stoppingFling=false;village.animateTo(village.camera.nearestEquivalent(x-village.field.width()/village.unitPixels()/2));
    clearHighlight(village);FocusHighlight highlight=new FocusHighlight(village,button);highlights.put(village,new WeakReference<>(highlight));village.getOverlay().add(highlight);highlight.setBounds(0,0,village.getWidth(),village.getHeight());village.postDelayed(highlight.retire,2400);return true;
  }
  static void clearHighlight(NativeVillageHomeView village){WeakReference<FocusHighlight> reference=highlights.remove(village);FocusHighlight old=reference==null?null:reference.get();if(old!=null){village.removeCallbacks(old.retire);village.getOverlay().remove(old);}}
  static final class FocusHighlight extends Drawable {
    final NativeVillageHomeView village;final Button button;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    final Runnable retire;
    FocusHighlight(NativeVillageHomeView village,Button button){this.village=village;this.button=button;retire=()->clearHighlight(village);}
    @Override public void draw(Canvas canvas){
      if(!village.owner.current()||!village.sceneReady()||button.getVisibility()!=View.VISIBLE)return;
      RectF world=button==village.statue?village.landmarkRect():village.imageSlot(button);RectF target=village.screenRect(world);if(!RectF.intersects(target,village.field))return;
      int saved=canvas.save();canvas.clipRect(village.field);target.inset(-village.h.dp(4),-village.h.dp(4));paint.setColor(NavyWorkbenchTheme.GOLD_EDGE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(village.h.dp(3));canvas.drawRoundRect(target,village.h.dp(10),village.h.dp(10),paint);canvas.restoreToCount(saved);
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
  }
}
