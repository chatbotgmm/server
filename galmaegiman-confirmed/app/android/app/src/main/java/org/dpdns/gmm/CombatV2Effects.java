package org.dpdns.gmm;
import android.content.Context;
import android.graphics.*;
import org.json.*;
/** Optional display-only common effects. No timers, model mutations or damage callbacks. */
final class CombatV2Effects {
 static void draw(Context context,Canvas c,Paint p,JSONArray events,float w,float y,float progress,boolean enabled){
  if(!enabled||progress<0||progress>=1)return;
  for(int i=0;i<events.length();i++){
   JSONObject e=events.optJSONObject(i);if(e==null)continue;
   float actor=w*(e.optInt("actor")==0?.25f:.75f),target=w*(e.optInt("target")==0?.25f:.75f);
   p.setStrokeWidth(2);p.setColor(0xffeeeeee);p.setStyle(Paint.Style.STROKE);
   if("focus".equals(e.optString("kind"))){
    p.setColor(0xff75bfd8);c.drawCircle(actor,y,12+18*progress,p);c.drawLine(actor-8,y,actor+8,y,p);c.drawLine(actor,y-8,actor,y+8,p);
    p.setStyle(Paint.Style.FILL);c.drawText(CombatV2ReviewStyle.label(context,"recover"),actor-18,y-30,p);continue;
   }
   // Cast on actor; immunity may show casting but never a successful impact/hit.
   c.drawLine(actor-9,y-32,actor+9,y-32,p);c.drawLine(actor,y-41,actor,y-23,p);
   if(e.optInt("damage")>0){
    if("reflect".equals(e.optString("defense"))){float from=w-actor;float direction=Math.signum(target-from);c.drawLine(from,y,target,y,p);c.drawLine(target,y,target-direction*10,y-7,p);c.drawLine(target,y,target-direction*10,y+7,p);}
    c.drawCircle(target,y,10+22*progress,p);c.drawLine(target-9,y-9,target+9,y+9,p);c.drawLine(target-9,y+9,target+9,y-9,p);
   }
   p.setStyle(Paint.Style.FILL);String defense=e.optString("defense","normal");
   c.drawText(CombatV2ReviewStyle.label(context,"badge_"+defense),target-28,y-48,p);
  }p.setStyle(Paint.Style.FILL);
 }
}
