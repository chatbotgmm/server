package org.dpdns.gmm;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.widget.*;
import android.content.Context;
/** Existing workbench font/panel tokens; newly authored vectors, no new font or raster texture. */
final class CombatV2ReviewStyle {
 static int resource(Context c,String type,String name){return c.getResources().getIdentifier("combat_v2_"+name,type,c.getPackageName());}
 static String label(Context c,String name){return c.getString(resource(c,"string",name));}
 static String format(Context c,String name,Object... args){return c.getString(resource(c,"string",name),args);}
 static void text(TextView v){v.setIncludeFontPadding(false);v.setTextColor(0xfff5ead0);v.setTypeface(Typeface.createFromAsset(v.getContext().getAssets(),"fonts/workbench-noto-regular.otf"));}
 static void button(Button b){text(b);b.setBackgroundResource(resource(b.getContext(),"drawable","button"));}
 static void icon(Button b,String key){int id=resource(b.getContext(),"drawable",key);if(id==0)return;
  Drawable d=b.getResources().getDrawable(id);int size=(int)(12*b.getResources().getDisplayMetrics().density);d.setBounds(0,0,size,size);b.setCompoundDrawables(null,d,null,null);
 }
}
