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
 /** 서버 기술 속성만 표시한다. 모르는 속성을 일반 속성으로 대신 표시하지 않는다. */
 static void skillIcon(Button b,String element,float scale){
  b.setCompoundDrawables(null,null,null,null);
  String key=element==null?"":element.toLowerCase(java.util.Locale.ROOT);
  switch(key){
   case "normal":case "fire":case "water":case "lightning":case "ice":case "nature":case "holy":case "dark":break;
   default:return;
  }
  int id=resource(b.getContext(),"drawable",key);if(id==0)return;
  float density=b.getResources().getDisplayMetrics().density;
  Drawable d=b.getResources().getDrawable(id).mutate();int size=Math.round(18*scale*density);
  d.setBounds(0,0,size,size);b.setCompoundDrawables(d,null,null,null);
  b.setCompoundDrawablePadding(Math.round(3*scale*density));
  b.setPadding(Math.round(6*scale*density),Math.round(2*density),Math.round(6*scale*density),Math.round(2*density));
 }
}
