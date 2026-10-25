package org.dpdns.gmm;
import android.view.*;import android.widget.*;import org.json.*;import java.util.*;import java.util.function.Consumer;
/** Product party selection before a server battle; no fixture, local combat or grant. */
final class Roster60FormationView extends LinearLayout {
 final MainActivity h;final JSONArray units;final Consumer<JSONArray> confirm;final ArrayList<String> selected=new ArrayList<>();
 final LinearLayout left,right;final ImageView focus;final TextView caption;final Button start;int page;String focused="";boolean locked;
 Roster60FormationView(MainActivity h,JSONArray units,Consumer<JSONArray> confirm,Runnable cancel){
  super(h);this.h=h;this.units=units;this.confirm=confirm;setOrientation(HORIZONTAL);setTag("roster60-formation");setBackground(ThemeArt.of(h).background("backgrounds/bg-navigation-workshop-v1.png"));
  left=new LinearLayout(h);left.setOrientation(VERTICAL);left.setBackground(NavyWorkbenchTheme.surface(h,NavyWorkbenchTheme.PANEL,NavyWorkbenchTheme.LINE,8));addView(left,new LayoutParams(0,-1,3));
  right=new LinearLayout(h);right.setOrientation(VERTICAL);right.setBackground(NavyWorkbenchTheme.surface(h,NavyWorkbenchTheme.PANEL,NavyWorkbenchTheme.LINE,8));addView(right,new LayoutParams(0,-1,1));
  focus=new ImageView(h);focus.setTag("formation-focused-portrait");right.addView(focus,new LayoutParams(-1,0,1));caption=h.text("출전 1 + 예비 최대 5",12);CombatV2ReviewStyle.text(caption);caption.setTextColor(NavyWorkbenchTheme.TEXT);right.addView(caption);
  start=h.button("편성 확정 · 전투 시작",()->{if(locked||selected.isEmpty())return;submit();});start.setTag("formation-confirm");start.setTextSize(12);start.setBackground(new BattleSkin(h,"button-surrender"));CombatV2ReviewStyle.text(start);start.setTextColor(JournalTheme.INK);right.addView(start,new LayoutParams(-1,h.dp(48)));
  right.addView(h.button("돌아가기",cancel),new LayoutParams(-1,h.dp(48)));draw();
 }
 static String memberDetails(JSONObject u){String text=u.optString("name")+(u.has("level")?" · Lv"+u.optInt("level")+"/"+u.optInt("maxLevel",60):"");JSONObject stats=u.optJSONObject("stats");if(stats!=null)text+="\nHP "+stats.optInt("hp")+" · MP "+stats.optInt("mp")+"\n내구 "+stats.optInt("durability")+" · 저항 "+stats.optInt("statusResistance")+" · 속도 "+stats.optInt("speed");return text;}
 JSONObject unit(String id){for(int i=0;i<units.length();i++)if(id.equals(units.optJSONObject(i).optString("ownedId")))return units.optJSONObject(i);return null;}
 void toggle(JSONObject u){if(locked)return;String id=u.optString("ownedId");focused=id;if(selected.contains(id))selected.remove(id);else if(selected.size()<6)selected.add(id);draw();}
 void submit(){locked=true;start.setEnabled(false);confirm.accept(new JSONArray(selected));}
 void unlock(){locked=false;draw();}
 void draw(){left.removeAllViews();LinearLayout slots=new LinearLayout(h);left.addView(slots,new LayoutParams(-1,0,1));
  for(int n=0;n<6;n++){JSONObject u=n<selected.size()?unit(selected.get(n)):null;Roster60MemberTile tile=new Roster60MemberTile(h,u,(n==0?"출전":"예비 "+n)+(u==null?"":" · "+u.optString("name")),u!=null&&!locked,()->toggle(u));slots.addView(tile,new LayoutParams(0,-1,1));}
  int pages=Math.max(1,(units.length()+2)/3);page=Math.min(page,pages-1);
  for(int row=0;row<1;row++){LinearLayout line=new LinearLayout(h);left.addView(line,new LayoutParams(-1,0,1));for(int col=0;col<3;col++){int index=page*3+col;if(index>=units.length()){line.addView(new View(h),new LayoutParams(0,-1,1));continue;}JSONObject u=units.optJSONObject(index);Roster60MemberTile tile=new Roster60MemberTile(h,u,(selected.contains(u.optString("ownedId"))?"✓ ":"")+u.optString("name"),!locked,()->toggle(u));tile.action.setTag("formation-unit-"+u.optString("ownedId"));line.addView(tile,new LayoutParams(0,-1,1));}}
  if(pages>1){LinearLayout nav=new LinearLayout(h);Button prev=h.button("‹",()->{page--;draw();}),next=h.button("›",()->{page++;draw();});prev.setEnabled(page>0&&!locked);next.setEnabled(page<pages-1&&!locked);nav.addView(prev,new LayoutParams(0,h.dp(48),1));nav.addView(next,new LayoutParams(0,h.dp(48),1));left.addView(nav);}
  JSONObject u=unit(focused);if(u!=null){Roster60Portrait.bind(h,focus,u.optString("speciesId"));caption.setText(memberDetails(u)+"\n편성 "+selected.size()+"/6");}start.setEnabled(!locked&&!selected.isEmpty());
 }
}
