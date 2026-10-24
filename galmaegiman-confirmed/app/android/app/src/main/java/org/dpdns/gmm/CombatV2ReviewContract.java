package org.dpdns.gmm;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Review DTO gate, independent of Android View lifecycle. */
final class CombatV2ReviewContract {
  static final String EPOCH="review-roster60-combat-v1";
  static final String[] COMMANDS={"attack","skills","focus","item","switch","escape"};
  static JSONObject checked(JSONObject source) throws Exception {return checked(source,false);}
  static JSONObject checked(JSONObject source,boolean product) throws Exception {
    JSONObject dto=new JSONObject(source.toString());
    if(!(product?"product-five-battle-v1":EPOCH).equals(dto.getString("epoch")) || dto.getString("battleId").isEmpty()
        || dto.getInt("revision")<0)throw new IllegalArgumentException("Invalid identity/version");
    for(String side:new String[]{"self","foe"}) {
      JSONObject f=dto.getJSONObject(side);
      if(f.getInt("hp")<0||f.getInt("maxHp")<1||f.getInt("hp")>f.getInt("maxHp")||f.getInt("mp")<0||f.getInt("maxMp")<(product?0:1)||f.getInt("mp")>f.getInt("maxMp"))throw new IllegalArgumentException("Invalid vitals");
      f.getString("name");f.getJSONObject("suitability");
    }
    dto.getJSONArray("events");
    JSONObject self=dto.getJSONObject("self");
    int mp=self.getInt("mp"),max=self.getInt("maxMp");
    if(max<(product?0:1) || mp<0 || mp>max)throw new IllegalArgumentException("Invalid MP");
    JSONArray commands=dto.getJSONArray("commands"),skills=dto.getJSONArray("skills");
    if(commands.length()!=6 || skills.length()>6)throw new IllegalArgumentException("Invalid command count");
    for(int i=0;i<6;i++) {
      JSONObject c=commands.getJSONObject(i);
      if(!COMMANDS[i].equals(c.getString("kind")))throw new IllegalArgumentException("Command order");
      c.getBoolean("enabled");c.getString("label");c.getString("reason");
    }
    Set<String> seen=new HashSet<>();
    for(int i=0;i<skills.length();i++) {
      JSONObject sk=skills.getJSONObject(i);
      if(sk.getString("id").isEmpty() || !seen.add(sk.getString("id")) || sk.getInt("mpCost")<0
          || !Arrays.asList("NORMAL","FIRE","WATER","LIGHTNING","ICE","NATURE","HOLY","DARK").contains(sk.getString("element")))
        throw new IllegalArgumentException("Invalid skill");
      sk.getBoolean("enabled");sk.getString("name");sk.getString("reason");
    }
    return dto;
  }
}
