package org.dpdns.gmm;
import org.json.*;
/** Manifest rects are exclusive. No midpoint splitting, legacy ID conversion or approval inference. */
final class CombatV2ArtCatalog {
 static JSONObject view(JSONObject manifest,String id,String facing)throws Exception {
  if(!facing.equals("front")&&!facing.equals("rear"))throw new IllegalArgumentException("view");
  JSONArray entries=manifest.getJSONArray("entries");
  for(int i=0;i<entries.length();i++)if(id.equals(entries.getJSONObject(i).getString("id"))){
   JSONObject v=new JSONObject(entries.getJSONObject(i).getJSONObject("active_views").getJSONObject(facing).toString());
   String path=v.getString("file");if(path.startsWith("/")||path.contains(".."))throw new IllegalArgumentException("path");
   JSONArray r=v.getJSONArray("source_rect_ltrb_exclusive");
   if(r.length()!=4||r.getInt(0)<0||r.getInt(1)<0||r.getInt(2)<=r.getInt(0)||r.getInt(3)<=r.getInt(1))throw new IllegalArgumentException("rect");
   return v;
  }throw new IllegalArgumentException("Unknown roster ID");
 }
 static float relativeHeight(String id){return "u02".equals(id)?.6f:1f;} // Others require individual calibration; 1 is preview only.
}
