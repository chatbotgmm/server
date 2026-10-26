package org.dpdns.gmm;

import org.json.JSONArray;
import org.json.JSONObject;

/** Reads only the server's public attack-observation projection; never reconstructs a defense table. */
final class Roster60OpponentIntel {
  static final String VERSION="opponent-intel-v2", COLLECTION_VERSION="observed-defense-v1";
  static final String[] ELEMENTS={"NORMAL","FIRE","WATER","LIGHTNING","ICE","NATURE","HOLY","DARK"};
  static final String[] ELEMENT_NAMES={"일반","불","물","번개","얼음","자연","성","어둠"};
  final String name,speciesId,defenseKnowledgeScope;
  final int level,completedTurns,knownCount;
  final boolean statsKnown,changing;
  final int[] stats=new int[5];
  final String[] defenses=new String[8];

  Roster60OpponentIntel(JSONObject displayed,boolean changing) {
    this.changing=changing;
    JSONObject state=displayed==null?null:displayed.optJSONObject("state");
    JSONArray fighters=state==null?null:state.optJSONArray("fighters");
    JSONObject foe=fighters==null?null:fighters.optJSONObject(1);
    speciesId=foe==null?"":foe.optString("speciesId");
    name=changing?"상대 교체 확인 중":foe==null?"상대 정보":foe.optString("name","상대");
    level=changing||foe==null?0:foe.optInt("level");
    JSONObject intel=state==null?null:state.optJSONObject("opponentIntel");
    boolean valid=!changing&&intel!=null&&VERSION.equals(intel.optString("version"))
        &&!speciesId.isEmpty()&&speciesId.equals(intel.optString("speciesId"));
    completedTurns=valid?Math.max(0,intel.optInt("completedTurns")):0;
    JSONObject disclosed=valid?intel.optJSONObject("stats"):null;
    String[] keys={"hp","mp","durability","statusResistance","speed"};
    boolean usable=valid&&completedTurns>=1&&intel.optBoolean("statsKnown")&&disclosed!=null;
    for(int i=0;i<keys.length;i++){
      Object raw=disclosed==null?null:disclosed.opt(keys[i]);
      double n=raw instanceof Number?((Number)raw).doubleValue():-1;
      if(!Double.isFinite(n)||n<0||n>Integer.MAX_VALUE||n!=Math.floor(n))usable=false;
      stats[i]=n>=0&&n<=Integer.MAX_VALUE?(int)n:0;
    }
    statsKnown=usable;
    defenseKnowledgeScope=valid?intel.optString("defenseKnowledgeScope","unverified"):"unverified";
    boolean observed="base-observed".equals(defenseKnowledgeScope)||"battle-observed".equals(defenseKnowledgeScope);
    JSONObject known=valid&&observed?intel.optJSONObject("defenses"):null;int count=0;
    for(int i=0;i<ELEMENTS.length;i++){
      defenses[i]=reaction(known==null?"unknown":known.optString(ELEMENTS[i],"unknown"));
      if(!"unknown".equals(defenses[i]))count++;
    }
    knownCount=count;
  }

  static String reaction(String value){
    return java.util.Arrays.asList("weak","normal","resist","immune","reflect").contains(value)?value:"unknown";
  }

  /** Collection knowledge belongs to this account's server response and this exact species. */
  static String[] collectionDefenses(JSONObject species){
    JSONObject knowledge=species==null?null:species.optJSONObject("defenseKnowledge");
    boolean valid=knowledge!=null&&COLLECTION_VERSION.equals(knowledge.optString("version"))
        &&!species.optString("id").isEmpty()&&species.optString("id").equals(knowledge.optString("speciesId"));
    JSONObject map=valid?knowledge.optJSONObject("defenses"):null;
    String[] values=new String[ELEMENTS.length];
    for(int i=0;i<values.length;i++)values[i]=reaction(map==null?"unknown":map.optString(ELEMENTS[i],"unknown"));
    return values;
  }

  static String defenseLabel(String value){
    switch(value){case "weak":return "약점";case "normal":return "보통";case "resist":return "내성";
      case "immune":return "무효";case "reflect":return "반사";default:return "?";}
  }
  String statsNotice(){
    return changing?"새 상대를 확인하고 있어요.":completedTurns<1?"한 턴 뒤 스탯 공개":"스탯 정보 확인 중";
  }
}
