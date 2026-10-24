package org.dpdns.gmm;

import android.widget.*;
import org.json.*;

/** Product authenticated API. No local award, fixture, or client completion proof. */
final class NativeRoster60Onboarding {
  final MainActivity h;
  boolean submitting;
  NativeRoster60Onboarding(MainActivity host) { h=host; }
  void open() {
    if(h.api.token().isEmpty()){h.login();return;}
    String owner=h.api.token();
    h.api.call("native/roster60/start",new JSONObject(),(r,e)->{
      if(!valid(owner))return;
      if(e!=null){h.message(h.getString(R.string.roster60_connection_failed));return;}
      JSONObject ticket=r.optJSONObject("ticket");
      new GameDialog(h).navy().setTitle(h.getString(R.string.roster60_title))
        .setMessage(h.getString(R.string.roster60_start_description))
        .setNegativeButton(h.getString(R.string.roster60_close),null)
        .setPositiveButton(h.getString(R.string.roster60_reward_status),(d,w)->{
          if(ticket!=null)preview(ticket.optString("ticketId"));
          else h.message(h.getString(R.string.roster60_tutorial_pending));
        }).show();
    });
  }
  boolean valid(String owner){return !h.isFinishing()&&!h.isDestroyed()&&owner.equals(h.api.token());}
  static JSONObject body(String... values){JSONObject b=new JSONObject();try{for(int i=0;i<values.length;i+=2)b.put(values[i],values[i+1]);}catch(JSONException impossible){throw new IllegalArgumentException(impossible);}return b;}
  void preview(String ticketId){
    String owner=h.api.token();
    h.api.call("native/roster60/preview",body("ticketId",ticketId),(r,e)->{
      if(!valid(owner))return;
      if(e!=null){h.message(h.getString(R.string.roster60_catalog_pending));return;}
      JSONObject ticket=r.optJSONObject("ticket");
      if(ticket!=null&&"used".equals(ticket.optString("status"))){h.message(h.getString(R.string.roster60_already_used));return;}
      JSONArray choices=r.optJSONArray("characters");
      if(choices==null||choices.length()!=7){h.message(h.getString(R.string.roster60_catalog_pending));return;}
      String[] names=new String[7];for(int i=0;i<7;i++)names[i]=choices.optJSONObject(i).optString("name");
      new GameDialog(h).navy().setTitle(h.getString(R.string.roster60_ticket))
        .setItems(names,(d,index)->detail(ticketId,r.optString("catalogVersion"),choices.optJSONObject(index)))
        .setNegativeButton(h.getString(R.string.roster60_cancel),null).show();
    });
  }
  void detail(String ticketId,String version,JSONObject unit){
    // Server supplied profile is shown before the only consuming action.
    StringBuilder description=new StringBuilder(h.getString(R.string.roster60_stats)).append('\n');
    JSONObject stats=unit.optJSONObject("stats");
    String[] statKeys={"hp","patk","pdef","matk","mdef","spd"};
    String[] statNames=h.getResources().getStringArray(R.array.roster60_stat_names);
    for(int i=0;i<statKeys.length;i++)if(stats!=null&&stats.has(statKeys[i]))description.append(statNames[i]).append(": ").append(stats.opt(statKeys[i])).append('\n');
    JSONArray growth=unit.optJSONArray("growth");
    if(growth!=null&&growth.optJSONObject(0)!=null)description.append(h.getString(R.string.roster60_start_mp)).append(": ").append(growth.optJSONObject(0).optInt("maxMp")).append('\n');
    description.append('\n').append(h.getString(R.string.roster60_skills)).append('\n');
    JSONArray skills=unit.optJSONArray("skills");
    if(skills!=null)for(int i=0;i<skills.length();i++){JSONObject sk=skills.optJSONObject(i);if(sk!=null)description.append(sk.optString("name")).append(" · MP ").append(sk.optInt("mpCost")).append('\n');}
    String[] elements={"NORMAL","FIRE","WATER","LIGHTNING","ICE","NATURE","HOLY","DARK"};
    String[] names=h.getResources().getStringArray(R.array.roster60_element_names);
    String[] defenseCodes={"weak","normal","resist","immune","reflect"};
    String[] defenseNames=h.getResources().getStringArray(R.array.roster60_defense_names);
    JSONObject suitability=unit.optJSONObject("suitability"),defense=unit.optJSONObject("defense");
    description.append('\n').append(h.getString(R.string.roster60_suitability)).append('\n');
    for(int i=0;i<elements.length;i++)if(suitability!=null)description.append(names[i]).append(": ").append(suitability.opt(elements[i])).append('\n');
    description.append('\n').append(h.getString(R.string.roster60_defense)).append('\n');
    for(int i=0;i<elements.length;i++)if(defense!=null){String code=defense.optString(elements[i]);for(int j=0;j<defenseCodes.length;j++)if(defenseCodes[j].equals(code))description.append(names[i]).append(": ").append(defenseNames[j]).append('\n');}
    String details=description.toString();
    TextView text=new TextView(h);text.setText(details);text.setTextColor(0xfff5ead0);text.setPadding(h.dp(16),h.dp(12),h.dp(16),h.dp(12));
    ScrollView scroll=new ScrollView(h);scroll.setBackgroundColor(0xe8102433);scroll.addView(text);
    new GameDialog(h).navy().setTitle(unit.optString("name")).setView(scroll)
      .setNegativeButton(h.getString(R.string.roster60_cancel),null)
      .setPositiveButton(h.getString(R.string.roster60_confirm),(d,w)->confirm(ticketId,version,unit.optString("id"))).show();
  }
  void confirm(String ticketId,String version,String id){
    if(submitting)return;submitting=true;String owner=h.api.token();
    h.api.call("native/roster60/confirm",body("ticketId",ticketId,"characterId",id,"catalogVersion",version),(r,e)->{
      submitting=false;if(!valid(owner))return;
      h.message(h.getString(e==null?R.string.roster60_granted:R.string.roster60_confirm_failed));
    });
  }
}
