package org.dpdns.gmm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Display groups only. A party still consists of individual owned identifiers. */
final class NativeOwnedRosterGroups {
  private NativeOwnedRosterGroups() {}

  static final class Group {
    /** Internal view key; never submit this value as an owned identifier. */
    final String key, speciesId, name;
    final List<JSONObject> members;
    final int highestLevel;
    private final String identityField;

    private Group(String key, String speciesId, String identityField, List<JSONObject> records) {
      this.key=key;this.speciesId=speciesId;this.identityField=identityField;
      ArrayList<JSONObject> sorted=new ArrayList<>(records);
      sorted.sort((a,b)->Integer.compare(level(b),level(a)));
      members=Collections.unmodifiableList(sorted);
      highestLevel=level(representative());
      name=representative().optString("name",speciesId.isEmpty()?"대원":speciesId);
    }
    JSONObject representative(){return members.get(0);}
    int count(){return members.size();}
    String summary(){return "최고 Lv."+highestLevel+" · 보유 "+count()+"명";}
    /** Sorted members for an expanded picker, not a ready-made party selection. */
    JSONArray ownedIds(){JSONArray ids=new JSONArray();for(JSONObject member:members)ids.put(identity(member,identityField));return ids;}
  }

  static ArrayList<Group> group(JSONArray source){return group(source,"ownedId","speciesId");}

  /** Legacy story uses key/id; its aggregate PvP catalogue already has one row per species. */
  static ArrayList<Group> group(JSONArray source,String identityField,String speciesField){
    ArrayList<Group> result=new ArrayList<>();
    if(source==null||identityField==null||identityField.isEmpty())return result;
    HashSet<String> seen=new HashSet<>();
    LinkedHashMap<String,ArrayList<JSONObject>> records=new LinkedHashMap<>();
    LinkedHashMap<String,String> species=new LinkedHashMap<>();
    for(int i=0;i<source.length();i++){
      JSONObject unit=source.optJSONObject(i);if(unit==null)continue;
      String ownedId=identity(unit,identityField);if(ownedId.isEmpty()||!seen.add(ownedId))continue;
      String speciesId=identity(unit,speciesField);
      // Unknown species remain individually selectable instead of being merged into one blank group.
      String key=speciesId.isEmpty()?"owned:"+ownedId:"species:"+speciesId;
      if(!records.containsKey(key)){records.put(key,new ArrayList<>());species.put(key,speciesId);}
      records.get(key).add(unit);
    }
    for(String key:records.keySet())result.add(new Group(key,species.get(key),identityField,records.get(key)));
    // Stable sorting preserves the first species occurrence when highest levels tie.
    result.sort((a,b)->Integer.compare(b.highestLevel,a.highestLevel));
    return result;
  }

  static int level(JSONObject unit){return Math.max(0,unit.optInt("level",0));}
  private static String identity(JSONObject unit,String field){Object value=field==null?null:unit.opt(field);return value instanceof String?(String)value:"";}
}
