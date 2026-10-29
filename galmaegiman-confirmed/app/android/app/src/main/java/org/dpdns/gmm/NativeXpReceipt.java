package org.dpdns.gmm;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Presentation of committed server receipts. This class never awards or predicts growth. */
final class NativeXpReceipt {
  static final class Point {
    final int level,maxLevel;
    final BigInteger total,start,next;
    Point(int level,int maxLevel,BigInteger total,BigInteger start,BigInteger next){this.level=level;this.maxLevel=maxLevel;this.total=total;this.start=start;this.next=next;}
    static Point read(JSONObject raw){
      Long level=amount(raw,"level"),max=amount(raw,"maxLevel");BigInteger xp=total(raw,"totalXp"),start=total(raw,"levelStartTotalXp"),next=total(raw,"nextLevelTotalXp");
      if(raw==null||level==null||level<1||level>1000||max==null||max<level||max>1000||xp==null||start==null||start.compareTo(xp)>0)return null;
      if(!raw.has("nextLevelTotalXp")||next==null&&!raw.isNull("nextLevelTotalXp")||next!=null&&(next.compareTo(xp)<=0||next.compareTo(start)<=0))return null;
      return new Point(level.intValue(),max.intValue(),xp,start,next);
    }
    int progress(){return next==null?1000:total.subtract(start).multiply(BigInteger.valueOf(1000)).divide(next.subtract(start)).min(BigInteger.valueOf(1000)).max(BigInteger.ZERO).intValue();}
  }
  static final class Row {
    final String label;
    final long amount;
    BigInteger before, after;
    final BigInteger target;
    int level, previousLevel;
    Point progressBefore,progressAfter;
    final boolean pending;
    Row(String label,long amount,BigInteger before,BigInteger after,BigInteger target,
        int level,int previousLevel,boolean pending) {
      this.label=label;this.amount=amount;this.before=before;this.after=after;this.target=target;
      this.level=level;this.previousLevel=previousLevel;this.pending=pending;
    }
    boolean hasBar(){return progressBefore!=null&&progressAfter!=null||before!=null&&after!=null&&target!=null&&target.signum()>0;}
    boolean levelUp(){return previousLevel>0&&level>previousLevel;}
    Row progression(JSONObject raw){
      if(pending||raw==null)return this;Point first=Point.read(raw.optJSONObject("before")),last=Point.read(raw.optJSONObject("after"));
      if(first==null||last==null||first.level>last.level||first.maxLevel!=last.maxLevel
          ||!last.total.subtract(first.total).equals(BigInteger.valueOf(amount))
          ||before!=null&&!before.equals(first.total)||after!=null&&!after.equals(last.total)
          ||level>0&&level!=last.level)return this;
      before=first.total;after=last.total;level=last.level;previousLevel=first.level;progressBefore=first;progressAfter=last;return this;
    }
    int progress(BigInteger total){
      if(!hasBar()||total==null)return 0;
      return total.multiply(BigInteger.valueOf(1000)).divide(target).min(BigInteger.valueOf(1000)).max(BigInteger.ZERO).intValue();
    }
  }
  final String event;
  final List<Row> rows=new ArrayList<>();
  boolean replayed;
  private NativeXpReceipt(String event){this.event=event;}
  boolean positive(){for(Row row:rows)if(!row.pending&&row.amount>0)return true;return false;}

  static Long amount(JSONObject value,String key){
    Object raw=value==null?null:value.opt(key);
    if(!(raw instanceof Number))return null;
    Number number=(Number)raw;
    return number.longValue()>=0&&number.doubleValue()==number.longValue()?number.longValue():null;
  }
  static BigInteger total(JSONObject value,String key){
    Object raw=value==null?null:value.opt(key);
    if(!(raw instanceof String)||!((String)raw).matches("[0-9]{1,19}"))return null;
    BigInteger total=new BigInteger((String)raw);
    return total.compareTo(BigInteger.valueOf(Long.MAX_VALUE))<=0?total:null;
  }
  static NativeXpReceipt from(JSONObject snapshot,JSONObject commanderBefore){
    if(snapshot==null)return new NativeXpReceipt("");
    String battle=snapshot.optString("battleId"),adventure=snapshot.optString("adventureId");
    String event=!battle.isEmpty()?"battle:"+battle:!adventure.isEmpty()?"adventure:"+adventure:"";
    JSONObject directCommander=snapshot.optJSONObject("commanderReward"),commander=directCommander==null?null:directCommander.optJSONObject("commander");
    Long commanderRevision=amount(commander,"revision");
    if(event.isEmpty()&&commander!=null&&commanderRevision!=null&&!commander.optString("policyVersion").isEmpty())event="commander:"+commander.optString("policyVersion")+":"+commanderRevision;
    NativeXpReceipt receipt=new NativeXpReceipt(event);
    if(!java.util.Arrays.asList("FINISHED","SETTLED","RETURNED").contains(snapshot.optString("status"))&&directCommander==null)return receipt;
    JSONObject settlement=snapshot.optJSONObject("settlement");
    if(settlement==null&&directCommander!=null)settlement=MainActivity.obj("commanderReward",directCommander);
    if(settlement==null)return receipt;
    receipt.commander(settlement.optJSONObject("commanderReward"),commanderBefore);
    Map<String,String> names=names(snapshot.optJSONObject("state"));names.putAll(names(snapshot));
    JSONObject unitXp=settlement.optJSONObject("unitXp");
    if(unitXp!=null){
      boolean pending="pending-return".equals(unitXp.optString("delivery"));
      if(!pending&&!"immediate".equals(unitXp.optString("delivery")))return receipt;
      JSONArray units=unitXp.optJSONArray("units");java.util.HashSet<String> seen=new java.util.HashSet<>();
      for(int i=0;units!=null&&i<units.length();i++){
        JSONObject unit=units.optJSONObject(i);String id=unit==null?"":unit.optString("ownedId");
        Long applied=amount(unit,"appliedXp");if(id.isEmpty()||!seen.add(id)||applied==null)continue;
        BigInteger before=total(unit,"startingXp"),after=total(unit,"endingXp");
        if(before==null||after==null||!after.subtract(before).equals(BigInteger.valueOf(applied))){before=null;after=null;}
        if(pending){before=null;after=null;}
        receipt.rows.add(new Row(names.getOrDefault(id,"대원 "+(i+1)),applied,before,after,after,0,0,pending).progression(unit.optJSONObject("xpProgress")));
      }
      return receipt;
    }
    JSONObject normal=settlement.optJSONObject("normalXp");
    if(normal!=null){
      JSONArray units=normal.optJSONArray("units");java.util.HashSet<String> seen=new java.util.HashSet<>();
      for(int i=0;units!=null&&i<units.length();i++){
        JSONObject unit=units.optJSONObject(i);String id=unit==null?"":unit.optString("ownedId");Long applied=amount(unit,"appliedXp");
        if(id.isEmpty()||!seen.add(id)||applied==null)continue;
        receipt.rows.add(new Row(names.getOrDefault(id,"대원 "+(i+1)),applied,null,null,null,0,0,false).progression(unit.optJSONObject("xpProgress")));
      }
      return receipt;
    }
    JSONObject rewards=settlement.optJSONObject("rewards"),xp=rewards==null?null:rewards.optJSONObject("xp");
    if(xp!=null){
      java.util.Iterator<String> keys=xp.keys();int n=0;JSONObject progress=settlement.optJSONObject("xpProgressByOwnedId");
      while(keys.hasNext()){String id=keys.next();Long awarded=amount(xp,id);if(awarded!=null)receipt.rows.add(new Row(names.getOrDefault(id,"대원 "+(++n)),awarded,null,null,null,0,0,false).progression(progress==null?null:progress.optJSONObject(id)));}
      return receipt;
    }
    // Legacy one-unit receipt exposes final total and the applied amount, not the initial member's identity.
    Long awarded=amount(settlement,"xpAwarded");BigInteger after=total(settlement,"totalXp");
    if(awarded!=null&&!settlement.has("adventureStatus")){
      BigInteger before=after==null?null:after.subtract(BigInteger.valueOf(awarded));
      if(before!=null&&before.signum()<0){before=null;after=null;}
      receipt.rows.add(new Row("대원 경험치",awarded,before,after,after,0,0,false).progression(settlement.optJSONObject("xpProgress")));
    }
    return receipt;
  }
  private void commander(JSONObject reward,JSONObject baseline){
    if(reward==null||!java.util.Arrays.asList("APPLIED","RECORDED_ZERO").contains(reward.optString("status")))return;
    Long recorded=amount(reward,"recordedXp"),awarded=amount(reward,"xpAwarded");
    if(recorded==null||awarded==null)return;
    replayed=reward.optBoolean("replayed");
    if(!replayed&&!recorded.equals(awarded))return;
    JSONObject state=reward.optJSONObject("commander");
    BigInteger after=total(state,"totalXp"),before=after==null?null:after.subtract(BigInteger.valueOf(recorded));
    if(before!=null&&before.signum()<0){before=null;after=null;}
    BigInteger target=total(state,"nextLevelXp");
    if(target!=null&&(after==null||target.compareTo(after)<=0))target=null;
    Long level=amount(state,"level");int currentLevel=level!=null&&level>0&&level<=1000?level.intValue():0,previous=0;
    // A known consecutive authoritative revision can prove a level transition. No growth curve is inferred.
    Long oldRevision=amount(baseline,"revision"),revision=amount(state,"revision"),oldLevel=amount(baseline,"level");
    BigInteger oldTotal=total(baseline,"totalXp");
    if(!replayed&&recorded>0&&baseline!=null&&state!=null&&oldRevision!=null&&revision!=null
        &&oldRevision<Long.MAX_VALUE&&revision==oldRevision+1&&oldTotal!=null&&oldTotal.equals(before)
        &&oldLevel!=null&&oldLevel>0&&oldLevel<=currentLevel
        &&!state.optString("policyVersion").isEmpty()
        &&state.optString("policyVersion").equals(baseline.optString("policyVersion")))previous=oldLevel.intValue();
    Row row=new Row("방범대장",recorded,before,after,target==null?after:target,currentLevel,previous,false).progression(reward.optJSONObject("xpProgress"));
    if(replayed)row.previousLevel=0;
    rows.add(row);
  }
  private static Map<String,String> names(JSONObject state){
    Map<String,String> names=new HashMap<>();if(state==null)return names;
    JSONArray fighters=state.optJSONArray("fighters");if(fighters!=null)name(names,fighters.optJSONObject(0));
    JSONArray reserves=state.optJSONArray("reserves");for(int i=0;reserves!=null&&i<reserves.length();i++)name(names,reserves.optJSONObject(i));
    JSONArray party=state.optJSONArray("party");for(int i=0;party!=null&&i<party.length();i++)name(names,party.optJSONObject(i));
    return names;
  }
  private static void name(Map<String,String> names,JSONObject unit){if(unit!=null&&!unit.optString("ownedId").isEmpty()&&!unit.optString("name").isEmpty())names.put(unit.optString("ownedId"),unit.optString("name"));}
}
