package org.dpdns.gmm;
import android.view.*;import android.widget.*;import org.json.*;import java.util.*;import java.util.function.Consumer;
/** Product party selection before a server battle; no fixture, local combat or grant. */
final class Roster60FormationView extends LinearLayout {
 final MainActivity h;final JSONArray units;final Consumer<JSONArray> confirm;final ArrayList<String> selected=new ArrayList<>();
 final LinearLayout left,right;final ImageView focus;final TextView caption,title,level;final Button start;int page;String focused="";boolean locked;
 Roster60FormationView(MainActivity h,JSONArray units,Consumer<JSONArray> confirm,Runnable cancel){
  super(h);this.h=h;this.units=units;this.confirm=confirm;setOrientation(HORIZONTAL);setTag("roster60-formation");setBackground(ThemeArt.of(h).background("backgrounds/bg-navigation-workshop-v1.png"));
  left=new LinearLayout(h);left.setOrientation(VERTICAL);left.setBackground(NavyWorkbenchTheme.surface(h,NavyWorkbenchTheme.PANEL,NavyWorkbenchTheme.LINE,8));addView(left,new LayoutParams(0,-1,3));
  right=new LinearLayout(h);right.setOrientation(VERTICAL);right.setBackground(NavyWorkbenchTheme.surface(h,NavyWorkbenchTheme.PANEL,NavyWorkbenchTheme.LINE,8));addView(right,new LayoutParams(0,-1,1));
  title=h.text("대원을 선택하세요",16);CombatV2ReviewStyle.text(title);title.setTextColor(NavyWorkbenchTheme.TEXT);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);right.addView(title);level=h.text("출전 1 + 예비 5",12);CombatV2ReviewStyle.text(level);level.setTextColor(NavyWorkbenchTheme.MUTED);right.addView(level);
  focus=new ImageView(h);focus.setTag("formation-focused-portrait");right.addView(focus,new LayoutParams(-1,0,1));caption=h.text("출전 1 + 예비 최대 5",12);CombatV2ReviewStyle.text(caption);caption.setTextColor(NavyWorkbenchTheme.TEXT);right.addView(caption);
  start=h.button("편성 확정 · 전투 시작",()->{if(locked||selected.isEmpty())return;submit();});start.setTag("formation-confirm");start.setTextSize(12);start.setBackground(new BattleSkin(h,"button-surrender"));CombatV2ReviewStyle.text(start);start.setTextColor(JournalTheme.INK);right.addView(start,new LayoutParams(-1,h.dp(48)));
  if(units.length()>0)focused=units.optJSONObject(0).optString("ownedId");draw();
 }
 static String grade(String grade){switch(grade){case "COMMON":return "커먼";case "UNCOMMON":return "언커먼";case "RARE":return "레어";case "EPIC":return "에픽";case "LEGEND":return "전설";case "HIDDEN":return "히든";case "LIMITED":return "제한";case "TRANSCENDENT":return "초월";case "IMMORTAL":return "불멸";case "ETERNAL":return "영원";default:return grade;}}
 static String memberDetails(JSONObject u){String text=u.optString("name")+(u.has("level")?" · Lv"+u.optInt("level")+"/"+u.optInt("maxLevel",60):"");JSONObject stats=u.optJSONObject("stats");if(stats!=null)text+="\nHP "+stats.optInt("hp")+" · MP "+stats.optInt("mp")+"\n내구 "+stats.optInt("durability")+" · 저항 "+stats.optInt("statusResistance")+" · 속도 "+stats.optInt("speed");return text;}
 JSONObject unit(String id){for(int i=0;i<units.length();i++)if(id.equals(units.optJSONObject(i).optString("ownedId")))return units.optJSONObject(i);return null;}
 void toggle(JSONObject u){if(locked)return;String id=u.optString("ownedId");focused=id;if(selected.contains(id))selected.remove(id);else if(selected.size()<6)selected.add(id);draw();}
 void submit(){locked=true;start.setEnabled(false);confirm.accept(new JSONArray(selected));}
 void unlock(){locked=false;draw();}
 void draw(){left.removeAllViews();LinearLayout slots=new LinearLayout(h);left.addView(slots,new LayoutParams(-1,0,1));
  for(int n=0;n<6;n++){JSONObject u=n<selected.size()?unit(selected.get(n)):null;Roster60MemberTile tile=new Roster60MemberTile(h,u,(n==0?"출전":"예비 "+n)+(u==null?"":" · "+u.optString("name")),u!=null&&!locked,()->toggle(u));slots.addView(tile,new LayoutParams(0,-1,1));}
  int pages=Math.max(1,(units.length()+3)/4);page=Math.min(page,pages-1);
  for(int row=0;row<1;row++){LinearLayout line=new Roster60SwipeRow(h,d->{if(!locked){page=Math.max(0,Math.min(pages-1,page+d));draw();}});line.setTag("formation-owned-swipe");left.addView(line,new LayoutParams(-1,0,1));for(int col=0;col<4;col++){int index=page*4+col;if(index>=units.length()){line.addView(new View(h),new LayoutParams(0,-1,1));continue;}JSONObject u=units.optJSONObject(index);Roster60MemberTile tile=new Roster60MemberTile(h,u,(selected.contains(u.optString("ownedId"))?"✓ ":"")+u.optString("name"),!locked,()->toggle(u));tile.action.setTag("formation-unit-"+u.optString("ownedId"));line.addView(tile,new LayoutParams(0,-1,1));}}
  TextView indicator=h.text((page+1)+" / "+pages+" · 좌우로 넘기기",11);CombatV2ReviewStyle.text(indicator);indicator.setTextColor(NavyWorkbenchTheme.MUTED);indicator.setGravity(Gravity.CENTER);indicator.setTag("formation-page");left.addView(indicator,new LayoutParams(-1,h.dp(22)));
  JSONObject u=unit(focused);if(u!=null){Roster60Portrait.bind(h,focus,u.optString("speciesId"));title.setText(u.optString("name"));level.setText(grade(u.optString("rarity"))+" · Lv"+u.optInt("level",1)+"/"+u.optInt("maxLevel",60));JSONObject st=u.optJSONObject("stats");caption.setText(st==null?"편성 "+selected.size()+"/6":"HP "+st.optInt("hp")+" · MP "+st.optInt("mp")+"\n내구 "+st.optInt("durability")+" · 저항 "+st.optInt("statusResistance")+" · 속도 "+st.optInt("speed"));}start.setEnabled(!locked&&!selected.isEmpty());
 }
}
