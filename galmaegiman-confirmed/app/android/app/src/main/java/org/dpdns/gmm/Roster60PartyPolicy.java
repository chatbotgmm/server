package org.dpdns.gmm;

import java.util.HashSet;
import org.json.JSONArray;
import org.json.JSONObject;

/** New party admission policy only. Active battle/adventure snapshots keep their own members. */
final class Roster60PartyPolicy {
  static final String VERSION="single-active-party-3-v1";
  static final String RESELECT="PARTY_RESELECTION_REQUIRED";
  static final int LEGACY_MAX=6;
  final boolean ready,selectionRequired;
  final int minParty,maxParty;
  private Roster60PartyPolicy(boolean ready,boolean required,int min,int max){this.ready=ready;selectionRequired=required;minParty=min;maxParty=max;}
  static Roster60PartyPolicy current(){return new Roster60PartyPolicy(true,false,1,3);}
  static Roster60PartyPolicy missing(){return new Roster60PartyPolicy(false,false,1,3);}
  static boolean number(JSONObject source,String key,int expected){Object value=source==null?null:source.opt(key);return value instanceof Number&&((Number)value).doubleValue()==expected;}
  static Roster60PartyPolicy read(JSONObject response,boolean selection){
    JSONObject policy=response==null?null:response.optJSONObject("partyPolicy");
    boolean valid=policy!=null&&VERSION.equals(policy.optString("version"))&&"up-to-max".equals(policy.optString("sizeRule"))
        &&number(policy,"activeSlots",1)&&number(policy,"minParty",1)&&number(policy,"maxParty",3)
        &&number(response,"minParty",1)&&number(response,"maxParty",3);
    Object required=response==null?null:response.opt("partySelectionRequired"),condition=response==null?null:response.opt("partyCondition");
    if(selection)valid&=required instanceof Boolean&&response.has("partyCondition")
        &&(Boolean.TRUE.equals(required)?RESELECT.equals(condition):condition==JSONObject.NULL);
    return valid?new Roster60PartyPolicy(true,Boolean.TRUE.equals(required),1,3):missing();
  }
  boolean allows(JSONArray ids,JSONArray units){return ready&&members(ids,units,minParty,maxParty);}
  static boolean members(JSONArray ids,JSONArray units,int minimum,int maximum){
    if(ids==null||units==null||ids.length()<minimum||ids.length()>maximum)return false;
    HashSet<String> available=new HashSet<>(),unique=new HashSet<>();
    for(int i=0;i<units.length();i++){JSONObject unit=units.optJSONObject(i);if(unit!=null&&!unit.optString("ownedId").isEmpty())available.add(unit.optString("ownedId"));}
    for(int i=0;i<ids.length();i++){if(!(ids.opt(i) instanceof String))return false;String id=ids.optString(i);if(id.isEmpty()||!available.contains(id)||!unique.add(id))return false;}
    return true;
  }
  int visibleSlots(int selected){return Math.min(LEGACY_MAX,Math.max(maxParty,selected));}
  static boolean reselection(String error){return RESELECT.equals(error);}
  static String selectionMessage(){return "새 편성은 최대 3마리예요. 편성대에서 출전 대원을 다시 선택해 주세요.";}
  static String compatibilityMessage(){return "서버 편성 규칙을 확인하지 못했어요. 앱과 서버 업데이트를 확인해 주세요.";}
}
