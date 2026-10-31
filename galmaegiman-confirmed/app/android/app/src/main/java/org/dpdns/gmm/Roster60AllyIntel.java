package org.dpdns.gmm;

import org.json.JSONArray;
import org.json.JSONObject;

/** Only an authenticated own fighter's battle-local read projection. */
final class Roster60AllyIntel {
  final String name,ownedId;
  final int level,knownCount;
  final String[] defenses=new String[Roster60OpponentIntel.ELEMENTS.length];
  Roster60AllyIntel(JSONObject snapshot){this(activeFighter(snapshot),true);}
  static JSONObject activeFighter(JSONObject snapshot){
    JSONObject state=snapshot==null?null:snapshot.optJSONObject("state");
    JSONArray fighters=state==null?null:state.optJSONArray("fighters");
    return fighters==null?null:fighters.optJSONObject(0);
  }
  static Roster60AllyIntel forFighter(JSONObject own){return new Roster60AllyIntel(own,true);}
  private Roster60AllyIntel(JSONObject self,boolean own){
    name=self==null?"아군":self.optString("name","아군");
    ownedId=self==null?"":self.optString("ownedId");level=self==null?0:self.optInt("level");
    // Old servers do not provide this field. Do not infer a table from species,
    // collection observations, the enemy projection or a cached owned-unit DTO.
    JSONObject map=self==null?null:self.optJSONObject("ownResistance");int known=0;
    for(int i=0;i<defenses.length;i++){
      defenses[i]=Roster60OpponentIntel.reaction(map==null?"unknown":map.optString(Roster60OpponentIntel.ELEMENTS[i],"unknown"));
      if(!"unknown".equals(defenses[i]))known++;
    }
    knownCount=known;
  }
  String headline(){return name+(level>0?" Lv."+level:"")+" · "+(knownCount==8?"현재 전투 내성":"내성 정보 확인 중 · "+knownCount+" / 8");}
}
