package org.dpdns.gmm;

import org.json.JSONArray;
import org.json.JSONObject;

/** 서버가 공개한 상대 정보만 표시한다. 원본 fighter의 숨은 스탯/상성으로 보충하지 않는다. */
final class Roster60OpponentIntel {
  static final String VERSION="opponent-intel-v1";
  static final String[] ELEMENTS={"NORMAL","FIRE","WATER","LIGHTNING","ICE","NATURE","HOLY","DARK"};
  static final String[] ELEMENT_NAMES={"일반","불","물","번개","얼음","자연","성","어둠"};
  static final String[] ELEMENT_ICONS={"○","♨","≈","ϟ","❄","♧","✧","☾"};
  final String name,speciesId;
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
    JSONObject known=valid?intel.optJSONObject("defenses"):null;int count=0;
    for(int i=0;i<ELEMENTS.length;i++){
      String value=known==null?"unknown":known.optString(ELEMENTS[i],"unknown");
      if(!java.util.Arrays.asList("weak","normal","resist","immune","reflect").contains(value))value="unknown";
      defenses[i]=value;if(!"unknown".equals(value))count++;
    }
    knownCount=count;
  }
  static String defenseLabel(String value){
    switch(value){case "weak":return "약점";case "normal":return "보통";case "resist":return "내성";
      case "immune":return "무효";case "reflect":return "반사";default:return "?";}
  }
  String statsNotice(){
    return changing?"새 상대를 확인하고 있어요.":completedTurns<1?"한 턴이 끝나면 상대 스탯을 볼 수 있어요.":"상대 스탯 정보가 아직 도착하지 않았어요.";
  }
}
