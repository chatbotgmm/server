package org.dpdns.gmm;

import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;

/** Own candidate's currently equipped attacks only. No species attribute or opponent inference. */
final class Roster60SkillElements extends LinearLayout {
  final MainActivity h;
  final JSONObject unit;
  final BooleanSupplier current;
  android.app.AlertDialog dialog;

  static LinkedHashMap<String,ArrayList<JSONObject>> groups(JSONObject unit){
    LinkedHashMap<String,ArrayList<JSONObject>> result=new LinkedHashMap<>();
    if(unit==null)return result;
    ArrayList<JSONObject> equipped=new ArrayList<>();JSONObject attack=unit.optJSONObject("attack");if(attack!=null)equipped.add(attack);
    JSONArray skills=unit.optJSONArray("skills");for(int i=0;skills!=null&&i<skills.length();i++){JSONObject skill=skills.optJSONObject(i);if(skill!=null)equipped.add(skill);}
    HashSet<String> seen=new HashSet<>();
    for(String element:Roster60OpponentIntel.ELEMENTS)for(JSONObject skill:equipped){String id=skill.optString("id");if(element.equals(skill.optString("element"))&&!id.isEmpty()&&seen.add(id))result.computeIfAbsent(element,key->new ArrayList<>()).add(skill);}
    return result;
  }
  static String elementName(String element){for(int i=0;i<Roster60OpponentIntel.ELEMENTS.length;i++)if(Roster60OpponentIntel.ELEMENTS[i].equals(element))return Roster60OpponentIntel.ELEMENT_NAMES[i];return "";}
  static String details(JSONObject unit,String element){
    StringBuilder text=new StringBuilder();ArrayList<JSONObject> skills=groups(unit).get(element);if(skills==null)return "현재 기술 정보가 없어요.";
    int mp=unit.optInt("mp");
    for(JSONObject skill:skills){if(text.length()>0)text.append("\n");text.append(skill.optString("name","기술 정보 없음"));Object cost=skill.opt("mpCost");
      if(cost instanceof Number&&((Number)cost).doubleValue()>=0&&((Number)cost).doubleValue()==((Number)cost).intValue()){
        int amount=((Number)cost).intValue();text.append(" · MP ").append(amount);if(mp<amount)text.append(" · MP 부족 (현재 ").append(mp).append(")");
      }else text.append(" · MP 정보 없음");
    }
    return text.toString();
  }

  Roster60SkillElements(MainActivity host,JSONObject candidate,float scale,BooleanSupplier current){this(host,candidate,scale,current,false);}
  Roster60SkillElements(MainActivity host,JSONObject candidate,float scale,BooleanSupplier current,boolean compact){
    super(host);h=host;unit=candidate;this.current=current;setOrientation(HORIZONTAL);setGravity(android.view.Gravity.CENTER_VERTICAL);setTag("battle-own-skill-elements");
    if(!compact){TextView title=h.text("기술 속성",10*scale);CombatV2ReviewStyle.text(title);title.setTextColor(NavyWorkbenchTheme.TEXT);title.setSingleLine(true);addView(title,new LayoutParams(h.dp(56*scale),-2));}
    LinkedHashMap<String,ArrayList<JSONObject>> groups=groups(candidate);
    if(groups.isEmpty()){TextView missing=h.text("현재 기술 정보 없음",10*scale);missing.setTextColor(NavyWorkbenchTheme.MUTED);addView(missing,new LayoutParams(-1,-2));return;}
    HorizontalScrollView scroll=new HorizontalScrollView(h);scroll.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(h);scroll.addView(row,new HorizontalScrollView.LayoutParams(-2,-1));addView(scroll,new LayoutParams(0,-1,1));
    for(String element:groups.keySet()){
      int count=groups.get(element).size();Button button=h.button("×"+count,()->show(element));button.setTag("battle-own-element-"+element);button.setContentDescription("기술 속성 · "+elementName(element)+" "+count+"개");
      CombatV2ReviewStyle.text(button);button.setTextSize(10*scale);button.setTextColor(compact?NavyWorkbenchTheme.INK:NavyWorkbenchTheme.TEXT);button.setMinWidth(0);button.setMinimumWidth(0);button.setMinHeight(0);button.setMinimumHeight(0);button.setPadding(h.dp(3*scale),0,h.dp(3*scale),0);button.setBackground(BattleSkin.pressFeedback(h));BattleSkin.bindPressFeedback(button);
      Roster60ElementArt.bind(button,element,20*scale,false);row.addView(button,new LayoutParams(h.dp(48*scale),-1));
    }
  }
  void show(String element){if(!current.getAsBoolean())return;close();dialog=new GameDialog(h).navy().setTitle(unit.optString("name","대원")+" · 기술 속성 · "+elementName(element)).setMessage(details(unit,element)).setNegativeButton("닫기",null).show();}
  void close(){if(dialog!=null){dialog.dismiss();dialog=null;}}
  @Override protected void onDetachedFromWindow(){close();super.onDetachedFromWindow();}
}
