package org.dpdns.gmm;
import java.util.*;
public final class IconSpecCheck {
 interface Attempt {void run();}
 static void reject(Attempt action){try{action.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Invalid mapping accepted");}
 public static void main(String[] args){
  Map<String,String> map=new HashMap<>();for(String key:CombatV2IconSpec.SLOTS)map.put(key,"combat-v2/icons/"+key+".png");map.put("attack",map.get("normal"));
  Map<String,String> verified=CombatV2IconSpec.checked(map,"focus-v2");map.put("focus","changed");if(!verified.get("focus").equals("combat-v2/icons/focus.png"))throw new AssertionError("Mutable mapping");
  reject(()->CombatV2IconSpec.checked(verified,"focus-v1"));
  Map<String,String> invalid=new HashMap<>(verified);invalid.put("attack","combat-v2/icons/different.png");reject(()->CombatV2IconSpec.checked(invalid,"focus-v2"));
  invalid.put("attack",invalid.get("normal"));invalid.put("focus","combat-v2/icons/../rejected.png");reject(()->CombatV2IconSpec.checked(invalid,"focus-v2"));
  for(int target:new int[]{12,24,36,48,64}){int sample=CombatV2IconSpec.sampleSize(1254,1254,target);int decoded=(1254+sample-1)/sample;if(decoded<target||decoded>target*2)throw new AssertionError("Oversized decode");}
  reject(()->CombatV2IconSpec.sampleSize(-1,1254,24));reject(()->CombatV2IconSpec.sampleSize(1254,1254,0));
  System.out.println("PASS: 14-slot immutable mapping, normal/attack alias, focus-v2 gate, local paths, sampled decode math. No approved PNG decoded.");
 }
}
