package org.dpdns.gmm;
import java.util.*;
/** Explicit adapter input, NOT an assumed schema for the unread Library ZIP manifest. */
final class CombatV2IconSpec {
 static final String[] SLOTS={"normal","fire","water","lightning","ice","nature","holy","dark","attack","skills","focus","item","switch","escape"};
 static Map<String,String> checked(Map<String,String> input,String focusRevision){
  if(input==null||input.size()!=SLOTS.length||!"focus-v2".equals(focusRevision))throw new IllegalArgumentException("Approved 14-slot mapping and focus-v2 required");
  Map<String,String> out=new LinkedHashMap<>();
  for(String key:SLOTS){String path=input.get(key);
   if(path==null||!path.startsWith("combat-v2/icons/")||!path.endsWith(".png")||path.contains("..")||path.contains("\\")||path.contains(":"))throw new IllegalArgumentException("Local PNG mapping required: "+key);
   out.put(key,path);
  }
  if(!out.get("normal").equals(out.get("attack")))throw new IllegalArgumentException("Normal and attack must share the approved image");
  return Collections.unmodifiableMap(out);
 }
 static int sampleSize(int width,int height,int target){
  if(width<1||height<1||width>4096||height>4096||target<1||target>256)throw new IllegalArgumentException("Invalid icon decode bounds");
  int sample=1;while(width/(sample*2)>=target&&height/(sample*2)>=target)sample*=2;return sample;
 }
 private CombatV2IconSpec(){}
}
