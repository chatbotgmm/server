package org.dpdns.gmm;
import android.view.*;import android.widget.*;import org.json.*;import java.util.*;import java.util.function.Consumer;
/** Product party selection before a server battle; no fixture, local combat or grant. */
final class Roster60FormationView extends LinearLayout {
 final MainActivity h;final JSONArray units;final Consumer<JSONArray> confirm;final ArrayList<String> selected=new ArrayList<>();
 final LinearLayout left,right,slots;final ImageView focus;final TextView caption,title,level,selectedHeading,ownedHeading,indicator;final Button start;
 final Roster60CardStrip ownedStrip;final LinkedHashMap<String,Roster60MemberTile> ownedTiles=new LinkedHashMap<>();
 final String[] slotContent=new String[6],slotState=new String[6];final HashMap<String,String> ownedState=new HashMap<>();
 String focused="",focusSpecies="",renderedState="";boolean locked;float scale=1,renderedScale=-1;
 long slotReplacements,focusBindings,tileStateUpdates;
 Roster60FormationView(MainActivity h,JSONArray units,Consumer<JSONArray> confirm,Runnable cancel){
  super(h);this.h=h;this.units=units;this.confirm=confirm;setOrientation(HORIZONTAL);setTag("roster60-formation");setBackground(ThemeArt.of(h).background("backgrounds/bg-navigation-workshop-v1.png"));
  left=new LinearLayout(h);left.setOrientation(VERTICAL);left.setBackground(NavyWorkbenchTheme.surface(h,NavyWorkbenchTheme.PANEL,NavyWorkbenchTheme.LINE,8));addView(left,new LayoutParams(0,-1,3));
  right=new LinearLayout(h);right.setOrientation(VERTICAL);right.setBackground(NavyWorkbenchTheme.surface(h,NavyWorkbenchTheme.PANEL,NavyWorkbenchTheme.LINE,8));addView(right,new LayoutParams(0,-1,1));
  selectedHeading=section("출전 1명 + 예비 5명");left.addView(selectedHeading);
  slots=new LinearLayout(h);slots.setGravity(Gravity.CENTER_VERTICAL);slots.setTag("formation-current-slots");left.addView(slots,new LayoutParams(-1,0,1));
  ownedHeading=section("보유 대원 "+units.length()+"명");left.addView(ownedHeading);
  ownedStrip=new Roster60CardStrip(h);ownedStrip.setTag("formation-owned-swipe");ownedStrip.setContentDescription("전투 편성 보유 대원 · 좌우로 연속 스크롤");left.addView(ownedStrip,new LayoutParams(-1,0,1));
  indicator=h.text("보유 "+units.length()+"명 · 좌우로 밀어보기",11);CombatV2ReviewStyle.text(indicator);indicator.setTextColor(NavyWorkbenchTheme.MUTED);indicator.setGravity(Gravity.CENTER);indicator.setTag("formation-owned-count");left.addView(indicator,new LayoutParams(-1,-2));
  title=h.text("대원을 선택하세요",16);CombatV2ReviewStyle.text(title);title.setTextColor(NavyWorkbenchTheme.TEXT);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);right.addView(title);level=h.text("출전 1 + 예비 5",12);CombatV2ReviewStyle.text(level);level.setTextColor(NavyWorkbenchTheme.MUTED);right.addView(level);
  focus=new ImageView(h);focus.setTag("formation-focused-portrait");right.addView(focus,new LayoutParams(-1,0,1));caption=h.text("출전 1 + 예비 최대 5",12);CombatV2ReviewStyle.text(caption);caption.setTextColor(NavyWorkbenchTheme.TEXT);right.addView(caption);
  start=h.button("편성 확정 · 전투 시작",this::submit);start.setTag("formation-confirm");start.setTextSize(12);start.setBackground(new BattleSkin(h,"button-surrender"));CombatV2ReviewStyle.text(start);start.setTextColor(JournalTheme.INK);right.addView(start,new LayoutParams(-1,h.dp(48)));
  if(units.length()>0)focused=units.optJSONObject(0).optString("ownedId");draw();
 }
 @Override protected void onMeasure(int w,int hs){
  float s=Roster60Scale.of(this,MeasureSpec.getSize(w),MeasureSpec.getSize(hs),760,260);
  if(MeasureSpec.getMode(w)==MeasureSpec.EXACTLY&&MeasureSpec.getMode(hs)==MeasureSpec.EXACTLY&&MeasureSpec.getSize(w)>0&&MeasureSpec.getSize(hs)>0&&Math.abs(s-scale)>.01f){scale=s;draw();}
  super.onMeasure(w,hs);
 }
 static String grade(String grade){switch(grade){case "COMMON":return "커먼";case "UNCOMMON":return "언커먼";case "RARE":return "레어";case "EPIC":return "에픽";case "LEGEND":return "전설";case "HIDDEN":return "히든";case "LIMITED":return "제한";case "TRANSCENDENT":return "초월";case "IMMORTAL":return "불멸";case "ETERNAL":return "영원";default:return grade;}}
 static String memberDetails(JSONObject u){String text=u.optString("name")+(u.has("level")?" · Lv"+u.optInt("level")+"/"+u.optInt("maxLevel",60):"");JSONObject stats=u.optJSONObject("stats");if(stats!=null)text+="\nHP "+stats.optInt("hp")+" · MP "+stats.optInt("mp")+"\n내구 "+stats.optInt("durability")+" · 저항 "+stats.optInt("statusResistance")+" · 속도 "+stats.optInt("speed");return text;}
 JSONObject unit(String id){for(int i=0;i<units.length();i++)if(id.equals(units.optJSONObject(i).optString("ownedId")))return units.optJSONObject(i);return null;}
 void toggle(JSONObject u){if(locked||u==null||ownedStrip.blocksClick())return;String id=u.optString("ownedId");focused=id;if(selected.contains(id))selected.remove(id);else if(selected.size()<6)selected.add(id);draw();}
 void submit(){if(locked||selected.isEmpty()||ownedStrip.blocksClick())return;locked=true;draw();confirm.accept(new JSONArray(selected));}
 void unlock(){locked=false;draw();}
 static String role(int slot){return slot==0?"출전":"예비 "+slot;}
 TextView section(String text){TextView v=h.text(text,12);CombatV2ReviewStyle.text(v);v.setTextColor(NavyWorkbenchTheme.TEXT);v.setSingleLine(true);return v;}
 void draw(){
  boolean scaleChanged=renderedScale!=scale;
  if(scaleChanged){
   renderedScale=scale;renderedState="";Arrays.fill(slotContent,null);Arrays.fill(slotState,null);ownedState.clear();
   for(TextView heading:new TextView[]{selectedHeading,ownedHeading}){heading.setTextSize(12*scale);heading.setPadding(h.dp(8*scale),h.dp(4*scale),h.dp(8*scale),0);heading.setVisibility(scale>=1.25f?VISIBLE:GONE);}
   indicator.setTextSize(11*scale);indicator.setPadding(0,h.dp(3*scale),0,h.dp(3*scale));indicator.setMinHeight(h.dp(22*scale));
   title.setTextSize(16*scale);level.setTextSize(12*scale);caption.setTextSize(12*scale);start.setTextSize(12*scale);start.getLayoutParams().height=h.dp(48*scale);
   ArrayList<String> ids=new ArrayList<>();ArrayList<Roster60MemberTile> tiles=new ArrayList<>();ownedTiles.clear();
   for(int i=0;i<units.length();i++){
    JSONObject u=units.optJSONObject(i);String id=u.optString("ownedId");
    Roster60MemberTile tile=new Roster60MemberTile(h,u,u.optString("name"),!locked,()->toggle(u),null,false,scale,1.15f,true);
    tile.action.setTag("formation-unit-"+id);ids.add(id);tiles.add(tile);ownedTiles.put(id,tile);
   }
   ownedStrip.replace(ids,tiles);
  }
  for(int n=0;n<6;n++){
   String id=n<selected.size()?selected.get(n):"";JSONObject u=unit(id);String content=id+"|"+(u==null?"":u.toString());
   if(!content.equals(slotContent[n])){
    final JSONObject chosen=u;
    Roster60MemberTile tile=new Roster60MemberTile(h,u,u==null?role(n):u.optString("name"),u!=null&&!locked,()->toggle(chosen),u==null?null:role(n),u!=null&&focused.equals(id),scale,1.3f);
    tile.setTag("formation-selected-"+n);
    if(n<slots.getChildCount())slots.removeViewAt(n);slots.addView(tile,n,new LayoutParams(0,-1,1));slotContent[n]=content;slotState[n]=null;slotReplacements++;
   }
  }
  String state=selected.toString()+"|"+focused+"|"+locked;
  if(!state.equals(renderedState)){
   for(int i=0;i<slots.getChildCount();i++){
    String id=i<selected.size()?selected.get(i):"";boolean active=!id.isEmpty()&&focused.equals(id),enabled=!id.isEmpty()&&!locked;
    String tuple=active+"|"+enabled;
    if(!tuple.equals(slotState[i])){((Roster60MemberTile)slots.getChildAt(i)).state(id.isEmpty()?null:role(i),active,enabled);slotState[i]=tuple;tileStateUpdates++;}
   }
   for(Map.Entry<String,Roster60MemberTile> entry:ownedTiles.entrySet()){
    String id=entry.getKey();int at=selected.indexOf(id);String role=at<0?null:role(at);boolean active=focused.equals(id);
    String tuple=role+"|"+active+"|"+locked;
    if(!tuple.equals(ownedState.get(id))){entry.getValue().state(role,active,!locked);ownedState.put(id,tuple);tileStateUpdates++;}
   }
   renderedState=state;
  }
  JSONObject u=unit(focused);
  if(u!=null){String species=u.optString("speciesId");if(!species.equals(focusSpecies)){Roster60Portrait.bind(h,focus,species);focusSpecies=species;focusBindings++;}
   text(title,u.optString("name"));int at=selected.indexOf(focused);text(level,grade(u.optString("rarity"))+" · Lv"+u.optInt("level",1)+"/"+u.optInt("maxLevel",60)+(at<0?"":" · "+role(at)));JSONObject stats=u.optJSONObject("stats");text(caption,stats==null?"편성 "+selected.size()+"/6":"HP "+stats.optInt("hp")+" · MP "+stats.optInt("mp")+"\n내구 "+stats.optInt("durability")+" · 저항 "+stats.optInt("statusResistance")+" · 속도 "+stats.optInt("speed"));
  }
  start.setEnabled(!locked&&!selected.isEmpty());
 }
 static void text(TextView view,String value){if(!value.contentEquals(view.getText()))view.setText(value);}
}
