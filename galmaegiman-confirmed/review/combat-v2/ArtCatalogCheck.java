package org.dpdns.gmm;
import java.nio.file.*;
import org.json.*;
/** Real manifest -> exact Android rect contract, no Android render claim. */
public final class ArtCatalogCheck {
 public static void main(String[] args)throws Exception{
  JSONObject m=new JSONObject(Files.readString(Path.of(args[0])));int count=0;
  for(int i=1;i<=60;i++)for(String facing:new String[]{"front","rear"}){CombatV2ArtCatalog.view(m,String.format("u%02d",i),facing);count++;}
  if(!CombatV2ArtCatalog.view(m,"u17","rear").getString("file").contains("rear-v4"))throw new AssertionError();
  if(CombatV2ArtCatalog.view(m,"u31","front").getJSONArray("source_rect_ltrb_exclusive").getInt(2)!=906)throw new AssertionError();
  if(CombatV2ArtCatalog.relativeHeight("u02")!=.6f)throw new AssertionError();
  try{CombatV2ArtCatalog.view(m,"C1","front");throw new AssertionError("Legacy ID mapped");}catch(IllegalArgumentException expected){}
  System.out.println("PASS: Android catalog "+count+" active views, u17 override, u31 asymmetric v6 rect, mini 60%, no legacy mapping.");
 }
}
