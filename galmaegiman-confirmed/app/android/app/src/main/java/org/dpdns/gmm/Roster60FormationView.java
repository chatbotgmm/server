package org.dpdns.gmm;
import android.view.*;import android.widget.*;import org.json.*;import java.util.*;import java.util.function.Consumer;
/** Product party selection before a server battle; no fixture, local combat or grant. */
final class Roster60FormationView extends LinearLayout {
 final MainActivity h;final JSONArray units;final Consumer<JSONArray> confirm;final ArrayList<String> selected=new ArrayList<>();
 final LinearLayout left,right;final ImageView focus;final TextView caption,title,level;final Button start;int page;String focused="";boolean locked;
 // 휴대폰 가로(편성 영역 약 760×260dp)=1. 큰 화면에서는 글자·표지·버튼을 같은 비율로 키운다.
 float scale=1;
 Roster60FormationView(MainActivity h,JSONArray units,Consumer<JSONArray> confirm,Runnable cancel){
  super(h);this.h=h;this.units=units;this.confirm=confirm;setOrientation(HORIZONTAL);setTag("roster60-formation");setBackground(ThemeArt.of(h).background("backgrounds/bg-navigation-workshop-v1.png"));
  left=new LinearLayout(h);left.setOrientation(VERTICAL);left.setBackground(NavyWorkbenchTheme.surface(h,NavyWorkbenchTheme.PANEL,NavyWorkbenchTheme.LINE,8));addView(left,new LayoutParams(0,-1,3));
  right=new LinearLayout(h);right.setOrientation(VERTICAL);right.setBackground(NavyWorkbenchTheme.surface(h,NavyWorkbenchTheme.PANEL,NavyWorkbenchTheme.LINE,8));addView(right,new LayoutParams(0,-1,1));
  title=h.text("대원을 선택하세요",16);CombatV2ReviewStyle.text(title);title.setTextColor(NavyWorkbenchTheme.TEXT);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);right.addView(title);level=h.text("출전 1 + 예비 5",12);CombatV2ReviewStyle.text(level);level.setTextColor(NavyWorkbenchTheme.MUTED);right.addView(level);
  focus=new ImageView(h);focus.setTag("formation-focused-portrait");right.addView(focus,new LayoutParams(-1,0,1));caption=h.text("출전 1 + 예비 최대 5",12);CombatV2ReviewStyle.text(caption);caption.setTextColor(NavyWorkbenchTheme.TEXT);right.addView(caption);
  start=h.button("편성 확정 · 전투 시작",()->{if(locked||selected.isEmpty())return;submit();});start.setTag("formation-confirm");start.setTextSize(12);start.setBackground(new BattleSkin(h,"button-surrender"));CombatV2ReviewStyle.text(start);start.setTextColor(JournalTheme.INK);right.addView(start,new LayoutParams(-1,h.dp(48)));
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
 void toggle(JSONObject u){if(locked)return;String id=u.optString("ownedId");focused=id;if(selected.contains(id))selected.remove(id);else if(selected.size()<6)selected.add(id);draw();}
 void submit(){locked=true;start.setEnabled(false);confirm.accept(new JSONArray(selected));}
 void unlock(){locked=false;draw();}
 static String role(int slot){return slot==0?"출전":"예비 "+slot;}
 TextView section(String text){TextView v=h.text(text,12);CombatV2ReviewStyle.text(v);v.setTextSize(12*scale);v.setTextColor(NavyWorkbenchTheme.TEXT);v.setPadding(h.dp(8*scale),h.dp(4*scale),h.dp(8*scale),0);v.setSingleLine(true);return v;}
 void draw(){left.removeAllViews();
  // 큰 화면에서만 구역 제목을 둔다(휴대폰 가로에서는 칸 높이를 줄이지 않음).
  boolean roomy=scale>=1.25f;
  if(roomy)left.addView(section("출전 1명 + 예비 5명"));
  LinearLayout slots=new LinearLayout(h);slots.setGravity(Gravity.CENTER_VERTICAL);left.addView(slots,new LayoutParams(-1,0,1));
  for(int n=0;n<6;n++){JSONObject u=n<selected.size()?unit(selected.get(n)):null;// 칸 위치(출전·예비 n)는 왼쪽 위 표지, 아래에는 이름만 둔다(빈 칸은 위치만).
   Roster60MemberTile tile=new Roster60MemberTile(h,u,u==null?role(n):u.optString("name"),u!=null&&!locked,()->toggle(u),u==null?null:role(n),u!=null&&focused.equals(u.optString("ownedId")),scale,1.3f);slots.addView(tile,new LayoutParams(0,-1,1));}
  int pages=Math.max(1,(units.length()+3)/4);page=Math.min(page,pages-1);
  if(roomy)left.addView(section("보유 대원 "+units.length()+"명"));
  for(int row=0;row<1;row++){LinearLayout line=new Roster60SwipeRow(h,d->{if(!locked){page=Math.max(0,Math.min(pages-1,page+d));draw();}});line.setGravity(Gravity.CENTER_VERTICAL);line.setTag("formation-owned-swipe");left.addView(line,new LayoutParams(-1,0,1));for(int col=0;col<4;col++){int index=page*4+col;if(index>=units.length()){line.addView(new View(h),new LayoutParams(0,-1,1));continue;}JSONObject u=units.optJSONObject(index);String id=u.optString("ownedId");int slot=selected.indexOf(id);
   // 편성됨은 왼쪽 위 출전/예비 표지, 현재 선택은 크림 테두리와 「선택」 표지로 따로 보인다.
   Roster60MemberTile tile=new Roster60MemberTile(h,u,u.optString("name"),!locked,()->toggle(u),slot<0?null:role(slot),focused.equals(id),scale,1.15f);tile.action.setTag("formation-unit-"+id);line.addView(tile,new LayoutParams(0,-1,1));}}
  TextView indicator=h.text((page+1)+" / "+pages+" · 좌우로 넘기기",11);CombatV2ReviewStyle.text(indicator);indicator.setTextSize(11*scale);indicator.setTextColor(NavyWorkbenchTheme.MUTED);indicator.setGravity(Gravity.CENTER);indicator.setTag("formation-page");indicator.setPadding(0,h.dp(3*scale),0,h.dp(3*scale));indicator.setMinHeight(h.dp(22*scale));left.addView(indicator,new LayoutParams(-1,-2));
  title.setTextSize(16*scale);level.setTextSize(12*scale);caption.setTextSize(12*scale);start.setTextSize(12*scale);start.getLayoutParams().height=h.dp(48*scale);
  JSONObject u=unit(focused);if(u!=null){Roster60Portrait.bind(h,focus,u.optString("speciesId"));title.setText(u.optString("name"));int slot=selected.indexOf(focused);level.setText(grade(u.optString("rarity"))+" · Lv"+u.optInt("level",1)+"/"+u.optInt("maxLevel",60)+(slot<0?"":" · "+role(slot)));JSONObject st=u.optJSONObject("stats");caption.setText(st==null?"편성 "+selected.size()+"/6":"HP "+st.optInt("hp")+" · MP "+st.optInt("mp")+"\n내구 "+st.optInt("durability")+" · 저항 "+st.optInt("statusResistance")+" · 속도 "+st.optInt("speed"));}start.setEnabled(!locked&&!selected.isEmpty());
 }
}
