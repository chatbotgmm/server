package org.dpdns.gmm;
import android.view.*;import android.widget.*;import org.json.*;
/** Current snapshot values only; selection never submits an action. */
final class Roster60SwitchTile extends FrameLayout {
 final Button action;
 Roster60SwitchTile(MainActivity h,JSONObject unit,boolean current,boolean enabled,boolean selected,Runnable choose){
  super(h);setPadding(h.dp(3),h.dp(3),h.dp(3),h.dp(3));setBackground(new BattleSkin(h,"panel-hp"));
  LinearLayout row=new LinearLayout(h);row.setGravity(Gravity.CENTER_VERTICAL);addView(row,new LayoutParams(-1,-1));
  ImageView face=new ImageView(h);Roster60Portrait.bindFace(h,face,unit.optString("speciesId"));row.addView(face,new LinearLayout.LayoutParams(0,-1,1));
  JSONObject stats=unit.optJSONObject("stats");String maxHp=stats==null?"?":stats.optString("hp"),maxMp=stats==null?"?":stats.optString("mp");
  TextView text=h.text((selected?"✓ ":"")+unit.optString("name")+"\nHP "+unit.optInt("hp")+"/"+maxHp+"\nMP "+unit.optInt("mp")+"/"+maxMp+" · "+(current?"출전":unit.optInt("hp")<=0?"전투불능":"후보"),10);CombatV2ReviewStyle.text(text);text.setTextColor(JournalTheme.INK);text.setMaxLines(3);row.addView(text,new LinearLayout.LayoutParams(0,-2,2));
  action=h.button("",choose);action.setBackgroundColor(android.graphics.Color.TRANSPARENT);action.setEnabled(enabled);action.setContentDescription(text.getText());addView(action,new LayoutParams(-1,-1));if(!enabled)row.setAlpha(.5f);
 }
}
